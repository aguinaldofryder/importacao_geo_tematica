package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

/**
 * Cenários (a)–(o) de Story 3.3 AC11, mais validações complementares.
 */
class MapeamentoValidadorTest {

    private final MapeamentoValidador validador = new MapeamentoValidador();

    // ---------- Helpers ----------

    private static ColunaDinamica mapeado(int idcampo, Tipo tipo) {
        return new ColunaDinamica(StatusMapeamento.MAPEADO, idcampo, tipo, null, null, null);
    }

    private static ColunaDinamica mapeadoComAlternativas(int idcampo, Map<String, Integer> alts) {
        return new ColunaDinamica(StatusMapeamento.MAPEADO, idcampo, Tipo.MULTIPLA_ESCOLHA, alts, null, null);
    }

    private static ColunaDinamica pendente(String motivo) {
        return new ColunaDinamica(StatusMapeamento.PENDENTE, null, null, null, motivo, null);
    }

    private static ColunaDinamica pendenteNullMotivo() {
        return new ColunaDinamica(StatusMapeamento.PENDENTE, null, null, null, null, null);
    }

    private static Mapeamento mapeamento(Map<String, String> fixas, Map<String, ColunaDinamica> dinamicas) {
        return new Mapeamento(Fluxo.TERRITORIAL, "planilha.xlsx", "MATRICULA", null, fixas, dinamicas);
    }

    // ---------- (a) Mapeamento totalmente válido — resultado valido=true ----------

    @Test
    void cenario_a_mapeamentoValido_retornaValido() {
        Map<String, ColunaDinamica> dinamicas = Map.of(
                "OBSERVACAO", mapeado(1, Tipo.TEXTO),
                "DATA_VISTORIA", mapeado(2, Tipo.DATA),
                "TIPO_MURO", mapeadoComAlternativas(3, Map.of("ALVENARIA", 10, "MADEIRA", 11)));

        ResultadoValidacao resultado = validador.validar(
                mapeamento(Map.of("AREA_TERRENO", "area_terreno"), dinamicas));

        assertTrue(resultado.valido());
        assertTrue(resultado.pendencias().isEmpty());
    }

    // ---------- (b) Mapeamento com coluna fixa sem destino ----------

    @Test
    void cenario_b_colunaFixaSemDestino_geraPendencia() {
        Map<String, String> fixas = new HashMap<>();
        fixas.put("AREA_TERRENO", null);

        ResultadoValidacao resultado = validador.validar(mapeamento(fixas, Map.of()));

        assertFalse(resultado.valido());
        assertEquals(1, resultado.pendencias().size());
        assertTrue(resultado.pendencias().get(0).contains("AREA_TERRENO"));
        assertTrue(resultado.pendencias().get(0).contains("coluna fixa sem coluna-destino"));
    }

    // ---------- (c) Coluna fixa com destino em branco ----------

    @Test
    void cenario_c_colunaFixaDestinoBlanco_geraPendencia() {
        Map<String, String> fixas = Map.of("TESTADA", "   ");

        ResultadoValidacao resultado = validador.validar(mapeamento(fixas, Map.of()));

        assertFalse(resultado.valido());
        assertTrue(resultado.pendencias().get(0).contains("TESTADA"));
    }

    // ---------- (d) Coluna dinâmica PENDENTE com motivo ----------

    @Test
    void cenario_d_colunaPendenteComMotivo_geraPendenciaComMotivo() {
        Map<String, ColunaDinamica> dinamicas = Map.of(
                "COLUNA_X", pendente("header não encontrado no catálogo"));

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertEquals(1, resultado.pendencias().size());
        assertTrue(resultado.pendencias().get(0).contains("COLUNA_X"));
        assertTrue(resultado.pendencias().get(0).contains("header não encontrado no catálogo"));
    }

    // ---------- (e) Coluna MAPEADO sem idcampo ----------

    @Test
    void cenario_e_mapeadoSemIdcampo_geraPendencia() {
        ColunaDinamica semId = new ColunaDinamica(StatusMapeamento.MAPEADO, null, Tipo.TEXTO, null, null, null);
        Map<String, ColunaDinamica> dinamicas = Map.of("COLUNA_Y", semId);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertTrue(resultado.pendencias().get(0).contains("COLUNA_Y"));
        assertTrue(resultado.pendencias().get(0).contains("idcampo ausente"));
    }

