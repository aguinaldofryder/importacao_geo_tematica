package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.arxcode.tematica.geo.dominio.Tipo;

/**
 * Testes do {@link Coercionador} — função pura, JUnit Jupiter puro
 * (sem {@code @QuarkusTest}, sem AssertJ — alinhado ao padrão da Story 2.2).
 */
class CoercionadorTest {

    private final Coercionador coercionador = new Coercionador();

    // ---------- AC3: null/vazio em qualquer tipo vira NULL ----------

    @Nested
    class NullEVazioViramNullLiteral {

        @ParameterizedTest
        @EnumSource(Tipo.class)
        void valorNulo_qualquerTipo_retornaNull(Tipo tipo) {
            ResultadoCoercao r = coercionador.coagir(null, tipo, null);
            assertOk(r, "NULL");
        }

        @ParameterizedTest
        @EnumSource(Tipo.class)
        void stringVazia_qualquerTipo_retornaNull(Tipo tipo) {
            ResultadoCoercao r = coercionador.coagir("", tipo, null);
            assertOk(r, "NULL");
        }

        @ParameterizedTest
        @EnumSource(Tipo.class)
        void apenasEspacos_qualquerTipo_retornaNull(Tipo tipo) {
            // AC3 precede AC4: "   " vira NULL mesmo para TEXTO (não vira "''").
            ResultadoCoercao r = coercionador.coagir("   ", tipo, null);
            assertOk(r, "NULL");
        }

        @ParameterizedTest
        @ValueSource(strings = { "\t", "\n", " \t \n " })
        void whitespaceVariado_retornaNull(String valor) {
            assertOk(coercionador.coagir(valor, Tipo.TEXTO, null), "NULL");
        }
    }

    // ---------- AC4: TEXTO ----------

    @Nested
    class Texto {

        @ParameterizedTest
        @CsvSource(delimiterString = "|", quoteCharacter = '"', value = {
                "abc|'abc'",
                "  texto|'texto'",
                "  texto com espaco  |'texto com espaco'",
                "Olá Mundo|'Olá Mundo'",
        })
        void textoSimples_envolveAspasETrim(String entrada, String esperado) {
            assertOk(coercionador.coagir(entrada, Tipo.TEXTO, null), esperado);
        }

        @Test
        void aspaSimples_escapaDuplicandoAspa() {
            // AC9 — caso emblemático de segurança (CON-03).
            assertOk(coercionador.coagir("O'Brien", Tipo.TEXTO, null), "'O''Brien'");
        }

        @Test
        void multiplasAspasSimples_todasEscapadas() {
            assertOk(coercionador.coagir("a''b'c", Tipo.TEXTO, null), "'a''''b''c'");
        }

        @Test
        void newlineLiteral_preservadaSemEscape() {
            // PostgreSQL com standard_conforming_strings=on aceita newline em '...'.
            assertOk(coercionador.coagir("linha1\nlinha2", Tipo.TEXTO, null), "'linha1\nlinha2'");
        }

        @Test
        void backslash_naoEscapado() {
            // Sem prefixo E'', backslash é literal.
            assertOk(coercionador.coagir("a\\b", Tipo.TEXTO, null), "'a\\b'");
        }
    }

    // ---------- AC5: DECIMAL ----------

    @Nested
    class Decimal {

