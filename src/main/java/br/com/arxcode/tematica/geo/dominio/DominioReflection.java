package br.com.arxcode.tematica.geo.dominio;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Classe marker (package-private) que centraliza o registro de reflexão dos
 * records de domínio para sobrevivência ao GraalVM {@code native-image}.
 *
 * <p>Padrão escolhido por {@code CONVENCOES.md} §Native Image, opção (b) — quando há
 * ≥ 3 tipos correlatos no mesmo pacote, uma classe marker com
 * {@link RegisterForReflection#targets()} é preferida em relação a anotar cada record
 * individualmente: centraliza a lista, evita poluição visual e facilita revisão.
 *
 * <p><strong>Por que enums não entram em {@code targets}:</strong> {@link Fluxo} e
 * {@link Tipo} são deliberadamente omitidos. O GraalVM {@code native-image} trata enums
 * Java sem reflexão (constantes resolvidas em compile time); incluí-los seria ruído
 * inofensivo, mas induz crença errada nas próximas stories.
 *
 * <p>A validação completa do {@code reflect-config} efetivo em binário nativo é
 * responsabilidade das Stories 6.1 (perfil nativo) e 6.3 (e2e binário). Esta classe
 * apenas deixa o registro pronto.
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
@RegisterForReflection(targets = {Campo.class, Alternativa.class, GrupoCampo.class})
final class DominioReflection {

    private DominioReflection() {
        // classe marker — não instanciável
    }
}
