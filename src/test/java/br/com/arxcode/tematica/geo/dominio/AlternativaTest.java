package br.com.arxcode.tematica.geo.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AlternativaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void descricao_recebe_trim() {
        Alternativa a = new Alternativa(1L, "  Sim  ", 99L);
        assertEquals("Sim", a.descricao());
    }

    @Test
    void descricao_nula_lanca_excecao_em_pt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Alternativa(1L, null, 99L));
        assertEquals("descricao não pode ser nula em Alternativa", ex.getMessage());
    }

    @Test
    void igualdade_estrutural() {
        Alternativa a = new Alternativa(1L, "Sim", 99L);
        Alternativa b = new Alternativa(1L, "Sim", 99L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void json_round_trip() throws Exception {
        Alternativa original = new Alternativa(5L, "Não", 99L);
        String json = mapper.writeValueAsString(original);
        Alternativa decoded = mapper.readValue(json, Alternativa.class);
        assertEquals(original, decoded);
    }
}
