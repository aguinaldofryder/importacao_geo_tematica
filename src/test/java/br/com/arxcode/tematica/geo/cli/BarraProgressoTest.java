package br.com.arxcode.tematica.geo.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários para {@link BarraProgresso}.
 *
 * <p>Usa {@link StringWriter} como stderr falso, evitando dependência de TTY real.
 * O método package-private {@code construirBarra(int, int)} é testado diretamente,
 * independentemente do guard {@code ativo} (que depende de {@code System.console()}).
 *
 * <p>Story: 5.3 — Barra de progresso durante a importação (AC10).
 */
class BarraProgressoTest {

    private BarraProgresso barra;

    @BeforeEach
    void setUp() {
        // totalLinhas=100; PrintWriter sobre StringWriter evita dependência de TTY
        barra = new BarraProgresso(100, new PrintWriter(new StringWriter()));
    }

    /**
     * Cenário 1 (AC10.1): linhaAtual=0 → 0%, bloco vazio com cursor no início.
     */
    @Test
    void construirBarra_zeroPorcento_mostraContadorECursorNoInicio() {
        String resultado = barra.construirBarra(0, 0);

        assertTrue(resultado.contains("0/100"), "deve conter '0/100'");
        assertTrue(resultado.contains("(0%)"), "deve conter '(0%)'");
        assertTrue(resultado.contains("erros: 0"), "deve conter 'erros: 0'");
        // barra deve começar com \r
        assertTrue(resultado.startsWith("\r"), "deve iniciar com \\r");
        // cursor '>' deve estar presente (0% < 100%)
        assertTrue(resultado.contains(">"), "deve conter cursor '>'");
        // nenhum '=' de preenchimento (blocoPreenchido = 0)
        assertFalse(resultado.contains("="), "não deve conter '=' quando 0%");
    }

    /**
     * Cenário 2 (AC10.2): linhaAtual=50 → 50%, barra metade preenchida.
     */
    @Test
    void construirBarra_cinquentaPorcento_barraMétadePreenchida() {
        String resultado = barra.construirBarra(50, 0);

        assertTrue(resultado.contains("50/100"), "deve conter '50/100'");
        assertTrue(resultado.contains("(50%)"), "deve conter '(50%)'");
        assertTrue(resultado.contains("erros: 0"), "deve conter 'erros: 0'");
        // deve ter '=' de preenchimento
        assertTrue(resultado.contains("="), "deve conter '=' de preenchimento");
        // cursor '>' presente (50% < 100%)
        assertTrue(resultado.contains(">"), "deve conter cursor '>'");
    }

    /**
     * Cenário 3 (AC10.3): linhaAtual=100 → 100%, barra cheia sem cursor.
     */
    @Test
    void construirBarra_cemPorcento_barraCheiaSemCursor() {
        String resultado = barra.construirBarra(100, 0);

        assertTrue(resultado.contains("100/100"), "deve conter '100/100'");
        assertTrue(resultado.contains("(100%)"), "deve conter '(100%)'");
        assertTrue(resultado.contains("erros: 0"), "deve conter 'erros: 0'");
        // barra cheia: '=' presentes
        assertTrue(resultado.contains("="), "deve conter '=' na barra cheia");
        // sem cursor a 100%
        assertFalse(resultado.contains(">"), "não deve conter '>' quando 100%");
    }

    /**
     * Cenário 4 (AC10.4): erros > 0 aparecem corretamente no texto.
     */
    @Test
    void construirBarra_comErros_exibeContadorDeErros() {
        String resultado = barra.construirBarra(30, 5);

        assertTrue(resultado.contains("30/100"), "deve conter '30/100'");
        assertTrue(resultado.contains("erros: 5"), "deve conter 'erros: 5'");
    }

    /**
     * Cenário 5 (AC10.5): totalLinhas=0 → construtor não lança exceção;
     * atualizar(0, 0) é no-op (guard ativo=false por totalLinhas==0).
     */
    @Test
    void construtor_totalLinhasZero_naoLancaExcecao() {
        StringWriter sw = new StringWriter();
        BarraProgresso barraVazia = new BarraProgresso(0, new PrintWriter(sw));

        assertDoesNotThrow(() -> barraVazia.atualizar(0, 0),
                "atualizar() não deve lançar exceção com totalLinhas=0");
        assertDoesNotThrow(() -> barraVazia.finalizar(),
                "finalizar() não deve lançar exceção com totalLinhas=0");
        // nada foi escrito (no-op)
        assertTrue(sw.toString().isEmpty(), "nenhum conteúdo deve ser escrito quando ativo=false");
    }

    /**
     * Cenário extra: formato geral — colchetes, espaçamento, estrutura.
     */
    @Test
    void construirBarra_formatoGeral_possuiColchetesEEspacamento() {
        String resultado = barra.construirBarra(10, 2);

        assertTrue(resultado.contains("["), "deve conter '['");
        assertTrue(resultado.contains("]"), "deve conter ']'");
        assertTrue(resultado.contains("erros: 2"), "deve conter 'erros: 2'");
    }
}
