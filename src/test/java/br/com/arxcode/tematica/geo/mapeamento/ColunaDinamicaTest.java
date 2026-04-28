package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.arxcode.tematica.geo.dominio.Tipo;

/**
 * Testes do record {@link ColunaDinamica} — validação de status null
 * e construção de variantes MAPEADO/PENDENTE.
 *
 * <p>Cobre AC3.
 */
class ColunaDinamicaTest {

    @Test
    void construtor_statusNulo_lanca() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ColunaDinamica(null, 142, Tipo.DECIMAL, null, null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("status"),
                "mensagem deve identificar o campo: " + ex.getMessage());
    }

    @Test
    void construtor_variantePendente_aceitaCamposMapeadoNulos() {
        ColunaDinamica pendente = new ColunaDinamica(
                StatusMapeamento.PENDENTE, null, null, null,
                "Nenhum campo encontrado com descricao='X'", List.of(140, 171));
        assertEquals(StatusMapeamento.PENDENTE, pendente.status());
        assertNull(pendente.idcampo());
        assertNull(pendente.tipo());
        assertNull(pendente.alternativas());
        assertEquals("Nenhum campo encontrado com descricao='X'", pendente.motivo());
        assertEquals(List.of(140, 171), pendente.sugestoes());
    }

    @Test
    void construtor_varianteMapeado_aceitaCamposPendenteNulos() {
        ColunaDinamica mapeado = new ColunaDinamica(
                StatusMapeamento.MAPEADO, 142, Tipo.MULTIPLA_ESCOLHA,
                Map.of("Alvenaria", 501, "Madeira", 502), null, null);
        assertEquals(StatusMapeamento.MAPEADO, mapeado.status());
        assertEquals(142, mapeado.idcampo());
        assertEquals(Tipo.MULTIPLA_ESCOLHA, mapeado.tipo());
        assertNotNull(mapeado.alternativas());
        assertEquals(501, mapeado.alternativas().get("Alvenaria"));
        assertNull(mapeado.motivo());
        assertNull(mapeado.sugestoes());
    }

    @Test
    void igualdadeEstrutural_records() {
        ColunaDinamica a = new ColunaDinamica(
                StatusMapeamento.MAPEADO, 10, Tipo.TEXTO, Map.of(), null, null);
        ColunaDinamica b = new ColunaDinamica(
                StatusMapeamento.MAPEADO, 10, Tipo.TEXTO, Map.of(), null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
