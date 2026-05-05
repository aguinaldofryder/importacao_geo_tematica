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
 * <p>WHERE do UPDATE usa a chave composta real da tabela principal:
 * <ul>
 *   <li>TERRITORIAL: {@code tipocadastro = 1 AND cadastrogeral = <numeric>}</li>
 *   <li>PREDIAL:     {@code tipocadastro = 1 AND cadastrogeral = <numeric> AND sequencia = <numeric>}</li>
 * </ul>
 * Os valores de {@code cadastrogeral} e {@code sequencia} são coagidos via
 * {@code Tipo.DECIMAL} (sem aspas, sem escape de string — SQL injection impossível
 * por natureza do tipo numérico).
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
                null,
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
                null,
                fixas,
                Map.<String, ColunaDinamica>of());
    }

    private static Mapeamento mapeamentoVazio(Fluxo fluxo) {
        return new Mapeamento(
                fluxo,
                "planilha.xlsx",
                "INSCRICAO_IMOBILIARIA",
                null,
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
            LinhaMapeada l = new LinhaMapeada("123", null, Map.of(), Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, null, Fluxo.TERRITORIAL, coercionador));
        }

        @Test
        void fluxoNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", null, Map.of(), Map.of());
            Mapeamento m = mapeamentoTerritorialPadrao();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, null, coercionador));
        }

        @Test
        void coercionadorNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", null, Map.of(), Map.of());
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
            LinhaMapeada linha = new LinhaMapeada("123", null, celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "esperado ok=true; erros=" + r.erros());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = '350', testada = '12' WHERE tipocadastro = 1 AND cadastrogeral = 123;",
                    r.sql());
            assertEquals(List.of(), r.erros());
        }

        @Test
        void predial_linhaCompleta_geraSqlEsperado() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_CONSTRUIDA", "120,5");
            celulas.put("PADRAO", "Médio");
            LinhaMapeada linha = new LinhaMapeada("999", "1", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoPredialPadrao(), Fluxo.PREDIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "UPDATE tribimobiliariosegmento SET area_construida = '120,5', padrao = 'Médio' WHERE tipocadastro = 1 AND cadastrogeral = 999 AND sequencia = 1;",
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
                null, fixas, Map.<String, ColunaDinamica>of());

        Map<String, String> celulas = new LinkedHashMap<>();
        celulas.put("Z_HEADER", "z");
        celulas.put("A_HEADER", "a");
        celulas.put("M_HEADER", "m");
        celulas.put("B_HEADER", "b");
        LinhaMapeada linha = new LinhaMapeada("777", null, celulas, Map.of());

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
            LinhaMapeada linha = new LinhaMapeada("1001", null, celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = '100', testada = NULL WHERE tipocadastro = 1 AND cadastrogeral = 1001;",
                    r.sql());
        }

        @Test
        void celulaVazia_viraNullLiteral() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "");
            celulas.put("TESTADA", "  ");
            LinhaMapeada linha = new LinhaMapeada("1002", null, celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = NULL, testada = NULL WHERE tipocadastro = 1 AND cadastrogeral = 1002;",
                    r.sql());
        }

        @Test
        void todasCelulasNull_aindaEmiteUpdateValido() {
            // "coluna = NULL" é UPDATE válido (zerar campos é operação legítima).
            LinhaMapeada linha = new LinhaMapeada("1003", null, Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "esperado UPDATE com NULLs; erros=" + r.erros());
            assertEquals(
                    "UPDATE tribcadastroimobiliario SET area_terreno = NULL, testada = NULL WHERE tipocadastro = 1 AND cadastrogeral = 1003;",
                    r.sql());
        }
    }

    // ---------- AC8: caso degenerado ----------

    @Nested
    class CasoDegenerado {

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void mapeamentoSemColunasFixas_retornaFalha(Fluxo fluxo) {
            LinhaMapeada linha = new LinhaMapeada("123", null, Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoVazio(fluxo), fluxo, coercionador);

            assertFalse(r.ok());
            assertNull(r.sql());
            assertEquals(List.of("Linha sem colunas fixas a atualizar"), r.erros());
        }

        @Test
        void codigoNaoNumerico_retornaFalha() {
            // cadastrogeral é NUMERIC — valor não-numérico na planilha é erro de dado.
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "100");
            celulas.put("TESTADA", "10");
            LinhaMapeada linha = new LinhaMapeada("NAO_NUMERICO", null, celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

            assertFalse(r.ok(), "Código não-numérico deve resultar em falha");
            assertTrue(r.erros().stream().anyMatch(e -> e.contains("cadastrogeral")),
                    () -> "Mensagem de erro deve mencionar 'cadastrogeral'; erros=" + r.erros());
        }

        @Test
        void predial_sequenciaNaoNumerica_retornaFalha() {
            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_CONSTRUIDA", "100");
            celulas.put("PADRAO", "Alto");
            LinhaMapeada linha = new LinhaMapeada("900001", "NAO_NUMERICO", celulas, Map.of());

            ResultadoUpdate r = gerador.gerar(linha, mapeamentoPredialPadrao(), Fluxo.PREDIAL, coercionador);

            assertFalse(r.ok(), "Sequência não-numérica deve resultar em falha");
            assertTrue(r.erros().stream().anyMatch(e -> e.contains("sequencia")),
                    () -> "Mensagem de erro deve mencionar 'sequencia'; erros=" + r.erros());
        }
    }

    // ---------- AC9: formato da mensagem de erro (teste documental) ----------

    @Nested
    class FormatoMensagemDeErro {

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
            String mensagemEsperada = "Código do imóvel (cadastrogeral): valor 'X' não é decimal válido";

            ResultadoUpdate r = ResultadoUpdate.falha(List.of(mensagemEsperada));

            assertFalse(r.ok());
            assertTrue(r.erros().get(0).startsWith("Código do imóvel (cadastrogeral):"),
                    () -> "Mensagem deve começar com 'Código do imóvel (cadastrogeral):'; obtido: " + r.erros().get(0));
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
            // Para PREDIAL, sequenciaPredial deve ser numérico
            String seq = fluxo == Fluxo.PREDIAL ? "1" : null;
            LinhaMapeada linha = new LinhaMapeada("900001", seq, celulas, Map.of());

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
            String seq = fluxo == Fluxo.PREDIAL ? "1" : null;
            LinhaMapeada linha = new LinhaMapeada("900001", seq, Map.of(), Map.of());

            ResultadoUpdate r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            assertSemInsert(r.sql());
        }

        private static void assertSemInsert(String sql) {
            assertFalse(sql.toUpperCase(Locale.ROOT).contains("INSERT INTO"),
                    () -> "SQL não deve conter 'INSERT INTO'; obtido: " + sql);
        }
    }

    // ---------- WHERE usa chave composta real ----------

    @Test
    void territorial_whereUsaPkComposta() {
        Map<String, String> celulas = new LinkedHashMap<>();
        celulas.put("AREA_TERRENO", "v");
        celulas.put("TESTADA", "10");
        LinhaMapeada linha = new LinhaMapeada("900001", null, celulas, Map.of());

        ResultadoUpdate r = gerador.gerar(linha, mapeamentoTerritorialPadrao(), Fluxo.TERRITORIAL, coercionador);

        assertTrue(r.ok());
        assertTrue(r.sql().contains("WHERE tipocadastro = 1 AND cadastrogeral = 900001;"),
                () -> "WHERE TERRITORIAL deve usar PK composta sem aspas; obtido: " + r.sql());
        assertTrue(r.sql().startsWith("UPDATE tribcadastroimobiliario "),
                () -> "UPDATE deve usar tabela TERRITORIAL; obtido: " + r.sql());
    }

    @Test
    void predial_whereUsaPkCompostaComSequencia() {
        Map<String, String> celulas = new LinkedHashMap<>();
        celulas.put("AREA_CONSTRUIDA", "v");
        celulas.put("PADRAO", "Alto");
        LinhaMapeada linha = new LinhaMapeada("900001", "2", celulas, Map.of());

        ResultadoUpdate r = gerador.gerar(linha, mapeamentoPredialPadrao(), Fluxo.PREDIAL, coercionador);

        assertTrue(r.ok());
        assertTrue(r.sql().contains("WHERE tipocadastro = 1 AND cadastrogeral = 900001 AND sequencia = 2;"),
                () -> "WHERE PREDIAL deve incluir sequencia sem aspas; obtido: " + r.sql());
        assertTrue(r.sql().startsWith("UPDATE tribimobiliariosegmento "),
                () -> "UPDATE deve usar tabela PREDIAL; obtido: " + r.sql());
    }
}
