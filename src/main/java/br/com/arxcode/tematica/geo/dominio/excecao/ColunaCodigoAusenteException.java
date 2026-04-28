package br.com.arxcode.tematica.geo.dominio.excecao;

import java.util.List;

/**
 * Lançada quando o cabeçalho da coluna de código do imóvel não é encontrado
 * na planilha. Subtipo de {@link ImportacaoException} para permitir tratamento
 * específico no comando {@code mapear} (Story 3.4) — exit code distinto e
 * mensagem orientada ao operador.
 *
 * <p>Mensagem em português (NFR-01).
 *
 * <p>Story: 2.4 — ClassificadorColunas.
 */
public class ColunaCodigoAusenteException extends ImportacaoException {

    private static final long serialVersionUID = 1L;

    public ColunaCodigoAusenteException(String nomeColuna, List<String> cabecalhosDisponiveis) {
        super("Coluna de código '" + nomeColuna + "' não encontrada na planilha. "
            + "Cabeçalhos disponíveis: " + cabecalhosDisponiveis + ".");
    }
}
