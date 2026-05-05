package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Testes do record {@link Mapeamento} — validações de null e
 * normalização (trim, null→Map.of()).
 *
 * <p>Cobre AC2 e AC9.
 */
class MapeamentoTest {

    @Test
    void construtor_camposObrigatoriosOk() {
        Mapeamento m = new Mapeamento(
                Fluxo.TERRITORIAL, "X.xlsx", "MAT", null, Map.of(), Map.of());
        assertNotNull(m);
        assertEquals(Fluxo.TERRITORIAL, m.fluxo());
        assertEquals("X.xlsx", m.planilha());
        assertEquals("MAT", m.colunaCodigoImovel());
    }

    @Test
    void construtor_fluxoNulo_lanca() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Mapeamento(null, "X.xlsx", "MAT", null, Map.of(), Map.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("fluxo"),
                "mensagem deve identificar o campo: " + ex.getMessage());
    }

    @Test
    void construtor_planilhaNula_lanca() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Mapeamento(Fluxo.TERRITORIAL, null, "MAT", null, Map.of(), Map.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("planilha"),
                "mensagem deve identificar o campo: " + ex.getMessage());
    }

    @Test
    void construtor_colunaCodigoImovelNula_lanca() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Mapeamento(Fluxo.TERRITORIAL, "X.xlsx", null, null, Map.of(), Map.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("colunacodigoimovel"),
                "mensagem deve identificar o campo: " + ex.getMessage());
    }

    @Test
    void construtor_aplicaTrim() {
        Mapeamento m = new Mapeamento(
                Fluxo.PREDIAL, "  Y.xlsx  ", "  IDKEY  ", null, Map.of(), Map.of());
        assertEquals("Y.xlsx", m.planilha());
        assertEquals("IDKEY", m.colunaCodigoImovel());
    }

    @Test
    void construtor_mapasNulosViramVazios() {
        Mapeamento m = new Mapeamento(
                Fluxo.TERRITORIAL, "X.xlsx", "MAT", null, null, null);
        assertNotNull(m.colunasFixas());
        assertNotNull(m.colunasDinamicas());
        assertTrue(m.colunasFixas().isEmpty());
        assertTrue(m.colunasDinamicas().isEmpty());
    }

    @Test
    void igualdadeEstrutural_records() {
        Mapeamento a = new Mapeamento(
                Fluxo.TERRITORIAL, "X.xlsx", "MAT", null,
                Map.of("AREA", "area_terreno"), Map.of());
        Mapeamento b = new Mapeamento(
                Fluxo.TERRITORIAL, "X.xlsx", "MAT", null,
                Map.of("AREA", "area_terreno"), Map.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void mapasNaoNulos_saoPreservadosPorReferencia() {
        Map<String, String> fixas = Map.of("A", "a");
        Mapeamento m = new Mapeamento(
                Fluxo.TERRITORIAL, "X.xlsx", "MAT", null, fixas, Map.of());
        assertSame(fixas, m.colunasFixas());
    }
}
