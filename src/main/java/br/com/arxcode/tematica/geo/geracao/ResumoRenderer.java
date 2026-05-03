package br.com.arxcode.tematica.geo.geracao;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renderiza um {@link ResumoSnapshot} em dois formatos:
 * bloco ASCII para exibição no terminal (Arquitetura §7.1) e
 * linha JSON compacta para o arquivo {@code .log} (Arquitetura §7.2).
 *
 * <p>Todos os métodos são estáticos e sem estado — classe utilitária, sem instâncias.
 *
 * <p>Story: 5.2 — Resumo: formatação ASCII + JSON + exit codes. Rastreia PRD FR-15, FR-16.
 */
public final class ResumoRenderer {

    /** Linha separadora dupla (moldura externa). */
    private static final String SEP_DUPLO   = "========================================================";

    /** Linha separadora simples (divisores de seção). */
    private static final String SEP_SIMPLES = "--------------------------------------------------------";

    /** Formatador de timestamp no padrão {@code yyyy-MM-dd HH:mm:ss}. */
    private static final DateTimeFormatter FMT_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Locale pt-BR: usa ponto como separador de milhar. */
    private static final Locale LOCALE_BR = new Locale("pt", "BR");

    /** Largura da coluna de label (com prefixo de espaços), antes de {@code ": "}. */
    private static final int LARGURA_LABEL = 25;

    /** Largura reservada para o número formatado (alinhamento direita). */
    private static final int LARGURA_NUMERO = 5;

    private ResumoRenderer() {
        /* utilitário — sem instâncias */
    }

    /**
     * Renderiza o resumo em bloco ASCII formatado conforme Arquitetura §7.1.
     *
     * <p>Formato:
     * <pre>
     * ========================================================
     *   RESUMO DA IMPORTAÇÃO — territorial
     *   Planilha : TABELA_TERRITORIAL_V001.xlsx
     *   Início   : 2026-04-20 14:32:10
     *   Fim      : 2026-04-20 14:34:47
     *   Duração  : 00:02:37
     * --------------------------------------------------------
     *   Registros lidos       :  1.204
     *   Registros com sucesso :  1.187
     *   Registros com erro    :     17
     * --------------------------------------------------------
     *   Tabela principal (tribcadastroimobiliario)
     *     UPDATEs gerados     :  1.187
     * --------------------------------------------------------
     *   Tabela de respostas (respostaterreno)
     *     Atualizados         :  4.210
     *     Inseridos           :    893
     *     Total               :  5.103
     * ========================================================
     *   Artefatos:
     *     SQL : ./saida/saida-territorial-20260420-143447.sql
     *     LOG : ./saida/saida-territorial-20260420-143447.log
     * ========================================================
     * </pre>
     *
     * @param r snapshot imutável da execução
     * @return string multi-linha pronta para exibição no terminal (termina com {@code \n})
     */
    public static String renderizarAscii(ResumoSnapshot r) {
        NumberFormat nf = NumberFormat.getIntegerInstance(LOCALE_BR);

        String inicioStr = FMT_TS.format(r.inicio().atZone(ZoneId.systemDefault()));
        String fimStr    = FMT_TS.format(r.fim().atZone(ZoneId.systemDefault()));
        String durStr    = formatarDuracao(r);
        String fluxoNome = r.fluxo().name().toLowerCase(LOCALE_BR);

        StringBuilder sb = new StringBuilder();
        String ls = System.lineSeparator();

        sb.append(SEP_DUPLO).append(ls);
        sb.append("  RESUMO DA IMPORTAÇÃO — ").append(fluxoNome).append(ls);
        sb.append("  Planilha : ").append(r.nomePlanilha()).append(ls);
        sb.append("  Início   : ").append(inicioStr).append(ls);
        sb.append("  Fim      : ").append(fimStr).append(ls);
        sb.append("  Duração  : ").append(durStr).append(ls);

        sb.append(SEP_SIMPLES).append(ls);
        sb.append(linhaContador("  Registros lidos",       r.lidos(),               nf)).append(ls);
        sb.append(linhaContador("  Registros com sucesso", r.sucesso(),             nf)).append(ls);
        sb.append(linhaContador("  Registros com erro",    r.erro(),                nf)).append(ls);

        sb.append(SEP_SIMPLES).append(ls);
        sb.append("  Tabela principal (").append(r.fluxo().tabelaPrincipal()).append(")").append(ls);
        sb.append(linhaContador("    UPDATEs gerados",     r.principalAtualizados(), nf)).append(ls);

        sb.append(SEP_SIMPLES).append(ls);
        sb.append("  Tabela de respostas (").append(r.fluxo().tabelaRespostas()).append(")").append(ls);
        sb.append(linhaContador("    Atualizados",         r.respostasAtualizadas(), nf)).append(ls);
        sb.append(linhaContador("    Inseridos",           r.respostasInseridas(),   nf)).append(ls);
        sb.append(linhaContador("    Total",               r.totalRespostas(),       nf)).append(ls);

        sb.append(SEP_DUPLO).append(ls);
        sb.append("  Artefatos:").append(ls);
        sb.append("    SQL : ").append(r.arquivoSql()).append(ls);
        sb.append("    LOG : ").append(r.arquivoLog()).append(ls);
        sb.append(SEP_DUPLO).append(ls);

        return sb.toString();
    }

