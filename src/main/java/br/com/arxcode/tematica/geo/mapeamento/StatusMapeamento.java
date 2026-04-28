package br.com.arxcode.tematica.geo.mapeamento;

/**
 * Estado de mapeamento de uma coluna dinâmica no {@code mapping.json}.
 *
 * <ul>
 *   <li>{@link #MAPEADO} — coluna foi resolvida contra o catálogo
 *       ({@code idcampo}, {@code tipo} e, quando aplicável, {@code alternativas}
 *       estão presentes).</li>
 *   <li>{@link #PENDENTE} — coluna não foi resolvida automaticamente; carrega
 *       {@code motivo} e, opcionalmente, {@code sugestoes} para orientar a
 *       edição manual do arquivo (FR-08).</li>
 * </ul>
 *
 * <p>O <strong>gate de execução</strong> que recusa rodar o subcomando
 * {@code importar} enquanto qualquer item permanecer {@link #PENDENTE}
 * (FR-09) é responsabilidade da Story 3.3 ({@code MapeamentoValidador}) —
 * aqui só são definidas as duas constantes.
 *
 * <p>Story: 3.1 — MapeamentoStore (Jackson JSON I/O do mapping.json).
 */
public enum StatusMapeamento {
    MAPEADO,
    PENDENTE
}
