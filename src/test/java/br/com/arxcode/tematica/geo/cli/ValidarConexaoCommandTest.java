package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class ValidarConexaoCommandTest {

    // @Dependent bean — cada @Inject cria nova instância (sem proxy CDI)
    @Inject
    ValidarConexaoCommand command;

    // --- AC2: sucesso com metadados ---

    @Test
    void deveConectarComSucessoEImprimirMetadados() {
        var saida = new ByteArrayOutputStream();

        int exitCode = new CommandLine(command)
            .setOut(new PrintWriter(saida, true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .execute();

        assertEquals(0, exitCode, "Exit code deve ser 0 em sucesso");
        String out = saida.toString(StandardCharsets.UTF_8);
        assertTrue(out.startsWith("✓ Conexão OK"), "Saída deve iniciar com '✓ Conexão OK'");
        assertTrue(out.contains("base="), "Saída deve conter nome da base");
        assertTrue(out.contains("usuário="), "Saída deve conter nome do usuário");
        assertTrue(out.contains("versão PG="), "Saída deve conter versão do PostgreSQL");
    }

    // --- AC5: senha nunca aparece em stdout ---

    @Test
    void naoDeveExibirSenhaNoStdout() {
        var saida = new ByteArrayOutputStream();
        var erro  = new ByteArrayOutputStream();

        new CommandLine(command)
            .setOut(new PrintWriter(saida, true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(erro,  true, StandardCharsets.UTF_8))
            .execute();

        String senha = PostgresResource.PG.getPassword();
        assertFalse(saida.toString(StandardCharsets.UTF_8).contains(senha),
            "Senha não deve aparecer no stdout");
        assertFalse(erro.toString(StandardCharsets.UTF_8).contains(senha),
            "Senha não deve aparecer no stderr");
    }

    // --- AC3: mapeamento de SQLState → mensagem amigável PT ---

    @Test
    void mensagemParaCredencialInvalida() {
        var ex = new SQLException("password authentication failed for user \"geo_user\"", "28P01");
        assertEquals("Usuário ou senha incorretos", ValidarConexaoCommand.mensagemAmigavel(ex));
    }

    @Test
    void mensagemParaServidorInatingivel() {
        var ex = new SQLException("Connection refused. Check that the hostname and port are correct.", "08001");
        assertEquals("Servidor indisponível: verifique host e porta", ValidarConexaoCommand.mensagemAmigavel(ex));
    }

    @Test
    void mensagemParaBaseNaoEncontrada() {
        var ex = new SQLException("FATAL: database \"inexistente\" does not exist", "3D000");
        assertEquals("Base de dados não encontrada", ValidarConexaoCommand.mensagemAmigavel(ex));
    }

    @Test
    void mensagemGenerica() {
        var ex = new SQLException("algum erro desconhecido", "99999");
        assertEquals("Falha na conexão: 99999", ValidarConexaoCommand.mensagemAmigavel(ex));
    }

    @Test
    void mensagemParaExcecaoEncapsulada() {
        var causa = new SQLException("Connection refused", "08001");
        var wrapper = new SQLException("Timeout acquiring connection", (String) null);
        wrapper.initCause(causa);
        assertEquals("Servidor indisponível: verifique host e porta",
            ValidarConexaoCommand.mensagemAmigavel(wrapper));
    }
}
