package br.com.arxcode.tematica.geo.dominio;

/**
 * Representa uma linha da tabela {@code campo} do catálogo dinâmico.
 *
 * <p>Invariantes de construção:
 * <ul>
 *   <li>{@code descricao != null} (lança {@link IllegalArgumentException} em PT);</li>
 *   <li>{@code tipo != null} (lança {@link IllegalArgumentException} em PT);</li>
 *   <li>{@code descricao} é normalizada com {@link String#trim()} no construtor canônico
 *       — alinhamento com {@code importacao.mapeamento.trim-espacos=true} (Arquitetura §6).</li>
 * </ul>
 *
 * <p>Falhas de invariante de construtor lançam {@link IllegalArgumentException}, não
 * {@link br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException} — esta última
 * é reservada para falhas recuperáveis de fluxo (linha de planilha), não para defeitos
 * de programação ou corrupção de banco.
 *
 * <p>Serialização Jackson: o construtor canônico é suficiente (Jackson 2.12+ suporta
 * records nativamente). Registro {@code @RegisterForReflection} é centralizado em
 * {@link DominioReflection} para sobrevivência ao {@code native-image}.
 *
 * <p>Consumidores: Story 2.3 (repositório), Story 2.4 (classificador), Story 3.x (mapeamento).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
public record Campo(long id, String descricao, Tipo tipo, boolean ativo, long idGrupoCampo) {

    public Campo {
        if (descricao == null) {
            throw new IllegalArgumentException("descricao não pode ser nula em Campo");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo não pode ser nulo em Campo");
        }
        descricao = descricao.trim();
    }
}
