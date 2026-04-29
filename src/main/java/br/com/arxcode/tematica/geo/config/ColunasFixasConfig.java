package br.com.arxcode.tematica.geo.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;
import java.util.Set;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Catálogo externo (configurável via {@code application.properties}) de cabeçalhos
 * que devem ser tratados como <em>colunas fixas</em> para cada fluxo de importação.
 *
 * <p>Convenção de chaves (kebab-case alinhado com {@code importacao.mapeamento.case-sensitive}):
 * <ul>
 *   <li>{@code importacao.colunas-fixas.territorial=AREA_TERRENO,TESTADA,...}</li>
 *   <li>{@code importacao.colunas-fixas.predial=PADRAO_CONSTRUTIVO,...}</li>
 * </ul>
 *
 * <p>Quando a propriedade não é definida, os métodos públicos {@link #territorial()}
 * e {@link #predial()} retornam um conjunto <strong>vazio</strong>. Internamente
 * usamos {@code Optional<Set<String>>} porque o conversor padrão do SmallRye trata
 * a string vazia ({@code ""}) como {@code null} para coleções (SRCFG00040), o que
 * impede o uso direto de {@code @WithDefault("")} em {@code Set<String>}. O método
 * {@code default} acima resolve isto sem expor {@code Optional} no contrato público.
 *
 * <p>O {@code ClassificadorColunas} <strong>não</strong> depende desta interface
 * diretamente — quem decide qual conjunto passar (Territorial vs Predial) é o
 * orquestrador (Story 3.4 — comando {@code mapear}). Isto preserva a função pura
 * do classificador (testabilidade unitária sem CDI).
 *
 * <p>Story: 2.4 — ClassificadorColunas.
 */
@ConfigMapping(prefix = "importacao.colunas-fixas")
public interface ColunasFixasConfig {

    @WithName("territorial")
    Optional<Set<String>> territorialOpt();

    @WithName("predial")
    Optional<Set<String>> predialOpt();

    default Set<String> territorial() {
        return territorialOpt().orElse(Set.of());
    }

    default Set<String> predial() {
        return predialOpt().orElse(Set.of());
    }

    /**
     * Retorna o conjunto de colunas fixas conhecidas para o {@code fluxo} informado.
     * Método de conveniência adicionado pela Story 3.4 para uso pelo
     * {@code MapearCommand} (orquestrador), evitando duplicação de {@code switch}
     * no caller.
     *
     * @param fluxo {@link Fluxo#TERRITORIAL} ou {@link Fluxo#PREDIAL}
     * @return conjunto (possivelmente vazio) de cabeçalhos fixos
     * @throws IllegalArgumentException se {@code fluxo == null}
     */
    default Set<String> por(Fluxo fluxo) {
        if (fluxo == null) {
            throw new IllegalArgumentException("Fluxo não pode ser nulo em ColunasFixasConfig.por(Fluxo).");
        }
        return switch (fluxo) {
            case TERRITORIAL -> territorial();
            case PREDIAL -> predial();
        };
    }
}
