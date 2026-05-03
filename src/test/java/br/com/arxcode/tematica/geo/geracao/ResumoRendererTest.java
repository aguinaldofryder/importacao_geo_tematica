package br.com.arxcode.tematica.geo.geracao;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários de {@link ResumoRenderer}.
 *
 * <p>Estratégia: asserts por conteúdo ({@code contains}) em vez de equals —
 * evita fragilidade frente a ajustes de formatação (Guideline Story 5.2 §Testing).
 * Testes de JSON verificam chaves e valores individualmente.
 *
 * <p>Story: 5.2 — Resumo: formatação ASCII + JSON + exit codes.
 */
class ResumoRendererTest {

    /** Snapshot territorial com dados realistas reutilizado em vários testes. */
    private ResumoSnapshot snapshotTerritorial;

    /** Snapshot predial para testes de discriminação de fluxo. */
    private ResumoSnapshot snapshotPredial;

    @BeforeEach
    void setUp() {
        Instant inicio = Instant.parse("2026-04-20T17:32:10Z");
        Instant fim    = Instant.parse("2026-04-20T17:34:47Z");

        snapshotTerritorial = new ResumoSnapshot(
                Fluxo.TERRITORIAL,
                "TABELA_TERRITORIAL_V001.xlsx",
                inicio,
                fim,
                1204,
                1187,
                17,
                1187,
                4210,
                893,
                Path.of("./saida/saida-territorial-20260420-143447.sql"),
                Path.of("./saida/saida-territorial-20260420-143447.log")
        );

        snapshotPredial = new ResumoSnapshot(
                Fluxo.PREDIAL,
                "TABELA_PREDIAL_V001.xlsx",
                inicio,
                fim,
                50,
                48,
                2,
                48,
                0,
                120,
                Path.of("./saida/saida-predial-20260420-143447.sql"),
                Path.of("./saida/saida-predial-20260420-143447.log")
        );
    }

    // ── ASCII — cabeçalho e metadados ────────────────────────────────────────

