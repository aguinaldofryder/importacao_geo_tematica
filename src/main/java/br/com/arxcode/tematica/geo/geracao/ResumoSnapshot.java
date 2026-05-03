package br.com.arxcode.tematica.geo.geracao;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Snapshot imutável dos contadores de uma execução do comando {@code importar}.
 *
 * <p>Criado por {@link ResumoExecucao#toResumoImutavel(Path, Path)} após
 * {@link ResumoExecucao#finalizar()} e consumido pela camada de renderização
 * (Story 5.2 — {@code ResumoRenderer}).
 *
 * <p>{@code @RegisterForReflection}: necessário para serialização Jackson em
 * GraalVM native-image (linha JSON estruturada no {@code .log} — Story 5.2).
 *
 * <p>Story: 5.1 — ResumoExecucao (contadores). Rastreia PRD FR-14, Arquitetura §7.
 *
 * @param fluxo                fluxo de importação (TERRITORIAL ou PREDIAL)
 * @param nomePlanilha         nome do arquivo de planilha processado
 * @param inicio               instante de início da importação
 * @param fim                  instante de fim da importação
 * @param lidos                total de linhas lidas (excluindo cabeçalho)
 * @param sucesso              linhas processadas sem erro
 * @param erro                 linhas que falharam ou foram puladas
 * @param principalAtualizados {@code UPDATE}s gerados na tabela principal
 * @param respostasAtualizadas respostas atualizadas na tabela de respostas (v1: sempre 0)
 * @param respostasInseridas   respostas inseridas na tabela de respostas
 * @param arquivoSql           caminho do artefato {@code .sql} gerado
 * @param arquivoLog           caminho do artefato {@code .log} gerado
 */
@RegisterForReflection
public record ResumoSnapshot(
        Fluxo fluxo,
        String nomePlanilha,
        Instant inicio,
        Instant fim,
        int lidos,
        int sucesso,
        int erro,
        int principalAtualizados,
        int respostasAtualizadas,
        int respostasInseridas,
        Path arquivoSql,
        Path arquivoLog
) {

    /**
     * Retorna o total de respostas geradas ({@code atualizadas + inseridas}).
     *
     * @return {@code respostasAtualizadas + respostasInseridas}
     */
    public int totalRespostas() {
        return respostasAtualizadas + respostasInseridas;
    }

    /**
     * Retorna a duração da execução calculada a partir dos instantes de início e fim.
     *
     * @return duração positiva entre {@link #inicio()} e {@link #fim()}
     */
    public Duration duracao() {
        return Duration.between(inicio, fim);
    }
}
