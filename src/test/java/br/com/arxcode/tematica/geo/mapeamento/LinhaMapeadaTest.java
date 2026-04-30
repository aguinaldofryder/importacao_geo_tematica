package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LinhaMapeadaTest {

    @Test
    void construtor_camposValidos_constroiOk() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("AREA", "100");
        Map<String, String> dinamicas = new LinkedHashMap<>();
        dinamicas.put("USO", "Residencial");

        LinhaMapeada l = new LinhaMapeada("12345", fixas, dinamicas);

        assertEquals("12345", l.codigoImovel());
        assertEquals(Map.of("AREA", "100"), l.celulasFixas());
        assertEquals(Map.of("USO", "Residencial"), l.celulasDinamicas());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "  ", "\t", "\n" })
    void construtor_codigoImovelNullOuBlank_lancaIae(String codigo) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new LinhaMapeada(codigo, Map.of(), Map.of()));
        assertEquals("Código do imóvel não pode ser nulo ou em branco.", ex.getMessage());
    }

    @Test
    void construtor_celulasFixasNull_viraMapVazio() {
        LinhaMapeada l = new LinhaMapeada("123", null, Map.of());
        assertEquals(Map.of(), l.celulasFixas());
    }

    @Test
    void construtor_celulasDinamicasNull_viraMapVazio() {
        LinhaMapeada l = new LinhaMapeada("123", Map.of(), null);
        assertEquals(Map.of(), l.celulasDinamicas());
    }

    @Test
    void construtor_ambosMapsNull_viramVazios() {
        LinhaMapeada l = new LinhaMapeada("123", null, null);
        assertSame(Map.of(), l.celulasFixas());
        assertSame(Map.of(), l.celulasDinamicas());
    }

    @Test
    void celulasFixas_ordemPreservada() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("Z", "1");
        fixas.put("A", "2");
        fixas.put("M", "3");

        LinhaMapeada l = new LinhaMapeada("123", fixas, null);

        assertEquals(List.of("Z", "A", "M"), new ArrayList<>(l.celulasFixas().keySet()));
    }

    @Test
    void celulasFixas_imutavel_naoAceitaPut() {
        LinhaMapeada l = new LinhaMapeada("123", new LinkedHashMap<>(Map.of("A", "1")), null);
        assertThrows(UnsupportedOperationException.class, () -> l.celulasFixas().put("B", "2"));
    }

    @Test
    void celulasDinamicas_imutavel_naoAceitaPut() {
        LinhaMapeada l = new LinhaMapeada("123", null, new LinkedHashMap<>(Map.of("X", "v")));
        assertThrows(UnsupportedOperationException.class, () -> l.celulasDinamicas().put("Y", "v2"));
    }

    @Test
    void copia_defensiva_isolaMutacaoExterna() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("A", "1");
        LinhaMapeada l = new LinhaMapeada("123", fixas, null);

        // Mutar o map original não afeta a cópia interna.
        fixas.put("B", "2");
        assertEquals(Map.of("A", "1"), l.celulasFixas());
    }
}
