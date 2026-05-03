package br.com.arxcode.tematica.geo.cli;

import br.com.arxcode.tematica.geo.catalogo.ExistenciaRepository;
import br.com.arxcode.tematica.geo.config.ImportacaoConfig;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.excecao.MapeamentoIoException;
import br.com.arxcode.tematica.geo.excel.ExcelLeitor;
import br.com.arxcode.tematica.geo.excel.ExcelSessao;
import br.com.arxcode.tematica.geo.geracao.Coercionador;
import br.com.arxcode.tematica.geo.geracao.LogErros;
import br.com.arxcode.tematica.geo.geracao.ResultadoUpdate;
import br.com.arxcode.tematica.geo.geracao.ResultadoUpsert;
import br.com.arxcode.tematica.geo.geracao.SqlGeradorUpdate;
import br.com.arxcode.tematica.geo.geracao.SqlGeradorUpsert;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoValidador;
import br.com.arxcode.tematica.geo.mapeamento.ResultadoValidacao;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Subcomando {@code importar} — orquestra as Fases 3/4/5 do pipeline de importação
 * (AC3): valida arquivo, conexão e mapeamento; itera linhas da planilha; gera os
 * artefatos {@code .sql} e {@code .log} no diretório configurado.
 *
 * <p><strong>Pipeline (5 fases sequenciais — fail-fast em qualquer falha de infra):</strong>
 * <ol>
 *   <li>Valida arquivo — existência + extensão {@code .xlsx} (sem conexão ao banco).</li>
 *   <li>Valida conexão — {@code getConnection()} + close imediato.</li>
 *   <li>Carrega mapeamento — {@link MapeamentoStore#carregar(Path)}.</li>
 *   <li>Gate PENDENTE — recusa execução se houver colunas {@link StatusMapeamento#PENDENTE}.</li>
 *   <li>Iteração e geração — streaming de linhas, UPDATE + UPSERT por linha, gravação de
 *       artefatos {@code .sql} e {@code .log}.</li>
 * </ol>
 *
 * <p><strong>Exit codes</strong> (FR-16): {@code 0} sem erros; {@code 2} com ≥ 1 linha
 * com erro ou pulada (artefatos gravados); {@code 1} em qualquer falha de infra
 * (Fases 1–4 ou {@link IOException} ao gravar artefatos).
 *
 * <p><strong>Invariante CON-02:</strong> o {@code .sql} gerado nunca contém
 * {@code INSERT INTO tribcadastroimobiliario} nem {@code INSERT INTO tribimobiliariosegmento}
 * — garantia arquitetural dos geradores 4.2/4.3 ({@link SqlGeradorUpdate} gera apenas
 * {@code UPDATE}; {@link SqlGeradorUpsert} gera {@code INSERT} somente em
 * {@code aise.<fluxo.tabelaRespostas()>}).
 *
 * <p>Story: 4.5 — Comando {@code importar}. Fecha Marco M2 (parcial).
 */
// @Dependent: evita client proxy do CDI, necessário para que picocli possa
// injetar @CommandLine.Spec diretamente na instância real (não num wrapper).
// @Unremovable: impede que o ARC elimine o bean em modo prod (ele é resolvido
// via lookup programático pelo PicocliBeansFactory, e o build não detecta o uso).
@Dependent
@Unremovable
@CommandLine.Command(
    name = "importar",
    description = "Executa a importação: gera .sql e .log a partir de planilha .xlsx e mapping.json validado.",
    mixinStandardHelpOptions = true
)
public class ImportarCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ImportarCommand.class);

    /** Formatador do sufixo de timestamp nos nomes dos artefatos (AC11). */
    private static final DateTimeFormatter FMT_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Validador stateless — não-CDI, instância única JVM-wide (padrão Story 3.5 AC10).
     * Usado na Fase 4 para recusar mapeamentos com dinâmicas {@link StatusMapeamento#PENDENTE}.
     */
    private static final MapeamentoValidador VALIDADOR = new MapeamentoValidador();

    /** Gerador de UPDATE stateless — não-CDI (padrão Story 4.2). */
    private static final SqlGeradorUpdate GERADOR_UPDATE = new SqlGeradorUpdate();

    /** Gerador de UPSERT stateless — não-CDI (padrão Story 4.3). */
    private static final SqlGeradorUpsert GERADOR_UPSERT = new SqlGeradorUpsert();

    /** Coercionador stateless — não-CDI (padrão Story 4.1). */
    private static final Coercionador COERCIONADOR = new Coercionador();

    @Option(
        names = {"-a", "--arquivo"},
        required = true,
        description = "Caminho da planilha .xlsx a importar."
    )
    Path arquivo;

    @Option(
        names = {"-f", "--fluxo"},
        required = true,
        description = "Fluxo de importação: territorial ou predial.",
        converter = MapearCommand.FluxoConverter.class
    )
    Fluxo fluxo;

    @Option(
        names = {"-m", "--mapeamento"},
        defaultValue = "./mapping.json",
        description = "Caminho do mapping.json validado (padrão: ./mapping.json)."
    )
    Path mapeamento;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    /** {@code Instance<DataSource>} permite startup com datasource inativo (padrão MapearCommand). */
    @Inject
    Instance<DataSource> dataSourceInstance;

    @Inject
    MapeamentoStore mapeamentoStore;

    @Inject
    ImportacaoConfig importacaoConfig;

    @Inject
    ExistenciaRepository existenciaRepository;

    /**
     * Executa o pipeline de importação em 5 fases sequenciais, gerando artefatos
     * {@code .sql} e {@code .log} no diretório configurado em
     * {@code importacao.saida.diretorio}.
     *
     * <p>O timestamp {@code inicio} é capturado uma única vez no início deste método,
     * garantindo que o par de artefatos gerados compartilhe o mesmo sufixo (AC11/AC12).
     *
     * @return {@code 0} sem erros; {@code 2} com ≥ 1 linha com erro/pulada; {@code 1} em falha de infra.
     */
    @Override
    public Integer call() {
        LocalDateTime inicio = LocalDateTime.now();
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        LOG.infof("Iniciando importação: arquivo=%s, fluxo=%s, mapeamento=%s", arquivo, fluxo, mapeamento);

        // ── Fase 1: valida arquivo (fail-fast sem conexão — AC4) ──────────────────
        Path arquivoAbsoluto = arquivo.toAbsolutePath().normalize();
        if (!Files.exists(arquivo)) {
            err.println("✗ Arquivo não encontrado: " + arquivoAbsoluto);
            return 1;
        }
        if (!arquivo.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            err.println("✗ Arquivo não é uma planilha .xlsx: " + arquivoAbsoluto);
            return 1;
        }

        // ── Fase 2: valida conexão ────────────────────────────────────────────────
        try (Connection conn = dataSourceInstance.get().getConnection()) {
            // conexão OK — fecha imediatamente via try-with-resources
            LOG.debugf("Conexão validada para importação.");
        } catch (Exception e) {
            err.println("✗ Falha na conexão: " + ValidarConexaoCommand.mensagemAmigavel(e));
            return 1;
        }

        // ── Fase 3: carrega mapeamento ────────────────────────────────────────────
        Mapeamento mapeamentoObj;
        try {
            mapeamentoObj = mapeamentoStore.carregar(mapeamento);
        } catch (MapeamentoIoException e) {
            err.println("✗ Mapeamento inválido: " + e.getMessage());
            return 1;
        }
        LOG.infof("Mapeamento carregado: fluxo=%s, planilha=%s, fixas=%d, dinâmicas=%d",
            mapeamentoObj.fluxo(), mapeamentoObj.planilha(),
            mapeamentoObj.colunasFixas().size(), mapeamentoObj.colunasDinamicas().size());

        // ── Fase 4: gate PENDENTE ─────────────────────────────────────────────────
        ResultadoValidacao validacao = VALIDADOR.validar(mapeamentoObj);
        if (!validacao.valido()) {
            int total = validacao.pendencias().size();
            err.println("✗ Mapeamento incompleto. " + total + " pendência(s) encontrada(s):");
            err.println();
            for (String p : validacao.pendencias()) {
                err.println("  - " + p);
            }
            err.println();
            err.println("✗ Mapeamento incompleto — rode 'validar' primeiro.");
            return 1;
        }

        // ── Fase 5: iteração e geração ────────────────────────────────────────────
        String nomePlanilha = arquivo.getFileName() != null
                ? arquivo.getFileName().toString()
                : arquivo.toString();

        LogErros logErros = new LogErros(fluxo, nomePlanilha);
        StringBuilder sqlBuffer = new StringBuilder();
        // array wrapper para mutabilidade em lambda (effectively-final — AC13/Dev Notes)
        boolean[] houveErros = {false};
        // linha 1 = cabeçalho; dados começam em 2 (AC10)
        int[] linhaExcel = {1};

        // Cabeçalho do .sql (AC11)
        sqlBuffer.append("-- Importacao georreferenciada | fluxo: ")
                .append(fluxo.name())
                .append(" | planilha: ").append(nomePlanilha)
                .append(" | gerado: ").append(inicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n");

        try (ExcelSessao sessao = ExcelLeitor.abrir(arquivo)) {
            sessao.linhas().forEach(row ->
                processarLinha(row, linhaExcel, mapeamentoObj, logErros, sqlBuffer, houveErros));
        }

        // Gravar artefatos (AC11/AC12) ──────────────────────────────────────────
        String ts = inicio.format(FMT_ARQUIVO);
        String base = "saida-" + fluxo.name().toLowerCase(Locale.ROOT) + "-" + ts;
        Path dirSaida = Path.of(importacaoConfig.saida().diretorio());
        Path arquivoSql = dirSaida.resolve(base + ".sql");
        Path arquivoLog = dirSaida.resolve(base + ".log");

        try {
            Files.createDirectories(dirSaida);
            Files.writeString(arquivoSql, sqlBuffer.toString(), StandardCharsets.UTF_8,
                    CREATE, TRUNCATE_EXISTING);
            Files.writeString(arquivoLog, logErros.gerar(), StandardCharsets.UTF_8,
                    CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            err.println("✗ Falha ao gravar artefatos: " + e.getMessage());
            return 1;
        }

        LOG.infof("Artefatos gravados: sql=%s, log=%s", arquivoSql, arquivoLog);

        // Saída ao terminal (AC14) ─────────────────────────────────────────────
        out.println("✓ Artefatos gerados:");
        out.println("  SQL: " + arquivoSql.toAbsolutePath());
        out.println("  LOG: " + arquivoLog.toAbsolutePath());

        if (houveErros[0]) {
            err.println("⚠ Execução concluída com erros — consulte o .log para detalhes.");
            return 2;
        }
        return 0;
    }

    /**
     * Processa uma linha da planilha: extrai código do imóvel, verifica existência,
     * constrói {@link LinhaMapeada} e aciona os geradores de UPDATE e UPSERT.
     *
     * <p>{@code linhaExcel[0]} é incrementado no início deste método (AC10), antes de
     * qualquer processamento, garantindo numeração correta mesmo em linhas puladas.
     * {@code houveErros[0]} é setado para {@code true} em qualquer erro ou linha pulada.
     *
     * @param row         linha do Excel como {@code Map<header, valor-bruto>}
     * @param linhaExcel  array de 1 elemento com o número da linha atual (workaround effectively-final)
     * @param mapeamento  mapeamento carregado do {@code mapping.json}
     * @param logErros    acumulador de erros
     * @param sqlBuffer   buffer do arquivo {@code .sql}
     * @param houveErros  array de 1 elemento de flag de erro (workaround effectively-final)
     */
    private void processarLinha(Map<String, String> row,
                                 int[] linhaExcel,
                                 Mapeamento mapeamento,
                                 LogErros logErros,
                                 StringBuilder sqlBuffer,
                                 boolean[] houveErros) {
        linhaExcel[0]++;
        int numLinha = linhaExcel[0];

        // Extrair código do imóvel (AC6) ──────────────────────────────────────
        String codigoImovel = row.getOrDefault(mapeamento.colunaCodigoImovel(), "").trim();
        if (codigoImovel.isEmpty()) {
            logErros.registrarLinhaPulada(numLinha, "(vazio)",
                    "Código do imóvel vazio ou ausente na coluna '" + mapeamento.colunaCodigoImovel() + "'");
            houveErros[0] = true;
            return;
        }

        // Verificar existência do imóvel no banco (AC5) ───────────────────────
        if (!existenciaRepository.existeImovel(codigoImovel, fluxo)) {
            logErros.registrarLinhaPulada(numLinha, codigoImovel,
                    "Imóvel não encontrado em " + fluxo.tabelaPrincipal());
            houveErros[0] = true;
            return;
        }

        // Construir LinhaMapeada (AC6) ─────────────────────────────────────────
        Map<String, String> celulasFixas = new LinkedHashMap<>();
        for (String header : mapeamento.colunasFixas().keySet()) {
            celulasFixas.put(header, row.getOrDefault(header, ""));
        }
        Map<String, String> celulasDinamicas = new LinkedHashMap<>();
        for (Map.Entry<String, ColunaDinamica> entry : mapeamento.colunasDinamicas().entrySet()) {
            // dinâmicas PENDENTE são ignoradas defensivamente (gate Fase 4 garante ausência em produção)
            if (entry.getValue().status() == StatusMapeamento.MAPEADO) {
                celulasDinamicas.put(entry.getKey(), row.getOrDefault(entry.getKey(), ""));
            }
        }

        LinhaMapeada linhaMapeada = new LinhaMapeada(codigoImovel, celulasFixas, celulasDinamicas);
        boolean houveErroNaLinha = false;

        // Geração UPDATE (AC7) ─────────────────────────────────────────────────
        ResultadoUpdate resultadoUpdate = GERADOR_UPDATE.gerar(linhaMapeada, mapeamento, fluxo, COERCIONADOR);
        if (resultadoUpdate.ok()) {
            sqlBuffer.append(resultadoUpdate.sql()).append("\n");
        } else {
            logErros.registrarErrosLinha(numLinha, codigoImovel, resultadoUpdate.erros());
            houveErroNaLinha = true;
        }

        // Geração UPSERT (AC8) ─────────────────────────────────────────────────
        ResultadoUpsert resultadoUpsert = GERADOR_UPSERT.gerar(linhaMapeada, mapeamento, fluxo, COERCIONADOR);
        if (resultadoUpsert.ok()) {
            for (String sql : resultadoUpsert.sqls()) {
                sqlBuffer.append(sql).append("\n");
            }
        } else {
            // erros de UPDATE e UPSERT registrados em chamadas separadas (AC8 Dev Notes)
            logErros.registrarErrosLinha(numLinha, codigoImovel, resultadoUpsert.erros());
            houveErroNaLinha = true;
        }

        // Contagem de sucesso (AC9) ─────────────────────────────────────────────
        if (!houveErroNaLinha) {
            logErros.registrarLinhaProcessada();
        } else {
            houveErros[0] = true;
        }
    }
}