    /**
     * Renderiza o resumo como linha JSON compacta conforme Arquitetura §7.2.
     *
     * <p>Exemplo de saída:
     * <pre>
     * {"evento":"resumo","fluxo":"territorial","lidos":1204,"sucesso":1187,"erro":17,
     *  "principal_updates":1187,"respostas_update":4210,"respostas_insert":893,
     *  "respostas_total":5103,"duracao_ms":157000}
     * </pre>
     *
     * @param r snapshot imutável da execução
     * @return string JSON de uma única linha, sem quebra de linha final
     */
    public static String renderizarJsonLine(ResumoSnapshot r) {
        String fluxoNome = r.fluxo().name().toLowerCase(LOCALE_BR);
        return "{\"evento\":\"resumo\""
                + ",\"fluxo\":\"" + fluxoNome + "\""
                + ",\"lidos\":" + r.lidos()
                + ",\"sucesso\":" + r.sucesso()
                + ",\"erro\":" + r.erro()
                + ",\"principal_updates\":" + r.principalAtualizados()
                + ",\"respostas_update\":" + r.respostasAtualizadas()
                + ",\"respostas_insert\":" + r.respostasInseridas()
                + ",\"respostas_total\":" + r.totalRespostas()
                + ",\"duracao_ms\":" + r.duracao().toMillis()
                + "}";
    }

    // ── Auxiliares privados ──────────────────────────────────────────────────

    /**
     * Formata uma linha de contador com label à esquerda (padding fixo) e número
     * à direita (alinhado).
     *
     * @param label rótulo sem padding (ex.: {@code "  Registros lidos"})
     * @param valor valor inteiro do contador
     * @param nf    formatador de número com locale pt-BR
     * @return linha formatada sem separador de linha
     */
    private static String linhaContador(String label, int valor, NumberFormat nf) {
        String labelPadded = String.format("%-" + LARGURA_LABEL + "s", label);
        String numFormatado = String.format("%" + LARGURA_NUMERO + "s", nf.format(valor));
        return labelPadded + ": " + numFormatado;
    }

    /**
     * Formata a duração do snapshot no padrão {@code HH:MM:SS}.
     *
     * @param r snapshot com início e fim definidos
     * @return string no formato {@code "00:02:37"}
     */
    private static String formatarDuracao(ResumoSnapshot r) {
        long totalSecs = r.duracao().getSeconds();
        long hours   = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
