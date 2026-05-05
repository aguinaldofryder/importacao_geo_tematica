package br.com.arxcode.tematica.geo.mapeamento;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import br.com.arxcode.tematica.geo.dominio.Alternativa;
import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

/**
 * Fase 2 do pipeline de importação: auto-mapeia colunas dinâmicas da planilha
 * contra o catálogo {@code campo}/{@code alternativa}, produzindo um
 * {@link Mapeamento} com itens {@link StatusMapeamento#MAPEADO MAPEADO} ou
 * {@link StatusMapeamento#PENDENTE PENDENTE}.
 *
 * <p><strong>Características (espelham {@link ClassificadorColunas} 2.4):</strong>
 * <ul>
 *   <li><em>Stateless e thread-safe.</em> Toda entrada chega via
 *       {@link EntradaAutoMapeamento}; não há campo mutável.</li>
 *   <li><em>Não é {@code @ApplicationScoped}.</em> O orquestrador 3.4
 *       instancia diretamente. Mantém testes JUnit puros (sem
 *       {@code @QuarkusTest}, ~30× mais rápidos).</li>
 *   <li><em>Normalização configurável</em> ({@code caseSensitive},
 *       {@code trimEspacos}) passada por construtor. Defaults
 *       {@code (false, true)} alinhados a {@code ImportacaoConfig.Mapeamento}.</li>
 *   <li><em>Determinismo:</em> ordem de iteração de
 *       {@code Mapeamento.colunasDinamicas} segue a ordem de aparição em
 *       {@code Classificacao.dinamicas()} (preserva ordem da planilha).</li>
 * </ul>
 *
 * <p><strong>Repasse literal de {@code colunasFixas}:</strong> esta classe
 * não resolve o nome físico da coluna na tabela principal. As entradas de
 * {@link Classificacao#fixas()} (key=value=header) são repassadas como estão
 * para {@link Mapeamento#colunasFixas()}. A tradução
 * {@code header → coluna física} é responsabilidade do gerador de SQL UPDATE
 * (Story 4.2), que conhece o vocabulário da
 * {@code tribcadastroimobiliario}/{@code tribimobiliariosegmento}.
 *
 * <p><strong>Discriminação Territorial vs Predial:</strong> não é
 * responsabilidade desta classe. O caller (orquestrador 3.4) deve garantir
 * que {@link EntradaAutoMapeamento#campos()} foi pré-filtrado pelo fluxo
 * correto via {@code CampoRepository.listarPorFluxo(fluxo)} (Story 2.3 AC1,
 * que aplica {@code JOIN grupocampo ... WHERE funcionalidade=?} e
 * {@code ativo='S'}). Receber campos do fluxo errado produzirá um
 * {@link Mapeamento} semanticamente inválido sem que o {@code AutoMapeador}
 * detecte. Como salvaguarda mínima, é feito sanity-check de
 * {@link Campo#ativo()} (lança {@link ImportacaoException} se encontrar
 * campo inativo).
 *
 * <p>Story: 3.2 — AutoMapeador.
 */
public class AutoMapeador {

    private static final Logger LOG = Logger.getLogger(AutoMapeador.class);

    private final boolean caseSensitive;
    private final boolean trimEspacos;

    /**
     * Construtor de conveniência alinhado com os defaults do
     * {@code ImportacaoConfig.Mapeamento}: {@code caseSensitive=false},
     * {@code trimEspacos=true}.
     */
    public AutoMapeador() {
        this(false, true);
    }

    public AutoMapeador(boolean caseSensitive, boolean trimEspacos) {
        this.caseSensitive = caseSensitive;
        this.trimEspacos = trimEspacos;
    }

    /**
     * Auto-mapeia as colunas dinâmicas da {@link EntradaAutoMapeamento}.
     *
     * @param entrada parameter object com classificação, fluxo, lista de campos
     *                pré-filtrada e callbacks de catálogo/planilha
     * @return {@link Mapeamento} com {@code colunasFixas} repassadas
     *         literalmente e {@code colunasDinamicas} marcadas como
     *         {@code MAPEADO} ou {@code PENDENTE}
     * @throws ImportacaoException se {@code entrada} for nulo ou se algum
     *                             campo da lista estiver inativo
     */
    public Mapeamento mapear(EntradaAutoMapeamento entrada) {
        if (entrada == null) {
            throw new ImportacaoException("Entrada do AutoMapeador não pode ser nula.");
        }

        // Sanity-check: nenhum campo inativo deve chegar até aqui (AC10).
        for (Campo c : entrada.campos()) {
            if (!c.ativo()) {
                throw new ImportacaoException(
                        "AutoMapeador recebeu campo inativo (id=" + c.id()
                                + "); o caller deve passar apenas campos ativos (CampoRepository.listarPorFluxo já filtra).");
            }
        }

        // Indexa campos por descrição normalizada (1 passada).
        Map<String, List<Campo>> camposPorDescricaoNormalizada = entrada.campos().stream()
                .collect(Collectors.groupingBy(c -> normalizar(c.descricao())));

        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        for (String header : entrada.classificacao().dinamicas()) {
            String descNorm = normalizar(header);
            List<Campo> matches = camposPorDescricaoNormalizada.getOrDefault(descNorm, List.of());

            ColunaDinamica cd;
            if (matches.isEmpty()) {
                cd = colunaPendenteSemMatch(header);
            } else if (matches.size() >= 2) {
                cd = colunaPendenteMultiplosMatches(header, matches);
            } else {
                Campo campo = matches.get(0);
                if (campo.tipo() == Tipo.MULTIPLA_ESCOLHA) {
                    cd = popularAlternativas(header, campo, entrada);
                } else {
                    cd = colunaMapeada(campo);
                }
            }
            dinamicas.put(header, cd);
        }

        LOG.debugf("AutoMapeador: %d colunas dinâmicas processadas para fluxo %s (planilha=%s).",
                dinamicas.size(), entrada.fluxo(), entrada.nomeArquivoPlanilha());

        return new Mapeamento(
                entrada.fluxo(),
                entrada.nomeArquivoPlanilha(),
                entrada.classificacao().codigo(),
                entrada.colunaSequenciaPredial(),
                entrada.classificacao().fixas(),
                Collections.unmodifiableMap(dinamicas));
    }

