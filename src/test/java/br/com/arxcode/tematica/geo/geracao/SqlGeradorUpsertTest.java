package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

/**
 * Testes do {@link SqlGeradorUpsert} — função pura, JUnit Jupiter puro.
 *
 * <p>Cobertura dos critérios de aceite AC2–AC11, AC15. Usa
 * {@link LinkedHashMap} explícito em colunasDinamicas/celulasDinamicas
 * (premissa de ordem — pendência ISSUE-4.2-01).
 */
class SqlGeradorUpsertTest {

    private final SqlGeradorUpsert gerador = new SqlGeradorUpsert();
    private final Coercionador coercionador = new Coercionador();

    // ---------- Helpers ----------

    private static ColunaDinamica colunaMapeada(int idcampo, Tipo tipo, Map<String, Integer> alternativas) {
        return new ColunaDinamica(StatusMapeamento.MAPEADO, idcampo, tipo, alternativas, null, null);
    }

    private static ColunaDinamica colunaPendente() {
        return new ColunaDinamica(StatusMapeamento.PENDENTE, null, null, null, "não mapeada", null);
    }

    private static Mapeamento mapeamentoComDinamicas(Fluxo fluxo, Map<String, ColunaDinamica> dinamicas) {
        return new Mapeamento(fluxo, "p.xlsx", "INSCRICAO", null, Map.of(), dinamicas);
    }

    // ---------- AC2: validação de argumentos ----------

    @Nested
    class ValidacaoDeArgumentos {

