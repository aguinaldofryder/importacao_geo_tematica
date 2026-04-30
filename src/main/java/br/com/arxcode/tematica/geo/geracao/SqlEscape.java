package br.com.arxcode.tematica.geo.geracao;

/**
 * Utilitário estático para escapar literais SQL destinados ao PostgreSQL com
 * {@code standard_conforming_strings=on} (default desde PostgreSQL 9.1).
 *
 * <p><strong>Função pura, sem estado.</strong> Sem CDI, sem instância — todos
 * os métodos são estáticos e o construtor é privado para impedir instanciação.
 *
 * <p><strong>Por que existe?</strong> O escape de aspas simples é necessário em
 * múltiplos pontos do pipeline de geração de SQL (Stories 4.1, 4.2 e 4.3): no
 * literal {@code TEXTO} produzido pelo {@link Coercionador} (envolvido em
 * aspas), no {@code WHERE} do {@code SqlGeradorUpdate} (Story 4.2 — coerção do
 * {@code codigoImovel}) e no {@code WHERE}/{@code VALUES} do
 * {@code SqlGeradorUpsert} (Story 4.3). Centralizar o helper em um único ponto
 * fecha a {@code ISSUE-4.1-02} do gate da Story 4.1 e elimina duplicação.
 *
 * <p><strong>Convenção de escape:</strong> cada {@code '} vira {@code ''}.
 * Backslash é literal — não precisa escape porque não usamos prefixo
 * {@code E'...'}. Newline é literal aceito por PostgreSQL em string entre
 * aspas simples padrão.
 *
 * <p>Story: 4.2 — SqlGeradorUpdate (extração compartilhada com Story 4.1
 * e futura Story 4.3).
 */
public final class SqlEscape {

    private SqlEscape() {
        // Utilitário estático — não instanciável.
    }

    /**
     * Escapa aspas simples para uso em literal SQL PostgreSQL com
     * {@code standard_conforming_strings=on}: cada {@code '} é duplicado
     * para {@code ''}.
     *
     * @param s string a escapar (não-{@code null})
     * @return a mesma string com todas as aspas simples duplicadas
     * @throws NullPointerException se {@code s} for {@code null}
     *         (entrada {@code null} é defeito de programação no caller, não
     *         dado de planilha — a normalização de {@code null} para o literal
     *         {@code NULL} é responsabilidade do {@link Coercionador})
     */
    public static String aspas(String s) {
        return s.replace("'", "''");
    }
}
