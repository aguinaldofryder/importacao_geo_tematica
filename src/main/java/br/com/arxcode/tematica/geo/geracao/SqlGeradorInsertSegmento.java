package br.com.arxcode.tematica.geo.geracao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;

/**
 * Gera, para uma linha predial cujo segmento ainda <strong>não existe</strong> em
 * {@code aise.tribimobiliariosegmento}, a instrução
 * {@code INSERT INTO aise.tribimobiliariosegmento (idkey, tipocadastro, cadastrogeral, sequencia, ...)}
 * e o literal de referência {@code referenciaSubselect} a ser usado nos INSERTs
 * subsequentes de {@code aise.respostasegmento}.
 *
 * <h2>Motivação</h2>
 * <p>A regra original (Stories 4.5/4.7) proibia qualquer INSERT em
 * {@code tribimobiliariosegmento} — apenas UPDATE era permitido. Esta classe
 * abre uma exceção controlada: quando a construção não existe no banco e a
 * planilha traz dados suficientes para criá-la (colunas fixas mapeadas +
 * chave composta), geramos o INSERT para execução posterior pelo DBA.
 *
 * <h2>Sequence e referência</h2>
 * <p>O {@code idkey} da nova construção é gerado via
 * {@code nextval('aise.s_tribimobiliariosegmento_id')} — a sequence confirmada
 * em {@code information_schema.sequences}. Como o SQL é executado fora do
 * contexto desta ferramenta, <strong>não conhecemos o valor concreto do idkey</strong>
 * em tempo de geração. Por isso, os INSERTs em {@code respostasegmento} usam
 * um <strong>subselect</strong> como referência:
 * <pre>
 * (SELECT idkey FROM aise.tribimobiliariosegmento
 *  WHERE tipocadastro = 1 AND cadastrogeral = &lt;n&gt; AND sequencia = &lt;s&gt;)
 * </pre>
 * Este subselect é seguro porque {@code (tipocadastro, cadastrogeral, sequencia)}
 * possui constraint de unicidade na tabela — retorna exatamente um valor.
 *
 * <h2>Exclusividade PREDIAL</h2>
 * <p>Somente {@link Fluxo#PREDIAL} é suportado. Chamar este gerador com
 * {@link Fluxo#TERRITORIAL} lança {@link IllegalArgumentException} — a tabela
 * {@code tribcadastroimobiliario} nunca recebe INSERT (CON-03).
 *
 * <h2>Política de erro</h2>
 * <p>Falhas de coerção das colunas fixas são acumuladas (sem short-circuit) e
 * retornadas em {@link ResultadoInsertSegmento#erros()}. Quando há erros, nenhum
 * SQL é emitido — equivalente ao comportamento de {@link SqlGeradorUpdate}.
 *
 * <p><strong>Função pura, sem CDI.</strong> {@code public final class} com
 * construtor sem argumentos — padrão do projeto (Stories 4.1/4.2/4.3).
 */
public final class SqlGeradorInsertSegmento {

    public SqlGeradorInsertSegmento() {
        // Construtor explícito — função pura, sem dependências.
    }

