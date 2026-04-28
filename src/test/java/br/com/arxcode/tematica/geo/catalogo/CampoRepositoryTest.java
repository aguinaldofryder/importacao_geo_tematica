package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = CatalogoPostgresResource.class, restrictToAnnotatedClass = true)
class CampoRepositoryTest {

    @Inject
    CampoRepository repository;

    @Test
    void listarPorFluxoTerritorialDeveRetornarApenasCamposDeTerrenoAtivos() {
        List<Campo> campos = repository.listarPorFluxo(Fluxo.TERRITORIAL);

        assertEquals(3, campos.size(), "Esperado 3 campos ativos do TERRENO");
        assertTrue(campos.stream().allMatch(Campo::ativo),
            "Nenhum campo inativo deve ser retornado");
        assertTrue(campos.stream().allMatch(c -> c.idGrupoCampo() == 1L),
            "Todos os campos devem pertencer ao grupo TERRENO (id=1)");
        assertTrue(campos.stream().anyMatch(c -> c.id() == 10L && c.tipo() == Tipo.DECIMAL));
        assertTrue(campos.stream().anyMatch(c -> c.id() == 11L && c.tipo() == Tipo.MULTIPLA_ESCOLHA));
        assertTrue(campos.stream().anyMatch(c -> c.id() == 12L && c.tipo() == Tipo.TEXTO));
        assertTrue(campos.stream().noneMatch(c -> c.id() == 13L),
            "Campo inativo (id=13) não deve ser retornado");
    }

    @Test
    void listarPorFluxoPredialDeveRetornarApenasCamposDeSegmento() {
        List<Campo> campos = repository.listarPorFluxo(Fluxo.PREDIAL);

        assertEquals(2, campos.size(), "Esperado 2 campos ativos do SEGMENTO");
        assertTrue(campos.stream().allMatch(c -> c.idGrupoCampo() == 2L));
        assertTrue(campos.stream().anyMatch(c -> c.id() == 20L && c.tipo() == Tipo.MULTIPLA_ESCOLHA));
        assertTrue(campos.stream().anyMatch(c -> c.id() == 21L && c.tipo() == Tipo.DATA));
    }

    @Test
    void listarTodosDeveIgnorarCamposInativos() {
        List<Campo> campos = repository.listarTodos();

        assertEquals(5, campos.size(),
            "Esperado 5 campos ativos no total (TERRENO 3 + SEGMENTO 2)");
        assertTrue(campos.stream().noneMatch(c -> c.id() == 13L),
            "Campo inativo (id=13) não deve aparecer em listarTodos()");
        assertTrue(campos.stream().allMatch(Campo::ativo));
    }
}
