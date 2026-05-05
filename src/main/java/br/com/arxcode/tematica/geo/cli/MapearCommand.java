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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import br.com.arxcode.tematica.geo.catalogo.AlternativaRepository;
import br.com.arxcode.tematica.geo.catalogo.CampoRepository;
import br.com.arxcode.tematica.geo.config.CodigoImovelConfig;
import br.com.arxcode.tematica.geo.config.ColunasFixasConfig;
import br.com.arxcode.tematica.geo.config.ImportacaoConfig;
import br.com.arxcode.tematica.geo.dominio.Alternativa;
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

        // (T1) Leitura do mapping.json pré-existente — ANTES de abrir a planilha (RISCO-1)
        Optional<Mapeamento> mapeamentoExistente = Optional.empty();
        if (Files.exists(saida)) {
            try {
                mapeamentoExistente = Optional.of(mapeamentoStore.carregar(saida));
                LOG.infof("mapping.json pré-existente carregado: %s", saida);
            } catch (Exception ex) {
                err.println("⚠ Aviso: mapping.json existente não pôde ser lido, iniciando mapeamento do zero: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                LOG.warnf("mapping.json existente inválido, continuando sem modo incremental: %s", ex.getMessage());
            }
        }

        // (T2) Identificação de candidatos para resolução incremental (RISCO-1)
        List<String> candidatos = mapeamentoExistente
            .map(this::identificarCandidatos)
            .orElse(List.of());

        if (mapeamentoExistente.isPresent()) {
            LOG.infof("Modo incremental ativado: %d candidato(s) para resolução de alternativas encontrado(s) em mapping.json existente",
                candidatos.size());
            for (String header : candidatos) {
                ColunaDinamica cd = mapeamentoExistente.get().colunasDinamicas().get(header);
                long nullCount = cd.alternativas() == null ? 0
                    : cd.alternativas().values().stream().filter(v -> v == null).count();
                LOG.debugf("Candidato incremental: header=%s, idcampo=%d, alternativas null=%d (alternativas=%s)",
                    header, cd.idcampo(), nullCount, cd.alternativas() == null ? "ausente" : nullCount + " sem match");
            }
        }

        // (1) Validação de conexão fail-fast ---------------------------------
        try (Connection conn = dataSourceInstance.get().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
        } catch (Exception e) {
            err.println("✗ Falha de conexão: " + ValidarConexaoCommand.mensagemAmigavel(e));
            return 1;
        }

        // (2) Validação do arquivo -------------------------------------------
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

        // Resoluções a partir da config
        ImportacaoConfig.Mapeamento cfgMap = importacaoConfig.mapeamento();
        String nomeColunaCodigo = codigoImovelConfig.por(fluxo);
        String nomeColunaCSequencia = fluxo == Fluxo.PREDIAL ? codigoImovelConfig.sequenciaPredial() : null;
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

            // (5/T3) Acumulação DISTINCT — no modo incremental, apenas para candidatos
            Map<String, Set<String>> distinctMap;
            if (mapeamentoExistente.isPresent() && !candidatos.isEmpty()) {
                // Modo incremental: acumular apenas para headers candidatos presentes na planilha atual
                Set<String> headersNaPlanilha = new LinkedHashSet<>(classificacao.dinamicas());
                List<String> candidatosNaPlanilha = candidatos.stream()
                    .filter(headersNaPlanilha::contains)
                    .toList();
                distinctMap = acumularDistintosParaCandidatos(sessao, candidatosNaPlanilha);
            } else {
                // Modo normal: acumular para todos os headers MULTIPLA_ESCOLHA do catálogo
                distinctMap = acumularDistintos(sessao, classificacao, campos, cfgMap);
            }

            // (6) Auto-mapeamento normal ------------------------------------
            EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                classificacao,
                arquivo.getFileName().toString(),
                fluxo,
                nomeColunaCSequencia,
                campos,
                alternativaRepository::listarPorCampo,
                header -> distinctMap.getOrDefault(header, Set.of())
            );
            AutoMapeador autoMapeador = new AutoMapeador(cfgMap.caseSensitive(), cfgMap.trimEspacos());
            Mapeamento mapeamentoNovo = autoMapeador.mapear(entrada);

            // (T5) Mesclagem com mapeamento pré-existente (RISCO-2: novo LinkedHashMap)
            if (mapeamentoExistente.isPresent()) {
                mapeamento = mesclarMapeamento(
                    mapeamentoExistente.get(),
                    mapeamentoNovo,
                    candidatos,
                    distinctMap,
                    autoMapeador,
                    cfgMap);
            } else {
                mapeamento = mapeamentoNovo;
            }

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
     * (T2) Identifica candidatos para resolução incremental de alternativas.
     * Candidato é qualquer item de {@code colunasDinamicas} que satisfaz:
     * (a) status == PENDENTE; (b) idcampo != null; (c) tipo == MULTIPLA_ESCOLHA;
     * (d) alternativas == null (campo configurado manualmente sem alternativas)
     *     OU alternativas != null e ao menos uma entrada tem valor null.
     *
     * @param existente mapeamento pré-existente carregado do disco
     * @return lista de headers candidatos
     */
    private List<String> identificarCandidatos(Mapeamento existente) {
        List<String> candidatos = new ArrayList<>();
        for (Map.Entry<String, ColunaDinamica> e : existente.colunasDinamicas().entrySet()) {
            ColunaDinamica cd = e.getValue();
            if (cd.status() == StatusMapeamento.PENDENTE
                    && cd.idcampo() != null
                    && cd.tipo() == Tipo.MULTIPLA_ESCOLHA
                    && (cd.alternativas() == null
                        || cd.alternativas().values().stream().anyMatch(v -> v == null))) {
                candidatos.add(e.getKey());
            }
        }
        return candidatos;
    }

    /**
     * (T3/T5) Mescla o resultado do auto-mapeamento com o mapeamento pré-existente.
     * Regras (AC5):
     * <ul>
     *   <li>MAPEADO no existente → mantém sem reprocessar.</li>
     *   <li>PENDENTE não-candidato no existente → mantém sem reprocessar.</li>
     *   <li>Candidatos → substituídos pelo resultado de resolução incremental (T4).</li>
     *   <li>Headers novos na planilha → adicionados do auto-mapeamento normal.</li>
     *   <li>Headers ausentes na planilha atual → descartados (planilha é fonte de verdade).</li>
     * </ul>
     */
    private Mapeamento mesclarMapeamento(
            Mapeamento existente,
            Mapeamento novo,
            List<String> candidatos,
            Map<String, Set<String>> distinctMap,
            AutoMapeador autoMapeador,
            ImportacaoConfig.Mapeamento cfgMap) {

        // RISCO-2: construir novo LinkedHashMap — não modificar in-place (unmodifiableMap)
        Map<String, ColunaDinamica> resultado = new LinkedHashMap<>();

        Set<String> candidatosSet = new LinkedHashSet<>(candidatos);

        // Iterar sobre headers presentes na planilha atual (fonte de verdade)
        for (String header : novo.colunasDinamicas().keySet()) {
            ColunaDinamica cdExistente = existente.colunasDinamicas().get(header);

            if (cdExistente == null) {
                // Header novo na planilha — usar resultado do auto-mapeamento
                resultado.put(header, novo.colunasDinamicas().get(header));
            } else if (cdExistente.status() == StatusMapeamento.MAPEADO) {
                // Preservar MAPEADO intocado (AC5)
                resultado.put(header, cdExistente);
            } else if (candidatosSet.contains(header)) {
                // (T4) Candidato: tentar resolução incremental de alternativas
                Set<String> distintos = distinctMap.getOrDefault(header, Set.of());
                List<Alternativa> alternativasCampo =
                    alternativaRepository.listarPorCampo(cdExistente.idcampo());
                ColunaDinamica resolvida = autoMapeador.popularAlternativasIncremental(
                    cdExistente.idcampo(), distintos, alternativasCampo);
                // Se ainda PENDENTE, atualizar motivo com formato AC6
                if (resolvida.status() == StatusMapeamento.PENDENTE && resolvida.alternativas() != null) {
                    long semMatch = resolvida.alternativas().values().stream()
                        .filter(v -> v == null).count();
                    long total = resolvida.alternativas().size();
                    String motivo = semMatch + " de " + total
                        + " alternativas sem mapeamento (idcampo=" + cdExistente.idcampo()
                        + " informado; verifique a tabela alternativa)";
                    resolvida = new ColunaDinamica(
                        StatusMapeamento.PENDENTE,
                        resolvida.idcampo(),
                        resolvida.tipo(),
                        resolvida.alternativas(),
                        motivo,
                        resolvida.sugestoes());
                }
                resultado.put(header, resolvida);
            } else {
                // PENDENTE não-candidato → mantém sem alteração (AC5)
                resultado.put(header, cdExistente);
            }
        }
        // Headers do existente ausentes da planilha atual → descartados automaticamente
        // (não iteramos sobre existente.colunasDinamicas(), apenas sobre novo)

        return new Mapeamento(
            novo.fluxo(),
            novo.planilha(),
            novo.colunaCodigoImovel(),
            novo.colunaSequenciaPredial(),
            novo.colunasFixas(),
            java.util.Collections.unmodifiableMap(resultado));
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

        return acumularDistintosParaHeaders(sessao, headersMultiplaEscolha);
    }

    /**
     * (T3) Acumula valores DISTINCT apenas para os headers candidatos no modo
     * incremental. Só é chamado quando há candidatos na lista (AC3).
     */
    private Map<String, Set<String>> acumularDistintosParaCandidatos(
            ExcelSessao sessao,
            List<String> candidatosNaPlanilha) {

        if (candidatosNaPlanilha.isEmpty()) {
            LOG.info("Nenhum candidato incremental na planilha atual: pulando varredura de DISTINCT.");
            return Map.of();
        }

        Set<String> headersSet = new LinkedHashSet<>(candidatosNaPlanilha);
        return acumularDistintosParaHeaders(sessao, headersSet);
    }

    /**
     * Varredura única de linhas (NFR-04 — stream-once) acumulando valores
     * distintos para o conjunto de headers informado.
     */
    private Map<String, Set<String>> acumularDistintosParaHeaders(
            ExcelSessao sessao,
            Set<String> headers) {

        Map<String, Set<String>> distinctMap = new HashMap<>();
        for (String h : headers) {
            distinctMap.put(h, new LinkedHashSet<>());
        }

        sessao.linhas().forEach(linha -> {
            for (String h : headers) {
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
     * (a) resumo numérico, (b) colunas fixas detectadas,
     * (c) PENDENTES detalhados — distinguindo subtipo conforme AC7 (Story 3.6).
     *
     * <p><strong>Decisão T6 (AC8):</strong> subtipo calculado inline com
     * {@code if (cd.idcampo() == null)} — sem enum {@code SubtipoPendente} separado.
     * Escolha registrada nas Completion Notes da Story 3.6.
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

        // (c) PENDENTES detalhados com distinção de subtipo (AC7 Story 3.6)
        if (pendentes > 0) {
            out.println("Itens pendentes (edite o arquivo manualmente — FR-08):");
            for (Map.Entry<String, ColunaDinamica> e : m.colunasDinamicas().entrySet()) {
                ColunaDinamica cd = e.getValue();
                if (cd.status() == StatusMapeamento.PENDENTE) {
                    if (cd.idcampo() == null) {
                        // Subtipo SEM_CAMPO: bloqueador total
                        String motivo = cd.motivo() == null ? "(sem motivo)" : cd.motivo();
                        out.println("  - [CAMPO PENDENTE] " + e.getKey() + ": " + motivo);
                    } else if (cd.tipo() == Tipo.MULTIPLA_ESCOLHA
                            && cd.alternativas() != null
                            && cd.alternativas().values().stream().anyMatch(v -> v == null)) {
                        // Subtipo SEM_ALTERNATIVAS: idcampo preenchido, faltam alternativas
                        long nullCount = cd.alternativas().values().stream()
                            .filter(v -> v == null).count();
                        long totalAlts = cd.alternativas().size();
                        out.println("  - [ALTERNATIVAS PENDENTES] " + e.getKey() + ": "
                            + nullCount + " de " + totalAlts
                            + " alternativas sem mapeamento (idcampo=" + cd.idcampo()
                            + " informado; verifique a tabela alternativa)");
                    } else {
                        // Outros casos PENDENTE (ex: tipo preenchido mas sem alternativas null)
                        String motivo = cd.motivo() == null ? "(sem motivo)" : cd.motivo();
                        out.println("  - " + e.getKey() + ": " + motivo);
                    }
                }
            }
            out.println();
        } else {
            out.println("Todos os itens foram resolvidos automaticamente — pronto para 'validar' e 'importar'.");
        }
    }
}
