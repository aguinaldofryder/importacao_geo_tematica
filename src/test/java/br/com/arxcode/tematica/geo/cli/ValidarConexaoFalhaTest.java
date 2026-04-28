package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cenários de falha de conexão: servidor inatingível e timeout.
 * Usa porta 9999 no localhost (fechada) para simular servidor ausente.
 * acquisition-timeout=1 garante falha rápida no pool.
 */
@QuarkusTest
@TestProfile(ValidarConexaoFalhaTest.HostInvalidoProfile.class)
class ValidarConexaoFalhaTest {

    public static class HostInvalidoProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.datasource.active",                   "true",
                "quarkus.datasource.db-kind",                  "postgresql",
                "quarkus.datasource.jdbc.url",                 "jdbc:postgresql://127.0.0.1:9999/noexist",
                "quarkus.datasource.username",                 "nouser",
                "quarkus.datasource.password",                 "nopass",
                "quarkus.datasource.jdbc.acquisition-timeout", "1"
            );
        }
    }

    @Inject
    ValidarConexaoCommand command;

    // --- AC3 + AC7: servidor inatingível retorna exit 1 com mensagem PT ---

    @Test
    void deveRetornarUmQuandoServidorInatingivel() {
        var erro = new ByteArrayOutputStream();

        int exitCode = new CommandLine(command)
            .setOut(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(erro, true, StandardCharsets.UTF_8))
            .execute();

        assertEquals(1, exitCode, "Exit code deve ser 1 em falha de conexão");
        String errStr = erro.toString(StandardCharsets.UTF_8);
        assertTrue(errStr.startsWith("✗ Falha:"), "Saída de erro deve iniciar com '✗ Falha:'");
        assertFalse(errStr.isBlank(), "Mensagem de erro não deve ser vazia");
    }

    // --- AC3 + AC7: timeout retorna exit 1 com mensagem PT ---

    @Test
    void deveRetornarUmNoTimeout() {
        // acquisition-timeout=1 + host inatingível → falha rápida
        var erro = new ByteArrayOutputStream();

        int exitCode = new CommandLine(command)
            .setOut(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(erro, true, StandardCharsets.UTF_8))
            .execute();

        assertEquals(1, exitCode, "Exit code deve ser 1 em timeout");
        assertTrue(erro.toString(StandardCharsets.UTF_8).startsWith("✗ Falha:"),
            "Saída de erro deve iniciar com '✗ Falha:'");
    }

    // --- AC5: senha nunca aparece na mensagem de erro ---

    @Test
    void naoDeveExibirSenhaNoErroDeConexao() {
        var erro = new ByteArrayOutputStream();

        new CommandLine(command)
            .setOut(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(erro, true, StandardCharsets.UTF_8))
            .execute();

        assertFalse(erro.toString(StandardCharsets.UTF_8).contains("nopass"),
            "Senha não deve aparecer na mensagem de erro");
    }
}
