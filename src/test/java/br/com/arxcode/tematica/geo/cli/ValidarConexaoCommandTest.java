package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ValidarConexaoCommandTest {

    @Inject
    CommandLine.IFactory factory;

    @Test
    void deveRetornarExitZeroComMensagemTodo() {
        var output = new ByteArrayOutputStream();
        var writer = new PrintWriter(output, true, StandardCharsets.UTF_8);

        int exitCode = new CommandLine(new ValidarConexaoCommand(), factory)
                .setOut(writer)
                .execute();

        assertEquals(0, exitCode, "Exit code deve ser 0");
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("[TODO Story 1.5]"),
                "Saída deve conter referência à Story 1.5");
    }
}
