package br.com.arxcode.tematica.geo.geracao;

import java.util.List;

/**
 * Resultado da geração de INSERT para uma nova construção em
 * {@code aise.tribimobiliariosegmento} ({@link SqlGeradorInsertSegmento}).
 *
 * <p>Em <strong>sucesso</strong>: contém o SQL do INSERT e o subselect de
 * referência pronto para uso nos INSERTs de {@code aise.respostasegmento}.
 *
 * <p>Em <strong>falha</strong>: contém a lista de erros de coerção (PT);
 * {@link #sql()} e {@link #referenciaSubselect()} são {@code null}.
 *
 * <p><strong>Imutável.</strong> Fábricas estáticas em vez de construtores
 * públicos — padrão dos demais resultados do projeto ({@link ResultadoUpdate},
 * {@link ResultadoUpsert}).
 */
public final class ResultadoInsertSegmento {

    private final String sql;
    private final String referenciaSubselect;
    private final List<String> erros;

    private ResultadoInsertSegmento(String sql, String referenciaSubselect, List<String> erros) {
        this.sql = sql;
        this.referenciaSubselect = referenciaSubselect;
        this.erros = erros;
    }

    /**
     * Cria resultado de sucesso com o SQL do INSERT e o subselect de referência.
     *
     * @param sql                 INSERT completo pronto para o arquivo {@code .sql}
     * @param referenciaSubselect subselect {@code (SELECT idkey FROM ... WHERE ...)} para
     *                            uso como valor da coluna {@code referencia} em {@code respostasegmento}
     */
    public static ResultadoInsertSegmento sucesso(String sql, String referenciaSubselect) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql não pode ser nulo ou vazio em resultado de sucesso.");
        }
        if (referenciaSubselect == null || referenciaSubselect.isBlank()) {
            throw new IllegalArgumentException("referenciaSubselect não pode ser nulo ou vazio em resultado de sucesso.");
        }
        return new ResultadoInsertSegmento(sql, referenciaSubselect, List.of());
    }

    /**
     * Cria resultado de falha com a lista de erros de coerção.
     *
     * @param erros lista não-vazia de mensagens de erro em PT
     */
    public static ResultadoInsertSegmento falha(List<String> erros) {
        if (erros == null || erros.isEmpty()) {
            throw new IllegalArgumentException("erros não pode ser nulo ou vazio em resultado de falha.");
        }
        return new ResultadoInsertSegmento(null, null, List.copyOf(erros));
    }

    /** {@code true} se a geração foi bem-sucedida; {@code false} em caso de falha. */
    public boolean ok() {
        return sql != null;
    }

    /**
     * SQL do INSERT em {@code aise.tribimobiliariosegmento}.
     * {@code null} em caso de falha.
     */
    public String sql() {
        return sql;
    }

    /**
     * Subselect pronto para uso como valor da coluna {@code referencia} nos INSERTs
     * de {@code aise.respostasegmento}. Tem a forma:
     * <pre>(SELECT idkey FROM aise.tribimobiliariosegmento WHERE tipocadastro = 1
     *  AND cadastrogeral = &lt;n&gt; AND sequencia = &lt;s&gt;)</pre>
     * {@code null} em caso de falha.
     */
    public String referenciaSubselect() {
        return referenciaSubselect;
    }

    /**
     * Erros de coerção das colunas fixas (PT). Lista vazia em caso de sucesso.
     */
    public List<String> erros() {
        return erros;
    }
}
