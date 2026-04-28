package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.arxcode.tematica.geo.dominio.Tipo;

/**
 * Verifica a política de serialização {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_NULL}
 * em {@link ColunaDinamica}: campos {@code null} são omitidos do JSON gerado.
 *
 * <p>Cobre AC4 e Arquitetura §5 (variantes MAPEADO sem motivo/sugestoes;
 * PENDENTE sem idcampo/tipo/alternativas).
 */
class ColunaDinamicaJsonTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void mapeado_nãoEmiteCamposPendente() throws Exception {
        ColunaDinamica c = new ColunaDinamica(
                StatusMapeamento.MAPEADO, 142, Tipo.MULTIPLA_ESCOLHA,
                Map.of("Alvenaria", 501), null, null);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(c));

        assertTrue(json.has("status"));
        assertEquals("MAPEADO", json.get("status").asText());
        assertTrue(json.has("idcampo"));
        assertTrue(json.has("tipo"));
        assertTrue(json.has("alternativas"));
        assertFalse(json.has("motivo"), "motivo não deve aparecer quando null");
        assertFalse(json.has("sugestoes"), "sugestoes não deve aparecer quando null");
    }

    @Test
    void pendente_nãoEmiteCamposMapeado() throws Exception {
        ColunaDinamica c = new ColunaDinamica(
                StatusMapeamento.PENDENTE, null, null, null,
                "Nenhum campo encontrado com descricao='X'", List.of(140, 171));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(c));

        assertTrue(json.has("status"));
        assertEquals("PENDENTE", json.get("status").asText());
        assertTrue(json.has("motivo"));
        assertTrue(json.has("sugestoes"));
        assertFalse(json.has("idcampo"), "idcampo não deve aparecer quando null");
        assertFalse(json.has("tipo"), "tipo não deve aparecer quando null");
        assertFalse(json.has("alternativas"), "alternativas não deve aparecer quando null");
    }

    @Test
    void desserializacao_aceitaCamposNullExplicitos() throws Exception {
        // Política NON_NULL afeta apenas serialização — JSON com nulls explícitos
        // continua aceito (Jackson default).
        String json = "{ \"status\": \"PENDENTE\", \"motivo\": null, \"sugestoes\": null }";

        ColunaDinamica c = mapper.readValue(json, ColunaDinamica.class);

        assertEquals(StatusMapeamento.PENDENTE, c.status());
    }
}
