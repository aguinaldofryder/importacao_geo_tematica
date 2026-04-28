package br.com.arxcode.tematica.geo.dominio;

/**
 * Representa uma linha da tabela {@code grupocampo} do catálogo dinâmico.
 *
 * <p>O componente {@code funcionalidade} carrega o discriminador {@code TERRENO} ou
 * {@code SEGMENTO} (vide {@link Fluxo#funcionalidade()}). <strong>Não</strong> aplicamos
 * {@link String#trim()} aqui de propósito: o valor vem do banco e precisa bater
 * exatamente com {@link Fluxo#funcionalidade()} para o classificador (Story 2.4).
 * Linhas com valores inesperados (incluindo espaços) devem aparecer no log do
 * classificador como ruído, e não falhar silenciosamente em construção.
 *
 * <p>Invariantes de construção:
 * <ul>
 *   <li>{@code funcionalidade != null} (lança {@link IllegalArgumentException} em PT).</li>
 * </ul>
 *
 * <p>Não há validação de domínio sobre o conteúdo de {@code funcionalidade} — o banco
 * é a fonte de verdade.
 *
 * <p>Serialização Jackson: o construtor canônico é suficiente. Registro de reflexão
 * centralizado em {@link DominioReflection}.
 *
 * <p>Consumidores: Story 2.3 (repositório), Story 2.4 (classificador).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
public record GrupoCampo(long id, String funcionalidade) {

    public GrupoCampo {
        if (funcionalidade == null) {
            throw new IllegalArgumentException("funcionalidade não pode ser nula em GrupoCampo");
        }
    }
}
