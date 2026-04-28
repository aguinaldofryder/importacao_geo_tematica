package br.com.arxcode.tematica.geo.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CampoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void igualdade_estrutural() {
        Campo a = new Campo(1L, "Área", Tipo.DECIMAL, true, 10L);
        Campo b = new Campo(1L, "Área", Tipo.DECIMAL, true, 10L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void alterar_componente_quebra_igualdade() {
        Campo a = new Campo(1L, "Área", Tipo.DECIMAL, true, 10L);
        assertNotEquals(a, new Campo(2L, "Área", Tipo.DECIMAL, true, 10L));
        assertNotEquals(a, new Campo(1L, "Outro", Tipo.DECIMAL, true, 10L));
        assertNotEquals(a, new Campo(1L, "Área", Tipo.TEXTO, true, 10L));
        assertNotEquals(a, new Campo(1L, "Área", Tipo.DECIMAL, false, 10L));
        assertNotEquals(a, new Campo(1L, "Área", Tipo.DECIMAL, true, 99L));
    }

    @Test
    void descricao_recebe_trim() {
        Campo c = new Campo(1L, "  Área  ", Tipo.DECIMAL, true, 10L);
        assertEquals("Área", c.descricao());
    }

    @Test
    void descricao_nula_lanca_excecao_em_pt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Campo(1L, null, Tipo.DECIMAL, true, 10L));
        assertEquals("descricao não pode ser nula em Campo", ex.getMessage());
    }

    @Test
    void tipo_nulo_lanca_excecao_em_pt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Campo(1L, "Área", null, true, 10L));
        assertEquals("tipo não pode ser nulo em Campo", ex.getMessage());
    }

    @Test
    void json_round_trip() throws Exception {
        Campo original = new Campo(42L, "Testada", Tipo.MULTIPLA_ESCOLHA, true, 7L);
        String json = mapper.writeValueAsString(original);
        Campo decoded = mapper.readValue(json, Campo.class);
        assertEquals(original, decoded);
    }
}
