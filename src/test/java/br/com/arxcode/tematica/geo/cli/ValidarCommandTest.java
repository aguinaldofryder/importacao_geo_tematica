package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.mapeamento.ColunaDinamica;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.StatusMapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes E2E do {@link ValidarCommand} em JVM com {@code @QuarkusTest}
 * (Story 3.5 AC13). Sem {@code PostgresResource} — comando offline (AC4).
 *
 * <p>Cobre 8 cenários AC13: happy path, arquivo inexistente, default
 * {@code ./mapping.json}, JSON corrompido, JSON parcial, 1 PENDENTE,
 * múltiplas pendências em headers distintos, múltiplas pendências no
 * mesmo header.
 */
@QuarkusTest
class ValidarCommandTest {

    @Inject
    CommandLine.IFactory factory;

    @Inject
    MapeamentoStore mapeamentoStore;

    /**
     * {@link ValidarCommand} é {@code @Dependent} — esta instância é reusada
     * entre testes JUnit. Funciona porque picocli reaplica {@code defaultValue}
     * em cada {@code execute()}. Se algum dia for adicionado um {@code @Option}
     * sem {@code defaultValue}, considerar criar uma instância por teste via
     * {@code factory.create(ValidarCommand.class)} para evitar herança de
     * estado entre testes (revisão @qa Story 3.5).
     */
    @Inject
    ValidarCommand command;

    /** Captura {stdout, stderr, exitCode} de uma invocação do comando. */
    private record Saida(int exit, String stdout, String stderr) {}

    private Saida executar(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(outBuf, true, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(errBuf, true, StandardCharsets.UTF_8);

        int exit = new CommandLine(command, factory)
                .setOut(out)
                .setErr(err)
                .execute(args);
        return new Saida(exit,
                outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8));
    }

    /** Fixture: mapeamento territorial completo (2 fixas + 3 dinâmicas MAPEADAS). */
    private Mapeamento mapeamentoCompleto() {
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("AREA_TERRENO",
                new ColunaDinamica(StatusMapeamento.MAPEADO, 10, Tipo.TEXTO, null, null, null));
        dinamicas.put("TESTADA",
                new ColunaDinamica(StatusMapeamento.MAPEADO, 11, Tipo.DECIMAL, null, null, null));
        Map<String, Integer> alternativas = new LinkedHashMap<>();
        alternativas.put("BAIXO", 101);
        alternativas.put("ALTO", 102);
        dinamicas.put("TIPO_MURO",
                new ColunaDinamica(StatusMapeamento.MAPEADO, 12, Tipo.MULTIPLA_ESCOLHA, alternativas, null, null));

        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("PROPRIETARIO", "tribcadastroimobiliario.proprietario");
        fixas.put("CADASTRO", "tribcadastroimobiliario.cadastro");

        return new Mapeamento(Fluxo.TERRITORIAL, "TABELA_TERRITORIAL_V001.xlsx",
                "tribcadastrogeral_idkey", fixas, dinamicas);
    }

