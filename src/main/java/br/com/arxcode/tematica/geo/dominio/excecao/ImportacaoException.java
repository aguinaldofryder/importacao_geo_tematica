package br.com.arxcode.tematica.geo.dominio.excecao;

/**
 * Exceção raiz da hierarquia de exceções do projeto.
 *
 * <p>Toda exceção de domínio/aplicação deste projeto deve estender esta classe
 * (direta ou indiretamente). Demais stories (4.4 log-erros-estruturado,
 * 4.5 comando-importar, etc.) herdarão desta exceção para implementar
 * subtipos específicos por categoria de erro.
 *
 * <p>Por ser {@link RuntimeException}, não requer declaração em {@code throws}
 * — alinhada com a prática de exceções não-checadas em domínios CLI/batch
 * onde a recuperação é por etapa (linha/comando), não por chamada de método.
 *
 * <p>Mensagens devem ser sempre em português (NFR-01).
 *
 * <p>Story: 2.1 — ExcelLeitor com fastexcel-reader (streaming).
 */
public class ImportacaoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImportacaoException(String message) {
        super(message);
    }

    public ImportacaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
