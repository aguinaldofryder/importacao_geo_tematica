package br.com.arxcode.tematica.geo.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida que {@link ColunasFixasConfig} expõe conjuntos vazios quando nenhuma
 * propriedade é definida (apenas defaults via {@code @WithDefault("")}).
 *
 * <p>Story 2.4 — AC11 (cenário "defaults"). Sem {@code @TestProfile} para
 * exercitar o caminho default puro.
 */
@QuarkusTest
class ColunasFixasConfigDefaultsTest {

    @Inject
    ColunasFixasConfig config;

    @Test
    void territorialDeveSerVazioPorDefault() {
        assertNotNull(config.territorial(), "territorial() não deve ser nulo");
        assertTrue(config.territorial().isEmpty(),
            "territorial() deve ser vazio quando nenhuma propriedade é definida");
    }

    @Test
    void predialDeveSerVazioPorDefault() {
        assertNotNull(config.predial(), "predial() não deve ser nulo");
        assertTrue(config.predial().isEmpty(),
            "predial() deve ser vazio quando nenhuma propriedade é definida");
    }
}
