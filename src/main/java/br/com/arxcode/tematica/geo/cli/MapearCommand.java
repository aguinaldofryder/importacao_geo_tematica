package br.com.arxcode.tematica.geo.cli;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import br.com.arxcode.tematica.geo.catalogo.AlternativaRepository;
import br.com.arxcode.tematica.geo.catalogo.CampoRepository;
import br.com.arxcode.tematica.geo.config.CodigoImovelConfig;
import br.com.arxcode.tematica.geo.config.ColunasFixasConfig;
import br.com.arxcode.tematica.geo.config.ImportacaoConfig;
import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;
import br.com.arxcode.tematica.geo.excel.ExcelLeitor;
import br.com.arxcode.tematica.geo.excel.ExcelSessao;
import br.com.arxcode.tematica.geo.mapeamento.Classificacao;
import br.com.arxcode.tematica.geo.mapeamento.ClassificadorColunas;
import br.com.arxcode.tematica.geo.mapeamento.AutoMapeador;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.EntradaAutoMapeamento;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

/**
 * Subcomando {@code mapear} — orquestrador das Fases 1 e 2 do pipeline de
 * importação. Lê uma planilha {@code .xlsx}, classifica colunas (Fase 1),
 * executa auto-mapeamento contra o catálogo {@code campo}/{@code alternativa}
 * (Fase 2) e grava o {@code mapping.json} resultante para edição manual
 * (FR-08) antes de {@code validar} e {@code importar}.
 *
 * <p><strong>Pipeline (6 etapas — fail-fast em qualquer falha):</strong>
 * <ol>
 *   <li>Validar conexão DB ({@code SELECT 1}) — reusa
 *       {@link ValidarConexaoCommand#mensagemAmigavel(Exception)} para
 *       mensagem PT (NFR-07).</li>
 *   <li>Validar arquivo (existência + extensão {@code .xlsx}) — delegado
 *       ao {@link ExcelLeitor#abrir(Path)} da Story 2.1.</li>
 *   <li>Carregar catálogo via {@link CampoRepository#listarPorFluxo(Fluxo)}
 *       (Story 2.3); lista vazia → erro de configuração.</li>
 *   <li>Abrir planilha + Fase 1 via {@link ClassificadorColunas} (Story 2.4).</li>
 *   <li>Acumular DISTINCT por header {@code MULTIPLA_ESCOLHA} em uma única
 *       varredura (NFR-04 — stream-once); skippable se não houver match
 *       de tipo {@link Tipo#MULTIPLA_ESCOLHA} (AC13).</li>
 *   <li>Auto-mapear via {@link AutoMapeador} (Story 3.2) e persistir via
 *       {@link MapeamentoStore} (Story 3.1).</li>
 * </ol>
 *
 * <p><strong>Exit codes:</strong> {@code 0} em sucesso (independente do número
 * de itens {@link StatusMapeamento#PENDENTE}); {@code 1} em qualquer falha de
 * infraestrutura antes de produzir o {@code mapping.json}.
 *
 * <p>Story: 3.4 — Comando {@code mapear} (orquestrador da Fase 1+2).
 */
