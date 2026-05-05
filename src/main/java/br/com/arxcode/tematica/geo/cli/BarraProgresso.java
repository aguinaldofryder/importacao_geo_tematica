package br.com.arxcode.tematica.geo.cli;

import java.io.PrintWriter;

/**
 * Renderiza uma barra de progresso em tempo real no terminal durante a execução
 * do comando {@code importar}.
 *
 * <p>Formato de saída (largura fixa de 40 caracteres de progresso):
 * <pre>
 *   \r  [=========>                              ]  847/1204 (70%)  erros: 17
 * </pre>
 *
 * <p>Regras do caractere de cursor ({@code >}):
 * <ul>
 *   <li>Quando {@code 0 < linhaAtual < totalLinhas}: {@code >} aparece na posição
 *       {@code blocoPreenchido}, seguido de espaços até 40.</li>
 *   <li>Quando {@code linhaAtual == 0}: barra inicia com {@code >} seguido de 39 espaços.</li>
 *   <li>Quando {@code linhaAtual == totalLinhas} (100%): barra cheia de {@code =} sem {@code >}.</li>
 * </ul>
 *
 * <p><strong>Guard de TTY (AC9):</strong> se {@code System.console() == null} ou
 * {@code totalLinhas == 0}, todos os métodos são no-op — a barra não é emitida
 * em redirecionamentos, pipes ou CI/CD.
 *
 * <p><strong>Não-CDI:</strong> instância por execução, construída em
 * {@code ImportarCommand.call()} — mesmo padrão de {@code LogErros} e
 * {@code ResumoExecucao}.
 *
 * <p>Story: 5.3 — Barra de progresso durante a importação.
 */
public class BarraProgresso {

    /** Largura em caracteres da região de progresso (entre colchetes). */
    private static final int LARGURA = 40;

    private final int totalLinhas;
    private final PrintWriter err;

    /**
     * {@code true} quando a barra deve ser emitida: {@code totalLinhas > 0}
     * E o processo está conectado a um TTY real ({@code System.console() != null}).
     */
    private final boolean ativo;

    /**
     * Cria a barra de progresso.
     *
     * @param totalLinhas número total de linhas de dados da planilha (excluindo cabeçalho)
     * @param err         writer de stderr onde a barra será impressa
     */
    public BarraProgresso(int totalLinhas, PrintWriter err) {
        this.totalLinhas = totalLinhas;
        this.err = err;
        this.ativo = totalLinhas > 0 && System.console() != null;
    }

    /**
     * Atualiza a barra de progresso para a linha atual.
     *
     * <p>No-op se {@link #ativo} for {@code false}.
     *
     * @param linhaAtual número de linhas processadas até o momento
     * @param erros      número de linhas com erro ou puladas até o momento
     */
    public void atualizar(int linhaAtual, int erros) {
        if (!ativo) {
            return;
        }
        err.print(construirBarra(linhaAtual, erros));
        err.flush();
    }

    /**
     * Finaliza a barra emitindo {@code '\n'} para que o próximo output apareça
     * em uma nova linha no terminal.
     *
     * <p>No-op se {@link #ativo} for {@code false}.
     */
    public void finalizar() {
        if (!ativo) {
            return;
        }
        err.print('\n');
        err.flush();
    }

    /**
     * Constrói a string da barra para os valores fornecidos.
     *
     * <p>Visibilidade package-private para permitir testes unitários diretos
     * sem depender da detecção de TTY ({@link #ativo}).
     *
     * @param linhaAtual número de linhas processadas
     * @param erros      número de linhas com erro ou puladas
     * @return string formatada com {@code \r} no início
     */
    String construirBarra(int linhaAtual, int erros) {
        int blocoPreenchido = (linhaAtual * LARGURA) / totalLinhas;
        int pct = (linhaAtual * 100) / totalLinhas;

        StringBuilder sb = new StringBuilder(80);
        sb.append('\r').append("  [");

        if (linhaAtual == totalLinhas) {
            // 100%: barra cheia, sem cursor
            for (int i = 0; i < LARGURA; i++) {
                sb.append('=');
            }
        } else {
            // preenchido com '='
            for (int i = 0; i < blocoPreenchido; i++) {
                sb.append('=');
            }
            // cursor '>'
            sb.append('>');
            // espaços restantes
            for (int i = blocoPreenchido + 1; i < LARGURA; i++) {
                sb.append(' ');
            }
        }

        sb.append(']');
        sb.append(String.format("  %d/%d (%d%%)", linhaAtual, totalLinhas, pct));
        sb.append(String.format("  erros: %d", erros));

        return sb.toString();
    }
}
