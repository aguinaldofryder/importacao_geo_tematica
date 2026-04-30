package br.com.arxcode.tematica.geo.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes E2E do {@link MapearCommand} em JVM com {@code @QuarkusTest} +
 * Testcontainers (Story 3.4 AC15).
 *
 * <p>Cobre 4 dos 6 cenários obrigatórios — happy path, arquivo inexistente,
 * fluxo inválido, planilha sem MULTIPLA_ESCOLHA. Os 2 cenários que requerem
 * profile distinto (falha de conexão DB e catálogo vazio) ficam em
 * {@link MapearCommandFalhaConexaoTest} e {@link MapearCommandCatalogoVazioTest}
 * para evitar reload de Quarkus dentro do mesmo arquivo.
 *
 * <p><b>Isolamento de configuração</b> — usa {@link DefaultsCodigoImovelProfile}
 * para forçar {@code importacao.codigo-imovel.{territorial,predial}=MATRICULA}
 * mesmo quando o desenvolvedor tem um {@code ./config/application.properties}
 * local (gitignored, parte da distribuição) com overrides como
 * {@code COD}/{@code INSCRICAO}. Sem este profile, as fixtures cujos cabeçalhos
 * usam {@code MATRICULA} falham localmente embora o CI fique verde.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@TestProfile(MapearCommandTest.DefaultsCodigoImovelProfile.class)
class MapearCommandTest {

