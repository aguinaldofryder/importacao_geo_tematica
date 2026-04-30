package br.com.arxcode.tematica.geo.mapeamento;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representa uma linha da planilha já lida e indexada por <em>header</em> da
 * planilha, pronta para a fase de geração de SQL (Stories 4.2 / 4.3).
 *
 * <p>Slots:
 * <ul>
 *   <li>{@code codigoImovel} — valor cru da célula identificada como chave do
 *       imóvel (a coluna apontada por {@link Mapeamento#colunaCodigoImovel()}).
 *       Sempre presente; será coagido como {@code Tipo.TEXTO} pelo
 *       {@code SqlGeradorUpdate} para gerar o {@code WHERE}.</li>
 *   <li>{@code celulasFixas} — valores das colunas fixas, indexados pelo
 *       <em>header</em> da planilha (a mesma chave usada em
 *       {@link Mapeamento#colunasFixas()}). Ordem de iteração preservada via
 *       {@link LinkedHashMap}. Pode estar vazio.</li>
 *   <li>{@code celulasDinamicas} — valores das colunas dinâmicas, indexados
 *       pelo <em>header</em> da planilha (a mesma chave usada em
 *       {@link Mapeamento#colunasDinamicas()}). Ordem de iteração preservada.
 *       Pode estar vazio.</li>
 * </ul>
 *
 * <p><strong>Construtor canônico — invariantes</strong>:
 * <ul>
 *   <li>{@code codigoImovel} {@code null} ou em branco → {@link IllegalArgumentException}
 *       com mensagem PT (defesa estrita: a regra invariável do AGENTS.md §Domínio
 *       é "código imobiliário ausente → log de erro, pula a linha inteira", então
 *       {@code LinhaMapeada} não deveria sequer ser construída sem código).</li>
 *   <li>{@code celulasFixas} / {@code celulasDinamicas} {@code null} → tornam-se
 *       {@link Map#of()} (tolerância: o leitor da Story 4.5 pode legitimamente
 *       receber linhas sem dinâmicas/fixas).</li>
 *   <li>Cópias defensivas via
 *       {@link Collections#unmodifiableMap(Map)} sobre {@link LinkedHashMap}
 *       preservando ordem — espelha o padrão de {@code Classificacao} (Story 2.4).</li>
 * </ul>
 *
 * <p>Consumidores: Story 4.2 ({@code SqlGeradorUpdate} — usa
 * {@code codigoImovel} e {@code celulasFixas}); Story 4.3
 * ({@code SqlGeradorUpsert} — usa {@code codigoImovel} e {@code celulasDinamicas});
 * Story 4.5 ({@code ImportarCommand} — orquestra leitura → mapeamento → SQL).
 *
 * <p>Story: 4.2 — SqlGeradorUpdate (introdução do VO).
 */
public record LinhaMapeada(
        String codigoImovel,
        Map<String, String> celulasFixas,
        Map<String, String> celulasDinamicas) {

    public LinhaMapeada {
        if (codigoImovel == null || codigoImovel.isBlank()) {
            throw new IllegalArgumentException("Código do imóvel não pode ser nulo ou em branco.");
        }
        celulasFixas = celulasFixas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasFixas));
        celulasDinamicas = celulasDinamicas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasDinamicas));
    }
}
