package br.com.arxcode.tematica.geo.geracao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

/**
 * Gera, para cada <strong>célula dinâmica não-vazia</strong> de uma linha
 * mapeada da planilha, um par de instruções
 * {@code DELETE FROM aise.<tabela_respostas> WHERE referencia=...; INSERT INTO aise.<tabela_respostas> ...;}
 * destinadas ao arquivo {@code .sql} produzido pelo pipeline (PRD FR-12, FR-13,
 * CON-03, CON-04).
 *
 * <p><strong>Função pura, sem CDI.</strong> {@code public final class} com
 * construtor sem argumentos — segue o padrão de {@link Coercionador} (Story 4.1
 * AC8) e {@link SqlGeradorUpdate} (Story 4.2 AC5). O {@link Coercionador} é
 * injetado por <em>parâmetro</em> (não por campo {@code final}) — mesma
 * justificativa da 4.2.
 *
 * <p><strong>Diferença chave vs. {@link SqlGeradorUpdate} (4.2):</strong>
 * <ul>
 *   <li>Retorno é {@link ResultadoUpsert} com {@code List<String> sqls} —
 *       uma linha gera <strong>N</strong> UPSERTs (um par DELETE+INSERT por
 *       célula dinâmica não-vazia em coluna {@code MAPEADO}).</li>
 *   <li><strong>Caso degenerado retorna {@code sucesso(List.of())}</strong>
 *       (não falha como na 4.2). Linha sem dinâmicas é cenário válido — o
 *       orquestrador da Story 4.5 ainda chamará {@code SqlGeradorUpdate} para
 *       a mesma linha.</li>
 *   <li><strong>Célula dinâmica vazia → pula</strong> (na 4.2 vira
 *       {@code coluna = NULL}). Semântica correta para tabela de respostas:
 *       vazio significa "não respondi", não "respondi NULL".</li>
 * </ul>
 *
 * <p><strong>Sintaxe DELETE+INSERT</strong> (em vez de {@code ON CONFLICT}):
 * o schema real não tem UNIQUE INDEX em {@code (referencia, idcampo)}, então
 * {@code INSERT ... ON CONFLICT (referencia, idcampo)} lançaria erro Postgres.
 * O par {@code DELETE; INSERT VALUES (nextval('aise.<seq>'), ...);} é
 * idempotente em re-execução (DELETE tolerante a 0 linhas), não exige DDL
 * prévio, e é PG-específico via {@code nextval()} (CON-04).
 *
 * <p><strong>Coluna física única {@code valor varchar(250)}</strong> mais
 * {@code idalternativa}: helper privado {@link #serializarValor} mapeia o
 * {@link ResultadoCoercao#literalSql()} em {@code (valorParaColunaValor,
 * idAlternativaOuNull)} por {@link Tipo}. Para {@code DATA}, executa pipeline
 * <strong>ISO → BR</strong> (extrai ISO de {@code "DATE 'YYYY-MM-DD'"},
 * parseia, reformata como {@code 'dd/MM/yyyy'}) — a coluna é varchar e o
 * contexto BR exige legibilidade {@code dd/MM/yyyy} para o operador
 * municipal. <strong>Divergência intencional vs. Story 4.2</strong>: lá o
 * literal ISO é consumido direto pelas colunas tipadas {@code date} das
 * tabelas principais.
 *
 * <p><strong>Política de erro (PRD FR-13).</strong> Falhas de coerção são
 * dados, não exceções: vão para {@link ResultadoUpsert#erros()}. Falhas de
 * <em>todas</em> as células dinâmicas e do {@code codigoImovel} são acumuladas
 * (não há short-circuit no primeiro erro) — fecha plenamente o ramo de
 * acumulação que a Story 4.2 só pôde exercitar documentalmente
 * (ISSUE-4.2-02). Em qualquer falha, nenhum SQL é emitido.
 *
 * <p>Story: 4.3 — SqlGeradorUpsert.
 */
public final class SqlGeradorUpsert {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public SqlGeradorUpsert() {
        // Construtor explícito — função pura, sem dependências.
    }

