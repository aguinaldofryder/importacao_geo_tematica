package br.com.arxcode.tematica.geo.geracao;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários de {@link ResumoExecucao} e {@link ResumoSnapshot}
 * (Story 5.1 — AC14). Unitário puro: sem CDI, sem banco, sem I/O de arquivo.
 */
class ResumoExecucaoTest {

    private static final Fluxo FLUXO = Fluxo.TERRITORIAL;
    private static final String PLANILHA = "TABELA_TERRITORIAL_V001.xlsx";
    private static final Path PATH_SQL = Path.of("/saida/saida-territorial-20260503-120000.sql");
    private static final Path PATH_LOG = Path.of("/saida/saida-territorial-20260503-120000.log");

    private ResumoExecucao resumo;

    @BeforeEach
    void setUp() {
        resumo = new ResumoExecucao(FLUXO, PLANILHA);
    }

    // ── Cenário 1: estado inicial ────────────────────────────────────────────

    @Test
    void estadoInicial_todosContadoresZero() {
        assertEquals(0, resumo.totalRespostas(), "totalRespostas deve ser 0 no estado inicial");
        assertEquals(0, resumo.erro(), "erro deve ser 0 no estado inicial");
    }

    // ── Cenário 2: incrementarLido ────────────────────────────────────────────

    @Test
    void incrementarLido_tresChamadas_lidosIgualTres() {
        resumo.incrementarLido();
        resumo.incrementarLido();
        resumo.incrementarLido();

        // Verificar via snapshot após ciclo de vida completo
        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(3, snap.lidos());
        assertEquals(0, snap.sucesso(), "sucesso não deve ser afetado por incrementarLido");
        assertEquals(0, snap.erro(), "erro não deve ser afetado por incrementarLido");
        assertEquals(0, snap.principalAtualizados(), "principalAtualizados não deve ser afetado");
    }

    // ── Cenário 3: registrarSucesso ───────────────────────────────────────────

