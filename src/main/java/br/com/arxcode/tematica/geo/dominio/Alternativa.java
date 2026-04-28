package br.com.arxcode.tematica.geo.dominio;

/**
 * Representa uma linha da tabela {@code alternativa} (alternativas de campos
 * {@link Tipo#MULTIPLA_ESCOLHA}).
 *
 * <p>Invariantes de construção:
 * <ul>
 *   <li>{@code descricao != null} (lança {@link IllegalArgumentException} em PT);</li>
 *   <li>{@code descricao} é normalizada com {@link String#trim()} no construtor canônico
 *       — alinhamento com {@code importacao.mapeamento.trim-espacos=true} (Arquitetura §6).</li>
 * </ul>
 *
 * <p>Serialização Jackson: o construtor canônico é suficiente. Registro de reflexão
 * centralizado em {@link DominioReflection}.
 *
 * <p>Consumidores: Story 2.3 (repositório), Story 3.x (mapeamento {@code MULTIPLA_ESCOLHA}).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
public record Alternativa(long id, String descricao, long idCampo) {

    public Alternativa {
        if (descricao == null) {
            throw new IllegalArgumentException("descricao não pode ser nula em Alternativa");
        }
        descricao = descricao.trim();
    }
}
