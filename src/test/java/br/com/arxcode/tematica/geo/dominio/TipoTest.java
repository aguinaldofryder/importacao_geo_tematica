package br.com.arxcode.tematica.geo.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TipoTest {

    @Test
    void possui_quatro_constantes() {
        assertEquals(4, Tipo.values().length);
    }

    @Test
    void valueOf_resolve_todas_as_constantes() {
        assertNotNull(Tipo.valueOf("TEXTO"));
        assertNotNull(Tipo.valueOf("DECIMAL"));
        assertNotNull(Tipo.valueOf("DATA"));
        assertNotNull(Tipo.valueOf("MULTIPLA_ESCOLHA"));
    }
}