    @Test
    void ascii_contemCabecalhoResumo() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("RESUMO DA IMPORTAÇÃO"),
                "deve conter título RESUMO DA IMPORTAÇÃO");
    }

    @Test
    void ascii_contemFluxoTerritorialMinusculo() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        // o título deve conter "— territorial" em minúsculo
        assertTrue(ascii.contains("— territorial"),
                "título deve conter '— territorial' em minúsculo");
    }

    @Test
    void ascii_contemFluxoPredialMinusculo() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotPredial);
        assertTrue(ascii.contains("predial"),
                "deve conter fluxo predial em minúsculo");
    }

    @Test
    void ascii_contemNomePlanilha() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("TABELA_TERRITORIAL_V001.xlsx"),
                "deve conter o nome da planilha");
    }

    @Test
    void ascii_contemSeparadoresMoldura() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertAll("separadores",
                () -> assertTrue(ascii.contains("========"),
                        "deve conter separador duplo (==)"),
                () -> assertTrue(ascii.contains("--------"),
                        "deve conter separador simples (--)"));
    }

    // ── ASCII — contadores ───────────────────────────────────────────────────

    @Test
    void ascii_contemNumerosFormatadosLocaleBR() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        // 1204 deve aparecer como "1.204" (ponto como separador de milhar em pt-BR)
        assertTrue(ascii.contains("1.204"),
                "lidos 1204 deve ser formatado como 1.204 em pt-BR");
        assertTrue(ascii.contains("1.187"),
                "sucesso 1187 deve ser formatado como 1.187");
    }

    @Test
    void ascii_contemRegistrosComErro() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        // 17 sem separador de milhar
        assertTrue(ascii.contains("17"),
                "deve conter o valor de erro (17)");
    }

    @Test
    void ascii_contemTotalRespostas() {
        // 4210 + 893 = 5103
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("5.103"),
                "total de respostas deve ser 5.103");
    }

    // ── ASCII — tabelas e artefatos ──────────────────────────────────────────

    @Test
    void ascii_contemNomeTabelaPrincipalTerritorial() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("tribcadastroimobiliario"),
                "deve conter nome da tabela principal territorial");
    }

    @Test
    void ascii_contemNomeTabelaRespostasTerritorial() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("respostaterreno"),
                "deve conter nome da tabela de respostas territorial");
    }

    @Test
    void ascii_contemCaminhoArtefatos() {
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertAll("artefatos",
                () -> assertTrue(ascii.contains("saida-territorial-20260420-143447.sql"),
                        "deve conter caminho do SQL"),
                () -> assertTrue(ascii.contains("saida-territorial-20260420-143447.log"),
                        "deve conter caminho do LOG"));
    }

    @Test
    void ascii_contemDuracaoHHMMSS() {
        // 2026-04-20T17:32:10Z → 2026-04-20T17:34:47Z = 2 min 37 seg = 00:02:37
        String ascii = ResumoRenderer.renderizarAscii(snapshotTerritorial);
        assertTrue(ascii.contains("00:02:37"),
                "duração deve ser formatada como 00:02:37");
    }

    // ── JSON — estrutura e valores ───────────────────────────────────────────

    @Test
    void json_iniciaComEventoResumo() {
        String json = ResumoRenderer.renderizarJsonLine(snapshotTerritorial);
        assertTrue(json.startsWith("{\"evento\":\"resumo\""),
                "JSON deve iniciar com evento:resumo");
    }

    @Test
    void json_contemFluxoTerritorial() {
        String json = ResumoRenderer.renderizarJsonLine(snapshotTerritorial);
        assertTrue(json.contains("\"fluxo\":\"territorial\""),
                "JSON deve conter fluxo territorial em minúsculo");
    }

    @Test
    void json_contemValoresDeContadoresCorretos() {
        String json = ResumoRenderer.renderizarJsonLine(snapshotTerritorial);
        assertAll("contadores no JSON",
                () -> assertTrue(json.contains("\"lidos\":1204"),        "lidos"),
                () -> assertTrue(json.contains("\"sucesso\":1187"),       "sucesso"),
                () -> assertTrue(json.contains("\"erro\":17"),            "erro"),
                () -> assertTrue(json.contains("\"principal_updates\":1187"), "principal_updates"),
                () -> assertTrue(json.contains("\"respostas_update\":4210"),  "respostas_update"),
                () -> assertTrue(json.contains("\"respostas_insert\":893"),   "respostas_insert"),
                () -> assertTrue(json.contains("\"respostas_total\":5103"),   "respostas_total"));
    }

    @Test
    void json_contemDuracaoEmMs() {
        // 2 min 37 seg = 157 seg = 157000 ms
        String json = ResumoRenderer.renderizarJsonLine(snapshotTerritorial);
        assertTrue(json.contains("\"duracao_ms\":157000"),
                "duracao_ms deve ser 157000");
    }

    @Test
    void json_ehLinhaUnica_semQuebra() {
        String json = ResumoRenderer.renderizarJsonLine(snapshotTerritorial);
        assertFalse(json.contains("\n"), "JSON não deve conter \\n");
        assertFalse(json.contains("\r"), "JSON não deve conter \\r");
    }

    @Test
    void json_zeroValoresSaoIncluidos() {
        // snapshotPredial: respostasAtualizadas = 0
        String json = ResumoRenderer.renderizarJsonLine(snapshotPredial);
        assertTrue(json.contains("\"respostas_update\":0"),
                "valor zero deve estar presente no JSON");
    }

    // ── ASCII — fluxo predial usa tabelas corretas ───────────────────────────

    @Nested
    class FluxoPredial {

        @Test
        void ascii_contemTabelaPrincipalPredial() {
            String ascii = ResumoRenderer.renderizarAscii(snapshotPredial);
            assertTrue(ascii.contains("tribimobiliariosegmento"),
                    "deve conter tabela principal predial");
        }

        @Test
        void ascii_contemTabelaRespostasPredial() {
            String ascii = ResumoRenderer.renderizarAscii(snapshotPredial);
            assertTrue(ascii.contains("respostasegmento"),
                    "deve conter tabela de respostas predial");
        }

        @Test
        void json_contemFluxoPredial() {
            String json = ResumoRenderer.renderizarJsonLine(snapshotPredial);
            assertTrue(json.contains("\"fluxo\":\"predial\""),
                    "JSON deve conter fluxo predial");
        }
    }
}
