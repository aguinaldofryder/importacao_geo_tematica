package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cenário (d) do AC15 — falha de conexão DB com senha inválida (NFR-07).
 * Em arquivo separado pois requer {@link TestProfile} próprio (recarrega Quarkus).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@TestProfile(MapearCommandFalhaConexaoTest.SenhaInvalidaProfile.class)
class MapearCommandFalhaConexaoTest {

    @Inject
    MapearCommand command;

    @Inject
    CommandLine.IFactory factory;

    @Test
    void deveFalharComSenhaInvalidaSemVazarSenhaNoStderr(@TempDir Path tmp) {
        var sout = new ByteArrayOutputStream();
        var serr = new ByteArrayOutputStream();
        int exit = new CommandLine(command, factory)
            .setOut(new PrintWriter(sout, true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(serr, true, StandardCharsets.UTF_8))
            .execute(
                "--arquivo", tmp.resolve("x.xlsx").toString(),
                "--fluxo", "territorial",
                "--saida", tmp.resolve("mapping.json").toString()
            );
        String err = serr.toString(StandardCharsets.UTF_8);
        String stdout = sout.toString(StandardCharsets.UTF_8);
        assertEquals(1, exit, "Exit esperado 1. exit=" + exit + " stdout=" + stdout + " stderr=" + err);
        assertTrue(err.contains("Falha de conexão"), "stderr=" + err);
        // NFR-07: nenhuma das senhas deve aparecer em stderr.
        assertFalse(err.contains(PostgresResource.PG.getPassword()),
            "Senha real do container não deve vazar.");
        assertFalse(err.contains("senha-errada-1234"),
            "Senha injetada não deve vazar.");
    }

    public static class SenhaInvalidaProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.password", "senha-errada-1234");
        }
    }
}
