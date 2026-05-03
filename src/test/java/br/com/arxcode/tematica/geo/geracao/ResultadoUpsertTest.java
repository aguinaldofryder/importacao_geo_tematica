package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Testes do {@link ResultadoUpsert} (Story 4.3 AC1).
 */
class ResultadoUpsertTest {

    // ---------- Fábrica sucesso ----------

    @Nested
    class FabricaSucesso {

        @Test
        void sucesso_listaVazia_aceita() {
            ResultadoUpsert r = ResultadoUpsert.sucesso(List.of());
            assertTrue(r.ok());
            assertEquals(List.of(), r.sqls());
            assertEquals(List.of(), r.erros());
        }

        @Test
        void sucesso_listaComSqls_aceita() {
            ResultadoUpsert r = ResultadoUpsert.sucesso(List.of("DELETE FROM x;", "INSERT INTO x;"));
            assertTrue(r.ok());
            assertEquals(2, r.sqls().size());
            assertEquals("DELETE FROM x;", r.sqls().get(0));
        }

        @Test
        void sucesso_listaNula_lancaIae() {
            assertThrows(IllegalArgumentException.class, () -> ResultadoUpsert.sucesso(null));
        }

        @Test
        void sucesso_elementoNulo_lancaIae() {
            List<String> sqls = Arrays.asList("DELETE FROM x;", null);
            assertThrows(IllegalArgumentException.class, () -> ResultadoUpsert.sucesso(sqls));
        }

        @Test
        void sucesso_elementoBlank_lancaIae() {
            assertThrows(IllegalArgumentException.class,
                    () -> ResultadoUpsert.sucesso(List.of("DELETE FROM x;", "   ")));
        }
    }

    // ---------- Fábrica falha ----------

    @Nested
    class FabricaFalha {

        @Test
        void falha_listaComErros_aceita() {
            ResultadoUpsert r = ResultadoUpsert.falha(List.of("erro1", "erro2"));
            assertFalse(r.ok());
            assertEquals(List.of(), r.sqls());
            assertEquals(2, r.erros().size());
        }

        @Test
        void falha_listaNula_lancaIae() {
            assertThrows(IllegalArgumentException.class, () -> ResultadoUpsert.falha(null));
        }

        @Test
        void falha_listaVazia_lancaIae() {
            assertThrows(IllegalArgumentException.class, () -> ResultadoUpsert.falha(List.of()));
        }

        @Test
        void falha_elementoNulo_lancaIae() {
            List<String> erros = Arrays.asList("erro1", null);
            assertThrows(IllegalArgumentException.class, () -> ResultadoUpsert.falha(erros));
        }

        @Test
        void falha_elementoBlank_lancaIae() {
            assertThrows(IllegalArgumentException.class,
                    () -> ResultadoUpsert.falha(List.of("erro1", "")));
        }
    }

    // ---------- Construtor canônico ----------

    @Nested
    class ConstrutorCanonico {

        @Test
        void nullSqls_normalizaParaListaVazia() {
            ResultadoUpsert r = new ResultadoUpsert(null, List.of("e"));
            assertEquals(List.of(), r.sqls());
        }

        @Test
        void nullErros_normalizaParaListaVazia() {
            ResultadoUpsert r = new ResultadoUpsert(List.of("s"), null);
            assertEquals(List.of(), r.erros());
        }

        @Test
        void copyOf_garanteImutabilidade() {
            List<String> mutavel = new ArrayList<>();
            mutavel.add("DELETE FROM x;");
            ResultadoUpsert r = ResultadoUpsert.sucesso(mutavel);
            mutavel.add("outro");
            assertEquals(1, r.sqls().size());
        }
    }
}
