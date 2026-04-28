package br.com.arxcode.tematica.geo.dominio.excecao;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lançada quando a planilha apresenta dois ou mais cabeçalhos que colidem
 * após normalização (case/trim conforme configuração). Validação ocorre
 * <strong>antes</strong> da classificação.
 *
 * <p>Mensagem em português (NFR-01) listando cada cabeçalho duplicado e
 * sua contagem.
 *
 * <p>Story: 2.4 — ClassificadorColunas.
 */
public class CabecalhoDuplicadoException extends ImportacaoException {

    private static final long serialVersionUID = 1L;

    public CabecalhoDuplicadoException(Map<String, Integer> contagensDuplicadas) {
        super("Cabeçalho duplicado detectado: " + formatar(contagensDuplicadas) + ".");
    }

    private static String formatar(Map<String, Integer> contagens) {
        return contagens.entrySet().stream()
            .map(e -> "'" + e.getKey() + "' aparece " + e.getValue() + " vezes")
            .collect(Collectors.joining("; "));
    }
}
