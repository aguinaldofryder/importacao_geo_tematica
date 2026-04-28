package br.com.arxcode.tematica.geo.mapeamento;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado da Fase 1 do pipeline de importação: cabeçalhos da planilha já
 * particionados em três categorias mutuamente exclusivas.
 *
 * <p>Slots:
 * <ul>
 *   <li>{@code codigo} — header que identifica a coluna de código do imóvel
 *       (chave para o JOIN com a tabela principal). Sempre exatamente um.</li>
 *   <li>{@code fixas} — headers que mapeiam para colunas físicas da tabela
 *       principal ({@code tribcadastroimobiliario} ou {@code tribimobiliariosegmento}).
 *       Ordem de iteração das chaves preservada conforme aparecem na planilha
 *       (uso interno de {@link LinkedHashMap}).</li>
 *   <li>{@code dinamicas} — headers que virarão respostas em
 *       {@code respostaterreno}/{@code respostasegmento} via tabela {@code campo}.
 *       Ordem original preservada.</li>
 * </ul>
 *
 * <p><strong>Atenção sobre {@code fixas.value}:</strong> nesta Story 2.4 o valor
 * de cada entrada em {@code fixas} é apenas um <em>placeholder</em>: replica o
 * próprio header da planilha (key = value). A tradução para o nome físico real
 * da coluna na tabela principal (ex.: {@code "AREA_TERRENO" → "area_terreno"})
 * é responsabilidade da Story 3.2 (auto-mapeador), que tem o contexto do
 * vocabulário do banco. Manter a estrutura {@code Map<String,String>} agora
 * facilita a evolução sem mudar contrato público.
 *
 * <p>O record é serializável por Jackson sem anotações extras; o registro
 * para GraalVM {@code native-image} é centralizado em {@link MapeamentoReflection}.
 *
 * <p>Story: 2.4 — ClassificadorColunas (Fase 1).
 */
public record Classificacao(String codigo, Map<String, String> fixas, List<String> dinamicas) {

    public Classificacao {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Slot 'codigo' não pode ser nulo ou em branco.");
        }
        if (fixas == null) {
            throw new IllegalArgumentException("Slot 'fixas' não pode ser nulo (use Map vazio se não houver colunas fixas).");
        }
        if (dinamicas == null) {
            throw new IllegalArgumentException("Slot 'dinamicas' não pode ser nulo (use List vazia se não houver colunas dinâmicas).");
        }
        // cópias defensivas mantendo ordem de iteração original
        fixas = Collections.unmodifiableMap(new LinkedHashMap<>(fixas));
        dinamicas = List.copyOf(dinamicas);
    }
}
