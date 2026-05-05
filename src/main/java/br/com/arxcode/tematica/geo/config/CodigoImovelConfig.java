package br.com.arxcode.tematica.geo.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Catálogo externo (configurável via {@code application.properties}) do nome
 * físico da <em>coluna de código do imóvel</em> esperada na planilha {@code .xlsx}
 * para cada fluxo de importação.
 *
 * <p>Convenção de chaves (kebab-case alinhado com {@code importacao.colunas-fixas.*}):
 * <ul>
 *   <li>{@code importacao.codigo-imovel.territorial=MATRICULA}</li>
 *   <li>{@code importacao.codigo-imovel.predial=MATRICULA}</li>
 *   <li>{@code importacao.codigo-imovel.sequencia-predial=SEQUENCIA} — nome da coluna
 *       da planilha predial que carrega o valor da coluna {@code sequencia} da PK
 *       composta de {@code tribimobiliariosegmento}.</li>
 * </ul>
 *
 * <p>Quando a propriedade não é definida (ou está em branco), os métodos públicos
 * {@link #territorial()} e {@link #predial()} retornam {@code "MATRICULA"} —
 * default alinhado ao exemplo canônico da Arquitetura §5 e à fixture
 * {@code mapping-exemplo-territorial.json} da Story 3.1. {@link #sequenciaPredial()}
 * retorna {@code "SEQUENCIA"} como default. Internamente usamos
 * {@code Optional<String>} pois o conversor padrão SmallRye trata string vazia
 * como ausente para alguns tipos; o método {@code default} torna o contrato
 * público sem expor {@code Optional}.
 *
 * <p>Justificativa (FR-03 — configuração externa): o nome físico da coluna de
 * código pode variar entre municípios (ex.: {@code "INSCRICAO_IMOBILIARIA"} em
 * vez de {@code "MATRICULA"}); externalizar evita hardcoding sem perder o
 * default de Paranacity (banco {@code dump-paranacity.backup}).
 *
 * <p>O {@code MapearCommand} (Story 3.4) é o único consumidor — passa o valor
 * resolvido para o {@code ClassificadorColunas} (Story 2.4 AC5).
 *
 * <p>Story: 3.4 — Comando {@code mapear} (orquestrador da Fase 1+2).
 */
@ConfigMapping(prefix = "importacao.codigo-imovel")
public interface CodigoImovelConfig {

    String DEFAULT = "MATRICULA";
    String DEFAULT_SEQUENCIA_PREDIAL = "SEQUENCIA";

    @WithName("territorial")
    Optional<String> territorialOpt();

    @WithName("predial")
    Optional<String> predialOpt();

    @WithName("sequencia-predial")
    Optional<String> sequenciaPredialOpt();

    default String territorial() {
        return territorialOpt().filter(s -> !s.isBlank()).orElse(DEFAULT);
    }

    default String predial() {
        return predialOpt().filter(s -> !s.isBlank()).orElse(DEFAULT);
    }

    /**
     * Nome da coluna da planilha predial que carrega o valor de {@code sequencia}
     * (terceiro componente da PK de {@code tribimobiliariosegmento}).
     * Default: {@code "SEQUENCIA"}.
     */
    default String sequenciaPredial() {
        return sequenciaPredialOpt().filter(s -> !s.isBlank()).orElse(DEFAULT_SEQUENCIA_PREDIAL);
    }

    /**
     * Retorna o nome físico da coluna de código para o {@code fluxo} informado.
     *
     * @param fluxo {@link Fluxo#TERRITORIAL} ou {@link Fluxo#PREDIAL}
     * @return nome físico da coluna (default {@code "MATRICULA"} se ausente)
     * @throws IllegalArgumentException se {@code fluxo == null}
     */
    default String por(Fluxo fluxo) {
        if (fluxo == null) {
            throw new IllegalArgumentException("Fluxo não pode ser nulo em CodigoImovelConfig.por(Fluxo).");
        }
        return switch (fluxo) {
            case TERRITORIAL -> territorial();
            case PREDIAL -> predial();
        };
    }
}
