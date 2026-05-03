package br.com.arxcode.tematica.geo.cli;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
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
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes E2E do {@link ImportarCommand} em JVM com {@code @QuarkusTest} +
 * Testcontainers (Story 4.5 AC16).
 *
 * <p>Cobre 8 cenários: happy path, matrícula ausente, arquivo inexistente,
 * extensão inválida, mapping PENDENTE, mapping JSON inválido, criação automática
 * de diretório de saída e invariante CON-02.
 *
 * <p><b>Seed:</b> 2 matrículas inseridas em {@code aise.tribcadastroimobiliario}
 * em {@code @BeforeEach}, removidas em {@code @AfterEach}.
 *
 * <p><b>Artefatos:</b> gravados em {@code /tmp/opencode/importar-test-saida}
 * (configurado via {@link ImportarTestProfile}), limpados em {@code @BeforeEach}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@TestProfile(ImportarCommandTest.ImportarTestProfile.class)
class ImportarCommandTest {

    /** Diretório de saída fixo para os testes — apontado pelo profile. */
    static final Path OUTPUT_DIR = Path.of("/tmp/opencode/importar-test-saida");

    /**
     * Profile que aponta o diretório de saída para um caminho controlado
     * pelos testes, evitando conflito com a configuração local do dev.
     */
    public static class ImportarTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("importacao.saida.diretorio", OUTPUT_DIR.toString());
        }
    }

    @Inject
    ImportarCommand command;

    @Inject
    CommandLine.IFactory factory;

    @Inject
    Instance<DataSource> dataSource;

    @Inject
    MapeamentoStore mapeamentoStore;

    /** DDL mínimo — apenas as colunas utilizadas pelas fixtures de teste. */
    static final String DDL =
            "CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario ("
            + "id BIGSERIAL PRIMARY KEY, "
            + "tribcadastrogeral_idkey VARCHAR(50), "
            + "area_terreno VARCHAR(100)"
            + ");";

    @BeforeEach
    void setUp() throws Exception {
        // Seed JDBC
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL);
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tribcadastrogeral_idkey LIKE 'TEST_IMP_%'");
            s.execute("INSERT INTO aise.tribcadastroimobiliario (tribcadastrogeral_idkey) "
                    + "VALUES ('TEST_IMP_001')");
            s.execute("INSERT INTO aise.tribcadastroimobiliario (tribcadastrogeral_idkey) "
                    + "VALUES ('TEST_IMP_002')");
        }
        // Limpar artefatos anteriores no diretório de saída
        if (Files.exists(OUTPUT_DIR)) {
            try (var stream = Files.list(OUTPUT_DIR)) {
                stream.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            Files.createDirectories(OUTPUT_DIR);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tribcadastrogeral_idkey LIKE 'TEST_IMP_%'");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    /** Mapeamento mínimo TERRITORIAL: 1 coluna fixa, 0 dinâmicas, chave MATRICULA. */
    private Mapeamento mapeamentoTerritorialMinimo() {
        return new Mapeamento(
            Fluxo.TERRITORIAL,
            "planilha-test.xlsx",
            "MATRICULA",
            Map.of("AREA_TERRENO", "area_terreno"),
            Map.of()
        );
    }

    /** Planilha com 2 linhas de dados — TEST_IMP_001 e TEST_IMP_002 (ambas presentes no banco). */
    private Path criarXlsx2Presentes(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-test.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "importacao-geo-test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "MATRICULA");
            ws.value(0, 1, "AREA_TERRENO");
            ws.value(1, 0, "TEST_IMP_001");
            ws.value(1, 1, "100");
            ws.value(2, 0, "TEST_IMP_002");
            ws.value(2, 1, "200");
            wb.finish();
        }
        return xlsx;
    }

    /** Planilha com 3 linhas — 2 presentes + 1 ausente (TEST_IMP_AUSENTE). */
    private Path criarXlsx3Linhas(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-3linhas.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "importacao-geo-test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "MATRICULA");
            ws.value(0, 1, "AREA_TERRENO");
            ws.value(1, 0, "TEST_IMP_001");
            ws.value(1, 1, "100");
            ws.value(2, 0, "TEST_IMP_002");
            ws.value(2, 1, "200");
            ws.value(3, 0, "TEST_IMP_AUSENTE");
            ws.value(3, 1, "300");
            wb.finish();
        }
        return xlsx;
    }

    // ── Utilitário para listar artefatos gerados ──────────────────────────────

    private List<Path> listarArtefatos(String extensao) throws IOException {
        try (var stream = Files.list(OUTPUT_DIR)) {
            return stream.filter(p -> p.toString().endsWith(extensao))
                    .collect(Collectors.toList());
        }
    }

    // ── Cenário 1: happy path ─────────────────────────────────────────────────

    /**
     * Happy path: 2 matrículas presentes → exit 0, artefatos gerados com UPDATE,
     * sem INSERT proibido.
     */
    @Test
    void deveProduzirArtefatosComExitZeroParaDuasMatriculasPresentes(@TempDir Path tmp) throws Exception {
        Path xlsx = criarXlsx2Presentes(tmp);
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        ResultadoExec r = executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(0, r.exit(), "Exit code deve ser 0. stderr=" + r.stderr());
        assertTrue(r.stdout().contains("✓ Artefatos gerados"), "stdout deve confirmar geração. stdout=" + r.stdout());

        List<Path> sqls = listarArtefatos(".sql");
        assertFalse(sqls.isEmpty(), "Deve haver pelo menos um .sql");
        String conteudoSql = Files.readString(sqls.get(0), StandardCharsets.UTF_8);
        assertTrue(conteudoSql.contains("UPDATE"), "SQL deve conter UPDATE. conteudo=" + conteudoSql);

        List<Path> logs = listarArtefatos(".log");
        assertFalse(logs.isEmpty(), "Deve haver pelo menos um .log");
        assertTrue(Files.size(logs.get(0)) > 0, ".log não deve ser vazio");
    }

    // ── Cenário 2: matrícula ausente ──────────────────────────────────────────

    /** Matrícula ausente → exit 2, .sql com 2 UPDATEs, .log menciona o código ausente. */
    @Test
    void deveRetornarExit2ELogarMatriculaAusente(@TempDir Path tmp) throws Exception {
        Path xlsx = criarXlsx3Linhas(tmp);
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        ResultadoExec r = executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(2, r.exit(), "Exit code deve ser 2. stderr=" + r.stderr());
        assertTrue(r.stderr().contains("erros"), "stderr deve mencionar erros. stderr=" + r.stderr());

        List<Path> sqls = listarArtefatos(".sql");
        assertFalse(sqls.isEmpty(), "Deve haver .sql mesmo com erros");
        String conteudoSql = Files.readString(sqls.get(0), StandardCharsets.UTF_8);
        long countUpdate = conteudoSql.lines().filter(l -> l.startsWith("UPDATE")).count();
        assertEquals(2, countUpdate, "SQL deve conter exatamente 2 UPDATEs (matrículas presentes)");

        List<Path> logs = listarArtefatos(".log");
        assertFalse(logs.isEmpty(), "Deve haver .log");
        String conteudoLog = Files.readString(logs.get(0), StandardCharsets.UTF_8);
        assertTrue(conteudoLog.contains("TEST_IMP_AUSENTE"), "Log deve mencionar matrícula ausente");
    }

    // ── Cenário 3: arquivo inexistente ────────────────────────────────────────

    /** Arquivo inexistente → exit 1, stderr menciona "não encontrado". */
    @Test
    void deveRetornarExit1ParaArquivoInexistente(@TempDir Path tmp) throws Exception {
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        ResultadoExec r = executar(
                "--arquivo", tmp.resolve("nao-existe-4.5.xlsx").toString(),
                "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(1, r.exit(), "Exit code deve ser 1. stderr=" + r.stderr());
        assertTrue(r.stderr().contains("não encontrado") || r.stderr().contains("nao encontrado"),
                "stderr deve mencionar arquivo não encontrado. stderr=" + r.stderr());
    }

    // ── Cenário 4: extensão inválida ──────────────────────────────────────────

    /** Arquivo com extensão .csv → exit 1, stderr menciona ".xlsx". */
    @Test
    void deveRetornarExit1ParaExtensaoInvalida(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("planilha.csv");
        Files.writeString(csv, "MATRICULA,AREA_TERRENO\nTEST_IMP_001,100");
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        ResultadoExec r = executar(
                "--arquivo", csv.toString(),
                "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(1, r.exit(), "Exit code deve ser 1. stderr=" + r.stderr());
        assertTrue(r.stderr().contains(".xlsx"), "stderr deve mencionar .xlsx. stderr=" + r.stderr());
    }

    // ── Cenário 5: mapping com PENDENTE ───────────────────────────────────────

    /** Mapeamento com coluna dinâmica PENDENTE → exit 1, stderr menciona "pendência". */
    @Test
    void deveRetornarExit1ParaMappingComPendencias(@TempDir Path tmp) throws Exception {
        Path xlsx = criarXlsx2Presentes(tmp);
        Path mappingJson = tmp.resolve("mapping-pendente.json");
        Mapeamento mappingPendente = new Mapeamento(
            Fluxo.TERRITORIAL,
            "planilha-test.xlsx",
            "MATRICULA",
            Map.of("AREA_TERRENO", "area_terreno"),
            Map.of("COLUNA_NOVA", new ColunaDinamica(
                    StatusMapeamento.PENDENTE, null, null, null, "Não mapeado automaticamente", null))
        );
        mapeamentoStore.salvar(mappingPendente, mappingJson);

        ResultadoExec r = executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(1, r.exit(), "Exit code deve ser 1. stderr=" + r.stderr());
        assertTrue(r.stderr().toLowerCase().contains("pendência") || r.stderr().toLowerCase().contains("pendencia"),
                "stderr deve mencionar pendência. stderr=" + r.stderr());
    }

    // ── Cenário 6: mapping JSON inválido ──────────────────────────────────────

    /** Arquivo de mapeamento não é JSON → exit 1, stderr menciona "Mapeamento inválido". */
    @Test
    void deveRetornarExit1ParaMappingJsonInvalido(@TempDir Path tmp) throws Exception {
        Path xlsx = criarXlsx2Presentes(tmp);
        Path mappingJson = tmp.resolve("mapping-invalido.json");
        Files.writeString(mappingJson, "isto nao e um json valido {{{");

        ResultadoExec r = executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertEquals(1, r.exit(), "Exit code deve ser 1. stderr=" + r.stderr());
        assertTrue(r.stderr().contains("Mapeamento inválido") || r.stderr().contains("inválido"),
                "stderr deve mencionar mapeamento inválido. stderr=" + r.stderr());
    }

    // ── Cenário 7: diretório de saída criado automaticamente ─────────────────

    /**
     * Diretório de saída inexistente → {@code Files.createDirectories} o cria
     * automaticamente; artefatos gerados no interior.
     */
    @Test
    void deveCriarDiretorioDeSaidaAutomaticamente(@TempDir Path tmp) throws Exception {
        // Apagar o OUTPUT_DIR para forçar a criação automática
        if (Files.exists(OUTPUT_DIR)) {
            Files.walk(OUTPUT_DIR)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        Path xlsx = criarXlsx2Presentes(tmp);
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        ResultadoExec r = executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        assertTrue(r.exit() == 0 || r.exit() == 2,
                "Exit deve ser 0 ou 2 (não 1 — infra OK). exit=" + r.exit() + ", stderr=" + r.stderr());
        assertTrue(Files.exists(OUTPUT_DIR), "Diretório de saída deve ter sido criado automaticamente");
        List<Path> sqls = listarArtefatos(".sql");
        assertFalse(sqls.isEmpty(), "Deve haver .sql no diretório criado automaticamente");
    }

    // ── Cenário 8: invariante CON-02 ─────────────────────────────────────────

    /**
     * CON-02 (bloqueante): o arquivo {@code .sql} gerado não deve conter
     * {@code INSERT INTO tribcadastroimobiliario} nem {@code INSERT INTO tribimobiliariosegmento}.
     */
    @Test
    void deveVerificarInvarianteCon02NoSqlGerado(@TempDir Path tmp) throws Exception {
        Path xlsx = criarXlsx2Presentes(tmp);
        Path mappingJson = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoTerritorialMinimo(), mappingJson);

        executar("--arquivo", xlsx.toString(), "--fluxo", "territorial",
                "--mapeamento", mappingJson.toString());

        List<Path> sqls = listarArtefatos(".sql");
        assertFalse(sqls.isEmpty(), "Deve haver .sql gerado para verificar CON-02");
        String conteudo = Files.readString(sqls.get(0), StandardCharsets.UTF_8);

        assertFalse(conteudo.contains("INSERT INTO tribcadastroimobiliario"),
                "CON-02: SQL não deve conter INSERT em tabela principal territorial");
        assertFalse(conteudo.contains("INSERT INTO tribimobiliariosegmento"),
                "CON-02: SQL não deve conter INSERT em tabela principal predial");
    }
}
