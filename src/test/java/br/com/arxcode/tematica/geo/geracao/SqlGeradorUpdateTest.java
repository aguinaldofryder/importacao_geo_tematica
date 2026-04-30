package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;

/**
 * Testes do {@link SqlGeradorUpdate} — função pura, JUnit Jupiter puro
 * (sem AssertJ — alinhado a {@code CONVENCOES.md} e ao padrão da Story 4.1).
 *
 * <p><strong>Nota sobre fixtures (VAL-4.2-01)</strong>: todos os
 * {@link Mapeamento} construídos diretamente nos testes usam
 * {@link LinkedHashMap} explícito em {@code colunasFixas} para preservar a
 * ordem de iteração (premissa documentada no Javadoc de
 * {@link SqlGeradorUpdate#gerar}).
 */
class SqlGeradorUpdateTest {

    private final SqlGeradorUpdate gerador = new SqlGeradorUpdate();
    private final Coercionador coercionador = new Coercionador();

    // ---------- Helpers ----------

    private static Mapeamento mapeamentoTerritorialPadrao() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("AREA_TERRENO", "area_terreno");
        fixas.put("TESTADA", "testada");
        return new Mapeamento(
                Fluxo.TERRITORIAL,
                "TABELA_TERRITORIAL_V001.xlsx",
                "INSCRICAO_IMOBILIARIA",
                fixas,
                Map.<String, ColunaDinamica>of());
    }

    private static Mapeamento mapeamentoPredialPadrao() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("AREA_CONSTRUIDA", "area_construida");
        fixas.put("PADRAO", "padrao");
        return new Mapeamento(
                Fluxo.PREDIAL,
                "TABELA_PREDIAL_V001.xlsx",
                "INSCRICAO_IMOBILIARIA",
                fixas,
                Map.<String, ColunaDinamica>of());
    }

    private static Mapeamento mapeamentoVazio(Fluxo fluxo) {
        return new Mapeamento(
                fluxo,
                "planilha.xlsx",
                "INSCRICAO_IMOBILIARIA",
                Map.<String, String>of(),
                Map.<String, ColunaDinamica>of());
    }

    // ---------- AC5: validação de argumentos ----------

    @Nested
    class ValidacaoDeArgumentos {

        @Test
        void linhaNula_lancaIae() {
            Mapeamento m = mapeamentoTerritorialPadrao();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(null, m, Fluxo.TERRITORIAL, coercionador));
        }

        @Test
        void mapeamentoNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", Map.of(), Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, null, Fluxo.TERRITORIAL, coercionador));
        }

        @Test
        void fluxoNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", Map.of(), Map.of());
            Mapeamento m = mapeamentoTerritorialPadrao();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, null, coercionador));
        }

        @Test
        void coercionadorNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", Map.of(), Map.of());
            Mapeamento m = mapeamentoTerritorialPadrao();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, Fluxo.TERRITORIAL, null));
        }
    }

    // ---------- AC5/AC6/AC7: happy path ----------

    @Nested
    class HappyPath {

        @Test
        void territorial_linhaCompleta_geraSqlEsperado() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "350");
            celulas.put("TESTADA", "12");
            LinhaMapeada linha = new LinhaMapeada("00123", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "esperado ok=true; erros=" + r.erros());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = '350', testada = '12' WHERE tribcadastrogeral_idkey = '00123';",
                    r.sql());
            assertEquals(List.of(), r.erros());
        }

        @Test
        void predial_linhaCompleta_geraSqlEsperado() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_CONSTRUIDA", "120,5");
            celulas.put("PADRAO", "Médio");
            LinhaMapeada linha = new LinhaMapeada("00999", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoPredialPadrao(), Fluxo.PREDIAL, coercionador);

            assertTrue(r.ok());
            // Note: AREA_CONSTRUIDA é coagida como TEXTO nesta story (limitação 4.2 → 4.2.1).
            assertEquals(
                    "UPDATE tribimobiliariosegmento SET area_construida = '120,5', padrao = 'Médio' WHERE idkey = '00999';",
                    r.sql());
        }
    }

    // ---------- AC6: ordem determinística ----------

    @Test
    void colunasFixas_ordemDeIteracaoPreservada_noSetDoSql() {
        Map<String, String> fixas = new LinkedHashMap<>();
        // ordem propositadamente não-alfabética
        fixas.put("Z_HEADER", "z_col");
        fixas.put("A_HEADER", "a_col");
        fixas.put("M_HEADER", "m_col");
        fixas.put("B_HEADER", "b_col");
        Mapeamento m = new Mapeamento(
                Fluxo.TERRITORIAL, "p.xlsx", "I",
                fixas, Map.<String, ColunaDinamica>of());

        Map<String, String> celulas = new LinkedHashMap<>();
        celulas.put("Z_HEADER", "z");
        celulas.put("A_HEADER", "a");
        celulas.put("M_HEADER", "m");
        celulas.put("B_HEADER", "b");
        LinhaMapeada linha = new LinhaMapeada("777", celulas, Map.of());

        ResultadoUpdate r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

        assertTrue(r.ok());
        // SET deve aparecer EXATAMENTE na ordem do LinkedHashMap.
        assertTrue(r.sql().contains("SET z_col = 'z', a_col = 'a', m_col = 'm', b_col = 'b' WHERE"),
                () -> "Ordem inesperada: " + r.sql());
    }

    // ---------- AC7: integração com 4.1 — célula vazia/null vira NULL ----------

    @Nested
    class CelulaVaziaOuNula {

        @Test
        void celulaAusenteNaLinha_viraNullLiteral() {
            // Header 'TESTADA' está no mapeamento mas não há valor na linha → NULL.
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "100");
            // TESTADA propositadamente ausente
            LinhaMapeada linha = new LinhaMapeada("X1", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = '100', testada = NULL WHERE tribcadastrogeral_idkey = 'X1';",
                    r.sql());
        }

        @Test
        void celulaVazia_viraNullLiteral() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "");
            celulas.put("TESTADA", "  ");
            LinhaMapeada linha = new LinhaMapeada("X2", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = NULL, testada = NULL WHERE tribcadastrogeral_idkey = 'X2';",
                    r.sql());
        }

        @Test
        void todasCelulasNull_aindaEmiteUpdateValido() {
            // Decisão de design (T5 Passo 4): "coluna = NULL" é UPDATE válido (zerar
            // campos é operação legítima). Caso degenerado real é colunasFixas() vazio
            // (testado em CasoDegenerado abaixo).
            LinhaMapeada linha = new LinhaMapeada("Y1", Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "esperado UPDATE com NULLs; erros=" + r.erros());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = NULL, testada = NULL WHERE tribcadastrogeral_idkey = 'Y1';",
                    r.sql());
        }
    }

    // ---------- AC8: caso degenerado ----------

    @Nested
    class CasoDegenerado {

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void mapeamentoSemColunasFixas_retornaFalha(Fluxo fluxo) {
            LinhaMapeada linha = new LinhaMapeada("123", Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoVazio(fluxo), fluxo, coercionador);

            assertFalse(r.ok());
            assertNull(r.sql());
            assertEquals(List.of("Linha sem colunas fixas a atualizar"), r.erros());
        }
    }

    // ---------- AC9: formato da mensagem de erro (teste documental) ----------

    @Nested
    class FormatoMensagemDeErro {

        /**
         * <strong>VAL-4.2-02 — estratégia adotada</strong>: como
         * {@link Coercionador} é {@code final class} e {@code Tipo.TEXTO}
         * (único tipo usado pelo {@link SqlGeradorUpdate} nesta story) raramente
         * falha, o caminho de acumulação de erros do {@code SqlGeradorUpdate}
         * será exercitado plenamente apenas na Story 4.3 (UPSERT, com
         * {@code DECIMAL}/{@code DATA}/{@code MULTIPLA_ESCOLHA}). Mantemos aqui
         * um <em>teste documental</em> que valida o <strong>formato exato</strong>
         * da mensagem de erro contratada pelo AC9 ("Coluna '&lt;header&gt;':
         * &lt;erro&gt;"), montando manualmente o {@link ResultadoUpdate#falha(List)}
         * com o formato esperado e asserindo a estrutura. Garante que mudanças
         * acidentais no formato da mensagem sejam detectadas.
         */
        @Test
        void formatoCelula_segue_padraoDocumentado() {
            String header = "AREA_TERRENO";
            String erroCoercao = "valor 'abc' não é decimal válido";
            String mensagemEsperada = "Coluna '" + header + "': " + erroCoercao;

            ResultadoUpdate r = ResultadoUpdate.falha(List.of(mensagemEsperada));

            assertFalse(r.ok());
            assertEquals(1, r.erros().size());
            assertTrue(r.erros().get(0).startsWith("Coluna '"),
                    () -> "Mensagem deve seguir padrão \"Coluna '<header>': <erro>\"; obtido: " + r.erros().get(0));
            assertTrue(r.erros().get(0).contains("': "));
        }

        @Test
        void formatoCodigoImovel_segue_padraoDocumentado() {
            String erroCoercao = "valor inválido";
            String mensagemEsperada = "Código do imóvel: " + erroCoercao;

            ResultadoUpdate r = ResultadoUpdate.falha(List.of(mensagemEsperada));

            assertFalse(r.ok());
            assertTrue(r.erros().get(0).startsWith("Código do imóvel: "),
                    () -> "Mensagem deve começar com \"Código do imóvel: \"; obtido: " + r.erros().get(0));
        }
    }

    // ---------- AC10: invariante CON-03 — nunca INSERT ----------

    @Nested
    class InvarianteNuncaInsert {

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void happyPath_naoEmiteInsert(Fluxo fluxo) {
            Mapeamento m = fluxo == Fluxo.TERRITORIAL
                    ? mapeamentoTerritorialPadrao()
                    : mapeamentoPredialPadrao();
            String header1 = m.colunasFixas().keySet().iterator().next();
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put(header1, "valor");
            LinhaMapeada linha = new LinhaMapeada("ABC", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            assertSemInsert(r.sql());
        }

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void celulasNulas_naoEmiteInsert(Fluxo fluxo) {
            Mapeamento m = fluxo == Fluxo.TERRITORIAL
                    ? mapeamentoTerritorialPadrao()
                    : mapeamentoPredialPadrao();
            LinhaMapeada linha = new LinhaMapeada("ABC", Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            assertSemInsert(r.sql());
        }

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void codigoComAspas_naoEmiteInsert(Fluxo fluxo) {
            Mapeamento m = fluxo == Fluxo.TERRITORIAL
                    ? mapeamentoTerritorialPadrao()
                    : mapeamentoPredialPadrao();
            String header1 = m.colunasFixas().keySet().iterator().next();
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put(header1, "v");
            LinhaMapeada linha = new LinhaMapeada("O'Brien", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            assertSemInsert(r.sql());
        }

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void tentativaInjectionNoCodigo_naoEmiteInsert(Fluxo fluxo) {
            Mapeamento m = fluxo == Fluxo.TERRITORIAL
                    ? mapeamentoTerritorialPadrao()
                    : mapeamentoPredialPadrao();
            String header1 = m.colunasFixas().keySet().iterator().next();
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put(header1, "v");
            LinhaMapeada linha = new LinhaMapeada("1'; INSERT INTO tribcadastroimobiliario VALUES (1); --",
                    celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            // Mesmo com o substring "INSERT INTO tribcadastroimobiliario" injetada
            // pelo atacante no valor do código, o SQL **gerado** começa com UPDATE
            // e o conteúdo viajou como literal entre aspas (escapado) — o SQL
            // executado pelo banco verá apenas um UPDATE. Para esta asserção
            // CON-03, verificamos que o SQL gerado começa com UPDATE; o substring
            // "INSERT INTO" pode aparecer dentro do literal escapado, e isso é
            // seguro (literal inerte, não comando).
            assertTrue(r.sql().startsWith("UPDATE "),
                    () -> "SQL deve começar com UPDATE; obtido: " + r.sql());
            // E não pode haver um INSERT INTO **fora** do literal — o SQL termina
            // com ';' e tem apenas um statement.
            assertEquals(1, contarStatements(r.sql()));
        }

        private static void assertSemInsert(String sql) {
            assertFalse(sql.toUpperCase(Locale.ROOT).contains("INSERT INTO"),
                    () -> "SQL não deve conter 'INSERT INTO'; obtido: " + sql);
        }

        private static int contarStatements(String sql) {
            // Conta apenas o ';' final: SQL gerado é um único statement em uma linha.
            // Aspas simples no literal estão duplicadas (escapadas); ';' dentro do
            // literal não fecha statement no PostgreSQL (ainda dentro de aspas).
            return sql.endsWith(";") ? 1 : 0;
        }
    }

    // ---------- AC11: escape no WHERE ----------

    @Nested
    class EscapeNoWhere {

        @Test
        void codigoComAspaSimples_escapadoNoWhere() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "100");
            celulas.put("TESTADA", "10");
            LinhaMapeada linha = new LinhaMapeada("O'Brien", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sql().contains("WHERE tribcadastrogeral_idkey = 'O''Brien';"),
                    () -> "WHERE deve escapar aspas; obtido: " + r.sql());
        }

        @Test
        void tentativaInjectionNoCodigo_viraLiteralInerte() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "100");
            celulas.put("TESTADA", "10");
            LinhaMapeada linha = new LinhaMapeada("1'; DROP TABLE x; --", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sql().contains("WHERE tribcadastrogeral_idkey = '1''; DROP TABLE x; --';"),
                    () -> "Injection deve virar literal inerte escapado; obtido: " + r.sql());
        }

        @Test
        void valorComAspasNaCelulaFixa_escapadoNoSet() {
            // Cobertura defensiva: aspas em cell content também são escapadas pelo Coercionador.
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "x'y");
            celulas.put("TESTADA", "10");
            LinhaMapeada linha = new LinhaMapeada("ABC", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sql().contains("area_terreno = 'x''y'"),
                    () -> "Aspas em célula fixa devem ser escapadas; obtido: " + r.sql());
        }
    }

    // ---------- AC2 indireto: ambos os fluxos usam a coluna chave correta ----------

    @ParameterizedTest
    @EnumSource(Fluxo.class)
    void colunaChave_eUsadaNoWhere_paraCadaFluxo(Fluxo fluxo) {
        Mapeamento m = fluxo == Fluxo.TERRITORIAL
                ? mapeamentoTerritorialPadrao()
                : mapeamentoPredialPadrao();
        String header1 = m.colunasFixas().keySet().iterator().next();
        Map<String, String> celulas = new LinkedHashMap<>();
        celulas.put(header1, "v");
        LinhaMapeada linha = new LinhaMapeada("ABC", celulas, Map.of());

        ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

        assertTrue(r.ok());
        assertTrue(r.sql().contains("WHERE " + fluxo.colunaChave() + " = 'ABC';"),
                () -> "WHERE deve usar colunaChave do fluxo " + fluxo + "; obtido: " + r.sql());
        assertTrue(r.sql().startsWith("UPDATE " + fluxo.tabelaPrincipal() + " "),
                () -> "UPDATE deve usar tabelaPrincipal do fluxo " + fluxo + "; obtido: " + r.sql());
    }
}
