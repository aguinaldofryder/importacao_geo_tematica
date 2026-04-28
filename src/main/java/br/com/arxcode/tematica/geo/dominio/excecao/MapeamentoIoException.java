package br.com.arxcode.tematica.geo.dominio.excecao;

/**
 * Falha de I/O ou parse durante leitura/escrita do {@code mapping.json} pela
 * fronteira do {@code MapeamentoStore} (Story 3.1).
 *
 * <p>Cenários típicos (mensagens em PT, NFR-01):
 * <ul>
 *   <li>Arquivo de mapeamento ausente.</li>
 *   <li>Arquivo sem permissão de leitura ({@code AccessDeniedException}).</li>
 *   <li>JSON sintaticamente inválido ({@code JsonParseException}).</li>
 *   <li>Campo obrigatório ausente — {@code fluxo}, {@code planilha}, {@code colunaCodigoImovel}.</li>
 *   <li>Valor de enum desconhecido em {@code status}/{@code tipo}/{@code fluxo}.</li>
 *   <li>Falha ao escrever o arquivo (E/S do sistema de arquivos).</li>
 * </ul>
 *
 * <p>A causa original deve ser preservada em {@link Throwable#getCause()}
 * para que rotinas superiores possam diagnosticar o erro.
 *
 * <p>Story: 3.1 — MapeamentoStore (Jackson JSON I/O do mapping.json).
 */
public class MapeamentoIoException extends ImportacaoException {

    private static final long serialVersionUID = 1L;

    public MapeamentoIoException(String message) {
        super(message);
    }

    public MapeamentoIoException(String message, Throwable cause) {
        super(message, cause);
    }
}