    /**
     * Profile dedicado: restaura os defaults da {@code CodigoImovelConfig}
     * mesmo se o classpath/cwd injetar overrides via SmallRye (e.g.
     * {@code ./config/application.properties} de dev).
     */
    public static class DefaultsCodigoImovelProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "importacao.codigo-imovel.territorial", "MATRICULA",
                "importacao.codigo-imovel.predial",     "MATRICULA"
            );
        }
    }


    @Inject
    MapearCommand command;

    @Inject
    CommandLine.IFactory factory;

    @Inject
    Instance<DataSource> dsInstance;

    static final String DDL =
        "CREATE TABLE IF NOT EXISTS aise.grupocampo (id BIGINT PRIMARY KEY, funcionalidade VARCHAR(50));"
        + "CREATE TABLE IF NOT EXISTS aise.campo (id BIGINT PRIMARY KEY, descricao VARCHAR(200), tipo VARCHAR(50), ativo CHAR(1), idgrupo BIGINT);"
        + "CREATE TABLE IF NOT EXISTS aise.alternativa (id BIGINT PRIMARY KEY, descricao VARCHAR(200), idcampo BIGINT);";

    @BeforeEach
    void setupCatalogo() throws SQLException {
        try (Connection c = dsInstance.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS aise.alternativa");
            s.execute("DROP TABLE IF EXISTS aise.campo");
            s.execute("DROP TABLE IF EXISTS aise.grupocampo");
            for (String stmt : DDL.split(";")) {
                if (!stmt.isBlank()) s.execute(stmt);
            }
            s.execute("INSERT INTO aise.grupocampo VALUES (1, 'TERRENO')");
            s.execute("INSERT INTO aise.grupocampo VALUES (2, 'SEGMENTO')");
            s.execute("INSERT INTO aise.campo VALUES (10, 'AREA_TERRENO', 'TEXTO', 'S', 1)");
            s.execute("INSERT INTO aise.campo VALUES (11, 'TESTADA', 'DECIMAL', 'S', 1)");
            s.execute("INSERT INTO aise.campo VALUES (12, 'TIPO_MURO', 'MULTIPLA_ESCOLHA', 'S', 1)");
            s.execute("INSERT INTO aise.campo VALUES (13, 'OBSERVACAO', 'TEXTO', 'S', 1)");
            s.execute("INSERT INTO aise.campo VALUES (14, 'PROPRIETARIO', 'TEXTO', 'S', 1)");
            s.execute("INSERT INTO aise.alternativa VALUES (101, 'BAIXO', 12)");
            s.execute("INSERT INTO aise.alternativa VALUES (102, 'MEDIO', 12)");
            s.execute("INSERT INTO aise.alternativa VALUES (103, 'ALTO', 12)");
        }
    }

    static Path gerarPlanilhaTerritorial(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-territorial-mini.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "importacao-geo-test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            String[] headers = {"MATRICULA", "AREA_TERRENO", "TESTADA", "TIPO_MURO", "COLUNA_NOVA", "OBSERVACAO"};
            for (int i = 0; i < headers.length; i++) {
                ws.value(0, i, headers[i]);
            }
            String[][] dados = {
                {"1001", "100", "10", "BAIXO", "x", "ok"},
                {"1002", "200", "20", "MEDIO", "y", "ok"},
                {"1003", "300", "30", "ALTO",  "z", "ok"}
            };
            for (int r = 0; r < dados.length; r++) {
                for (int c = 0; c < dados[r].length; c++) {
                    ws.value(r + 1, c, dados[r][c]);
                }
            }
            wb.finish();
        }
        return xlsx;
    }

    static Path gerarPlanilhaSemMultiplaEscolha(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-sem-me.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "importacao-geo-test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            String[] headers = {"MATRICULA", "AREA_TERRENO", "TESTADA", "OBSERVACAO"};
            for (int i = 0; i < headers.length; i++) {
                ws.value(0, i, headers[i]);
            }
            ws.value(1, 0, "1");
            ws.value(1, 1, "10");
            ws.value(1, 2, "5");
            ws.value(1, 3, "ok");
            wb.finish();
        }
        return xlsx;
    }

    record ResultadoExec(int exit, String stdout, String stderr) {}

    private ResultadoExec executar(String... args) {
        var sout = new ByteArrayOutputStream();
        var serr = new ByteArrayOutputStream();
        int exit = new CommandLine(command, factory)
            .setOut(new PrintWriter(sout, true, StandardCharsets.UTF_8))
            .setErr(new PrintWriter(serr, true, StandardCharsets.UTF_8))
            .execute(args);
        return new ResultadoExec(exit,
            sout.toString(StandardCharsets.UTF_8),
            serr.toString(StandardCharsets.UTF_8));
    }

    // (a) happy path Territorial -------------------------------------------
    @Test
    void devePersistirMappingComContadoresCorretosNoFluxoTerritorial(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaTerritorial(tmp);
        Path saida = tmp.resolve("mapping.json");

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);
        assertTrue(r.stdout.contains("✓ Mapeamento concluído"), "stdout=" + r.stdout);
        assertTrue(r.stdout.contains("pendente(s)"));
        assertTrue(Files.exists(saida), "mapping.json deve existir");

        Mapeamento m = new MapeamentoStore(new ObjectMapper()).carregar(saida);
        assertEquals(Fluxo.TERRITORIAL, m.fluxo());
        assertEquals("MATRICULA", m.colunaCodigoImovel());
        assertEquals("planilha-territorial-mini.xlsx", m.planilha());

        long mapeados = m.colunasDinamicas().values().stream()
            .filter(c -> c.status() == StatusMapeamento.MAPEADO).count();
        long pendentes = m.colunasDinamicas().values().stream()
            .filter(c -> c.status() == StatusMapeamento.PENDENTE).count();
        assertTrue(mapeados >= 1, "Esperado ao menos 1 MAPEADO");
        assertTrue(pendentes >= 1, "Esperado ao menos 1 PENDENTE");

        ColunaDinamica nova = m.colunasDinamicas().get("COLUNA_NOVA");
        assertNotNull(nova);
        assertEquals(StatusMapeamento.PENDENTE, nova.status());
    }

    // (b) arquivo inexistente ---------------------------------------------
    @Test
    void deveFalharComArquivoInexistente(@TempDir Path tmp) {
        Path inexistente = tmp.resolve("nao-existe.xlsx");
        Path saida = tmp.resolve("mapping.json");

        ResultadoExec r = executar(
            "--arquivo", inexistente.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(1, r.exit);
        assertTrue(r.stderr.contains("Arquivo inválido") || r.stderr.contains("Arquivo não encontrado"),
            "stderr=" + r.stderr);
        assertFalse(Files.exists(saida));
    }

    // (c) fluxo inválido --------------------------------------------------
    @Test
    void deveRecusarFluxoInvalido(@TempDir Path tmp) {
        ResultadoExec r = executar(
            "--arquivo", tmp.resolve("x").toString(),
            "--fluxo", "invalido"
        );
        assertNotEquals(0, r.exit);
        assertTrue(r.stderr.toLowerCase().contains("inválido") || r.stderr.toLowerCase().contains("invalido"),
            "stderr deve mencionar valor inválido. stderr=" + r.stderr);
    }

    // (f) skippable: planilha sem MULTIPLA_ESCOLHA no catálogo ------------
    @Test
    void devePularSegundoPasseQuandoNaoHaMultiplaEscolha(@TempDir Path tmp) throws Exception {
        try (Connection c = dsInstance.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.alternativa WHERE idcampo = 12");
            s.execute("DELETE FROM aise.campo WHERE id = 12");
        }
        Path planilha = gerarPlanilhaSemMultiplaEscolha(tmp);
        Path saida = tmp.resolve("mapping.json");

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);
        assertTrue(Files.exists(saida));
        Mapeamento m = new MapeamentoStore(new ObjectMapper()).carregar(saida);
        m.colunasDinamicas().values().forEach(cd -> {
            if (cd.status() == StatusMapeamento.MAPEADO) {
                assertNotEquals(Tipo.MULTIPLA_ESCOLHA, cd.tipo());
            }
        });
    }
}
