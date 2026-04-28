package br.com.arxcode.tematica.geo.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valida que {@link ColunasFixasConfig} lê corretamente as listas
 * separadas por vírgula via {@code @TestProfile} para os fluxos
 * Territorial e Predial.
 *
 * <p>Story 2.4 — AC11 (cenário "override"). Classe separada do
 * {@code ColunasFixasConfigDefaultsTest} porque {@code @TestProfile} é
 * aplicado a nível de classe (mesmo padrão de
 * {@code ValidarConexaoCommandTest} / {@code ValidarConexaoFalhaTest}).
 */
@QuarkusTest
@TestProfile(ColunasFixasConfigOverrideTest.OverrideProfile.class)
class ColunasFixasConfigOverrideTest {

    public static class OverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "importacao.colunas-fixas.territorial", "AREA_TERRENO,TESTADA",
                "importacao.colunas-fixas.predial",     "PADRAO_CONSTRUTIVO"
            );
        }
    }

    @Inject
    ColunasFixasConfig config;

    @Test
    void territorialDeveRefletirOverride() {
        assertEquals(Set.of("AREA_TERRENO", "TESTADA"), config.territorial(),
            "territorial() deve refletir as duas chaves do override");
    }

    @Test
    void predialDeveRefletirOverride() {
        assertEquals(Set.of("PADRAO_CONSTRUTIVO"), config.predial(),
            "predial() deve refletir a chave do override");
    }
}
