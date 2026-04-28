package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.Alternativa;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = CatalogoPostgresResource.class, restrictToAnnotatedClass = true)
class AlternativaRepositoryTest {

    @Inject
    AlternativaRepository repository;

    @Test
    void listarPorCampoDeveRetornarAlternativasDoCampoMultiplaEscolha() {
        List<Alternativa> alternativas = repository.listarPorCampo(11L);

        assertEquals(2, alternativas.size());
        assertTrue(alternativas.stream().anyMatch(a -> a.id() == 501L && "Alvenaria".equals(a.descricao())));
        assertTrue(alternativas.stream().anyMatch(a -> a.id() == 502L && "Madeira".equals(a.descricao())));
        assertTrue(alternativas.stream().allMatch(a -> a.idCampo() == 11L));
    }

    @Test
    void listarPorCampoInexistenteDeveRetornarListaVaziaSemLancar() {
        List<Alternativa> alternativas = repository.listarPorCampo(999_999L);

        assertNotNull(alternativas);
        assertTrue(alternativas.isEmpty(),
            "Lista deve ser vazia para idCampo inexistente, sem lançar exceção");
    }
}