    /**
     * Gera os pares {@code DELETE+INSERT} correspondentes às células dinâmicas
     * não-vazias de uma linha da planilha.
     *
     * <p>Forma do SQL emitido por célula dinâmica não-vazia (sucesso):
     * <pre>
     * DELETE FROM aise.&lt;tabelaRespostas&gt; WHERE referencia = &lt;ref&gt; AND idcampo = &lt;idCampo&gt;;
     * INSERT INTO aise.&lt;tabelaRespostas&gt; (id, referencia, valor, idcampo, idalternativa)
     *     VALUES (nextval('aise.&lt;sequenceRespostas&gt;'), &lt;ref&gt;, &lt;valor&gt;, &lt;idCampo&gt;, &lt;idAlt|NULL&gt;);
     * </pre>
     *
     * <p>Caso degenerado (AC8): se nenhuma célula dinâmica passa nos filtros
     * (mapeamento sem dinâmicas, ou todas pendentes/vazias), retorna
     * {@link ResultadoUpsert#sucesso(List)} com lista vazia — divergência
     * intencional vs. 4.2 (ver Javadoc da classe).
     *
     * @param linha         linha lida da planilha (não-{@code null})
     * @param mapeamento    mapeamento header→coluna (não-{@code null})
     * @param fluxo         {@link Fluxo#TERRITORIAL} ou {@link Fluxo#PREDIAL} (não-{@code null})
     * @param coercionador  conversor de células em literais SQL (não-{@code null})
     * @return resultado em sucesso (com lista de SQLs, possivelmente vazia)
     *         ou falha (com lista de erros PT)
     * @throws IllegalArgumentException se qualquer parâmetro for {@code null}
     *         (programação defeituosa, não dado ruim — não vira
     *         {@code ResultadoUpsert.falha})
     */
    public ResultadoUpsert gerar(LinhaMapeada linha, Mapeamento mapeamento, Fluxo fluxo, Coercionador coercionador) {
        if (linha == null) {
            throw new IllegalArgumentException("linha não pode ser nula.");
        }
        if (mapeamento == null) {
            throw new IllegalArgumentException("mapeamento não pode ser nulo.");
        }
        if (fluxo == null) {
            throw new IllegalArgumentException("fluxo não pode ser nulo.");
        }
        if (coercionador == null) {
            throw new IllegalArgumentException("coercionador não pode ser nulo.");
        }

        List<String> erros = new ArrayList<>();
        List<String> sqls = new ArrayList<>();

        // Coage o codigoImovel como TEXTO (chave da referencia) — defesa em
        // profundidade. Falha acumula como erro nominal.
        ResultadoCoercao rcRef = coercionador.coagir(linha.codigoImovel(), Tipo.TEXTO, null);
        String referenciaLiteral = null;
        if (rcRef.ok()) {
            referenciaLiteral = rcRef.literalSql();
        } else {
            erros.add("Código do imóvel: " + rcRef.erro());
        }

        // Itera colunas dinâmicas preservando ordem (LinkedHashMap esperado).
        for (Map.Entry<String, ColunaDinamica> entry : mapeamento.colunasDinamicas().entrySet()) {
            String header = entry.getKey();
            ColunaDinamica coluna = entry.getValue();

            // Filtro AC3(a): pular não-MAPEADO.
            if (coluna.status() != StatusMapeamento.MAPEADO) {
                continue;
            }

            // Filtro AC3(b): pular célula null/blank (semântica "não respondi").
            String valorCelula = linha.celulasDinamicas().get(header);
            if (valorCelula == null || valorCelula.isBlank()) {
                continue;
            }

            // Coerção tipada (AC4).
            ResultadoCoercao rc = coercionador.coagir(valorCelula, coluna.tipo(), coluna.alternativas());
            if (!rc.ok()) {
                erros.add("Coluna '" + header + "': " + rc.erro());
                continue;
            }

            // Se já há erros, ainda iteramos para acumular outras falhas; mas
            // não geramos SQL para linha que terminará em falha.
            if (!erros.isEmpty()) {
                continue;
            }

            ValorSerializado vs = serializarValor(rc, coluna.tipo());
            int idCampo = coluna.idcampo();

            String delete = "DELETE FROM aise." + fluxo.tabelaRespostas()
                    + " WHERE referencia = " + referenciaLiteral
                    + " AND idcampo = " + idCampo + ";";

            String insert = "INSERT INTO aise." + fluxo.tabelaRespostas()
                    + " (id, referencia, valor, idcampo, idalternativa) VALUES ("
                    + "nextval('aise." + fluxo.sequenceRespostas() + "'), "
                    + referenciaLiteral + ", "
                    + vs.valor() + ", "
                    + idCampo + ", "
                    + vs.idAlternativa() + ");";

            sqls.add(delete);
            sqls.add(insert);
        }

        if (!erros.isEmpty()) {
            return ResultadoUpsert.falha(erros);
        }
        return ResultadoUpsert.sucesso(sqls);
    }

    /**
     * Pós-processa o {@link ResultadoCoercao#literalSql()} para a forma exigida
     * pela coluna física {@code valor varchar(250)} + {@code idalternativa} da
     * tabela de respostas:
     * <ul>
     *   <li><strong>TEXTO</strong>: usa {@code literalSql()} direto (já vem
     *       aspeado por {@code Coercionador.coagirTexto}); {@code idalternativa = NULL}.</li>
     *   <li><strong>DECIMAL</strong>: envelopa em aspas externas ({@code 'X'}) —
     *       {@code coagirDecimal} retorna número nu, mas a coluna é varchar.
     *       {@code idalternativa = NULL}.</li>
     *   <li><strong>DATA</strong>: pipeline ISO → BR — extrai ISO de
     *       {@code "DATE 'YYYY-MM-DD'"}, parseia com {@code ISO_LOCAL_DATE},
     *       reformata como {@code 'dd/MM/yyyy'}. {@code idalternativa = NULL}.</li>
     *   <li><strong>MULTIPLA_ESCOLHA</strong>: {@code valor = ''} (string vazia
     *       literal — formato observado no banco real); {@code idalternativa}
     *       recebe o id nu (sem aspas) retornado pelo Coercionador.</li>
     * </ul>
     */
    private static ValorSerializado serializarValor(ResultadoCoercao rc, Tipo tipo) {
        return switch (tipo) {
            case TEXTO -> new ValorSerializado(rc.literalSql(), "NULL");
            case DECIMAL -> new ValorSerializado("'" + rc.literalSql() + "'", "NULL");
            case DATA -> {
                // rc.literalSql() = "DATE 'YYYY-MM-DD'" (17 chars).
                String literal = rc.literalSql();
                String iso = literal.substring(6, 16);
                LocalDate data = LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE);
                String br = data.format(DATA_BR);
                yield new ValorSerializado("'" + br + "'", "NULL");
            }
            case MULTIPLA_ESCOLHA -> new ValorSerializado("''", rc.literalSql());
        };
    }

    /**
     * Par {@code (valor, idAlternativa)} pronto para concatenação no
     * {@code INSERT} da tabela de respostas.
     */
    private record ValorSerializado(String valor, String idAlternativa) { }
}
