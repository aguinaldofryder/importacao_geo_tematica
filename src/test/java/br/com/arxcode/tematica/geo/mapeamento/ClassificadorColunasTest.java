package br.com.arxcode.tematica.geo.mapeamento;

import br.com.arxcode.tematica.geo.dominio.excecao.CabecalhoDuplicadoException;
import br.com.arxcode.tematica.geo.dominio.excecao.ColunaCodigoAusenteException;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários puros (sem {@code @QuarkusTest}) do {@link ClassificadorColunas}
 * cobrindo os 12 cenários do AC10 (a–l) da Story 2.4.
 */
class ClassificadorColunasTest {

    private static List<String> headersDe(String... values) {
        return Arrays.asList(values);
    }

    // (a) happy path Territorial — código + 2 fixas + 3 dinâmicas
    @Test
    void aHappyPathTerritorial() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("MATRICULA", "AREA_TERRENO", "TESTADA",
            "OBSERVACAO", "USO_PREDOMINANTE", "DATA_VISTORIA");
        var fixasConhecidas = Set.of("AREA_TERRENO", "TESTADA");

        Classificacao c = classificador.classificar(headers, "MATRICULA", fixasConhecidas);

        assertEquals("MATRICULA", c.codigo());
        assertIterableEquals(List.of("AREA_TERRENO", "TESTADA"), c.fixas().keySet());
        assertEquals("AREA_TERRENO", c.fixas().get("AREA_TERRENO"));
        assertEquals("TESTADA", c.fixas().get("TESTADA"));
        assertIterableEquals(
            List.of("OBSERVACAO", "USO_PREDOMINANTE", "DATA_VISTORIA"),
            c.dinamicas());
    }

    // (b) happy path Predial
    @Test
    void bHappyPathPredial() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("IDKEY", "PADRAO_CONSTRUTIVO", "ANO_CONSTRUCAO");
        var fixasConhecidas = Set.of("PADRAO_CONSTRUTIVO");

        Classificacao c = classificador.classificar(headers, "IDKEY", fixasConhecidas);

        assertEquals("IDKEY", c.codigo());
        assertIterableEquals(List.of("PADRAO_CONSTRUTIVO"), c.fixas().keySet());
        assertIterableEquals(List.of("ANO_CONSTRUCAO"), c.dinamicas());
    }

    // (c) colunasFixasConhecidas vazio → tudo (exceto código) vai para dinâmicas
    @Test
    void cFixasVaziaTudoVaiParaDinamicas() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("MATRICULA", "AREA_TERRENO", "OBSERVACAO");

        Classificacao c = classificador.classificar(headers, "MATRICULA", Set.of());

        assertEquals("MATRICULA", c.codigo());
        assertTrue(c.fixas().isEmpty(), "fixas deve ser vazio");
        assertIterableEquals(List.of("AREA_TERRENO", "OBSERVACAO"), c.dinamicas());
    }

    // (d) coluna de código case-insensitive
    @Test
    void dCodigoCaseInsensitive() {
        var classificador = new ClassificadorColunas(); // default: case-insensitive
        var headers = headersDe("MATRICULA", "AREA_TERRENO");

        Classificacao c = classificador.classificar(headers, "matricula", Set.of("AREA_TERRENO"));

        assertEquals("MATRICULA", c.codigo(),
            "deve preservar o header original mesmo casando case-insensitive");
    }

    // (e) coluna de código com espaços extras (trim=true)
    @Test
    void eCodigoComEspacosExtrasTrim() {
        var classificador = new ClassificadorColunas(); // trim=true
        var headers = headersDe("  MATRICULA  ", "OBSERVACAO");

        Classificacao c = classificador.classificar(headers, "MATRICULA", Set.of());

        assertEquals("  MATRICULA  ", c.codigo(),
            "deve preservar o header original (com espaços) mesmo após match com trim");
    }

    // (f) coluna de código ausente
    @Test
    void fCodigoAusenteLancaExcecao() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("AREA_TERRENO", "OBSERVACAO");

        var ex = assertThrows(ColunaCodigoAusenteException.class,
            () -> classificador.classificar(headers, "MATRICULA", Set.of()));
        assertTrue(ex.getMessage().contains("MATRICULA"),
            "mensagem deve conter o nome procurado");
        assertTrue(ex.getMessage().contains("AREA_TERRENO"),
            "mensagem deve listar cabeçalhos disponíveis");
    }

    // (g) header duplicado
    @Test
    void gHeaderDuplicadoLancaExcecao() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("MATRICULA", "AREA_TERRENO", "AREA_TERRENO");

        var ex = assertThrows(CabecalhoDuplicadoException.class,
            () -> classificador.classificar(headers, "MATRICULA", Set.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("area_terreno"),
            "mensagem deve mencionar o header duplicado");
        assertTrue(ex.getMessage().contains("2"),
            "mensagem deve indicar a contagem (2) do duplicado");
    }

    // (h, i) validação de entrada — parametrizado
    static Stream<Arguments> entradasInvalidas() {
        return Stream.of(
            Arguments.of(
                "headers nulos", null, "MATRICULA", Set.of(), "vazia ou nula"),
            Arguments.of(
                "headers vazios", Collections.emptyList(), "MATRICULA", Set.of(), "vazia ou nula"),
            Arguments.of(
                "código nulo", List.of("A"), null, Set.of(), "código não informado"),
            Arguments.of(
                "código em branco", List.of("A"), "   ", Set.of(), "código não informado"),
            Arguments.of(
                "fixas null", List.of("A"), "A", null, "colunas fixas")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("entradasInvalidas")
    void hi_validacaoEntradaLancaImportacaoException(String descricao,
                                                    List<String> headers,
                                                    String nomeCodigo,
                                                    Set<String> fixas,
                                                    String fragmentoMensagem) {
        var classificador = new ClassificadorColunas();
        var ex = assertThrows(ImportacaoException.class,
            () -> classificador.classificar(headers, nomeCodigo, fixas));
        assertTrue(ex.getMessage().toLowerCase().contains(fragmentoMensagem.toLowerCase()),
            "mensagem deve conter '" + fragmentoMensagem + "', foi: " + ex.getMessage());
    }

    // (j) ordem preservada em fixas e dinâmicas
    @Test
    void jOrdemPreservadaFixasEDinamicas() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("MATRICULA", "Z_DIN", "TESTADA", "A_DIN", "AREA_TERRENO", "M_DIN");
        var fixasConhecidas = Set.of("AREA_TERRENO", "TESTADA");

        Classificacao c = classificador.classificar(headers, "MATRICULA", fixasConhecidas);

        // fixas: ordem de aparição em headers (TESTADA antes de AREA_TERRENO)
        assertIterableEquals(List.of("TESTADA", "AREA_TERRENO"),
            new ArrayList<>(c.fixas().keySet()));
        // dinâmicas: ordem original
        assertIterableEquals(List.of("Z_DIN", "A_DIN", "M_DIN"), c.dinamicas());
    }

    // (k) caseSensitive=true faz "matricula" ≠ "MATRICULA"
    @Test
    void kCaseSensitiveDistingueMatricula() {
        var classificador = new ClassificadorColunas(true, true);
        var headers = headersDe("MATRICULA", "AREA_TERRENO");

        assertThrows(ColunaCodigoAusenteException.class,
            () -> classificador.classificar(headers, "matricula", Set.of()));
    }

    // (l) precedência codigo > fixas: header igual ao código + listado em fixas → vai só para código
    @Test
    void lPrecedenciaCodigoSobreFixas() {
        var classificador = new ClassificadorColunas();
        var headers = headersDe("MATRICULA", "AREA_TERRENO");
        var fixasConhecidas = Set.of("MATRICULA", "AREA_TERRENO");

        Classificacao c = classificador.classificar(headers, "MATRICULA", fixasConhecidas);

        assertEquals("MATRICULA", c.codigo());
        assertIterableEquals(List.of("AREA_TERRENO"), c.fixas().keySet(),
            "MATRICULA não deve aparecer em fixas (precedência do slot codigo)");
        assertTrue(c.dinamicas().isEmpty(), "dinâmicas deve estar vazio");
    }
}
