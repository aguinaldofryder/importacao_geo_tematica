package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import br.com.arxcode.tematica.geo.dominio.Alternativa;
import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

/**
 * Cenários (a)–(o) de Story 3.2 AC14, mais validações complementares.
 */
class AutoMapeadorTest {

    private static final long ID_GRUPO = 99L;

    // ---------- Helpers ----------

    private static Campo campo(long id, String descricao, Tipo tipo) {
        return new Campo(id, descricao, tipo, true, ID_GRUPO);
    }

    private static Campo campoInativo(long id, String descricao, Tipo tipo) {
        return new Campo(id, descricao, tipo, false, ID_GRUPO);
    }

    private static Alternativa alt(long id, String descricao, long idCampo) {
        return new Alternativa(id, descricao, idCampo);
    }

    private static Classificacao classificacao(String codigo, Map<String, String> fixas, List<String> dinamicas) {
        return new Classificacao(codigo, fixas, dinamicas);
    }

    private static Function<Long, List<Alternativa>> alternativasMapToFn(Map<Long, List<Alternativa>> m) {
        return idCampo -> m.getOrDefault(idCampo, List.of());
    }

    private static Function<String, Set<String>> distinctMapToFn(Map<String, Set<String>> m) {
        return header -> m.getOrDefault(header, Set.of());
    }

    private static Set<String> linkedSet(String... values) {
        Set<String> s = new LinkedHashSet<>();
        for (String v : values) s.add(v);
        return s;
    }

    // ---------- (a) Happy path Territorial ----------

    @Test
    void cenario_a_happyPathTerritorial_todasMapeadas() {
        Classificacao cls = classificacao(
                "MATRICULA",
                Map.of("AREA_TERRENO", "AREA_TERRENO", "TESTADA", "TESTADA"),
                List.of("OBSERVACAO", "DATA_VISTORIA", "TIPO_MURO"));

        List<Campo> campos = List.of(
                campo(1, "OBSERVACAO", Tipo.TEXTO),
                campo(2, "DATA_VISTORIA", Tipo.DATA),
                campo(3, "TIPO_MURO", Tipo.MULTIPLA_ESCOLHA));

        Map<Long, List<Alternativa>> alternativas = Map.of(
                3L, List.of(alt(10, "ALVENARIA", 3), alt(11, "MADEIRA", 3)));

        Map<String, Set<String>> distinct = Map.of(
                "TIPO_MURO", linkedSet("ALVENARIA", "MADEIRA"));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "TABELA_TERRITORIAL_V001.xlsx", Fluxo.TERRITORIAL,
                campos, alternativasMapToFn(alternativas), distinctMapToFn(distinct));

        Mapeamento m = new AutoMapeador().mapear(entrada);

