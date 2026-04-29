package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cenário (e) do AC15 — catálogo vazio (sem registros em {@code campo}/{@code grupocampo}).
 * Em arquivo separado para isolar do seed do {@link MapearCommandTest}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class MapearCommandCatalogoVazioTest {

    @Inject
    MapearCommand command;

    @Inject
    CommandLine.IFactory factory;

    @Inject
    Instance<DataSource> dsInstance;

    @BeforeEach
    void prepararCatalogoVazio() throws SQLException {
        try (Connection c = dsInstance.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS aise.alternativa");
            s.execute("DROP TABLE IF EXISTS aise.campo");
            s.execute("DROP TABLE IF EXISTS aise.grupocampo");
            for (String stmt : MapearCommandTest.DDL.split(";")) {
                if (!stmt.isBlank()) s.execute(stmt);
            }
            // Sem nenhum INSERT.
        }
    }

    @Test
    void deveFalharComCatalogoVazio(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("p.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "importacao-geo-test", "1.0");
            Worksheet ws = wb.newWorksheet("s");
            ws.value(0, 0, "MATRICULA");
            wb.finish();
        }
        var sout = new ByteArrayOutputStream();
        var serr = new ByteArrayOutputStream();
        int exit = new CommandLine(command, factory)
            .setOut(new PrintWriter(sout, true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(serr, true, StandardCharsets.UTF_8))
            .execute(
                "--arquivo", xlsx.toString(),
                "--fluxo", "territorial",
                "--saida", tmp.resolve("mapping.json").toString()
            );
        assertEquals(1, exit);
        String err = serr.toString(StandardCharsets.UTF_8);
        assertTrue(err.contains("Catálogo vazio"), "stderr=" + err);
    }
}