    // ------------------------------------------------------------------
    // (a) happy path
    // ------------------------------------------------------------------
    @Test
    void deveRetornarZeroQuandoMapeamentoCompleto(@TempDir Path tmp) throws Exception {
        Path arq = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(mapeamentoCompleto(), arq);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(0, s.exit(), "exit deve ser 0; stderr=" + s.stderr());
        assertTrue(s.stdout().contains("✓ Mapeamento completo. Pronto para importar."),
                "stdout deve conter mensagem de sucesso. stdout=" + s.stdout());
        assertTrue(s.stderr().isEmpty(), "stderr deve estar vazio. stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (b) arquivo inexistente
    // ------------------------------------------------------------------
    @Test
    void deveRetornarUmQuandoArquivoNaoExiste(@TempDir Path tmp) {
        Path arq = tmp.resolve("nao-existe.json");

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("Arquivo de mapeamento não encontrado"),
                "stderr=" + s.stderr());
        assertTrue(s.stderr().contains(arq.toAbsolutePath().normalize().toString()),
                "stderr deve conter caminho absoluto. stderr=" + s.stderr());
        assertTrue(s.stdout().isEmpty(), "stdout deve estar vazio. stdout=" + s.stdout());
    }

    // ------------------------------------------------------------------
    // (c) default ./mapping.json (sem --mapeamento)
    // ------------------------------------------------------------------
    @Test
    void deveUsarMappingJsonNoDiretorioCorrenteQuandoOpcaoOmitida() {
        // Determinístico: skipa se algum dev tem ./mapping.json no CWD do
        // projeto (cenário plausível durante quarkus:dev). Sem o skip, o
        // teste degradaria para uma disjunção fraca que poderia esconder
        // regressão real do defaultValue (revisão @qa Story 3.5).
        Path defaultPath = Path.of("./mapping.json").toAbsolutePath().normalize();
        Assumptions.assumeFalse(Files.exists(defaultPath),
                "Skipado: existe " + defaultPath + " no CWD; remova para rodar este teste.");

        Saida s = executar();

        assertEquals(1, s.exit(), "exit deve ser 1 (arquivo default ausente). stderr=" + s.stderr());
        assertTrue(s.stderr().contains("mapping.json"),
                "stderr deve mencionar mapping.json — confirma que defaultValue está ativo. stderr=" + s.stderr());
        assertTrue(s.stderr().contains("Arquivo de mapeamento não encontrado"),
                "stderr deve usar a mensagem da etapa (1). stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (d) JSON corrompido
    // ------------------------------------------------------------------
    @Test
    void deveRetornarUmQuandoJsonCorrompido(@TempDir Path tmp) throws Exception {
        Path arq = tmp.resolve("mapping.json");
        Files.writeString(arq, "not a json", StandardCharsets.UTF_8);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("Mapeamento inválido"),
                "stderr deve indicar mapeamento inválido. stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (e) JSON parcial — campo obrigatório ausente
    // ------------------------------------------------------------------
    @Test
    void deveRetornarUmQuandoCampoObrigatorioAusente(@TempDir Path tmp) throws Exception {
        Path arq = tmp.resolve("mapping.json");
        Files.writeString(arq, "{\"fluxo\":\"TERRITORIAL\"}", StandardCharsets.UTF_8);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("Mapeamento inválido"),
                "stderr deve indicar mapeamento inválido. stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (f) 1 PENDENTE
    // ------------------------------------------------------------------
    @Test
    void deveRetornarUmQuandoUmaPendenciaEncontrada(@TempDir Path tmp) throws Exception {
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("COLUNA_X",
                new ColunaDinamica(StatusMapeamento.PENDENTE, null, null, null,
                        "Sem correspondência no catálogo", null));
        Mapeamento m = new Mapeamento(Fluxo.TERRITORIAL, "x.xlsx", "id",
                Map.of(), dinamicas);

        Path arq = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(m, arq);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("✗ Mapeamento incompleto. 1 pendência(s) encontrada(s):"),
                "stderr=" + s.stderr());
        assertTrue(s.stderr().contains("COLUNA_X"), "stderr deve listar header. stderr=" + s.stderr());
        assertTrue(s.stderr().contains("Sem correspondência no catálogo"),
                "stderr deve listar motivo. stderr=" + s.stderr());
        assertTrue(s.stderr().contains("Edite o arquivo manualmente (FR-08)"),
                "stderr deve conter rodapé. stderr=" + s.stderr());
        assertTrue(s.stderr().contains(arq.toAbsolutePath().normalize().toString()),
                "rodapé deve conter caminho absoluto. stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (g) múltiplas pendências em headers distintos
    // ------------------------------------------------------------------
    @Test
    void deveRetornarUmComMultiplasPendenciasEmHeadersDistintos(@TempDir Path tmp) throws Exception {
        // 1 fixa null + 1 dinâmica PENDENTE + 1 dinâmica MULTIPLA_ESCOLHA com alternativa null = 3 pendências
        Map<String, String> fixas = new LinkedHashMap<>();
        fixas.put("FIXA_VAZIA", null);

        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("PENDENTE_X",
                new ColunaDinamica(StatusMapeamento.PENDENTE, null, null, null,
                        "Não localizado no catálogo", null));
        Map<String, Integer> alternativas = new LinkedHashMap<>();
        alternativas.put("BAIXO", null);
        dinamicas.put("MULT_Y",
                new ColunaDinamica(StatusMapeamento.MAPEADO, 99, Tipo.MULTIPLA_ESCOLHA, alternativas, null, null));

        Mapeamento m = new Mapeamento(Fluxo.TERRITORIAL, "x.xlsx", "id", fixas, dinamicas);
        Path arq = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(m, arq);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("3 pendência(s)"), "stderr=" + s.stderr());
        assertTrue(s.stderr().contains("FIXA_VAZIA"), "stderr=" + s.stderr());
        assertTrue(s.stderr().contains("PENDENTE_X"), "stderr=" + s.stderr());
        assertTrue(s.stderr().contains("MULT_Y"), "stderr=" + s.stderr());
    }

    // ------------------------------------------------------------------
    // (h) múltiplas pendências no MESMO header (agrupamento)
    // ------------------------------------------------------------------
    @Test
    void deveAgruparMultiplasPendenciasDoMesmoHeaderSobUmUnicoBullet(@TempDir Path tmp) throws Exception {
        // Coluna MULTIPLA_ESCOLHA MAPEADA com 3 alternativas null → 3 pendências sob o MESMO header
        Map<String, Integer> alternativas = new LinkedHashMap<>();
        alternativas.put("A", null);
        alternativas.put("B", null);
        alternativas.put("C", null);
        Map<String, ColunaDinamica> dinamicas = new LinkedHashMap<>();
        dinamicas.put("HEADER_REPETIDO",
                new ColunaDinamica(StatusMapeamento.MAPEADO, 50, Tipo.MULTIPLA_ESCOLHA, alternativas, null, null));

        Mapeamento m = new Mapeamento(Fluxo.TERRITORIAL, "x.xlsx", "id", Map.of(), dinamicas);
        Path arq = tmp.resolve("mapping.json");
        mapeamentoStore.salvar(m, arq);

        Saida s = executar("--mapeamento", arq.toString());

        assertEquals(1, s.exit());
        assertTrue(s.stderr().contains("3 pendência(s)"), "stderr=" + s.stderr());
        // Header aparece UMA única vez como bullet "  - HEADER_REPETIDO:"
        int ocorrencias = contarOcorrencias(s.stderr(), "  - HEADER_REPETIDO:");
        assertEquals(1, ocorrencias,
                "header deve aparecer 1x como bullet, mas apareceu " + ocorrencias + "x. stderr=" + s.stderr());
        // E os 3 motivos devem aparecer (cada alternativa)
        assertTrue(s.stderr().contains("'A'"), "stderr deve conter alternativa A. stderr=" + s.stderr());
        assertTrue(s.stderr().contains("'B'"), "stderr deve conter alternativa B. stderr=" + s.stderr());
        assertTrue(s.stderr().contains("'C'"), "stderr deve conter alternativa C. stderr=" + s.stderr());
        // Não deve haver duplicação visual do prefixo "Coluna 'HEADER_REPETIDO':" sob o bullet
        assertFalse(s.stderr().contains("Coluna 'HEADER_REPETIDO':"),
                "prefixo 'Coluna X:' deve ter sido strippado. stderr=" + s.stderr());
    }

    private static int contarOcorrencias(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