// @Dependent + @Unremovable pelos mesmos motivos do ValidarConexaoCommand:
// evita client proxy CDI (necessário para @CommandLine.Spec na instância real)
// e impede ARC de eliminar o bean (lookup programático via PicocliBeansFactory).
@Dependent
@Unremovable
@CommandLine.Command(
    name = "mapear",
    description = "Gera o arquivo mapping.json a partir da planilha .xlsx (Fase 1 + Fase 2 do pipeline).",
    mixinStandardHelpOptions = true
)
public class MapearCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(MapearCommand.class);

    @Option(
        names = {"-a", "--arquivo"},
        required = true,
        description = "Caminho da planilha .xlsx a mapear."
    )
    Path arquivo;

    @Option(
        names = {"-f", "--fluxo"},
        required = true,
        converter = FluxoConverter.class,
        description = "Fluxo de importação: territorial ou predial."
    )
    Fluxo fluxo;

    /** Conversor case-insensitive para aceitar 'territorial'/'predial' (lowercase) na CLI. */
    public static class FluxoConverter implements picocli.CommandLine.ITypeConverter<Fluxo> {
        @Override
        public Fluxo convert(String value) {
            if (value == null) {
                throw new picocli.CommandLine.TypeConversionException(
                    "Valor inválido para --fluxo: esperado 'territorial' ou 'predial'");
            }
            try {
                return Fluxo.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new picocli.CommandLine.TypeConversionException(
                    "Valor inválido para --fluxo: '" + value + "' (esperado 'territorial' ou 'predial')");
            }
        }
    }

    @Option(
        names = {"-s", "--saida"},
        defaultValue = "./mapping.json",
        description = "Caminho do mapping.json de saída (padrão: ./mapping.json)."
    )
    Path saida;

    @Inject
    Instance<DataSource> dataSourceInstance;

    @Inject
    CampoRepository campoRepository;

    @Inject
    AlternativaRepository alternativaRepository;

    @Inject
    MapeamentoStore mapeamentoStore;

    @Inject
    ImportacaoConfig importacaoConfig;

    @Inject
    CodigoImovelConfig codigoImovelConfig;

    @Inject
    ColunasFixasConfig colunasFixasConfig;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        LOG.infof("Iniciando mapeamento: arquivo=%s fluxo=%s saida=%s", arquivo, fluxo, saida);

        // (1) Validação de conexão fail-fast ---------------------------------
        try (Connection conn = dataSourceInstance.get().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
        } catch (Exception e) {
            // NFR-07: nunca emitir e.getMessage() cru — pode conter senha
            err.println("✗ Falha de conexão: " + ValidarConexaoCommand.mensagemAmigavel(e));
            return 1;
        }

        // (2) Validação do arquivo (delegada ao ExcelLeitor.abrir, mas a
        // mensagem é re-emitida pelo orquestrador) ----------------------------
        if (arquivo == null || !Files.exists(arquivo)) {
            err.println("✗ Arquivo inválido: arquivo não encontrado: " + arquivo);
            return 1;
        }

        // (3) Carga do catálogo ---------------------------------------------
        List<Campo> campos;
        try {
            campos = campoRepository.listarPorFluxo(fluxo);
        } catch (Exception e) {
            err.println("✗ Falha ao carregar catálogo: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            LOG.error("Falha ao consultar catálogo", e);
            return 1;
        }
        if (campos.isEmpty()) {
            err.println("✗ Catálogo vazio para fluxo " + fluxo.name().toLowerCase(Locale.ROOT)
                + ": verifique a tabela campo/grupocampo");
            return 1;
        }
        LOG.infof("Catálogo carregado: %d campos para fluxo %s", campos.size(), fluxo);

        // Resoluções a partir da config ---------------------------------------
        ImportacaoConfig.Mapeamento cfgMap = importacaoConfig.mapeamento();
        String nomeColunaCodigo = codigoImovelConfig.por(fluxo);
        Set<String> colunasFixas = colunasFixasConfig.por(fluxo);

        // (4) Abrir planilha + Fase 1 ----------------------------------------
        Mapeamento mapeamento;
        try (ExcelSessao sessao = ExcelLeitor.abrir(arquivo)) {
            List<String> headers = sessao.cabecalhos();
            ClassificadorColunas classificador =
                new ClassificadorColunas(cfgMap.caseSensitive(), cfgMap.trimEspacos());
            Classificacao classificacao = classificador.classificar(headers, nomeColunaCodigo, colunasFixas);
            LOG.infof("Classificação: codigo=%s, %d fixas, %d dinamicas",
                classificacao.codigo(), classificacao.fixas().size(), classificacao.dinamicas().size());

            // (5) Acumulação skippable de DISTINCT --------------------------
            Map<String, Set<String>> distinctMap =
                acumularDistintos(sessao, classificacao, campos, cfgMap);

            // (6) Auto-mapeamento + persistência ----------------------------
            EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                classificacao,
                arquivo.getFileName().toString(),
                fluxo,
                campos,
                alternativaRepository::listarPorCampo,
                header -> distinctMap.getOrDefault(header, Set.of())
            );
            AutoMapeador autoMapeador = new AutoMapeador(cfgMap.caseSensitive(), cfgMap.trimEspacos());
            mapeamento = autoMapeador.mapear(entrada);
        } catch (ImportacaoException e) {
            err.println("✗ Arquivo inválido: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("✗ Falha inesperada ao processar a planilha: " + e.getMessage());
            LOG.error("Falha inesperada na Fase 1+2", e);
            return 1;
        }

        long mapeados = mapeamento.colunasDinamicas().values().stream()
            .filter(c -> c.status() == StatusMapeamento.MAPEADO).count();
        long pendentes = mapeamento.colunasDinamicas().values().stream()
            .filter(c -> c.status() == StatusMapeamento.PENDENTE).count();
        LOG.infof("Auto-mapeamento concluído: %d MAPEADO, %d PENDENTE", mapeados, pendentes);

        try {
            mapeamentoStore.salvar(mapeamento, saida);
        } catch (Exception e) {
            err.println("✗ Falha ao gravar mapping.json: " + e.getMessage());
            LOG.error("Falha ao gravar mapping.json", e);
            return 1;
        }

        imprimirRelatorio(out, mapeamento, saida);
        return 0;
    }

    /**
     * Etapa (5) do pipeline. Acumula valores distintos das colunas dinâmicas
     * cujo {@link Campo} correspondente seja {@link Tipo#MULTIPLA_ESCOLHA}.
     * Skippable (AC13): se nenhum header dinâmico tem match {@code MULTIPLA_ESCOLHA},
     * retorna mapa vazio sem varrer linhas.
     */
    private Map<String, Set<String>> acumularDistintos(
            ExcelSessao sessao,
            Classificacao classificacao,
            List<Campo> campos,
            ImportacaoConfig.Mapeamento cfgMap) {

        // Indexa campos por descrição normalizada (mesma regra do AutoMapeador)
        Map<String, List<Campo>> camposPorDescricaoNormalizada = campos.stream()
            .collect(Collectors.groupingBy(c -> normalizar(c.descricao(), cfgMap)));

        // Conjunto de headers dinâmicos cujo match é MULTIPLA_ESCOLHA
        Set<String> headersMultiplaEscolha = new LinkedHashSet<>();
        for (String header : classificacao.dinamicas()) {
            String norm = normalizar(header, cfgMap);
            List<Campo> matches = camposPorDescricaoNormalizada.getOrDefault(norm, List.of());
            if (matches.size() == 1 && matches.get(0).tipo() == Tipo.MULTIPLA_ESCOLHA) {
                headersMultiplaEscolha.add(header);
            }
        }

        if (headersMultiplaEscolha.isEmpty()) {
            LOG.info("Nenhum header MULTIPLA_ESCOLHA detectado: pulando varredura de DISTINCT.");
            return Map.of();
        }

        Map<String, Set<String>> distinctMap = new HashMap<>();
        for (String h : headersMultiplaEscolha) {
            distinctMap.put(h, new LinkedHashSet<>());
        }

        // Varredura única de linhas (NFR-04 — stream-once)
        sessao.linhas().forEach(linha -> {
            for (String h : headersMultiplaEscolha) {
                String valor = linha.get(h);
                if (valor != null && !valor.isBlank()) {
                    distinctMap.get(h).add(valor);
                }
            }
        });
        return distinctMap;
    }

    /**
     * Normalizador local — espelha {@code AutoMapeador.normalizar} e
     * {@code ClassificadorColunas.normalizar} (mesmas flags
     * {@code caseSensitive}/{@code trimEspacos}). Decisão @dev (AC13):
     * duplicação aceita por ora para evitar refatoração da Story 3.2 (Done).
     */
    private static String normalizar(String s, ImportacaoConfig.Mapeamento cfgMap) {
        if (s == null) {
            return null;
        }
        String r = cfgMap.trimEspacos() ? s.strip() : s;
        return cfgMap.caseSensitive() ? r : r.toLowerCase(Locale.ROOT);
    }

    /**
     * Imprime o relatório final ao stdout em 3 blocos (AC6):
     * (a) resumo numérico, (b) colunas fixas detectadas, (c) PENDENTES detalhados.
     */
    private void imprimirRelatorio(PrintWriter out, Mapeamento m, Path arquivoSaida) {
        Collection<ColunaDinamica> dinamicas = m.colunasDinamicas().values();
        long mapeados = dinamicas.stream().filter(c -> c.status() == StatusMapeamento.MAPEADO).count();
        long pendentes = dinamicas.stream().filter(c -> c.status() == StatusMapeamento.PENDENTE).count();
        long total = mapeados + pendentes;

        Path absoluto = arquivoSaida.toAbsolutePath().normalize();

        // (a) resumo
        out.println("✓ Mapeamento concluído: " + total + " coluna(s) dinâmica(s) — "
            + mapeados + " mapeada(s), " + pendentes + " pendente(s). Arquivo: " + absoluto);
        out.println();

        // (b) colunas fixas
        Map<String, String> fixas = m.colunasFixas();
        if (fixas != null && !fixas.isEmpty()) {
            String headers = String.join(", ", fixas.keySet());
            out.println("Colunas fixas detectadas (" + fixas.size() + "): " + headers);
            out.println();
        }

        // (c) PENDENTES detalhados
        if (pendentes > 0) {
            out.println("Itens pendentes (edite o arquivo manualmente — FR-08):");
            for (Map.Entry<String, ColunaDinamica> e : m.colunasDinamicas().entrySet()) {
                ColunaDinamica cd = e.getValue();
                if (cd.status() == StatusMapeamento.PENDENTE) {
                    String motivo = cd.motivo() == null ? "(sem motivo)" : cd.motivo();
                    out.println("  - " + e.getKey() + ": " + motivo);
                }
            }
            out.println();
        } else {
            out.println("Todos os itens foram resolvidos automaticamente — pronto para 'validar' e 'importar'.");
        }
    }
}
