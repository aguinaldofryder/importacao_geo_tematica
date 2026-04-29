package br.com.arxcode.tematica.geo.mapeamento;

import java.util.List;

/**
 * Resultado da validação semântica do {@code mapping.json} pelo
 * {@link MapeamentoValidador} (FR-09).
 *
 * <p><strong>Invariante:</strong> {@code valido == pendencias.isEmpty()}.
 * O construtor compacto impõe este invariante; instâncias inconsistentes
 * nunca existem. Chamadores podem usar {@code valido} como gate booleano
 * e {@code pendencias} para exibir mensagens em português ao operador.
 *
 * <p>{@code pendencias} é uma cópia imutável (defensiva) da lista recebida —
 * o construtor chama {@link List#copyOf(java.util.Collection)} antes de
 * validar o invariante, garantindo que alterações externas à lista original
 * não afetem esta instância.
 *
 * <p>Story: 3.3 — MapeamentoValidador (gate PENDENTE).
 * Consumidores: Story 3.5 (subcomando {@code validar}),
 * Story 4.5 (subcomando {@code importar}).
 */
public record ResultadoValidacao(boolean valido, List<String> pendencias) {

    public ResultadoValidacao {
        if (pendencias == null) {
            throw new IllegalArgumentException("pendencias não pode ser nula em ResultadoValidacao");
        }
        pendencias = List.copyOf(pendencias);
        if (valido && !pendencias.isEmpty()) {
            throw new IllegalArgumentException(
                    "ResultadoValidacao: valido=true incompatível com pendencias não-vazia");
        }
        if (!valido && pendencias.isEmpty()) {
            throw new IllegalArgumentException(
                    "ResultadoValidacao: valido=false incompatível com pendencias vazia");
        }
    }
}
