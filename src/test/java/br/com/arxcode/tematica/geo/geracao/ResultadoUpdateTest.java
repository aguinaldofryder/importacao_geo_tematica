package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResultadoUpdateTest {

    @Test
    void sucesso_sqlValido_constroiOk() {
        ResultadoUpdate r = ResultadoUpdate.sucesso("UPDATE t SET a = 1 WHERE id = '1';");
        assertTrue(r.ok());
        assertEquals("UPDATE t SET a = 1 WHERE id = '1';", r.sql());
        assertEquals(List.of(), r.erros());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "\t", "\n" })
    void sucesso_sqlNulOuBlank_lancaIae(String sql) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ResultadoUpdate.sucesso(sql));
        assertEquals("SQL de sucesso não pode ser nulo ou em branco.", ex.getMessage());
    }

    @Test
    void falha_listaValida_constroiOk() {
        ResultadoUpdate r = ResultadoUpdate.falha(List.of("erro 1", "erro 2"));
        assertFalse(r.ok());
        assertNull(r.sql());
        assertEquals(List.of("erro 1", "erro 2"), r.erros());
    }

    @Test
    void falha_listaNula_lancaIae() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ResultadoUpdate.falha(null));
        assertEquals("Lista de erros não pode ser nula ou vazia em ResultadoUpdate.falha.", ex.getMessage());
    }

    @Test
    void falha_listaVazia_lancaIae() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ResultadoUpdate.falha(List.of()));
        assertEquals("Lista de erros não pode ser nula ou vazia em ResultadoUpdate.falha.", ex.getMessage());
    }

    @Test
    void erros_listaImutavel_naoAceitaModificacao() {
        List<String> mutavel = new ArrayList<>(List.of("erro 1"));
        ResultadoUpdate r = ResultadoUpdate.falha(mutavel);
        // Mutação na lista original não afeta a lista interna (cópia defensiva).
        mutavel.add("intruso");
        assertEquals(List.of("erro 1"), r.erros());
        // Lista interna é imutável.
        assertThrows(UnsupportedOperationException.class, () -> r.erros().add("outro"));
    }

    @Test
    void construtorCanonico_errosNulos_normalizaParaListaVazia() {
        // Caminho do construtor canônico (não exposto diretamente ao caller, mas defendido).
        ResultadoUpdate r = new ResultadoUpdate("UPDATE t SET a = 1;", null);
        assertEquals(List.of(), r.erros());
        assertTrue(r.ok());
    }

    @Test
    void ok_retornaTrueQuandoSqlPresente() {
        assertTrue(ResultadoUpdate.sucesso("UPDATE t SET a = 1;").ok());
        assertFalse(ResultadoUpdate.falha(List.of("e")).ok());
    }
}
