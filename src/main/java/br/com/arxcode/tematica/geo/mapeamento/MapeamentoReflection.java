package br.com.arxcode.tematica.geo.mapeamento;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Classe marker (package-private) que centraliza o registro de reflexão dos
 * tipos do pacote {@code mapeamento} para sobrevivência ao GraalVM
 * {@code native-image}.
 *
 * <p>Padrão escolhido por {@code CONVENCOES.md} §Native Image, opção (b) —
 * espelhando {@code DominioReflection} da Story 2.2.
 *
 * <p>Story: 2.4 — ClassificadorColunas.
 */
@RegisterForReflection(targets = {Classificacao.class})
final class MapeamentoReflection {

    private MapeamentoReflection() {
        // classe marker — não instanciável
    }
}
