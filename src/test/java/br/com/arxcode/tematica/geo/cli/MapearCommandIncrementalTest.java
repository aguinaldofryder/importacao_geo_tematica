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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes E2E do modo incremental de {@link MapearCommand} (Story 3.6 AC12).
 *
 * <p>Cobre 5 cenários obrigatórios: (a) happy path incremental, (b) preservação
 * de MAPEADO, (c) resolução parcial, (d) mapping.json corrompido e
 * (e) modo inicial não-regressivo.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@TestProfile(MapearCommandIncrementalTest.DefaultsProfile.class)
class MapearCommandIncrementalTest {

    /** Perfil que fixa o nome da coluna de código para os testes. */
    public static class DefaultsProfile implements QuarkusTestProfile {
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
            // campo id=10: TIPO_MURO (MULTIPLA_ESCOLHA), id=11: AREA_TERRENO (TEXTO) — AC12 seed
            s.execute("INSERT INTO aise.campo VALUES (10, 'TIPO_MURO', 'MULTIPLA_ESCOLHA', 'S', 1)");
            s.execute("INSERT INTO aise.campo VALUES (11, 'AREA_TERRENO', 'TEXTO', 'S', 1)");
            // alternativas para TIPO_MURO (idcampo=10)
            s.execute("INSERT INTO aise.alternativa VALUES (501, 'Alvenaria', 10)");
            s.execute("INSERT INTO aise.alternativa VALUES (502, 'Madeira', 10)");
        }
    }

    // ---- helpers ----

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

    /**
     * Gera planilha com MATRICULA, AREA_TERRENO e TIPO_MURO.
     * GAP-1: AREA_TERRENO inclusa para que AC5 não descarte antes de verificar preservação.
     */
    static Path gerarPlanilhaBase(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-incremental.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            String[] headers = {"MATRICULA", "AREA_TERRENO", "TIPO_MURO"};
            for (int i = 0; i < headers.length; i++) ws.value(0, i, headers[i]);
            ws.value(1, 0, "1001"); ws.value(1, 1, "100"); ws.value(1, 2, "Alvenaria");
            ws.value(2, 0, "1002"); ws.value(2, 1, "200"); ws.value(2, 2, "Madeira");
            wb.finish();
        }
        return xlsx;
    }

    /** Gera planilha com MATRICULA, AREA_TERRENO, TIPO_MURO e Tijolo (valor extra). */
    static Path gerarPlanilhaComTijolo(Path dir) throws IOException {
        Path xlsx = dir.resolve("planilha-tijolo.xlsx");
        try (OutputStream os = Files.newOutputStream(xlsx)) {
            Workbook wb = new Workbook(os, "test", "1.0");
            Worksheet ws = wb.newWorksheet("Sheet1");
            String[] headers = {"MATRICULA", "AREA_TERRENO", "TIPO_MURO"};
            for (int i = 0; i < headers.length; i++) ws.value(0, i, headers[i]);
            ws.value(1, 0, "1001"); ws.value(1, 1, "100"); ws.value(1, 2, "Alvenaria");
            ws.value(2, 0, "1002"); ws.value(2, 1, "200"); ws.value(2, 2, "Tijolo");
            wb.finish();
        }
        return xlsx;
    }

    private MapeamentoStore store() {
        return new MapeamentoStore(new ObjectMapper());
    }

    // ---- (a) happy path incremental ----

    /**
     * Cenário (a): TIPO_MURO pré-existente como PENDENTE com idcampo=10 e
     * alternativas null → após re-execução deve ficar MAPEADO com IDs corretos.
     */
    @Test
    void cenario_a_happyPathIncremental_resolveCandidatos(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaBase(tmp);
        Path saida = tmp.resolve("mapping.json");

        // Criar mapping.json pré-existente com TIPO_MURO PENDENTE, idcampo=10, alternativas null
        Map<String, Integer> altsNull = new LinkedHashMap<>();
        altsNull.put("Alvenaria", null);
        altsNull.put("Madeira", null);
        ColunaDinamica tipoMuroPendente = new ColunaDinamica(
            StatusMapeamento.PENDENTE, 10, Tipo.MULTIPLA_ESCOLHA, altsNull,
            "2 de 2 alternativas sem mapeamento", null);
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("TIPO_MURO", tipoMuroPendente);
        Mapeamento existente = new Mapeamento(
            Fluxo.TERRITORIAL, "planilha-incremental.xlsx", "MATRICULA",
            null, Map.of("AREA_TERRENO", "area_terreno"), dinamicas);
        store().salvar(existente, saida);

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);
        assertTrue(r.stdout.contains("✓ Mapeamento concluído"), "stdout=" + r.stdout);

        Mapeamento resultado = store().carregar(saida);
        ColunaDinamica tipoMuro = resultado.colunasDinamicas().get("TIPO_MURO");
        assertNotNull(tipoMuro, "TIPO_MURO deve estar no resultado");
        assertEquals(StatusMapeamento.MAPEADO, tipoMuro.status(),
            "TIPO_MURO deve ficar MAPEADO após resolução incremental");
        assertNotNull(tipoMuro.alternativas());
        assertEquals(501, tipoMuro.alternativas().get("Alvenaria"),
            "Alternativa Alvenaria deve ter id 501");
        assertEquals(502, tipoMuro.alternativas().get("Madeira"),
            "Alternativa Madeira deve ter id 502");
    }

    // ---- (b) preservação de MAPEADO ----

    /**
     * Cenário (b): AREA_TERRENO pré-existente como MAPEADO não deve ser reprocessado.
     * COLUNA_NOVA como PENDENTE sem idcampo deve permanecer PENDENTE.
     * GAP-1: planilha inclui AREA_TERRENO para que não seja descartada como ausente.
     */
    @Test
    void cenario_b_preservaMapeadoEPendenteSemIdcampo(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaBase(tmp);
        Path saida = tmp.resolve("mapping.json");

        // AREA_TERRENO já MAPEADO com idcampo=11; TIPO_MURO como PENDENTE sem idcampo
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("AREA_TERRENO", new ColunaDinamica(
            StatusMapeamento.MAPEADO, 11, Tipo.TEXTO, null, null, null));
        dinamicas.put("TIPO_MURO", new ColunaDinamica(
            StatusMapeamento.PENDENTE, null, null, null,
            "Nenhum campo encontrado com descricao='TIPO_MURO'", null));
        Mapeamento existente = new Mapeamento(
            Fluxo.TERRITORIAL, "planilha-incremental.xlsx", "MATRICULA",
            null, Map.of(), dinamicas);
        store().salvar(existente, saida);

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);

        Mapeamento resultado = store().carregar(saida);

        // AREA_TERRENO deve permanecer MAPEADO com idcampo=11 intocado
        ColunaDinamica areaTerreno = resultado.colunasDinamicas().get("AREA_TERRENO");
        assertNotNull(areaTerreno, "AREA_TERRENO deve estar no resultado");
        assertEquals(StatusMapeamento.MAPEADO, areaTerreno.status());
        assertEquals(11, areaTerreno.idcampo());

        // TIPO_MURO estava PENDENTE sem idcampo — no existente era PENDENTE sem idcampo.
        // No modo incremental não é candidato; o auto-mapeamento normal vai tentar mapear.
        // O catálogo tem TIPO_MURO id=10, então o auto-mapeador vai mapear com DISTINCT.
        // Mas como não é candidato, o existente (PENDENTE sem idcampo) deve ser preservado.
        ColunaDinamica tipoMuro = resultado.colunasDinamicas().get("TIPO_MURO");
        assertNotNull(tipoMuro, "TIPO_MURO deve estar no resultado");
        assertEquals(StatusMapeamento.PENDENTE, tipoMuro.status(),
            "TIPO_MURO PENDENTE sem idcampo deve ser preservado sem reprocessamento");
        assertNull(tipoMuro.idcampo(), "idcampo deve permanecer null (não-candidato)");
    }

    // ---- (c) resolução parcial ----

    /**
     * Cenário (c): campo com 2 alternativas possíveis, planilha contém "Alvenaria"
     * (casou) e "Tijolo" (sem match). Após re-execução item permanece PENDENTE
     * com Alvenaria=501 e Tijolo=null. Motivo deve conter "1 de 2 alternativas sem mapeamento".
     */
    @Test
    void cenario_c_resolucaoParcial(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaComTijolo(tmp);
        Path saida = tmp.resolve("mapping.json");

        // TIPO_MURO pré-existente: PENDENTE, idcampo=10, alternativas null para Alvenaria e Tijolo
        Map<String, Integer> altsNull = new LinkedHashMap<>();
        altsNull.put("Alvenaria", null);
        altsNull.put("Tijolo", null);
        ColunaDinamica tipoMuroPendente = new ColunaDinamica(
            StatusMapeamento.PENDENTE, 10, Tipo.MULTIPLA_ESCOLHA, altsNull,
            "2 de 2 alternativas sem mapeamento", null);
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("TIPO_MURO", tipoMuroPendente);
        Mapeamento existente = new Mapeamento(
            Fluxo.TERRITORIAL, "planilha-tijolo.xlsx", "MATRICULA",
            null, Map.of("AREA_TERRENO", "area_terreno"), dinamicas);
        store().salvar(existente, saida);

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);

        Mapeamento resultado = store().carregar(saida);
        ColunaDinamica tipoMuro = resultado.colunasDinamicas().get("TIPO_MURO");
        assertNotNull(tipoMuro);
        assertEquals(StatusMapeamento.PENDENTE, tipoMuro.status(),
            "TIPO_MURO deve permanecer PENDENTE (resolução parcial)");
        assertNotNull(tipoMuro.alternativas());
        assertEquals(501, tipoMuro.alternativas().get("Alvenaria"),
            "Alvenaria deve ter sido resolvida para 501");
        assertNull(tipoMuro.alternativas().get("Tijolo"),
            "Tijolo deve permanecer null (sem match)");
        assertNotNull(tipoMuro.motivo());
        assertTrue(tipoMuro.motivo().contains("1 de 2 alternativas sem mapeamento"),
            "Motivo deve indicar '1 de 2 alternativas sem mapeamento'. Motivo=" + tipoMuro.motivo());
    }

    // ---- (d) mapping.json corrompido ----

    /**
     * Cenário (d): mapping.json pré-existente com JSON inválido.
     * Re-execução deve imprimir aviso PT e prosseguir como mapeamento do zero.
     */
    @Test
    void cenario_d_mappingCorrompido(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaBase(tmp);
        Path saida = tmp.resolve("mapping.json");

        // Escrever JSON inválido diretamente
        Files.writeString(saida, "{ JSON CORROMPIDO <<<");

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0 mesmo com mapping corrompido. stderr=" + r.stderr);
        // Aviso deve aparecer no stderr
        assertTrue(r.stderr.contains("Aviso") || r.stderr.contains("aviso")
            || r.stderr.contains("não pôde ser lido"),
            "stderr deve conter aviso sobre mapping inválido. stderr=" + r.stderr);
        // mapping.json deve ter sido sobrescrito com mapeamento novo
        assertTrue(Files.exists(saida), "mapping.json deve existir após mapeamento do zero");
        Mapeamento resultado = store().carregar(saida);
        assertNotNull(resultado);
        assertEquals(Fluxo.TERRITORIAL, resultado.fluxo());
    }

    // ---- (e) modo inicial não-regressivo ----

    /**
     * Cenário (e): sem mapping.json pré-existente, comportamento idêntico ao
     * happy path da Story 3.4 — garante não-regressão do modo normal.
     */
    @Test
    void cenario_e_modoInicialNaoRegressivo(@TempDir Path tmp) throws Exception {
        Path planilha = gerarPlanilhaBase(tmp);
        Path saida = tmp.resolve("mapping.json");

        // Confirmar que o arquivo não existe
        assertFalse(Files.exists(saida), "mapping.json não deve existir antes do teste");

        ResultadoExec r = executar(
            "--arquivo", planilha.toString(),
            "--fluxo", "territorial",
            "--saida", saida.toString()
        );

        assertEquals(0, r.exit, "Exit code deve ser 0. stderr=" + r.stderr);
        assertTrue(r.stdout.contains("✓ Mapeamento concluído"), "stdout=" + r.stdout);
        assertTrue(Files.exists(saida), "mapping.json deve ter sido criado");

        Mapeamento resultado = store().carregar(saida);
        assertEquals(Fluxo.TERRITORIAL, resultado.fluxo());
        assertEquals("MATRICULA", resultado.colunaCodigoImovel());
        assertFalse(resultado.colunasDinamicas().isEmpty(),
            "Deve haver ao menos uma coluna dinâmica");

        // TIPO_MURO deve ter sido auto-mapeado (catálogo tem TIPO_MURO id=10 com alternativas)
        ColunaDinamica tipoMuro = resultado.colunasDinamicas().get("TIPO_MURO");
        assertNotNull(tipoMuro, "TIPO_MURO deve estar no resultado");
        assertEquals(StatusMapeamento.MAPEADO, tipoMuro.status(),
            "TIPO_MURO deve ser MAPEADO no modo inicial (alternativas presentes no catálogo)");
    }
}
