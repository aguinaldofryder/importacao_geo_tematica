package br.com.arxcode.tematica.geo.mapeamento;

import br.com.arxcode.tematica.geo.dominio.excecao.CabecalhoDuplicadoException;
import br.com.arxcode.tematica.geo.dominio.excecao.ColunaCodigoAusenteException;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Fase 1 do pipeline de importação: classifica os cabeçalhos brutos de uma
 * planilha {@code .xlsx} em três categorias mutuamente exclusivas — código,
 * fixas e dinâmicas — produzindo um {@link Classificacao}.
 *
 * <p><strong>Características:</strong>
 * <ul>
 *   <li><em>Stateless e thread-safe.</em> Toda entrada chega por argumento;
 *       não há campo mutável.</li>
 *   <li><em>Não é {@code @ApplicationScoped}.</em> Quem precisa do classificador
 *       em CDI o instancia explicitamente (Story 3.4). Isto mantém os testes
 *       unitários puros (sem {@code @QuarkusTest}).</li>
 *   <li><em>Normalização configurável</em> ({@code caseSensitive}, {@code trimEspacos})
 *       passada por construtor, espelhando {@code ImportacaoConfig.Mapeamento}.
 *       Uso de {@link Locale#ROOT} no {@code toLowerCase} é deliberado para
 *       evitar o "Turkish locale bug".</li>
 *   <li><em>Ordem preservada</em> em {@code fixas} (via {@link LinkedHashMap})
 *       e em {@code dinamicas} conforme aparecem em {@code headers}.</li>
 * </ul>
 *
 * <p><strong>Precedência de classificação</strong> (importante para determinismo):
 * {@code codigo > fixas > dinamicas}. Um header que case com o nome da coluna
 * de código vai apenas para o slot {@code codigo}, mesmo que também esteja
 * listado em {@code colunasFixasConhecidas}.
 *
 * <p>Story: 2.4 — ClassificadorColunas.
 */
public class ClassificadorColunas {

    private final boolean caseSensitive;
    private final boolean trimEspacos;

    /**
     * Construtor de conveniência alinhado com os defaults do
     * {@code ImportacaoConfig.Mapeamento}: {@code caseSensitive=false},
     * {@code trimEspacos=true}.
     */
    public ClassificadorColunas() {
        this(false, true);
    }

    public ClassificadorColunas(boolean caseSensitive, boolean trimEspacos) {
        this.caseSensitive = caseSensitive;
        this.trimEspacos = trimEspacos;
    }

    /**
     * Classifica os cabeçalhos da planilha.
     *
     * @param headers cabeçalhos lidos da planilha, na ordem original
     * @param nomeColunaCodigo nome esperado da coluna de código do imóvel
     * @param colunasFixasConhecidas conjunto de cabeçalhos a tratar como fixas
     *                               (pode ser vazio; nunca {@code null})
     * @return {@link Classificacao} com os três slots preenchidos
     * @throws ColunaCodigoAusenteException se {@code nomeColunaCodigo} não
     *                                      aparece em {@code headers}
     * @throws CabecalhoDuplicadoException se houver duplicatas após normalização
     * @throws ImportacaoException em caso de violação de contrato de entrada
     */
    public Classificacao classificar(List<String> headers,
                                     String nomeColunaCodigo,
                                     Set<String> colunasFixasConhecidas) {

        // (1) validação de entrada — AC8
        if (headers == null || headers.isEmpty()) {
            throw new ImportacaoException("Lista de cabeçalhos vazia ou nula.");
        }
        if (nomeColunaCodigo == null || nomeColunaCodigo.isBlank()) {
            throw new ImportacaoException("Nome da coluna de código não informado.");
        }
        if (colunasFixasConhecidas == null) {
            throw new ImportacaoException("Conjunto de colunas fixas conhecidas não pode ser nulo (use Set vazio se não houver).");
        }

        // (2) detecta duplicatas após normalização — AC6
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (String header : headers) {
            String chave = normalizar(header);
            contagem.merge(chave, 1, Integer::sum);
        }
        Map<String, Integer> duplicatas = contagem.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a, TreeMap::new));
        if (!duplicatas.isEmpty()) {
            throw new CabecalhoDuplicadoException(duplicatas);
        }

        // (3) normaliza referências para o mesmo formato dos headers
        String codigoNormalizado = normalizar(nomeColunaCodigo);
        Set<String> fixasNormalizadas = colunasFixasConhecidas.stream()
            .map(this::normalizar)
            .collect(Collectors.toSet());

        // (4) classificação com precedência codigo > fixas > dinamicas — AC2/AC3/AC4/AC10(l)
        String codigoEncontrado = null;
        Map<String, String> fixas = new LinkedHashMap<>();
        List<String> dinamicas = new ArrayList<>();

        for (String header : headers) {
            String norm = normalizar(header);
            if (norm.equals(codigoNormalizado)) {
                codigoEncontrado = header;
                continue;
            }
            if (fixasNormalizadas.contains(norm)) {
                fixas.put(header, header); // placeholder — Story 3.2 resolve
                continue;
            }
            dinamicas.add(header);
        }

        // (5) coluna de código obrigatória — AC5
        if (codigoEncontrado == null) {
            throw new ColunaCodigoAusenteException(nomeColunaCodigo, headers);
        }

        return new Classificacao(codigoEncontrado, fixas, dinamicas);
    }

    /**
     * Normaliza string para comparação. {@link Locale#ROOT} é deliberado
     * para evitar o "Turkish locale bug" em ambientes com locale exótico.
     */
    private String normalizar(String s) {
        if (s == null) {
            return null;
        }
        String r = trimEspacos ? s.trim() : s;
        return caseSensitive ? r : r.toLowerCase(Locale.ROOT);
    }
}