    // ---------- (f) MULTIPLA_ESCOLHA sem alternativas (null) ----------

    @Test
    void cenario_f_multiplaEscolhaSemAlternativasNull_geraPendencia() {
        ColunaDinamica semAlts = new ColunaDinamica(
                StatusMapeamento.MAPEADO, 5, Tipo.MULTIPLA_ESCOLHA, null, null, null);
        Map<String, ColunaDinamica> dinamicas = Map.of("TIPO_SOLO", semAlts);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertTrue(resultado.pendencias().get(0).contains("TIPO_SOLO"));
        assertTrue(resultado.pendencias().get(0).contains("sem mapa de alternativas definido"));
    }

    // ---------- (g) MULTIPLA_ESCOLHA com alternativas vazias ----------

    @Test
    void cenario_g_multiplaEscolhaAlternativasVazias_geraPendencia() {
        ColunaDinamica altsVazias = mapeadoComAlternativas(5, Map.of());
        Map<String, ColunaDinamica> dinamicas = Map.of("TIPO_SOLO", altsVazias);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertTrue(resultado.pendencias().get(0).contains("TIPO_SOLO"));
        assertTrue(resultado.pendencias().get(0).contains("mapa de alternativas vazio"));
    }

    // ---------- (h) MULTIPLA_ESCOLHA com alternativa sem idAlternativa ----------

    @Test
    void cenario_h_multiplaEscolhaAlternativaSemId_geraPendencia() {
        Map<String, Integer> alts = new HashMap<>();
        alts.put("ALVENARIA", 10);
        alts.put("MADEIRA", null);
        ColunaDinamica comAltNull = mapeadoComAlternativas(5, alts);
        Map<String, ColunaDinamica> dinamicas = Map.of("TIPO_MURO", comAltNull);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertTrue(resultado.pendencias().get(0).contains("TIPO_MURO"));
        assertTrue(resultado.pendencias().get(0).contains("MADEIRA"));
        assertTrue(resultado.pendencias().get(0).contains("sem idAlternativa mapeado"));
    }

    // ---------- (i) Múltiplas pendências coletadas (fail-all) ----------

    @Test
    void cenario_i_multiplas_pendencias_fallaAll() {
        Map<String, String> fixas = new HashMap<>();
        fixas.put("AREA_TERRENO", null);

        Map<String, ColunaDinamica> dinamicas = Map.of(
                "COLUNA_PEND", pendente("sem correspondência"),
                "TIPO_MURO", mapeadoComAlternativas(3, Map.of()));

        ResultadoValidacao resultado = validador.validar(mapeamento(fixas, dinamicas));

        assertFalse(resultado.valido());
        assertEquals(3, resultado.pendencias().size());
    }

    // ---------- (j) Mapeamento com colunasFixas e colunasDinamicas vazias — válido ----------