    /**
     * Gera o INSERT de uma nova construção em {@code tribimobiliariosegmento} e
     * o subselect de referência para uso nos INSERTs de {@code respostasegmento}.
     *
     * <p>Forma do SQL emitido (sucesso):
     * <pre>
     * INSERT INTO aise.tribimobiliariosegmento
     *   (idkey, tipocadastro, cadastrogeral, sequencia, &lt;colunas_fixas&gt;)
     *   VALUES (nextval('aise.s_tribimobiliariosegmento_id'), 1, &lt;cadastrogeral&gt;, &lt;sequencia&gt;, &lt;valores_fixos&gt;);
     * </pre>
     *
     * <p>O {@code referenciaSubselect} retornado tem a forma:
     * <pre>
     * (SELECT idkey FROM aise.tribimobiliariosegmento
     *  WHERE tipocadastro = 1 AND cadastrogeral = &lt;n&gt; AND sequencia = &lt;s&gt;)
     * </pre>
     *
     * @param linha        linha lida da planilha (não-{@code null}; {@code sequenciaPredial} não-{@code null})
     * @param mapeamento   mapeamento header→coluna (não-{@code null})
     * @param coercionador conversor de células em literais SQL (não-{@code null})
     * @return resultado em sucesso (com SQL do INSERT e subselect de referência)
     *         ou falha (com lista de erros PT)
     * @throws IllegalArgumentException se {@code fluxo != PREDIAL} ou qualquer parâmetro
     *         obrigatório for {@code null}
     */
    public ResultadoInsertSegmento gerar(LinhaMapeada linha, Mapeamento mapeamento, Coercionador coercionador) {
        if (linha == null) {
            throw new IllegalArgumentException("linha não pode ser nula.");
        }
        if (mapeamento == null) {
            throw new IllegalArgumentException("mapeamento não pode ser nulo.");
        }
        if (coercionador == null) {
            throw new IllegalArgumentException("coercionador não pode ser nulo.");
        }
        if (mapeamento.fluxo() != Fluxo.PREDIAL) {
            throw new IllegalArgumentException(
                    "SqlGeradorInsertSegmento só suporta fluxo PREDIAL; recebido: " + mapeamento.fluxo());
        }
        if (linha.sequenciaPredial() == null) {
            throw new IllegalArgumentException("sequenciaPredial não pode ser nula para inserção de segmento predial.");
        }

        String literalCadastrogeral = String.valueOf(linha.codigoImovel());
        String literalSequencia = String.valueOf(linha.sequenciaPredial());

        Map<String, String> colunasFixas = mapeamento.colunasFixas();

        List<String> erros = new ArrayList<>();
        List<String> nomeColunas = new ArrayList<>();
        List<String> valores = new ArrayList<>();

        // Colunas fixas mapeadas (preservando ordem do mapeamento)
        for (Map.Entry<String, String> entry : colunasFixas.entrySet()) {
            String header = entry.getKey();
            String colunaFisica = entry.getValue();
            String valorCelula = linha.celulasFixas().get(header);
            ResultadoCoercao rc = coercionador.coagir(valorCelula, Tipo.TEXTO, null);
            if (rc.ok()) {
                nomeColunas.add(colunaFisica);
                valores.add(rc.literalSql());
            } else {
                erros.add("Coluna '" + header + "': " + rc.erro());
            }
        }

        if (!erros.isEmpty()) {
            return ResultadoInsertSegmento.falha(erros);
        }

        // Monta listas de colunas e valores: idkey + chave composta + fixas
        List<String> todasColunas = new ArrayList<>();
        todasColunas.add("idkey");
        todasColunas.add("tipocadastro");
        todasColunas.add("cadastrogeral");
        todasColunas.add("sequencia");
        todasColunas.addAll(nomeColunas);

        List<String> todosValores = new ArrayList<>();
        todosValores.add("nextval('aise." + Fluxo.PREDIAL.sequencePrincipal() + "')");
        todosValores.add("1");
        todosValores.add(literalCadastrogeral);
        todosValores.add(literalSequencia);
        todosValores.addAll(valores);

        String sql = "INSERT INTO aise." + Fluxo.PREDIAL.tabelaPrincipal()
                + " (" + String.join(", ", todasColunas) + ")"
                + " VALUES (" + String.join(", ", todosValores) + ");";

        // Subselect para uso como referencia nos INSERTs de respostasegmento.
        // Seguro pois (tipocadastro, cadastrogeral, sequencia) é unique.
        String referenciaSubselect = "(SELECT idkey FROM aise." + Fluxo.PREDIAL.tabelaPrincipal()
                + " WHERE tipocadastro = 1"
                + " AND cadastrogeral = " + literalCadastrogeral
                + " AND sequencia = " + literalSequencia + ")";

        return ResultadoInsertSegmento.sucesso(sql, referenciaSubselect);
    }
}
