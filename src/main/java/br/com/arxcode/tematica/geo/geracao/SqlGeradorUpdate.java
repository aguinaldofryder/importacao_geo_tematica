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
 * Gera, para uma única linha mapeada da planilha, a instrução
 * {@code UPDATE <tabela_principal> SET <colunas_fixas> WHERE <coluna_chave> = '<código>'}
 * destinada ao arquivo {@code .sql} produzido pelo pipeline (PRD FR-11, CON-03).
 *
 * <p><strong>Função pura, sem CDI.</strong> {@code public final class} com
 * construtor sem argumentos — segue o padrão do {@link Coercionador} (Story 4.1
 * AC8): determinístico, sem efeitos colaterais, instanciável uma vez e
 * compartilhável entre threads. O {@link Coercionador} é injetado por
 * <em>parâmetro</em> (não por campo {@code final}) para evitar acoplar a fábrica
 * deste gerador à do coercionador.
 *
 * <p><strong>Invariante CON-03 (bloqueante).</strong> Esta classe nunca emite
 * {@code INSERT} na tabela principal — apenas {@code UPDATE}. Garantido por
 * inspeção programática nos testes (AC10) e reforçado pela ausência da palavra
 * {@code INSERT} em qualquer caminho de código.
 *
 * <p><strong>Política de erro (PRD FR-13).</strong> Falhas de coerção são
 * dados, não exceções: vão para {@link ResultadoUpdate#erros()}. Falhas de
 * <em>todas</em> as células fixas da linha são acumuladas (não há short-circuit
 * no primeiro erro) — permite ao operador corrigir N células em uma única
 * passada na planilha. Em qualquer falha, nenhum SQL é emitido (UPDATE parcial
 * mascararia bugs).
 *
 * <p><strong>Determinismo da ordem das colunas no {@code SET}.</strong>
 * A ordem segue {@link Mapeamento#colunasFixas()} (header → coluna física),
 * que na prática é {@link LinkedHashMap} desserializado por Jackson preservando
 * a ordem das chaves do {@code mapping.json} (que por sua vez veio do
 * classificador da Story 2.4, ordenado conforme a planilha). Caller que
 * construa {@code Mapeamento} programaticamente <strong>deve</strong> usar
 * {@link LinkedHashMap} para preservar essa garantia (esta classe não força
 * cópia defensiva do {@code Mapeamento}; pendência registrada como (low) na
 * Story 3.1 linha 304 do change log).
 *
 * <p><strong>Limitação consciente — colunas fixas como {@code Tipo.TEXTO}.</strong>
 * Esta story coage <em>todas</em> as células fixas como texto. Colunas físicas
 * {@code numeric}/{@code date} aceitam o cast implícito do PostgreSQL para
 * inteiros simples ({@code '42'::numeric}); valores pt-BR como
 * {@code "1.234,56"} ou {@code "31/12/2025"} podem falhar. Tipagem completa
 * fica para a Story 4.2.1 (refatoração transversal de
 * {@link Mapeamento#colunasFixas()} para incluir {@link Tipo}).
 *
 * <p>Story: 4.2 — SqlGeradorUpdate.
 */
public final class SqlGeradorUpdate {

    public SqlGeradorUpdate() {
        // Construtor explícito — função pura, sem dependências.
    }

    /**
     * Gera o {@code UPDATE} correspondente a uma linha da planilha.
     *
     * <p>Forma do SQL emitido (sucesso):
     * <pre>UPDATE &lt;tabela_principal&gt; SET col1=&lt;lit1&gt;, col2=&lt;lit2&gt;, ... WHERE &lt;coluna_chave&gt; = &lt;literal_chave&gt;;</pre>
     *
     * <p>Caso degenerado (AC8): se {@link Mapeamento#colunasFixas()} estiver
     * vazio, nada há para atualizar e o método retorna
     * {@link ResultadoUpdate#falha(List)} com mensagem PT — não emite UPDATE
     * sintaticamente inválido. Note: {@code coluna = NULL} <em>é</em> UPDATE
     * válido (zerar campo é operação legítima); o caso degenerado real é
     * exclusivamente "{@code colunasFixas()} vazio".
     *
     * @param linha         linha lida da planilha (não-{@code null})
     * @param mapeamento    mapeamento header→coluna (não-{@code null})
     * @param fluxo         {@link Fluxo#TERRITORIAL} ou {@link Fluxo#PREDIAL} (não-{@code null})
     * @param coercionador  conversor de células em literais SQL (não-{@code null})
     * @return resultado em sucesso (com SQL pronto) ou falha (com lista de erros PT)
     * @throws IllegalArgumentException se qualquer parâmetro for {@code null}
     *         (programação defeituosa, não dado ruim — não vira
     *         {@code ResultadoUpdate.falha})
     */
    public ResultadoUpdate gerar(LinhaMapeada linha, Mapeamento mapeamento, Fluxo fluxo, Coercionador coercionador) {
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

        Map<String, String> colunasFixas = mapeamento.colunasFixas();

        // Caso degenerado (AC8): nada a atualizar.
        if (colunasFixas.isEmpty()) {
            return ResultadoUpdate.falha(List.of("Linha sem colunas fixas a atualizar"));
        }

        List<String> erros = new ArrayList<>();
        Map<String, String> colunaParaLiteral = new LinkedHashMap<>();

        // Passo 1: coagir cada célula fixa, preservando ordem do mapeamento.
        for (Map.Entry<String, String> entry : colunasFixas.entrySet()) {
            String header = entry.getKey();
            String colunaFisica = entry.getValue();
            String valorCelula = linha.celulasFixas().get(header);
            ResultadoCoercao rc = coercionador.coagir(valorCelula, Tipo.TEXTO, null);
            if (rc.ok()) {
                colunaParaLiteral.put(colunaFisica, rc.literalSql());
            } else {
                erros.add("Coluna '" + header + "': " + rc.erro());
            }
        }

        // Passo 2: coagir o codigoImovel como DECIMAL — cadastrogeral é NUMERIC no banco.
        ResultadoCoercao rcChave = coercionador.coagir(linha.codigoImovel(), Tipo.DECIMAL, null);
        String literalCadastrogeral = null;
        if (rcChave.ok()) {
            literalCadastrogeral = rcChave.literalSql();
        } else {
            erros.add("Código do imóvel (cadastrogeral): " + rcChave.erro());
        }

        // Passo 2b: para PREDIAL, coagir sequenciaPredial como DECIMAL.
        String literalSequencia = null;
        if (fluxo == Fluxo.PREDIAL) {
            ResultadoCoercao rcSeq = coercionador.coagir(linha.sequenciaPredial(), Tipo.DECIMAL, null);
            if (rcSeq.ok()) {
                literalSequencia = rcSeq.literalSql();
            } else {
                erros.add("Sequência predial (sequencia): " + rcSeq.erro());
            }
        }

        // Passo 3: se acumulamos erros, abandona o UPDATE inteiro (AC9).
        if (!erros.isEmpty()) {
            return ResultadoUpdate.falha(erros);
        }

        // Passo 4: monta o SET na ordem determinística de colunasFixas().
        List<String> paresSet = new ArrayList<>(colunaParaLiteral.size());
        for (Map.Entry<String, String> entry : colunaParaLiteral.entrySet()) {
            paresSet.add(entry.getKey() + " = " + entry.getValue());
        }

        // Passo 5: monta o WHERE com a chave composta real da tabela principal.
        // TERRITORIAL: tipocadastro = 1 AND cadastrogeral = <numeric>
        // PREDIAL:     tipocadastro = 1 AND cadastrogeral = <numeric> AND sequencia = <numeric>
        String where = fluxo == Fluxo.PREDIAL
                ? "tipocadastro = 1 AND cadastrogeral = " + literalCadastrogeral + " AND sequencia = " + literalSequencia
                : "tipocadastro = 1 AND cadastrogeral = " + literalCadastrogeral;

        // Passo 6: monta o SQL final em uma única linha terminada em ';'.
        String sql = "UPDATE " + fluxo.tabelaPrincipal()
                + " SET " + String.join(", ", paresSet)
                + " WHERE " + where + ";";

        return ResultadoUpdate.sucesso(sql);
    }
}