        @Test
        void linhaNula_lancaIae() {
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, Map.of());
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
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, null, coercionador));
        }

        @Test
        void coercionadorNulo_lancaIae() {
            LinhaMapeada l = new LinhaMapeada("123", null, Map.of(), Map.of());
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, Fluxo.TERRITORIAL, null));
        }
    }

    // ---------- AC8: caso degenerado ----------

    @Nested
    class CasosDegenerados {

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void mapeamentoSemDinamicas_retornaSucessoListaVazia(Fluxo fluxo) {
            LinhaMapeada linha = new LinhaMapeada("ABC", null, Map.of(), Map.of());
            Mapeamento m = mapeamentoComDinamicas(fluxo, Map.of());

            ResultadoUpsert r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok(), () -> "esperado ok=true; erros=" + r.erros());
            assertEquals(List.of(), r.sqls());
        }

        @Test
        void todasDinamicasVazias_retornaSucessoListaVazia() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("CAMPO_A", colunaMapeada(10, Tipo.TEXTO, null));
            dinamicas.put("CAMPO_B", colunaMapeada(11, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("CAMPO_A", "");
            celulas.put("CAMPO_B", "   ");
            LinhaMapeada linha = new LinhaMapeada("X1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(List.of(), r.sqls());
        }

        @Test
        void todasDinamicasPendentes_retornaSucessoListaVazia() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("CAMPO_A", colunaPendente());
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("CAMPO_A", "valor qualquer");
            LinhaMapeada linha = new LinhaMapeada("X1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(List.of(), r.sqls());
        }
    }

    // ---------- AC3: filtragem de células ----------

    @Nested
    class FiltragemDeCelulas {

        @Test
        void colunaPendente_pulada_outrasMapeadasGeramSql() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("CAMPO_PENDENTE", colunaPendente());
            dinamicas.put("CAMPO_OK", colunaMapeada(42, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("CAMPO_PENDENTE", "valor pendente");
            celulas.put("CAMPO_OK", "valor ok");
            LinhaMapeada linha = new LinhaMapeada("REF1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            // 2 SQLs (1 par DELETE+INSERT) — só CAMPO_OK gerou.
            assertEquals(2, r.sqls().size());
            assertTrue(r.sqls().get(0).contains("idcampo = 42"));
            assertFalse(r.sqls().get(0).contains("PENDENTE"),
                    () -> "PENDENTE não deveria aparecer no SQL: " + r.sqls().get(0));
        }

        @Test
        void celulaNullOuBlank_pulada() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("CAMPO_VAZIO", colunaMapeada(10, Tipo.TEXTO, null));
            dinamicas.put("CAMPO_BLANK", colunaMapeada(11, Tipo.TEXTO, null));
            dinamicas.put("CAMPO_OK", colunaMapeada(12, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("CAMPO_VAZIO", "");
            celulas.put("CAMPO_BLANK", "   ");
            celulas.put("CAMPO_OK", "valor");
            LinhaMapeada linha = new LinhaMapeada("REF", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(2, r.sqls().size());
            assertTrue(r.sqls().get(0).contains("idcampo = 12"));
        }

        @Test
        void celulaAusenteNoMap_pulada() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("CAMPO_AUSENTE", colunaMapeada(10, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            // Linha sem celula para CAMPO_AUSENTE
            LinhaMapeada linha = new LinhaMapeada("REF", null, Map.of(), Map.of());

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(List.of(), r.sqls());
        }
    }

    // ---------- AC6/AC11: forma do SQL por tipo ----------

    @Nested
    class FormaSqlPorTipo {

        @Test
        void texto_geraSqlComValorAspeadoEIdAlternativaNull() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBSERVACAO", colunaMapeada(100, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBSERVACAO", "casa simples");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "erros=" + r.erros());
            assertEquals(2, r.sqls().size());
            assertEquals(
                    "DELETE FROM aise.respostaterreno WHERE referencia = 'R1' AND idcampo = 100;",
                    r.sqls().get(0));
            assertEquals(
                    "INSERT INTO aise.respostaterreno (id, referencia, valor, idcampo, idalternativa) VALUES "
                            + "(nextval('aise.s_respostaterreno_id'), 'R1', 'casa simples', 100, NULL);",
                    r.sqls().get(1));
        }

        @Test
        void decimal_geraSqlComValorEnvelopadoEmAspas() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("AREA", colunaMapeada(200, Tipo.DECIMAL, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA", "1234.56");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "DELETE FROM aise.respostaterreno WHERE referencia = 'R1' AND idcampo = 200;",
                    r.sqls().get(0));
            assertEquals(
                    "INSERT INTO aise.respostaterreno (id, referencia, valor, idcampo, idalternativa) VALUES "
                            + "(nextval('aise.s_respostaterreno_id'), 'R1', '1234.56', 200, NULL);",
                    r.sqls().get(1));
        }

        @Test
        void data_isoNaEntrada_geraSaidaBR() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("DT_AVAL", colunaMapeada(300, Tipo.DATA, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("DT_AVAL", "2024-01-15");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "INSERT INTO aise.respostaterreno (id, referencia, valor, idcampo, idalternativa) VALUES "
                            + "(nextval('aise.s_respostaterreno_id'), 'R1', '15/01/2024', 300, NULL);",
                    r.sqls().get(1));
        }

        @Test
        void data_brNaEntrada_geraMesmaSaidaBR() {
            // Round-trip: ISO e BR convergem para saída BR (via Coercionador → ISO interno).
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("DT_AVAL", colunaMapeada(300, Tipo.DATA, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("DT_AVAL", "15/01/2024");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sqls().get(1).contains("'15/01/2024'"),
                    () -> "DATA deveria sair BR: " + r.sqls().get(1));
        }

        @Test
        void multiplaEscolha_geraValorVazioEIdAlternativaNumerico() {
            Map<String, Integer> alternativas = new LinkedHashMap<>();
            alternativas.put("CASA", 7);
            alternativas.put("APARTAMENTO", 8);
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("TIPO_IMOVEL", colunaMapeada(400, Tipo.MULTIPLA_ESCOLHA, alternativas));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("TIPO_IMOVEL", "CASA");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok(), () -> "erros=" + r.erros());
            assertEquals(
                    "DELETE FROM aise.respostaterreno WHERE referencia = 'R1' AND idcampo = 400;",
                    r.sqls().get(0));
            assertEquals(
                    "INSERT INTO aise.respostaterreno (id, referencia, valor, idcampo, idalternativa) VALUES "
                            + "(nextval('aise.s_respostaterreno_id'), 'R1', '', 400, 7);",
                    r.sqls().get(1));
        }

        @Test
        void predial_usaTabelaESequenciaCorretas() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBSERVACAO", colunaMapeada(100, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.PREDIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBSERVACAO", "obs");
            LinhaMapeada linha = new LinhaMapeada("R2", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.PREDIAL, coercionador);

            assertTrue(r.ok());
            assertEquals(
                    "DELETE FROM aise.respostasegmento WHERE referencia = 'R2' AND idcampo = 100;",
                    r.sqls().get(0));
            assertTrue(r.sqls().get(1).contains("nextval('aise.s_respostasegmento_id')"),
                    () -> "Predial deveria usar sequence respostasegmento: " + r.sqls().get(1));
        }
    }

    // ---------- AC9: acumulação plena de erros (fecha ISSUE-4.2-02) ----------

    @Nested
    class AcumulacaoDeErros {

        @Test
        void duasFalhasSimultaneas_decimalEData_acumulaAmbas() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("AREA", colunaMapeada(10, Tipo.DECIMAL, null));
            dinamicas.put("DATA_AVAL", colunaMapeada(20, Tipo.DATA, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA", "abc");
            celulas.put("DATA_AVAL", "xyz");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertFalse(r.ok());
            assertEquals(List.of(), r.sqls(), "nenhum SQL emitido em falha");
            assertEquals(2, r.erros().size());
            assertTrue(r.erros().get(0).startsWith("Coluna 'AREA': "), () -> r.erros().toString());
            assertTrue(r.erros().get(1).startsWith("Coluna 'DATA_AVAL': "), () -> r.erros().toString());
        }

        @Test
        void multiplaEscolhaSemAlternativaMapeada_acumulaErro() {
            Map<String, Integer> alternativas = Map.of("CASA", 7);
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("TIPO", colunaMapeada(30, Tipo.MULTIPLA_ESCOLHA, alternativas));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("TIPO", "INEXISTENTE");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertFalse(r.ok());
            assertEquals(1, r.erros().size());
            assertTrue(r.erros().get(0).startsWith("Coluna 'TIPO': "));
        }

        @Test
        void erroEmUmaCelula_naoEmiteSqlParaAsValidasDaMesmaLinha() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OK_TEXTO", colunaMapeada(10, Tipo.TEXTO, null));
            dinamicas.put("RUIM_DECIMAL", colunaMapeada(20, Tipo.DECIMAL, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OK_TEXTO", "valido");
            celulas.put("RUIM_DECIMAL", "abc");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertFalse(r.ok());
            assertEquals(List.of(), r.sqls());
            assertEquals(1, r.erros().size());
        }
    }

    // ---------- AC10: escape em referencia (CON-03) ----------

    @Nested
    class EscapeReferencia {

        @Test
        void codigoComAspaSimples_escapaNoDeleteEInsert() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBS", colunaMapeada(10, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBS", "ok");
            LinhaMapeada linha = new LinhaMapeada("O'Brien", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sqls().get(0).contains("referencia = 'O''Brien'"),
                    () -> "DELETE deve escapar aspas: " + r.sqls().get(0));
            assertTrue(r.sqls().get(1).contains("'O''Brien'"),
                    () -> "INSERT deve escapar aspas: " + r.sqls().get(1));
        }
    }

    // ---------- AC11: tentativa de injection ----------

    @Nested
    class EscapeInjection {

        @Test
        void injectionNaReferencia_viraLiteralInerte() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBS", colunaMapeada(10, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBS", "ok");
            LinhaMapeada linha = new LinhaMapeada("1'; DROP TABLE x; --", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sqls().get(0).contains("'1''; DROP TABLE x; --'"),
                    () -> "Injection deve virar literal escapado: " + r.sqls().get(0));
        }

        @Test
        void valorTextoComAspas_escapaNoInsert() {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBS", colunaMapeada(10, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(Fluxo.TERRITORIAL, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBS", "x'y");
            LinhaMapeada linha = new LinhaMapeada("R1", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, Fluxo.TERRITORIAL, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sqls().get(1).contains("'x''y'"),
                    () -> "Valor texto deve ter aspas escapadas: " + r.sqls().get(1));
        }
    }

    // ---------- AC15: smoke não-regressão de formato ----------

    @Nested
    class SmokeFormatoNaoRegressao {

        @ParameterizedTest
        @EnumSource(Fluxo.class)
        void cadaPar_seguePadraoCanonico(Fluxo fluxo) {
            Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
            dinamicas.put("OBS", colunaMapeada(10, Tipo.TEXTO, null));
            Mapeamento m = mapeamentoComDinamicas(fluxo, dinamicas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("OBS", "v");
            LinhaMapeada linha = new LinhaMapeada("REF", null, Map.of(), celulas);

            ResultadoUpsert r = gerador.gerar(linha, m, fluxo, coercionador);

            assertTrue(r.ok());
            assertEquals(2, r.sqls().size());

            String del = r.sqls().get(0);
            String ins = r.sqls().get(1);

            assertTrue(del.startsWith("DELETE FROM aise." + fluxo.tabelaRespostas()),
                    () -> "DELETE deve começar com schema+tabela: " + del);
            assertTrue(del.contains("WHERE referencia ="), () -> del);
            assertTrue(del.contains("AND idcampo ="), () -> del);
            assertTrue(del.endsWith(";"), () -> del);

            assertTrue(ins.startsWith("INSERT INTO aise." + fluxo.tabelaRespostas()),
                    () -> "INSERT deve começar com schema+tabela: " + ins);
            assertTrue(ins.contains("(id, referencia, valor, idcampo, idalternativa)"), () -> ins);
            assertTrue(ins.contains("nextval('aise." + fluxo.sequenceRespostas() + "')"), () -> ins);
            assertTrue(ins.endsWith(";"), () -> ins);
        }
    }
}
