package br.com.arxcode.tematica.geo.dominio;

/**
 * Tipo de dado de um {@link Campo} no catálogo dinâmico (tabela {@code campo.tipo}).
 *
 * <p>Os 4 valores espelham exatamente os literais aceitos na coluna {@code tipo}
 * do catálogo IPTU. Não há método de coerção {@code String → Tipo} de célula da
 * planilha aqui — esse parsing/coerção é responsabilidade da Story 4.1.
 *
 * <p>Consumidores: Story 2.3 (repositório de campos), Story 2.4 (classificador),
 * Story 3.1 (serialização do {@code mapping.json}), Story 4.1 (coerção de valores).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
public enum Tipo {
    TEXTO,
    DECIMAL,
    DATA,
    MULTIPLA_ESCOLHA
}
