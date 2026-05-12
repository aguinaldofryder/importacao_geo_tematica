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

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.LinhaMapeada;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

/**
 * Testes do {@link SqlGeradorInsertSegmento} — função pura, JUnit Jupiter puro.
 *
 * <p>Cobre:
 * <ul>
 *   <li>Validação de argumentos (null/fluxo incorreto/sequencia nula).</li>
 *   <li>Forma do SQL gerado (colunas, nextval, ordem).</li>
 *   <li>Forma do subselect de referência retornado.</li>
 *   <li>Falha de coerção de colunas fixas.</li>
 *   <li>Caso degenerado: sem colunas fixas.</li>
 * </ul>
 */
class SqlGeradorInsertSegmentoTest {

    private final SqlGeradorInsertSegmento gerador = new SqlGeradorInsertSegmento();
    private final Coercionador coercionador = new Coercionador();

    // ---------- Helpers ----------

    private static Mapeamento mapeamentoPredialComFixas(Map<String, String> fixas) {
        return new Mapeamento(Fluxo.PREDIAL, "p.xlsx", "INSCRICAO", "SEQUENCIA", fixas, Map.of());
    }

    private static Mapeamento mapeamentoPredialSemFixas() {
        return mapeamentoPredialComFixas(Map.of());
    }

    private static LinhaMapeada linhaPredial(long codigoImovel, long sequencia, Map<String, String> fixas) {
        return new LinhaMapeada(codigoImovel, sequencia, fixas, Map.of());
    }

    // ---------- Validação de argumentos ----------

    @Nested
    class ValidacaoDeArgumentos {

        @Test
        void linhaNula_lancaIae() {
            Mapeamento m = mapeamentoPredialSemFixas();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(null, m, coercionador));
        }

