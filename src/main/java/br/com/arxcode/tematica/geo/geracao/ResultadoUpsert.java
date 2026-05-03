package br.com.arxcode.tematica.geo.geracao;

import java.util.List;

/**
 * Resultado da geração de UPSERTs (DELETE+INSERT por célula dinâmica) pela
 * Story 4.3 ({@code SqlGeradorUpsert}). Forma análoga a {@link ResultadoUpdate}
 * (Story 4.2) com uma diferença chave: o slot de sucesso é
 * {@link #sqls()} ({@code List<String>}) em vez de {@code String sql} — uma
 * linha da planilha gera <strong>N</strong> UPSERTs (um por célula dinâmica
 * não-vazia em coluna {@code MAPEADO}), todos pareados como
 * {@code DELETE; INSERT;} consecutivos.
 *
 * <p><strong>Forma:</strong>
 * <ul>
 *   <li>Em sucesso, {@link #sqls} contém zero ou mais instruções
 *       {@code DELETE FROM aise....} / {@code INSERT INTO aise....} prontas para
 *       escrita direta no arquivo {@code .sql}, e {@link #erros} é uma lista
 *       vazia imutável. <strong>Lista vazia é sucesso válido</strong> (caso
 *       degenerado AC8) — divergência intencional vs.
 *       {@link ResultadoUpdate#sucesso(String)} (4.2 não admite SQL vazio
 *       porque atualizar nada na tabela principal é erro de mapeamento; aqui,
 *       linha sem dinâmicas é cenário válido — o orquestrador da Story 4.5
 *       ainda chamará {@code SqlGeradorUpdate} para a mesma linha).</li>
 *   <li>Em falha, {@link #sqls} é uma lista vazia imutável e {@link #erros}
 *       contém ao menos uma mensagem PT (todas as falhas de coerção da linha
 *       — agregadas para permitir ao operador corrigir N células em uma única
 *       passada).</li>
 * </ul>
 *
 * <p><strong>Invariantes</strong> (validados nas fábricas estáticas; o
 * construtor canônico apenas normaliza ambos os campos via
 * {@link List#copyOf(java.util.Collection)} — {@code null} vira lista vazia):
 * <ul>
 *   <li>{@link #sucesso(List)} aceita lista vazia (AC8) e exige
 *       {@code sqls != null} e nenhum elemento {@code null}/{@code blank}.</li>
 *   <li>{@link #falha(List)} exige {@code erros != null && !erros.isEmpty()}
 *       e nenhum elemento {@code null}/{@code blank}.</li>
 * </ul>
 *
 * <p>Story: 4.3 — SqlGeradorUpsert.
 */
public record ResultadoUpsert(List<String> sqls, List<String> erros) {

    public ResultadoUpsert {
        sqls = sqls == null ? List.of() : List.copyOf(sqls);
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    /**
     * Fábrica de sucesso. Aceita lista vazia (caso degenerado AC8 — linha sem
     * dinâmicas válidas é situação legítima na Story 4.3). Cada elemento de
     * {@code sqls}, quando presente, deve ser uma instrução SQL completa
     * (terminada em {@code ;}).
     *
     * @throws IllegalArgumentException se {@code sqls} for {@code null} ou se
     *         algum elemento for {@code null}/em branco
     */
    public static ResultadoUpsert sucesso(List<String> sqls) {
        if (sqls == null) {
            throw new IllegalArgumentException("Lista de SQLs não pode ser nula em ResultadoUpsert.sucesso.");
        }
        for (String s : sqls) {
            if (s == null || s.isBlank()) {
                throw new IllegalArgumentException("SQLs de sucesso não podem conter elementos nulos ou em branco.");
            }
        }
        return new ResultadoUpsert(sqls, List.of());
    }

    /**
     * Fábrica de falha. {@code erros} deve ser uma lista não-vazia de mensagens
     * em português a serem registradas no {@code .log} (Story 4.4).
     *
     * @throws IllegalArgumentException se {@code erros} for {@code null}, vazio,
     *         ou se algum elemento for {@code null}/em branco
     */
    public static ResultadoUpsert falha(List<String> erros) {
        if (erros == null || erros.isEmpty()) {
            throw new IllegalArgumentException("Lista de erros não pode ser nula ou vazia em ResultadoUpsert.falha.");
        }
        for (String e : erros) {
            if (e == null || e.isBlank()) {
                throw new IllegalArgumentException("Erros não podem conter elementos nulos ou em branco.");
            }
        }
        return new ResultadoUpsert(List.of(), erros);
    }

    /**
     * @return {@code true} se este resultado é de sucesso (sem erros);
     *         {@code false} se é falha. Note que sucesso pode ter
     *         {@link #sqls()} vazio (caso degenerado AC8).
     */
    public boolean ok() {
        return erros.isEmpty();
    }
}
