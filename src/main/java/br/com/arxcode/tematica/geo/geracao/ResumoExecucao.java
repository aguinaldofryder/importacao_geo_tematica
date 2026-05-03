package br.com.arxcode.tematica.geo.geracao;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Acumulador de contadores para uma única execução do comando {@code importar}.
 *
 * <p><strong>Single-thread:</strong> esta classe usa contadores {@code int} primitivos
 * sem sincronização. A CLI é single-thread por design (NFR); uso concorrente
 * produziria contagens incorretas. Se concorrência for necessária no futuro,
 * migrar para {@code AtomicInteger}.
 *
 * <p><strong>Ciclo de vida obrigatório:</strong>
 * <ol>
 *   <li>Chamar {@link #iniciar()} antes de começar a iteração de linhas.</li>
 *   <li>Chamar os métodos de incremento durante a iteração.</li>
 *   <li>Chamar {@link #finalizar()} após gravar os artefatos.</li>
 *   <li>Chamar {@link #toResumoImutavel(Path, Path)} para obter o snapshot.</li>
 * </ol>
 *
 * <p><strong>Limitação v1 — {@code respostasAtualizadas}:</strong> o
 * {@link SqlGeradorUpsert} gera pares DELETE+INSERT; a distinção entre "par
 * já existia" e "par novo" só é conhecível em tempo de execução do SQL gerado
 * (fora do escopo desta ferramenta offline). Por isso, {@link #registrarRespostaAtualizada()}
 * existe na API para compatibilidade com a Arquitetura §7 e uso futuro, mas não é
 * invocado pelo {@code ImportarCommand} em v1 — o contador permanece {@code 0}.
 *
 * <p>Story: 5.1 — ResumoExecucao (contadores). Rastreia PRD FR-14, NFR-09.
 */
public class ResumoExecucao {

    private final Fluxo fluxo;
    private final String nomePlanilha;

    private int lidos;
    private int sucesso;
    private int erro;
    private int principalAtualizados;
    private int respostasAtualizadas;
    private int respostasInseridas;

    private Instant inicio;
    private Instant fim;

    /**
     * Cria um novo acumulador para a execução do fluxo e planilha indicados.
     *
     * @param fluxo        fluxo de importação (TERRITORIAL ou PREDIAL)
     * @param nomePlanilha nome do arquivo de planilha (para o snapshot)
     */
    public ResumoExecucao(Fluxo fluxo, String nomePlanilha) {
        this.fluxo = fluxo;
        this.nomePlanilha = nomePlanilha;
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    /** Captura o instante de início da importação (deve ser chamado antes da iteração). */
    public void iniciar() {
        this.inicio = Instant.now();
    }

    /** Captura o instante de fim da importação (deve ser chamado após gravar os artefatos). */
    public void finalizar() {
        this.fim = Instant.now();
    }

    // ── Incrementos ─────────────────────────────────────────────────────────

    /** Incrementa o contador de linhas lidas da planilha (excluindo cabeçalho). */
    public void incrementarLido() {
        lidos++;
    }

    /** Incrementa o contador de linhas processadas sem erro. */
    public void registrarSucesso() {
        sucesso++;
    }

    /** Incrementa o contador de linhas que falharam ou foram puladas. */
    public void registrarErro() {
        erro++;
    }

    /** Incrementa o contador de {@code UPDATE}s gerados na tabela principal. */
    public void registrarUpdatePrincipal() {
        principalAtualizados++;
    }

    /**
     * Incrementa o contador de respostas atualizadas na tabela de respostas.
     *
     * <p><strong>Nota v1:</strong> não invocado pelo {@code ImportarCommand} em v1
     * (ver Javadoc da classe). Mantido para compatibilidade com Arquitetura §7.
     */
    public void registrarRespostaAtualizada() {
        respostasAtualizadas++;
    }

    /** Incrementa o contador de respostas inseridas na tabela de respostas. */
    public void registrarRespostaInserida() {
        respostasInseridas++;
    }

    // ── Leituras ─────────────────────────────────────────────────────────────

    /** Retorna o total de respostas geradas ({@code atualizadas + inseridas}). */
    public int totalRespostas() {
        return respostasAtualizadas + respostasInseridas;
    }

    /** Expõe o contador de erros para inspeção (ex.: testes e Story 5.2). */
    public int erro() {
        return erro;
    }

    /**
     * Retorna a duração da execução.
     *
     * @throws IllegalStateException se {@link #finalizar()} ainda não foi chamado
     */
    public Duration duracao() {
        if (fim == null) {
            throw new IllegalStateException(
                    "finalizar() deve ser chamado antes de duracao()");
        }
        return Duration.between(inicio, fim);
    }

    /**
     * Cria um snapshot imutável com todos os contadores e metadados.
     * Usado pela camada de renderização (Story 5.2).
     *
     * @param arquivoSql  caminho do artefato {@code .sql} gerado
     * @param arquivoLog  caminho do artefato {@code .log} gerado
     * @return snapshot imutável pronto para renderização
     * @throws IllegalStateException se {@link #finalizar()} ainda não foi chamado
     */
    public ResumoSnapshot toResumoImutavel(Path arquivoSql, Path arquivoLog) {
        if (fim == null) {
            throw new IllegalStateException(
                    "finalizar() deve ser chamado antes de toResumoImutavel()");
        }
        return new ResumoSnapshot(
                fluxo,
                nomePlanilha,
                inicio,
                fim,
                lidos,
                sucesso,
                erro,
                principalAtualizados,
                respostasAtualizadas,
                respostasInseridas,
                arquivoSql,
                arquivoLog
        );
    }
}
