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

    // ---------- Story 4.2: colunaReferencia() (FK para tabelas de respostas) ----------

    @Test
    void territorial_colunaReferencia_eh_tribcadastrogeral_idkey() {
        assertEquals("tribcadastrogeral_idkey", Fluxo.TERRITORIAL.colunaReferencia());
    }

    @Test
    void predial_colunaReferencia_eh_idkey() {
        assertEquals("idkey", Fluxo.PREDIAL.colunaReferencia());
    }

    // ---------- Story 4.3: sequenceRespostas() (AC16) ----------

    @Test
    void territorial_sequenceRespostas_eh_s_respostaterreno_id() {
        assertEquals("s_respostaterreno_id", Fluxo.TERRITORIAL.sequenceRespostas());
    }

    @Test
    void predial_sequenceRespostas_eh_s_respostasegmento_id() {
        assertEquals("s_respostasegmento_id", Fluxo.PREDIAL.sequenceRespostas());
    }
}
