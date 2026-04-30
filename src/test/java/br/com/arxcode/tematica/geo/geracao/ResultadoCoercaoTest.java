package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultadoCoercaoTest {

    @Test
    void ok_criaResultadoSucesso() {
        ResultadoCoercao r = ResultadoCoercao.ok("'texto'");
        assertTrue(r.ok());
        assertEquals("'texto'", r.literalSql());
        assertNull(r.erro());
    }

    @Test
    void falha_criaResultadoFalha() {
        ResultadoCoercao r = ResultadoCoercao.falha("erro PT");
        assertFalse(r.ok());
        assertNull(r.literalSql());
        assertEquals("erro PT", r.erro());
    }

    @Test
    void construtor_okSemLiteral_lancaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ResultadoCoercao(null, true, null));
        assertEquals("literalSql não pode ser nulo quando ok=true", ex.getMessage());
    }

    @Test
    void construtor_falhaSemErro_lancaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ResultadoCoercao(null, false, null));
        assertEquals("erro não pode ser nulo quando ok=false", ex.getMessage());
    }

    @Test
    void okFabrica_comLiteralNulo_lancaIllegalArgument() {
        // Reforça: fábrica delega ao construtor canônico, que valida.
        assertThrows(IllegalArgumentException.class, () -> ResultadoCoercao.ok(null));
    }

    @Test
    void falhaFabrica_comErroNulo_lancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> ResultadoCoercao.falha(null));
    }
}