        @Test
        void mapeamentoNulo_lancaIae() {
            LinhaMapeada l = linhaPredial(123L, 1L, Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, null, coercionador));
        }

        @Test
        void coercionadorNulo_lancaIae() {
            LinhaMapeada l = linhaPredial(123L, 1L, Map.of());
            Mapeamento m = mapeamentoPredialSemFixas();
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, null));
        }

        @Test
        void fluxoTerritorial_lancaIae() {
            Mapeamento m = new Mapeamento(Fluxo.TERRITORIAL, "p.xlsx", "INSCRICAO", null, Map.of(), Map.of());
            LinhaMapeada l = new LinhaMapeada(123L, null, Map.of(), Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, coercionador));
        }

        @Test
        void sequenciaPredialNula_lancaIae() {
            Mapeamento m = mapeamentoPredialSemFixas();
            LinhaMapeada l = new LinhaMapeada(123L, null, Map.of(), Map.of());
            assertThrows(IllegalArgumentException.class,
                    () -> gerador.gerar(l, m, coercionador));
        }
    }

    // ---------- Caso degenerado: sem colunas fixas ----------

    @Nested
    class SemColunasFixas {

        @Test
        void semColunasFixas_geraInsertApenasComChaveComposta() {
            Mapeamento m = mapeamentoPredialSemFixas();
            LinhaMapeada l = linhaPredial(456L, 2L, Map.of());

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            assertTrue(r.ok(), () -> "erros=" + r.erros());
            String sql = r.sql();
            assertTrue(sql.contains("INSERT INTO aise.tribimobiliariosegmento"), () -> sql);
            assertTrue(sql.contains("idkey"), () -> sql);
            assertTrue(sql.contains("tipocadastro"), () -> sql);
            assertTrue(sql.contains("cadastrogeral"), () -> sql);
            assertTrue(sql.contains("sequencia"), () -> sql);
            assertTrue(sql.contains("nextval('aise.s_tribimobiliariosegmento_id')"), () -> sql);
            assertTrue(sql.contains("456"), () -> "deve conter cadastrogeral: " + sql);
            assertTrue(sql.contains(", 2,") || sql.contains(", 2)"), () -> "deve conter sequencia=2: " + sql);
        }
    }

    // ---------- Forma do SQL e subselect ----------

    @Nested
    class FormaSql {

        @Test
        void comColunasFixas_inclui_fixasNoInsert() {
            Map<String, String> fixas = new LinkedHashMap<>();
            fixas.put("AREA_TERRENO", "area");
            fixas.put("SITUACAO", "situacao");
            Mapeamento m = mapeamentoPredialComFixas(fixas);

            Map<String, String> celulas = new LinkedHashMap<>();
            celulas.put("AREA_TERRENO", "120");
            celulas.put("SITUACAO", "ATIVO");
            LinhaMapeada l = linhaPredial(100L, 3L, celulas);

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            assertTrue(r.ok(), () -> "erros=" + r.erros());
            String sql = r.sql();

            // Colunas devem aparecer na ordem: idkey, tipocadastro, cadastrogeral, sequencia, area, situacao
            assertTrue(sql.contains("(idkey, tipocadastro, cadastrogeral, sequencia, area, situacao)"),
                    () -> "ordem de colunas incorreta: " + sql);
            // VALUES deve conter nextval, 1, 100, 3, '120', 'ATIVO'
            assertTrue(sql.contains("nextval('aise.s_tribimobiliariosegmento_id'), 1, 100, 3"), () -> sql);
            assertTrue(sql.contains("'120'"), () -> "valor de area deve aparecer: " + sql);
            assertTrue(sql.contains("'ATIVO'"), () -> "valor de situacao deve aparecer: " + sql);
            assertTrue(sql.endsWith(";"), () -> "SQL deve terminar com ;: " + sql);
        }

        @Test
        void referenciaSubselect_temFormaCorreta() {
            Mapeamento m = mapeamentoPredialSemFixas();
            LinhaMapeada l = linhaPredial(789L, 5L, Map.of());

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            assertTrue(r.ok());
            String sub = r.referenciaSubselect();
            assertEquals(
                    "(SELECT idkey FROM aise.tribimobiliariosegmento WHERE tipocadastro = 1 AND cadastrogeral = 789 AND sequencia = 5)",
                    sub);
        }

        @Test
        void sqlTerminaComPontoVirgula() {
            Mapeamento m = mapeamentoPredialSemFixas();
            LinhaMapeada l = linhaPredial(1L, 1L, Map.of());

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            assertTrue(r.ok());
            assertTrue(r.sql().endsWith(";"), () -> r.sql());
        }

        @Test
        void naoContemUpdateNemDelete() {
            Mapeamento m = mapeamentoPredialSemFixas();
            LinhaMapeada l = linhaPredial(1L, 1L, Map.of());

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            assertTrue(r.ok());
            assertFalse(r.sql().contains("UPDATE"), () -> "não deve conter UPDATE: " + r.sql());
            assertFalse(r.sql().contains("DELETE"), () -> "não deve conter DELETE: " + r.sql());
        }
    }

    // ---------- Falha de coerção ----------

    @Nested
    class FalhaCoercao {

        @Test
        void valorFixoVazio_naoFalha_tratadoComoNull() {
            // Coercionador TEXTO aceita null/vazio como NULL SQL — não deve ser falha de coerção
            Map<String, String> fixas = Map.of("COL", "col_fisica");
            Mapeamento m = mapeamentoPredialComFixas(fixas);

            Map<String, String> celulas = Map.of("COL", "");
            LinhaMapeada l = linhaPredial(1L, 1L, celulas);

            ResultadoInsertSegmento r = gerador.gerar(l, m, coercionador);

            // Coercionador.coagir("", TEXTO, null) retorna ok com NULL
            assertTrue(r.ok(), () -> "erros=" + r.erros());
        }
    }

    // ---------- ResultadoInsertSegmento: fábricas ----------

    @Nested
    class ResultadoInsertSegmentoFabricas {

        @Test
        void sucesso_okTrue_sqlENaoNulo() {
            ResultadoInsertSegmento r = ResultadoInsertSegmento.sucesso("INSERT INTO ...", "(SELECT idkey ...)");
            assertTrue(r.ok());
            assertEquals("INSERT INTO ...", r.sql());
            assertEquals("(SELECT idkey ...)", r.referenciaSubselect());
            assertEquals(List.of(), r.erros());
        }

        @Test
        void falha_okFalse_sqlNulo() {
            ResultadoInsertSegmento r = ResultadoInsertSegmento.falha(List.of("erro1", "erro2"));
            assertFalse(r.ok());
            assertEquals(null, r.sql());
            assertEquals(null, r.referenciaSubselect());
            assertEquals(List.of("erro1", "erro2"), r.erros());
        }

        @Test
        void sucesso_sqlVazio_lancaIae() {
            assertThrows(IllegalArgumentException.class,
                    () -> ResultadoInsertSegmento.sucesso("", "(SELECT ...)"));
        }

        @Test
        void falha_listaVazia_lancaIae() {
            assertThrows(IllegalArgumentException.class,
                    () -> ResultadoInsertSegmento.falha(List.of()));
        }
    }
}