    private ColunaDinamica colunaMapeada(Campo campo) {
        return new ColunaDinamica(
                StatusMapeamento.MAPEADO,
                Math.toIntExact(campo.id()),
                campo.tipo(),
                null,
                null,
                null);
    }

    private ColunaDinamica colunaPendenteSemMatch(String header) {
        return new ColunaDinamica(
                StatusMapeamento.PENDENTE,
                null,
                null,
                null,
                "Nenhum campo encontrado com descricao='" + header + "'",
                null);
    }

    private ColunaDinamica colunaPendenteMultiplosMatches(String header, List<Campo> matches) {
        List<Campo> ordenados = matches.stream()
                .sorted(Comparator.comparingLong(Campo::id))
                .toList();
        List<Integer> sugestoes = ordenados.stream()
                .map(c -> Math.toIntExact(c.id()))
                .toList();
        String idsCsv = ordenados.stream()
                .map(c -> Long.toString(c.id()))
                .collect(Collectors.joining(","));
        return new ColunaDinamica(
                StatusMapeamento.PENDENTE,
                null,
                null,
                null,
                "Múltiplos campos encontrados com descricao='" + header + "': IDs " + idsCsv,
                sugestoes);
    }

    /**
     * Implementa AC7: para campo {@code MULTIPLA_ESCOLHA} com match único, mapeia
     * cada valor DISTINCT da planilha contra {@link Alternativa#descricao()}.
     * Se algum valor não for resolvido (match múltiplo ou nenhum), o status é
     * rebaixado para {@link StatusMapeamento#PENDENTE}, mas {@code idcampo} e
     * {@code tipo} permanecem preenchidos para auxiliar edição manual.
     *
     * <p>Visibilidade {@code package-private} para permitir reuso no modo
     * incremental do {@code MapearCommand} (Story 3.6 T4) sem duplicação de lógica.
     */
    ColunaDinamica popularAlternativas(String header, Campo campo, EntradaAutoMapeamento entrada) {
        Set<String> valoresDistintos = entrada.valoresDistintosPorHeader().apply(header);
        List<Alternativa> alternativasCampo = entrada.alternativasPorCampo().apply(campo.id());
        return popularAlternativasComListas(campo.id(), campo.tipo(), valoresDistintos, alternativasCampo);
    }

    /**
     * Resolve alternativas a partir de listas explícitas de valores distintos e
     * alternativas do banco. Usado no modo incremental (Story 3.6 T4) para
     * candidatos com {@code idcampo} já preenchido.
     */
    public ColunaDinamica popularAlternativasIncremental(
            Integer idcampo,
            Set<String> valoresDistintos,
            List<Alternativa> alternativasCampo) {
        return popularAlternativasComListas(
                idcampo == null ? 0L : idcampo.longValue(),
                Tipo.MULTIPLA_ESCOLHA,
                valoresDistintos,
                alternativasCampo);
    }

    private ColunaDinamica popularAlternativasComListas(
            long idcampoLong,
            Tipo tipo,
            Set<String> valoresDistintos,
            List<Alternativa> alternativasCampo) {

        // Indexa alternativas por descrição normalizada.
        Map<String, List<Alternativa>> alternativasPorDescricaoNormalizada =
                (alternativasCampo == null ? List.<Alternativa>of() : alternativasCampo).stream()
                        .collect(Collectors.groupingBy(a -> normalizar(a.descricao())));

        Map<String, Integer> alternativasMap = new LinkedHashMap<>();
        int totalConsiderados = 0;
        int semMapeamento = 0;

        if (valoresDistintos != null) {
            for (String valor : valoresDistintos) {
                if (valor == null || valor.isBlank()) {
                    continue;
                }
                totalConsiderados++;
                List<Alternativa> matches = alternativasPorDescricaoNormalizada.getOrDefault(
                        normalizar(valor), List.of());
                if (matches.size() == 1) {
                    alternativasMap.put(valor, Math.toIntExact(matches.get(0).id()));
                } else {
                    alternativasMap.put(valor, null);
                    semMapeamento++;
                }
            }
        }

        Integer idcampoBoxed = Math.toIntExact(idcampoLong);

        if (semMapeamento == 0) {
            return new ColunaDinamica(
                    StatusMapeamento.MAPEADO,
                    idcampoBoxed,
                    tipo,
                    Collections.unmodifiableMap(alternativasMap),
                    null,
                    null);
        }
        return new ColunaDinamica(
                StatusMapeamento.PENDENTE,
                idcampoBoxed,
                tipo,
                Collections.unmodifiableMap(alternativasMap),
                semMapeamento + " de " + totalConsiderados + " alternativas sem mapeamento",
                null);
    }

    /**
     * Normaliza string para comparação. {@link Locale#ROOT} é deliberado para
     * evitar o "Turkish locale bug" (mesma justificativa de
     * {@link ClassificadorColunas}).
     */
    private String normalizar(String s) {
        if (s == null) {
            return null;
        }
        String r = trimEspacos ? s.strip() : s;
        return caseSensitive ? r : r.toLowerCase(Locale.ROOT);
    }

}
