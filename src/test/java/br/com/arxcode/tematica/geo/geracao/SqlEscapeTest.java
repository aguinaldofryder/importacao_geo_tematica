package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SqlEscapeTest {

    @Test
    void aspas_nullEntrada_lancaNpe() {
        // Defesa de programação: caller deve normalizar null antes de escapar.
        assertThrows(NullPointerException.class, () -> SqlEscape.aspas(null));
    }

    @Test
    void aspas_stringVazia_retornaVazia() {
        assertEquals("", SqlEscape.aspas(""));
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "|", quoteCharacter = '"', value = {
            "abc|abc",
            "  texto  |  texto  ",
            "a\\b|a\\b",
    })
    void aspas_semAspas_naoAltera(String entrada, String esperado) {
        assertEquals(esperado, SqlEscape.aspas(entrada));
    }

    @Test
    void aspas_newline_preservada() {
        assertEquals("linha1\nlinha2", SqlEscape.aspas("linha1\nlinha2"));
    }

    @Test
    void aspas_umaAspa_duplicada() {
        assertEquals("O''Brien", SqlEscape.aspas("O'Brien"));
    }

    @Test
    void aspas_multiplasAspas_todasDuplicadas() {
        assertEquals("a''''b''c", SqlEscape.aspas("a''b'c"));
    }

    @Test
    void aspas_tentativaInjection_neutralizada() {
        // Aspa de fechamento vira '', injection vira string inerte ao envolver em aspas.
        assertEquals("''; DROP TABLE x; --", SqlEscape.aspas("'; DROP TABLE x; --"));
    }
}
