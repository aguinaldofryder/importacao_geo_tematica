package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.GrupoCampo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = CatalogoPostgresResource.class, restrictToAnnotatedClass = true)
class GrupoCampoRepositoryTest {

    @Inject
    GrupoCampoRepository repository;

    @Test
    void listarPorFuncionalidadeTerrenoDeveRetornarUmGrupo() {
        List<GrupoCampo> grupos = repository.listarPorFuncionalidade("TERRENO");

        assertEquals(1, grupos.size());
        GrupoCampo grupo = grupos.get(0);
        assertEquals(1L, grupo.id());
        assertEquals("TERRENO", grupo.funcionalidade());
    }
}
