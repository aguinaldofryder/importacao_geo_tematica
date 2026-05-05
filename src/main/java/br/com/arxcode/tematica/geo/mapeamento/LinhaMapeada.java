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
 *       Corresponde ao campo {@code cadastrogeral} da PK das tabelas principais.
 *       Sempre presente; será coagido como {@code Tipo.TEXTO} pelo
 *       {@code SqlGeradorUpdate} para gerar o {@code WHERE}.</li>
 *   <li>{@code sequenciaPredial} — valor cru da célula de sequência do segmento
 *       predial (terceiro componente da PK de {@code tribimobiliariosegmento}).
 *       Presente apenas para o fluxo {@link br.com.arxcode.tematica.geo.dominio.Fluxo#PREDIAL};
 *       {@code null} para o fluxo TERRITORIAL. Nunca em branco se não-{@code null}.</li>
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
 *       com mensagem PT.</li>
 *   <li>{@code sequenciaPredial} não-{@code null} mas em branco → {@link IllegalArgumentException}
 *       (se informado, deve ter valor).</li>
 *   <li>{@code celulasFixas} / {@code celulasDinamicas} {@code null} → tornam-se
 *       {@link Map#of()}.</li>
 *   <li>Cópias defensivas via {@link Collections#unmodifiableMap(Map)} sobre
 *       {@link LinkedHashMap} preservando ordem.</li>
 * </ul>
 *
 * <p>Consumidores: Story 4.2 ({@code SqlGeradorUpdate} — usa
 * {@code codigoImovel}, {@code sequenciaPredial} e {@code celulasFixas});
 * Story 4.3 ({@code SqlGeradorUpsert} — usa {@code codigoImovel} e
 * {@code celulasDinamicas}); Story 4.5 ({@code ImportarCommand}).
 *
 * <p>Story: 4.2 — SqlGeradorUpdate (introdução do VO).
 */
public record LinhaMapeada(
        String codigoImovel,
        String sequenciaPredial,
        Map<String, String> celulasFixas,
        Map<String, String> celulasDinamicas) {

    public LinhaMapeada {
        if (codigoImovel == null || codigoImovel.isBlank()) {
            throw new IllegalArgumentException("Código do imóvel não pode ser nulo ou em branco.");
        }
        if (sequenciaPredial != null && sequenciaPredial.isBlank()) {
            throw new IllegalArgumentException("Sequência predial não pode ser em branco se informada.");
        }
        celulasFixas = celulasFixas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasFixas));
        celulasDinamicas = celulasDinamicas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasDinamicas));
    }
}