    @Test
    void cenario_j_mapsVazios_retornaValido() {
        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), Map.of()));

        assertTrue(resultado.valido());
        assertTrue(resultado.pendencias().isEmpty());
    }

    // ---------- (k) ResultadoValidacao invariante valido=true + pendencias vazia ----------

    @Test
    void cenario_k_resultadoValidacaoValido_invarianteOk() {
        ResultadoValidacao r = new ResultadoValidacao(true, List.of());
        assertTrue(r.valido());
        assertTrue(r.pendencias().isEmpty());
    }

    // ---------- (l) ResultadoValidacao invariante valido=false + pendencias não-vazia ----------

    @Test
    void cenario_l_resultadoValidacaoInvalido_invarianteOk() {
        ResultadoValidacao r = new ResultadoValidacao(false, List.of("pendência 1"));
        assertFalse(r.valido());
        assertEquals(1, r.pendencias().size());
    }

    // ---------- (m) ResultadoValidacao invariante violado: valido=true com pendencias ----------

    @Test
    void cenario_m_resultadoValidacaoInvarianteVioladoValidoTrue_lancaException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoValidacao(true, List.of("erro")));
    }

    // ---------- (n) ResultadoValidacao invariante violado: valido=false sem pendencias ----------

    @Test
    void cenario_n_resultadoValidacaoInvarianteVioladoValidoFalse_lancaException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoValidacao(false, List.of()));
    }

    // ---------- (o) PENDENTE com motivo null — fallback "status PENDENTE sem motivo" ----------

    @Test
    void cenario_o_pendenteSemMotivo_usaFallback() {
        Map<String, ColunaDinamica> dinamicas = Map.of("SEM_MOTIVO", pendenteNullMotivo());

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertEquals(1, resultado.pendencias().size());
        assertTrue(resultado.pendencias().get(0).contains("SEM_MOTIVO"));
        assertTrue(resultado.pendencias().get(0).contains("status PENDENTE sem motivo"));
    }

    // ---------- Guard: mapeamento null lança ImportacaoException ----------

    @Test
    void mapeamentoNulo_lancaImportacaoException() {
        assertThrows(ImportacaoException.class, () -> validador.validar(null));
    }

    // ---------- ResultadoValidacao: pendencias é cópia imutável ----------

    @Test
    void resultadoValidacao_pendenciasImutaveis() {
        ResultadoValidacao r = new ResultadoValidacao(false, List.of("p1"));
        assertThrows(UnsupportedOperationException.class, () -> r.pendencias().add("p2"));
    }

    // ---------- ResultadoValidacao: pendencias null lança IllegalArgumentException ----------

    @Test
    void resultadoValidacao_pendenciasNulas_lancaException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoValidacao(true, null));
    }

    // ---------- AC13 Story 3.6 — distinção de subtipo de PENDENTE ----------

    /**
     * AC13(a): coluna PENDENTE com idcampo != null + tipo MULTIPLA_ESCOLHA +
     * ao menos uma alternativa null → mensagem deve mencionar "alternativas parcialmente
     * resolvidas" e o idcampo (formato diferenciado AC9).
     */
    @Test
    void cenario_ac13a_pendenteSemAlternativas_mensagemDistingueSubtipo() {
        Map<String, Integer> alts = new HashMap<>();
        alts.put("Alvenaria", 501);
        alts.put("Tijolo", null);
        ColunaDinamica pendenteSemAlts = new ColunaDinamica(
            StatusMapeamento.PENDENTE, 10, Tipo.MULTIPLA_ESCOLHA, alts,
            "1 de 2 alternativas sem mapeamento", null);
        Map<String, ColunaDinamica> dinamicas = Map.of("TIPO_MURO", pendenteSemAlts);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertEquals(1, resultado.pendencias().size());
        String msg = resultado.pendencias().get(0);
        assertTrue(msg.contains("TIPO_MURO"), "Mensagem deve conter o header. msg=" + msg);
        assertTrue(msg.contains("alternativas parcialmente resolvidas"),
            "Mensagem deve conter 'alternativas parcialmente resolvidas'. msg=" + msg);
        assertTrue(msg.contains("idcampo=10"),
            "Mensagem deve conter o idcampo. msg=" + msg);
    }

    /**
     * AC13(b): coluna PENDENTE com idcampo == null → mensagem continua com
     * o formato existente (sem distinção de subtipo, sem regressão).
     */
    @Test
    void cenario_ac13b_pendenteSemIdcampo_mensagemFormatoOriginal() {
        ColunaDinamica pendenteSemId = new ColunaDinamica(
            StatusMapeamento.PENDENTE, null, null, null,
            "Nenhum campo encontrado com descricao='COLUNA_X'", null);
        Map<String, ColunaDinamica> dinamicas = Map.of("COLUNA_X", pendenteSemId);

        ResultadoValidacao resultado = validador.validar(mapeamento(Map.of(), dinamicas));

        assertFalse(resultado.valido());
        assertEquals(1, resultado.pendencias().size());
        String msg = resultado.pendencias().get(0);
        assertTrue(msg.contains("COLUNA_X"), "Mensagem deve conter o header. msg=" + msg);
        assertTrue(msg.contains("Nenhum campo encontrado"),
            "Mensagem deve conter o motivo original. msg=" + msg);
        assertFalse(msg.contains("alternativas parcialmente resolvidas"),
            "Mensagem não deve mencionar alternativas para item sem idcampo. msg=" + msg);
    }
}
