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
 *   <li>{@code codigoImovel} — valor numérico do campo {@code cadastrogeral}
 *       (PK das tabelas principais), já convertido de {@code String} para
 *       {@code long} em {@code ImportarCommand} (Story 4.8).
 *       Invariante: {@code codigoImovel > 0}.</li>
 *   <li>{@code sequenciaPredial} — sequência do segmento predial (terceiro
 *       componente da PK de {@code tribimobiliariosegmento}), boxed para
 *       permitir {@code null}. Presente apenas para o fluxo
 *       {@link br.com.arxcode.tematica.geo.dominio.Fluxo#PREDIAL};
 *       {@code null} para o fluxo TERRITORIAL. Nunca zero ou negativo
 *       se não-{@code null}.</li>
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
 *   <li>{@code codigoImovel} {@code <= 0} → {@link IllegalArgumentException}
 *       com mensagem PT.</li>
 *   <li>{@code sequenciaPredial} não-{@code null} e {@code <= 0} → {@link IllegalArgumentException}
 *       (se informada, deve ser positiva).</li>
 *   <li>{@code celulasFixas} / {@code celulasDinamicas} {@code null} → tornam-se
 *       {@link Map#of()}.</li>
 *   <li>Cópias defensivas via {@link Collections#unmodifiableMap(Map)} sobre
 *       {@link LinkedHashMap} preservando ordem.</li>
 * </ul>
 *
 * <p>Consumidores: Story 4.2 ({@code SqlGeradorUpdate} — usa
 * {@code codigoImovel}, {@code sequenciaPredial} e {@code celulasFixas});
 * Story 4.3 ({@code SqlGeradorUpsert} — usa {@code celulasDinamicas});
 * Story 4.5 ({@code ImportarCommand}).
 *
 * <p>Story: 4.8 — Conversão antecipada de codigoImovel para long.
 */
public record LinhaMapeada(
        long codigoImovel,
        Long sequenciaPredial,
        Map<String, String> celulasFixas,
        Map<String, String> celulasDinamicas) {

    public LinhaMapeada {
        if (codigoImovel <= 0) {
            throw new IllegalArgumentException("Código do imóvel deve ser maior que zero.");
        }
        if (sequenciaPredial != null && sequenciaPredial <= 0) {
            throw new IllegalArgumentException("Sequência predial deve ser maior que zero se informada.");
        }
        celulasFixas = celulasFixas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasFixas));
        celulasDinamicas = celulasDinamicas == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(celulasDinamicas));
    }
}