        assertEquals(Fluxo.TERRITORIAL, m.fluxo());
        assertEquals("TABELA_TERRITORIAL_V001.xlsx", m.planilha());
        assertEquals("MATRICULA", m.colunaCodigoImovel());
        assertEquals(StatusMapeamento.MAPEADO, m.colunasDinamicas().get("OBSERVACAO").status());
        assertEquals(StatusMapeamento.MAPEADO, m.colunasDinamicas().get("DATA_VISTORIA").status());
        ColunaDinamica tipoMuro = m.colunasDinamicas().get("TIPO_MURO");
        assertEquals(StatusMapeamento.MAPEADO, tipoMuro.status());
        assertEquals(2, tipoMuro.alternativas().size());
        assertEquals(10, tipoMuro.alternativas().get("ALVENARIA"));
        assertEquals(11, tipoMuro.alternativas().get("MADEIRA"));
    }

    // ---------- (b) Happy path Predial ----------

    @Test
    void cenario_b_happyPathPredial_todasMapeadas() {
        Classificacao cls = classificacao(
                "IDKEY",
                Map.of("AREA_CONSTRUIDA", "AREA_CONSTRUIDA"),
                List.of("PADRAO_ACABAMENTO"));

        List<Campo> campos = List.of(
                campo(50, "PADRAO_ACABAMENTO", Tipo.MULTIPLA_ESCOLHA));

        Map<Long, List<Alternativa>> alternativas = Map.of(
                50L, List.of(alt(80, "BAIXO", 50), alt(81, "MEDIO", 50), alt(82, "ALTO", 50)));

        Map<String, Set<String>> distinct = Map.of(
                "PADRAO_ACABAMENTO", linkedSet("BAIXO", "MEDIO", "ALTO"));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "TABELA_PREDIAL_V001.xlsx", Fluxo.PREDIAL,
                campos, alternativasMapToFn(alternativas), distinctMapToFn(distinct));

        Mapeamento m = new AutoMapeador().mapear(entrada);

        assertEquals(Fluxo.PREDIAL, m.fluxo());
        assertEquals("IDKEY", m.colunaCodigoImovel());
        assertEquals(StatusMapeamento.MAPEADO, m.colunasDinamicas().get("PADRAO_ACABAMENTO").status());
    }

    // ---------- (c) colunasFixas repassadas literalmente ----------

    @Test
    void cenario_c_colunasFixas_repasseLiteral() {
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("AREA_TERRENO", "AREA_TERRENO");
        fixas.put("TESTADA", "TESTADA");

        Classificacao cls = classificacao("MATRICULA", fixas, List.of());

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(),
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);

        assertEquals("AREA_TERRENO", m.colunasFixas().get("AREA_TERRENO"));
        assertEquals("TESTADA", m.colunasFixas().get("TESTADA"));
        assertEquals(2, m.colunasFixas().size());
    }

    // ---------- (d) Match único TEXTO ----------

    @Test
    void cenario_d_matchUnicoTexto_mapeado() {
        Classificacao cls = classificacao("M", Map.of(), List.of("OBSERVACAO"));
        List<Campo> campos = List.of(campo(7, "OBSERVACAO", Tipo.TEXTO));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);
        ColunaDinamica cd = m.colunasDinamicas().get("OBSERVACAO");
        assertEquals(StatusMapeamento.MAPEADO, cd.status());
        assertEquals(7, cd.idcampo());
        assertEquals(Tipo.TEXTO, cd.tipo());
        assertNull(cd.motivo());
        assertNull(cd.sugestoes());
        assertNull(cd.alternativas());
    }

    // ---------- (e) Match múltiplo ----------

    @Test
    void cenario_e_matchMultiplo_pendenteComSugestoesOrdenadas() {
        Classificacao cls = classificacao("M", Map.of(), List.of("OBSERVACAO"));
        List<Campo> campos = List.of(
                campo(42, "OBSERVACAO", Tipo.TEXTO),
                campo(7, "OBSERVACAO", Tipo.TEXTO),
                campo(15, "OBSERVACAO", Tipo.TEXTO));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);
        ColunaDinamica cd = m.colunasDinamicas().get("OBSERVACAO");
        assertEquals(StatusMapeamento.PENDENTE, cd.status());
        assertNull(cd.idcampo());
        assertNull(cd.tipo());
        assertNull(cd.alternativas());
        assertTrue(cd.motivo().contains("Múltiplos campos"));
        assertTrue(cd.motivo().contains("OBSERVACAO"));
        assertTrue(cd.motivo().contains("7,15,42"), "motivo deve listar IDs ordenados: " + cd.motivo());
        assertIterableEquals(List.of(7, 15, 42), cd.sugestoes());
    }

    // ---------- (f) Nenhum match ----------

    @Test
    void cenario_f_nenhumMatch_pendenteFormatoCanonico() {
        Classificacao cls = classificacao("M", Map.of(), List.of("COLUNA_NOVA"));
        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(),
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);
        ColunaDinamica cd = m.colunasDinamicas().get("COLUNA_NOVA");
        assertEquals(StatusMapeamento.PENDENTE, cd.status());
        assertEquals("Nenhum campo encontrado com descricao='COLUNA_NOVA'", cd.motivo());
        assertNull(cd.sugestoes());
        assertNull(cd.idcampo());
        assertNull(cd.tipo());
    }

    // ---------- (g) MULTIPLA_ESCOLHA 100% casadas ----------

    @Test
    void cenario_g_multiplaEscolha_totalmenteCasada_mapeada() {
        Classificacao cls = classificacao("M", Map.of(), List.of("TIPO_MURO"));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.MULTIPLA_ESCOLHA));
        Map<Long, List<Alternativa>> alts = Map.of(
                3L, List.of(alt(10, "ALVENARIA", 3), alt(11, "MADEIRA", 3)));
        Map<String, Set<String>> dist = Map.of("TIPO_MURO", linkedSet("ALVENARIA", "MADEIRA"));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                alternativasMapToFn(alts), distinctMapToFn(dist));

        ColunaDinamica cd = new AutoMapeador().mapear(entrada).colunasDinamicas().get("TIPO_MURO");
        assertEquals(StatusMapeamento.MAPEADO, cd.status());
        assertEquals(2, cd.alternativas().size());
        assertEquals(10, cd.alternativas().get("ALVENARIA"));
        assertEquals(11, cd.alternativas().get("MADEIRA"));
        assertNull(cd.motivo());
    }

    // ---------- (h) MULTIPLA_ESCOLHA parcial ----------

    @Test
    void cenario_h_multiplaEscolha_parcial_pendenteMantemIdcampo() {
        Classificacao cls = classificacao("M", Map.of(), List.of("TIPO_MURO"));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.MULTIPLA_ESCOLHA));
        Map<Long, List<Alternativa>> alts = Map.of(
                3L, List.of(alt(10, "ALVENARIA", 3), alt(11, "MADEIRA", 3)));
        Map<String, Set<String>> dist = Map.of(
                "TIPO_MURO", linkedSet("ALVENARIA", "MADEIRA", "DESCONHECIDO"));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                alternativasMapToFn(alts), distinctMapToFn(dist));

        ColunaDinamica cd = new AutoMapeador().mapear(entrada).colunasDinamicas().get("TIPO_MURO");
        assertEquals(StatusMapeamento.PENDENTE, cd.status());
        assertEquals(3, cd.idcampo());
        assertEquals(Tipo.MULTIPLA_ESCOLHA, cd.tipo());
        assertEquals("1 de 3 alternativas sem mapeamento", cd.motivo());
        assertEquals(10, cd.alternativas().get("ALVENARIA"));
        assertEquals(11, cd.alternativas().get("MADEIRA"));
        assertNull(cd.alternativas().get("DESCONHECIDO"));
        assertTrue(cd.alternativas().containsKey("DESCONHECIDO"));
    }

    // ---------- (i) MULTIPLA_ESCOLHA 0% ----------

    @Test
    void cenario_i_multiplaEscolha_zeroCasadas_pendente() {
        Classificacao cls = classificacao("M", Map.of(), List.of("TIPO_MURO"));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.MULTIPLA_ESCOLHA));
        Map<Long, List<Alternativa>> alts = Map.of(3L, List.of(alt(10, "ALVENARIA", 3)));
        Map<String, Set<String>> dist = Map.of("TIPO_MURO", linkedSet("X", "Y", "Z"));

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                alternativasMapToFn(alts), distinctMapToFn(dist));

        ColunaDinamica cd = new AutoMapeador().mapear(entrada).colunasDinamicas().get("TIPO_MURO");
        assertEquals(StatusMapeamento.PENDENTE, cd.status());
        assertEquals("3 de 3 alternativas sem mapeamento", cd.motivo());
        assertEquals(3, cd.alternativas().size());
    }

    // ---------- (j) Normalização case+trim ----------

    @Test
    void cenario_j_normalizacaoCaseTrim_default_match() {
        Classificacao cls = classificacao("M", Map.of(), List.of(" tipo_muro "));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.TEXTO));
        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                idC -> List.of(), h -> Set.of());

        ColunaDinamica cd = new AutoMapeador().mapear(entrada).colunasDinamicas().get(" tipo_muro ");
        assertEquals(StatusMapeamento.MAPEADO, cd.status());
        assertEquals(3, cd.idcampo());
    }

    // ---------- (k) caseSensitive=true ----------

    @Test
    void cenario_k_caseSensitive_true_naoCasa() {
        Classificacao cls = classificacao("M", Map.of(), List.of("tipo_muro"));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.TEXTO));
        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                idC -> List.of(), h -> Set.of());

        ColunaDinamica cd = new AutoMapeador(true, true).mapear(entrada).colunasDinamicas().get("tipo_muro");
        assertEquals(StatusMapeamento.PENDENTE, cd.status());
    }

    // ---------- (l) Campo inativo ----------

    @Test
    void cenario_l_campoInativo_lancaImportacaoException() {
        Classificacao cls = classificacao("M", Map.of(), List.of("X"));
        List<Campo> campos = List.of(campoInativo(99, "X", Tipo.TEXTO));
        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                idC -> List.of(), h -> Set.of());

        ImportacaoException ex = assertThrows(ImportacaoException.class,
                () -> new AutoMapeador().mapear(entrada));
        assertTrue(ex.getMessage().contains("inativo"));
        assertTrue(ex.getMessage().contains("99"));
    }

    // ---------- (m) Dinâmicas vazias ----------

    @Test
    void cenario_m_dinamicasVazias_mapeamentoComFixasECodigo() {
        Classificacao cls = classificacao(
                "MATRICULA",
                Map.of("AREA_TERRENO", "AREA_TERRENO"),
                List.of());

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(),
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);
        assertTrue(m.colunasDinamicas().isEmpty());
        assertEquals("MATRICULA", m.colunaCodigoImovel());
        assertEquals(1, m.colunasFixas().size());
    }

    // ---------- (n) Determinismo de ordem ----------

    @Test
    void cenario_n_ordemPreservada() {
        Classificacao cls = classificacao("M", Map.of(),
                List.of("ZULU", "ALPHA", "MIKE"));
        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(),
                idC -> List.of(), h -> Set.of());

        Mapeamento m = new AutoMapeador().mapear(entrada);
        assertIterableEquals(List.of("ZULU", "ALPHA", "MIKE"),
                new ArrayList<>(m.colunasDinamicas().keySet()));
    }

    // ---------- (o) valoresDistintos ignora vazios/null ----------

    @Test
    void cenario_o_valoresVazios_ignorados() {
        Classificacao cls = classificacao("M", Map.of(), List.of("TIPO_MURO"));
        List<Campo> campos = List.of(campo(3, "TIPO_MURO", Tipo.MULTIPLA_ESCOLHA));
        Map<Long, List<Alternativa>> alts = Map.of(3L, List.of(alt(10, "ALVENARIA", 3)));

        Set<String> dist = new LinkedHashSet<>();
        dist.add("ALVENARIA");
        dist.add("");
        dist.add("   ");
        dist.add(null);

        EntradaAutoMapeamento entrada = new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, campos,
                alternativasMapToFn(alts), h -> dist);

        ColunaDinamica cd = new AutoMapeador().mapear(entrada).colunasDinamicas().get("TIPO_MURO");
        assertEquals(StatusMapeamento.MAPEADO, cd.status());
        assertEquals(1, cd.alternativas().size());
        assertEquals(10, cd.alternativas().get("ALVENARIA"));
    }

    // ---------- Validações extras ----------

    @Test
    void entradaNula_lancaImportacaoException() {
        ImportacaoException ex = assertThrows(ImportacaoException.class,
                () -> new AutoMapeador().mapear(null));
        assertTrue(ex.getMessage().contains("Entrada do AutoMapeador"));
    }

    @Test
    void entradaAutoMapeamento_validaTodosOsSlots() {
        Classificacao cls = classificacao("M", Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                null, "p.xlsx", Fluxo.TERRITORIAL, List.of(), idC -> List.of(), h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, null, Fluxo.TERRITORIAL, List.of(), idC -> List.of(), h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, "  ", Fluxo.TERRITORIAL, List.of(), idC -> List.of(), h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, "p.xlsx", null, List.of(), idC -> List.of(), h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, null, idC -> List.of(), h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(), null, h -> Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntradaAutoMapeamento(
                cls, "p.xlsx", Fluxo.TERRITORIAL, List.of(), idC -> List.of(), null));
    }

    @Test
    void entradaAutoMapeamento_camposEhCopiaDefensivaImutavel() {
        List<Campo> mutavel = new ArrayList<>();
        mutavel.add(campo(1, "X", Tipo.TEXTO));
        EntradaAutoMapeamento e = new EntradaAutoMapeamento(
                classificacao("M", Map.of(), List.of()),
                "p.xlsx", Fluxo.TERRITORIAL, mutavel, idC -> List.of(), h -> Set.of());
        assertNotNull(e.campos());
        assertThrows(UnsupportedOperationException.class,
                () -> e.campos().add(campo(2, "Y", Tipo.TEXTO)));
    }
}
