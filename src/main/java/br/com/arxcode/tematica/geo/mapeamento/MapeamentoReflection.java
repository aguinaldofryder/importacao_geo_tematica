package br.com.arxcode.tematica.geo.mapeamento;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Classe marker (package-private) que centraliza o registro de reflexão dos
 * tipos do pacote {@code mapeamento} para sobrevivência ao GraalVM
 * {@code native-image}.
 *
 * <p>Padrão escolhido por {@code CONVENCOES.md} §Native Image, opção (b) —
 * espelhando {@code DominioReflection} da Story 2.2: quando há ≥ 3 tipos
 * correlatos no mesmo pacote, uma única classe marker com
 * {@link RegisterForReflection#targets()} é preferida em relação a anotar
 * cada record individualmente.
 *
 * <p><strong>Targets registrados:</strong>
 * <ul>
 *   <li>{@link Classificacao} — Story 2.4 (resultado da Fase 1 do pipeline).</li>
 *   <li>{@link Mapeamento} — Story 3.1 (forma serializada do {@code mapping.json}).</li>
 *   <li>{@link ColunaDinamica} — Story 3.1 (entrada de {@code colunasDinamicas}).</li>
 * </ul>
 *
 * <p><strong>Por que enums não entram em {@code targets}:</strong>
 * {@link StatusMapeamento} (3.1) e {@code Tipo}/{@code Fluxo} (2.2)
 * são deliberadamente omitidos. O GraalVM {@code native-image} trata enums
 * Java sem reflexão (constantes resolvidas em compile time).
 *
 * <p>A validação completa do {@code reflect-config} efetivo em binário nativo
 * é responsabilidade das Stories 6.1 (perfil nativo) e 6.3 (e2e binário).
 *
 * <p>Stories: 2.4 — ClassificadorColunas; 3.1 — MapeamentoStore.
 */
@RegisterForReflection(targets = {Classificacao.class, Mapeamento.class, ColunaDinamica.class})
final class MapeamentoReflection {

    private MapeamentoReflection() {
        // classe marker — não instanciável
    }
}
