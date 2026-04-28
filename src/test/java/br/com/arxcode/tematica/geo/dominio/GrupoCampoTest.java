package br.com.arxcode.tematica.geo.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GrupoCampoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void funcionalidade_nula_lanca_excecao_em_pt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new GrupoCampo(1L, null));
        assertEquals("funcionalidade não pode ser nula em GrupoCampo", ex.getMessage());
    }

    @Test
    void funcionalidade_nao_recebe_trim() {
        // Decisão de design: o valor deve bater EXATAMENTE com Fluxo.funcionalidade().
        // Espaço sobrando vira ruído visível no classificador (Story 2.4), não silencioso.
        GrupoCampo comEspaco = new GrupoCampo(1L, "TERRENO ");
        GrupoCampo limpo = new GrupoCampo(1L, "TERRENO");
        assertNotEquals(comEspaco, limpo);
        assertEquals("TERRENO ", comEspaco.funcionalidade());
    }

    @Test
    void igualdade_estrutural() {
        GrupoCampo a = new GrupoCampo(7L, "SEGMENTO");
        GrupoCampo b = new GrupoCampo(7L, "SEGMENTO");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void json_round_trip() throws Exception {
        GrupoCampo original = new GrupoCampo(7L, "TERRENO");
        String json = mapper.writeValueAsString(original);
        GrupoCampo decoded = mapper.readValue(json, GrupoCampo.class);
        assertEquals(original, decoded);
    }
}