    @Test
    void registrarSucesso_umaVez_incrementaApenasSucesso() {
        resumo.registrarSucesso();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.sucesso());
        assertEquals(0, snap.lidos());
        assertEquals(0, snap.erro());
        assertEquals(0, snap.principalAtualizados());
        assertEquals(0, snap.respostasAtualizadas());
        assertEquals(0, snap.respostasInseridas());
    }

    // ── Cenário 4: registrarErro ──────────────────────────────────────────────

    @Test
    void registrarErro_umaVez_incrementaApenasErro() {
        resumo.registrarErro();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.erro());
        assertEquals(1, resumo.erro(), "getter erro() deve refletir acumulador");
        assertEquals(0, snap.lidos());
        assertEquals(0, snap.sucesso());
        assertEquals(0, snap.principalAtualizados());
    }

    // ── Cenário 5: registrarUpdatePrincipal ───────────────────────────────────

    @Test
    void registrarUpdatePrincipal_umaVez_incrementaApenasPrincipalAtualizados() {
        resumo.registrarUpdatePrincipal();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.principalAtualizados());
        assertEquals(0, snap.lidos());
        assertEquals(0, snap.sucesso());
        assertEquals(0, snap.erro());
        assertEquals(0, snap.respostasAtualizadas());
        assertEquals(0, snap.respostasInseridas());
    }

    // ── Cenário 6: registrarRespostaAtualizada ─────────────────────────────────

    @Test
    void registrarRespostaAtualizada_umaVez_incrementaApenasRespostasAtualizadas() {
        resumo.registrarRespostaAtualizada();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.respostasAtualizadas());
        assertEquals(0, snap.respostasInseridas());
        assertEquals(0, snap.principalAtualizados());
        assertEquals(0, snap.sucesso());
        assertEquals(0, snap.erro());
    }

    // ── Cenário 7: registrarRespostaInserida ──────────────────────────────────

    @Test
    void registrarRespostaInserida_umaVez_incrementaApenasRespostasInseridas() {
        resumo.registrarRespostaInserida();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.respostasInseridas());
        assertEquals(0, snap.respostasAtualizadas());
        assertEquals(0, snap.principalAtualizados());
        assertEquals(0, snap.sucesso());
        assertEquals(0, snap.erro());
    }

    // ── Cenário 8: totalRespostas ─────────────────────────────────────────────

    @Test
    void totalRespostas_somaDosDoisCampos() {
        resumo.registrarRespostaAtualizada();
        resumo.registrarRespostaAtualizada();
        resumo.registrarRespostaInserida();
        resumo.registrarRespostaInserida();
        resumo.registrarRespostaInserida();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(5, snap.totalRespostas(), "totalRespostas = atualizadas(2) + inseridas(3)");
        assertEquals(5, resumo.totalRespostas(), "totalRespostas no acumulador deve bater");
    }

    // ── Cenário 9: acumulação mista ───────────────────────────────────────────

    @Test
    void acumulacaoMista_todosContadoresCorretos() {
        // 5 lidos, 3 sucesso, 2 erro, 3 updates, 10 inseridas
        for (int i = 0; i < 5; i++) resumo.incrementarLido();
        for (int i = 0; i < 3; i++) resumo.registrarSucesso();
        for (int i = 0; i < 2; i++) resumo.registrarErro();
        for (int i = 0; i < 3; i++) resumo.registrarUpdatePrincipal();
        for (int i = 0; i < 10; i++) resumo.registrarRespostaInserida();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(5, snap.lidos());
        assertEquals(3, snap.sucesso());
        assertEquals(2, snap.erro());
        assertEquals(3, snap.principalAtualizados());
        assertEquals(0, snap.respostasAtualizadas());
        assertEquals(10, snap.respostasInseridas());
        assertEquals(10, snap.totalRespostas());
    }

    // ── Cenário 10: duracao positiva ──────────────────────────────────────────

    @Test
    void duracao_aposIniciarFinalizarComSleep_retornaDuracaoPositiva() throws InterruptedException {
        resumo.iniciar();
        Thread.sleep(15);
        resumo.finalizar();

        Duration d = resumo.duracao();

        assertNotNull(d);
        assertTrue(d.toMillis() >= 10, "Duração deve ser ≥ 10 ms após sleep de 15 ms");
    }

    // ── Cenário 11: duracao antes de finalizar lança exceção ─────────────────

    @Test
    void duracao_semFinalizarAntes_lancaIllegalStateException() {
        resumo.iniciar();
        // finalizar() não chamado

        assertThrows(IllegalStateException.class, resumo::duracao,
                "duracao() deve lançar IllegalStateException antes de finalizar()");
    }

    // ── Cenário 12: toResumoImutavel antes de finalizar lança exceção ─────────

    @Test
    void toResumoImutavel_semFinalizarAntes_lancaIllegalStateException() {
        resumo.iniciar();
        // finalizar() não chamado

        assertThrows(IllegalStateException.class,
                () -> resumo.toResumoImutavel(PATH_SQL, PATH_LOG),
                "toResumoImutavel() deve lançar IllegalStateException antes de finalizar()");
    }

    // ── Cenário 13: toResumoImutavel captura todos os campos ──────────────────

    @Test
    void toResumoImutavel_todosOsCamposCorrespondentes() {
        resumo.incrementarLido();
        resumo.incrementarLido();
        resumo.registrarSucesso();
        resumo.registrarErro();
        resumo.registrarUpdatePrincipal();
        resumo.registrarRespostaInserida();
        resumo.registrarRespostaInserida();
        resumo.registrarRespostaInserida();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertSame(FLUXO, snap.fluxo());
        assertEquals(PLANILHA, snap.nomePlanilha());
        assertNotNull(snap.inicio());
        assertNotNull(snap.fim());
        assertEquals(2, snap.lidos());
        assertEquals(1, snap.sucesso());
        assertEquals(1, snap.erro());
        assertEquals(1, snap.principalAtualizados());
        assertEquals(0, snap.respostasAtualizadas());
        assertEquals(3, snap.respostasInseridas());
        assertSame(PATH_SQL, snap.arquivoSql());
        assertSame(PATH_LOG, snap.arquivoLog());
    }

    // ── Cenário 14: ResumoSnapshot.totalRespostas ─────────────────────────────

    @Test
    void snapshot_totalRespostas_somaDosDoisCampos() {
        resumo.registrarRespostaAtualizada();
        resumo.registrarRespostaInserida();
        resumo.registrarRespostaInserida();

        resumo.iniciar();
        resumo.finalizar();
        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        assertEquals(1, snap.respostasAtualizadas());
        assertEquals(2, snap.respostasInseridas());
        assertEquals(3, snap.totalRespostas(), "1 atualizada + 2 inseridas = 3 total");
    }

    // ── Cenário 15: ResumoSnapshot.duracao ────────────────────────────────────

    @Test
    void snapshot_duracao_igualDurationEntreinicioEFim() throws InterruptedException {
        resumo.iniciar();
        Thread.sleep(10);
        resumo.finalizar();

        ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);

        Duration esperada = Duration.between(snap.inicio(), snap.fim());
        assertEquals(esperada, snap.duracao(), "snapshot.duracao() deve ser Duration.between(inicio, fim)");
    }

    // ── Cenários extras: isolamento de contadores ──────────────────────────────

    @Nested
    class IsolamentoContadores {

        @Test
        void registrarSucesso_naoAfetaErro() {
            resumo.registrarSucesso();
            resumo.registrarSucesso();
            assertEquals(0, resumo.erro());
        }

        @Test
        void registrarErro_naoAfetaSucesso() {
            resumo.registrarErro();
            resumo.registrarErro();

            resumo.iniciar();
            resumo.finalizar();
            ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);
            assertEquals(0, snap.sucesso());
        }

        @Test
        void registrarUpdatePrincipal_naoAfetaRespostas() {
            resumo.registrarUpdatePrincipal();
            resumo.registrarUpdatePrincipal();

            resumo.iniciar();
            resumo.finalizar();
            ResumoSnapshot snap = resumo.toResumoImutavel(PATH_SQL, PATH_LOG);
            assertEquals(0, snap.respostasAtualizadas());
            assertEquals(0, snap.respostasInseridas());
        }
    }
}
