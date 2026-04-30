package br.com.arxcode.tematica.geo.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class FluxoTest {

    @Test
    void territorial_funcionalidade_eh_TERRENO() {
        assertEquals("TERRENO", Fluxo.TERRITORIAL.funcionalidade());
    }

    @Test
    void predial_funcionalidade_eh_SEGMENTO() {
        // Assimetria intencional: nome do enum (PREDIAL) != funcionalidade (SEGMENTO).
        assertEquals("SEGMENTO", Fluxo.PREDIAL.funcionalidade());
    }

    @Test
    void territorial_tabelas_corretas() {
        assertEquals("tribcadastroimobiliario", Fluxo.TERRITORIAL.tabelaPrincipal());
        assertEquals("respostaterreno", Fluxo.TERRITORIAL.tabelaRespostas());
    }

    @Test
    void predial_tabelas_corretas() {
        assertEquals("tribimobiliariosegmento", Fluxo.PREDIAL.tabelaPrincipal());
        assertEquals("respostasegmento", Fluxo.PREDIAL.tabelaRespostas());
    }

    @Test
    void fluxos_distintos_tem_funcionalidades_distintas() {
        assertNotEquals(Fluxo.TERRITORIAL.funcionalidade(), Fluxo.PREDIAL.funcionalidade());
    }

    // ---------- Story 4.2: colunaChave() ----------

    @Test
    void territorial_colunaChave_eh_tribcadastrogeral_idkey() {
        assertEquals("tribcadastrogeral_idkey", Fluxo.TERRITORIAL.colunaChave());
    }

    @Test
    void predial_colunaChave_eh_idkey() {
        assertEquals("idkey", Fluxo.PREDIAL.colunaChave());
    }
}
