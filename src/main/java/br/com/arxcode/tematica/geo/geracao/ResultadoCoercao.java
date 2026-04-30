package br.com.arxcode.tematica.geo.geracao;

/**
 * Resultado da coerção de uma célula da planilha em literal SQL tipado.
 *
 * <p><strong>Forma:</strong> em sucesso, {@link #literalSql} contém o literal
 * <em>pronto para concatenação</em> direta no SQL gerado — incluindo aspas
 * para texto/data ({@code 'O''Brien'}, {@code DATE '2025-01-01'}) e <em>sem</em>
 * aspas para números ({@code 1234.56}, {@code 42}). O caller (Stories 4.2 / 4.3
 * — {@code SqlGeradorUpdate} / {@code SqlGeradorUpsert}) faz apenas concatenação.
 *
 * <p><strong>Em falha:</strong> {@link #literalSql} é {@code null} e {@link #erro}
 * traz a mensagem em português a ser registrada no {@code .log} (Story 4.4
 * {@code LogErros}). A coerção <em>não</em> lança exceção por linha — falhas são
 * dados, não fluxo de controle (PRD FR-13: "registrando erro no .log em falhas").
 *
 * <p><strong>Invariantes</strong> (validados no construtor canônico):
 * <ul>
 *   <li>{@code ok=true} ⇒ {@code literalSql != null} e {@code erro == null}</li>
 *   <li>{@code ok=false} ⇒ {@code literalSql == null} e {@code erro != null}</li>
 * </ul>
 *
 * <p>Story: 4.1 — Coerção de tipos.
 */
public record ResultadoCoercao(String literalSql, boolean ok, String erro) {

    public ResultadoCoercao {
        if (ok && literalSql == null) {
            throw new IllegalArgumentException("literalSql não pode ser nulo quando ok=true");
        }
        if (!ok && erro == null) {
            throw new IllegalArgumentException("erro não pode ser nulo quando ok=false");
        }
    }

    /**
     * Fábrica de sucesso. {@code literal} deve estar pronto para concatenação
     * direta (com aspas se for texto/data; sem aspas se for número/{@code NULL}).
     *
     * @throws IllegalArgumentException se {@code literal} for {@code null}
     */
    public static ResultadoCoercao ok(String literal) {
        return new ResultadoCoercao(literal, true, null);
    }

    /**
     * Fábrica de falha. {@code erro} é a mensagem PT a ser registrada no
     * {@code .log} pela Story 4.4.
     *
     * @throws IllegalArgumentException se {@code erro} for {@code null}
     */
    public static ResultadoCoercao falha(String erro) {
        return new ResultadoCoercao(null, false, erro);
    }
}