        @ParameterizedTest
        @CsvSource(delimiterString = "|", value = {
                "1.234,56|1234.56",
                "1234.56|1234.56",
                "42|42",
                "-1,5|-1.5",
                "-1.234,56|-1234.56",
                "1.000.000,00|1000000.00",
                "0|0",
                "0,0|0.0",
        })
        void formatosValidos_normalizamParaIso(String entrada, String esperado) {
            assertOk(coercionador.coagir(entrada, Tipo.DECIMAL, null), esperado);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                ",5",        // sem inteiro
                "1.",        // ponto trailing (regex rejeita)
                "1e3",       // notação científica
                "1E3",
                "abc",       // letras
                "1 000",     // espaço interno
                "+1.5",      // sinal positivo explícito (rejeitado por design)
                "1,2,3",     // múltiplas vírgulas
                "1.2.3",     // múltiplos pontos sem vírgula → não é milhar
                "--1",       // duplo sinal
                "1,",        // vírgula trailing
        })
        void formatosInvalidos_retornamFalhaPt(String entrada) {
            ResultadoCoercao r = coercionador.coagir(entrada, Tipo.DECIMAL, null);
            assertFalha(r, "valor '" + entrada + "' não é decimal válido");
        }
    }

    // ---------- AC6: DATA ----------

    @Nested
    class Data {

        @ParameterizedTest
        @CsvSource(delimiterString = "|", quoteCharacter = '"', value = {
                "01/01/2025|DATE '2025-01-01'",
                "31/12/2025|DATE '2025-12-31'",
                "2025-12-31|DATE '2025-12-31'",
                "2025-01-01|DATE '2025-01-01'",
                "29/02/2024|DATE '2024-02-29'",
        })
        void formatosValidos_normalizamParaIso(String entrada, String esperado) {
            assertOk(coercionador.coagir(entrada, Tipo.DATA, null), esperado);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "31/02/2025",   // STRICT rejeita (fevereiro não tem 31)
                "2025-02-31",   // idem em ISO
                "01/01/25",     // ano com 2 dígitos
                "2025/01/01",   // separador errado para ano-primeiro
                "29/02/2025",   // 2025 não é bissexto
                "32/01/2025",   // dia inválido
                "01/13/2025",   // mês inválido
                "abc",          // sem separador
                "01-01-2025",   // separador errado para dd-MM-yyyy
        })
        void formatosInvalidos_retornamFalhaPt(String entrada) {
            ResultadoCoercao r = coercionador.coagir(entrada, Tipo.DATA, null);
            assertFalha(r, "valor '" + entrada + "' não é data válida (formatos aceitos: dd/MM/yyyy, yyyy-MM-dd)");
        }
    }

    // ---------- AC7: MULTIPLA_ESCOLHA ----------

    @Nested
    class MultiplaEscolha {

        private final Map<String, Integer> alternativasPadrao = Map.of("Sim", 1, "Não", 2);

        @Test
        void matchExato_retornaIdComoLiteralNumerico() {
            assertOk(coercionador.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, alternativasPadrao), "1");
            assertOk(coercionador.coagir("Não", Tipo.MULTIPLA_ESCOLHA, alternativasPadrao), "2");
        }

        @ParameterizedTest
        @ValueSource(strings = { "sim", "SIM", "SiM", "  sim  ", "\tSim\n" })
        void matchCaseInsensitiveETrim_resolveCorretamente(String entrada) {
            // Alinhado a importacao.mapeamento.case-sensitive=false / trim-espacos=true.
            assertOk(coercionador.coagir(entrada, Tipo.MULTIPLA_ESCOLHA, alternativasPadrao), "1");
        }

        @Test
        void semMatch_retornaFalhaNaoMapeada() {
            ResultadoCoercao r = coercionador.coagir("Talvez", Tipo.MULTIPLA_ESCOLHA, alternativasPadrao);
            assertFalha(r, "alternativa 'Talvez' não mapeada");
        }

        @ParameterizedTest
        @NullAndEmptySource
        void mapaNulOuVazio_retornaFalhaMapeamentoAusente(Map<String, Integer> alternativas) {
            ResultadoCoercao r = coercionador.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, alternativas);
            assertFalha(r, "mapeamento ausente para tipo MULTIPLA_ESCOLHA");
        }

        @Test
        void valueNulo_retornaFalhaMapeamentoCorrompido() {
            // AC7 defesa em profundidade — AutoMapeador.java:213 pode inserir put("Sim", null).
            // Map.of não aceita null, então usamos HashMap explicitamente.
            Map<String, Integer> mapaCorrompido = new HashMap<>();
            mapaCorrompido.put("Sim", null);

            ResultadoCoercao r = coercionador.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, mapaCorrompido);
            assertFalha(r, "alternativa 'Sim' encontrada no mapeamento mas com id nulo (mapeamento corrompido)");
        }

        @Test
        void chaveNulaNoMapa_naoQuebra_continuaIterando() {
            // Chave null no mapa não deve causar NPE — apenas não casa com nada.
            Map<String, Integer> mapa = new HashMap<>();
            mapa.put(null, 99);
            mapa.put("Sim", 1);

            assertOk(coercionador.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, mapa), "1");
        }

        @Test
        void chaveDoMapaComEspacos_normalizadaPorTrim() {
            Map<String, Integer> mapa = Map.of("  Sim  ", 1);
            assertOk(coercionador.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, mapa), "1");
        }
    }

    // ---------- AC9: Segurança (smoke) ----------

    @Nested
    class SegurancaCon03 {

        @Test
        void apostrofoEmTexto_viraDuploApostrofo() {
            // Caso emblemático — não pode regredir.
            assertOk(coercionador.coagir("O'Brien", Tipo.TEXTO, null), "'O''Brien'");
        }

        @Test
        void tentativaInjectionEmTexto_neutralizada() {
            // Aspa simples de fechamento vira '', injeção vira string inerte.
            ResultadoCoercao r = coercionador.coagir("'; DROP TABLE x; --", Tipo.TEXTO, null);
            assertOk(r, "'''; DROP TABLE x; --'");
            // Verificação adicional: o literal começa e termina com aspa simples
            // (não há aspa solta abrindo um novo statement).
            String lit = r.literalSql();
            assertNotNull(lit);
            assertTrue(lit.startsWith("'") && lit.endsWith("'"),
                    "literal deve estar envolvido em aspas simples balanceadas");
        }

        @Test
        void tentativaInjectionEmMultiplaEscolha_naoViraLiteralNumerico() {
            // Cai no caminho "alternativa não mapeada" — não vira "1; DROP".
            Map<String, Integer> alternativas = Map.of("Sim", 1, "Não", 2);
            ResultadoCoercao r = coercionador.coagir("1; DROP", Tipo.MULTIPLA_ESCOLHA, alternativas);
            assertFalha(r, "alternativa '1; DROP' não mapeada");
        }
    }

    // ---------- AC8: imutabilidade / thread-safety ----------

    @Test
    void coercionador_eReusavel() {
        Coercionador c = new Coercionador();
        // Múltiplas invocações com tipos diferentes — não deve haver estado.
        assertOk(c.coagir("abc", Tipo.TEXTO, null), "'abc'");
        assertOk(c.coagir("1.234,56", Tipo.DECIMAL, null), "1234.56");
        assertOk(c.coagir("01/01/2025", Tipo.DATA, null), "DATE '2025-01-01'");
        assertOk(c.coagir("Sim", Tipo.MULTIPLA_ESCOLHA, Map.of("Sim", 1)), "1");
        // Reexecutar primeira chamada — resultado idêntico.
        assertOk(c.coagir("abc", Tipo.TEXTO, null), "'abc'");
    }

    // ---------- Helpers ----------

    private static void assertOk(ResultadoCoercao r, String literalEsperado) {
        assertTrue(r.ok(), () -> "esperado ok=true, mas erro=" + r.erro());
        assertEquals(literalEsperado, r.literalSql());
        assertNull(r.erro());
    }

    private static void assertFalha(ResultadoCoercao r, String erroEsperado) {
        assertFalse(r.ok(), () -> "esperado ok=false, mas literal=" + r.literalSql());
        assertNull(r.literalSql());
        assertEquals(erroEsperado, r.erro());
    }
}
