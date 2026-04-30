package br.com.arxcode.tematica.geo.geracao;

import java.util.List;

/**
 * Resultado da geração de um {@code UPDATE} pela Story 4.2
 * ({@code SqlGeradorUpdate}). Forma análoga a {@link ResultadoCoercao} da
 * Story 4.1: o erro é dado, não exceção — o orquestrador (Story 4.5) decide
 * loga-lo no {@code .log} e prossegue com a próxima linha (PRD FR-13).
 *
 * <p><strong>Forma:</strong>
 * <ul>
 *   <li>Em sucesso, {@link #sql} contém a instrução {@code UPDATE ...} pronta
 *       para escrita direta no arquivo {@code .sql}, e {@link #erros} é uma
 *       lista vazia imutável.</li>
 *   <li>Em falha, {@link #sql} é {@code null} e {@link #erros} contém ao menos
 *       uma mensagem PT (todas as falhas de coerção da linha — agregadas para
 *       permitir ao operador corrigir N células em uma única passada).</li>
 * </ul>
 *
 * <p><strong>Invariantes</strong> (validados nas fábricas estáticas; o
 * construtor canônico apenas normaliza {@code erros} via
 * {@link List#copyOf(java.util.Collection)} — {@code null} vira lista vazia):
 * <ul>
 *   <li>{@link #sucesso(String)} exige {@code sql != null && !sql.isBlank()}.</li>
 *   <li>{@link #falha(List)} exige {@code erros != null && !erros.isEmpty()}.</li>
 * </ul>
 *
 * <p>Story: 4.2 — SqlGeradorUpdate.
 */
public record ResultadoUpdate(String sql, List<String> erros) {

    public ResultadoUpdate {
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    /**
     * Fábrica de sucesso. {@code sql} deve ser a instrução {@code UPDATE ...}
     * completa, terminada em {@code ;}.
     *
     * @throws IllegalArgumentException se {@code sql} for {@code null} ou em branco
     */
    public static ResultadoUpdate sucesso(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL de sucesso não pode ser nulo ou em branco.");
        }
        return new ResultadoUpdate(sql, List.of());
    }

    /**
     * Fábrica de falha. {@code erros} deve ser uma lista não-vazia de mensagens
     * em português a serem registradas no {@code .log} (Story 4.4).
     *
     * @throws IllegalArgumentException se {@code erros} for {@code null} ou vazio
     */
    public static ResultadoUpdate falha(List<String> erros) {
        if (erros == null || erros.isEmpty()) {
            throw new IllegalArgumentException("Lista de erros não pode ser nula ou vazia em ResultadoUpdate.falha.");
        }
        return new ResultadoUpdate(null, erros);
    }

    /**
     * @return {@code true} se este resultado é de sucesso (há SQL emitido);
     *         {@code false} se é falha.
     */
    public boolean ok() {
        return sql != null;
    }
}
