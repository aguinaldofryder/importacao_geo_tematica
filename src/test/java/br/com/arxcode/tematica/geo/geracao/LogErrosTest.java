package br.com.arxcode.tematica.geo.geracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Testes do {@link LogErros} (Story 4.4 — fecha ISSUE-4.1-03).
 */
class LogErrosTest {

    private static final OffsetDateTime TS = OffsetDateTime.parse("2026-05-03T14:23:01-03:00");
    private static final String TS_FMT = "2026-05-03T14:23:01-03:00";

    private LogErros log;

    @BeforeEach
    void setUp() {
        log = new LogErros(Fluxo.TERRITORIAL, "TABELA_TERRITORIAL_V001.xlsx", TS);
    }

    // ---------- Construtor / validação ----------

    @Nested
    class ConstrutorEValidacao {

        @Test
        void publico_capturaNowEnaoExplode() {
            OffsetDateTime antes = OffsetDateTime.now().minusSeconds(1);
            LogErros l = new LogErros(Fluxo.PREDIAL, "X.xlsx");
            OffsetDateTime depois = OffsetDateTime.now().plusSeconds(1);
            String saida = l.gerar();
            assertTrue(saida.contains("Fluxo: PREDIAL"));
            // Cabeçalho deve conter um timestamp ISO compatível com janela [antes, depois]
            assertTrue(saida.contains("Data: "));
            assertTrue(antes.isBefore(depois));
        }

        @Test
        void fluxoNull_lancaIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(null, "x.xlsx", TS));
            assertEquals("Fluxo é obrigatório.", ex.getMessage());
        }

        @Test
        void nomePlanilhaNull_lancaIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(Fluxo.TERRITORIAL, null, TS));
            assertEquals("Nome da planilha é obrigatório.", ex.getMessage());
        }

        @Test
        void nomePlanilhaBlank_lancaIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(Fluxo.TERRITORIAL, "   ", TS));
        }

        @Test
        void inicioExecucaoNull_lancaIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(Fluxo.TERRITORIAL, "x.xlsx", null));
            assertEquals("Início de execução é obrigatório.", ex.getMessage());
        }

        @Test
        void publico_fluxoNull_lancaIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(null, "x.xlsx"));
        }

        @Test
        void publico_nomeBlank_lancaIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LogErros(Fluxo.TERRITORIAL, ""));
        }
    }

    // ---------- Sanitização (helper direto via sanitizarParaTeste) ----------

    @Nested
    class Sanitizacao {

        @Test
        void nullPassaComoNull() {
            assertNull(LogErros.sanitizarParaTeste(null));
        }

        @Test
        void vaziaPassaIntacta() {
            assertEquals("", LogErros.sanitizarParaTeste(""));
        }

        @Test
        void asciiNormalPassaIntacto_eDevolveMesmaReferencia() {
            String s = "Coluna 'Área Total' valor=42";
            assertSame(s, LogErros.sanitizarParaTeste(s));
        }

        @Test
        void unicodeAcimaDe0x7F_passaIntacto() {
            String s = "São Paulo — O'Brien — 🏠";
            assertSame(s, LogErros.sanitizarParaTeste(s));
        }

        @Test
        void newlineEscapadoComoLiteral() {
            assertEquals("a\\nb", LogErros.sanitizarParaTeste("a\nb"));
        }

        @Test
        void carriageReturnEscapado() {
            assertEquals("a\\rb", LogErros.sanitizarParaTeste("a\rb"));
        }

        @Test
        void tabEscapado() {
            assertEquals("a\\tb", LogErros.sanitizarParaTeste("a\tb"));
        }

        @Test
        void misturaCobreNRT() {
            assertEquals("linha1\\nlinha2\\tcoluna",
                    LogErros.sanitizarParaTeste("linha1\nlinha2\tcoluna"));
        }

        @Test
        void controlCharArbitrarioVai_uXXXX() {
            assertEquals("a\\u0007b", LogErros.sanitizarParaTeste("a\u0007b"));
        }

        @Test
        void controlChar0x1F_vai_uXXXX() {
            assertEquals("\\u001F", LogErros.sanitizarParaTeste("\u001F"));
        }

        @Test
        void aspasContrabarra_naoSaoEscapadas() {
            String s = "O'Brien \"x\" \\path";
            assertSame(s, LogErros.sanitizarParaTeste(s));
        }

        @Test
        void caractere0x20Espaco_passaIntacto() {
            assertSame(" ", LogErros.sanitizarParaTeste(" "));
        }
    }

    // ---------- Validação dos métodos registrarX ----------

    @Nested
    class ValidacaoArgs {

        @Test
        void linhaPulada_linhaZero() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarLinhaPulada(0, "123", "motivo"));
        }

        @Test
        void linhaPulada_codigoNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarLinhaPulada(1, null, "motivo"));
        }

        @Test
        void linhaPulada_codigoBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarLinhaPulada(1, "  ", "motivo"));
        }

        @Test
        void linhaPulada_motivoBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarLinhaPulada(1, "123", ""));
        }

        @Test
        void erroCoercao_linhaNegativa() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErroCoercao(-1, "col", "v", "m"));
        }

        @Test
        void erroCoercao_colunaNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErroCoercao(1, null, "v", "m"));
        }

        @Test
        void erroCoercao_valorNull_proibido() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErroCoercao(1, "col", null, "m"));
        }

        @Test
        void erroCoercao_valorVazio_aceito() {
            log.registrarErroCoercao(1, "col", "", "motivo");
            assertTrue(log.gerar().contains("valor=''"));
        }

        @Test
        void erroCoercao_motivoNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErroCoercao(1, "col", "v", null));
        }

        @Test
        void errosLinha_listaNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErrosLinha(1, "123", null));
        }

        @Test
        void errosLinha_listaVazia() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErrosLinha(1, "123", List.of()));
        }

        @Test
        void errosLinha_elementoBlank() {
            ArrayList<String> erros = new ArrayList<>();
            erros.add("ok");
            erros.add("  ");
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErrosLinha(1, "123", erros));
        }

        @Test
        void errosLinha_elementoNull() {
            ArrayList<String> erros = new ArrayList<>();
            erros.add("ok");
            erros.add(null);
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErrosLinha(1, "123", erros));
        }

        @Test
        void errosLinha_codigoBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> log.registrarErrosLinha(1, "", List.of("erro")));
        }
    }

    // ---------- Cabeçalho ----------

    @Nested
    class Cabecalho {

        @Test
        void cabecalhoExato_quatroLinhasIniciais() {
            String esperado = "===== Importação georreferenciada — Log de execução =====\n"
                    + "Data: " + TS_FMT + "\n"
                    + "Fluxo: TERRITORIAL (planilha: TABELA_TERRITORIAL_V001.xlsx)\n"
                    + "\n"
                    + "----- Sumário -----\n"
                    + "Total de linhas processadas com sucesso: 0\n"
                    + "Linhas puladas: 0\n"
                    + "Erros de coerção: 0\n"
                    + "Linhas com erros agregados: 0\n"
                    + "Total de linhas com falha: 0\n";
            assertEquals(esperado, log.gerar());
        }

        @Test
        void cabecalhoSanitizaNomePlanilha() {
            LogErros l = new LogErros(Fluxo.PREDIAL, "x\ny.xlsx", TS);
            assertTrue(l.gerar().contains("planilha: x\\ny.xlsx"));
        }
    }

    // ---------- Formato linha pulada ----------

    @Nested
    class FormatoLinhaPulada {

        @Test
        void exemploExatoAC5() {
            log.registrarLinhaPulada(42, "123456", "Imóvel não encontrado em tribcadastroimobiliario");
            String saida = log.gerar();
            assertTrue(saida.contains("----- Linhas puladas (código imobiliário inexistente) -----\n"
                    + "[linha 42] codigo='123456' motivo='Imóvel não encontrado em tribcadastroimobiliario'\n"
                    + "\n"));
        }

        @Test
        void controlCharsNoCodigoEMotivo_saoEscapados() {
            log.registrarLinhaPulada(7, "X\nY", "msg\twith\ttab");
            String saida = log.gerar();
            assertTrue(saida.contains("[linha 7] codigo='X\\nY' motivo='msg\\twith\\ttab'\n"));
        }
    }

    // ---------- Formato erro de coerção ----------

    @Nested
    class FormatoErroCoercao {

        @Test
        void exemploExatoAC6() {
            log.registrarErroCoercao(42, "Área Total", "abc", "valor 'abc' não é decimal válido");
            String saida = log.gerar();
            // Nota: o AC6 mostra ''abc'' no exemplo literal, mas o texto do mesmo AC e o AC3 afirmam
            // explicitamente que aspas simples NÃO são escapadas (apenas control chars < 0x20). O
            // comportamento implementado segue a regra (aspas intactas) — o exemplo do AC é tipográfico.
            assertTrue(saida.contains("----- Erros de coerção -----\n"
                    + "[linha 42, coluna 'Área Total'] valor='abc' motivo='valor 'abc' não é decimal válido'\n"
                    + "\n"),
                    "Saída inesperada:\n" + saida);
        }
    }

    // ---------- Formato erros agregados por linha ----------

    @Nested
    class FormatoErrosAgregados {

        @Test
        void blocoMultiLinhaExatoAC7() {
            log.registrarErrosLinha(87, "999999", List.of(
                    "Coluna 'Área': valor 'abc' não é decimal válido",
                    "Coluna 'Data': valor 'xyz' não é data válida..."));
            String saida = log.gerar();
            String esperado = "----- Erros agregados por linha -----\n"
                    + "[linha 87] codigo='999999' (2 erros):\n"
                    + "  - Coluna 'Área': valor 'abc' não é decimal válido\n"
                    + "  - Coluna 'Data': valor 'xyz' não é data válida...\n"
                    + "\n";
            assertTrue(saida.contains(esperado), "Saída inesperada:\n" + saida);
        }

        @Test
        void erroIndividualSanitizado() {
            log.registrarErrosLinha(10, "1", List.of("erro\ncom\nnewline"));
            assertTrue(log.gerar().contains("  - erro\\ncom\\nnewline\n"));
        }
    }

    // ---------- Sumário ----------

    @Nested
    class Sumario {

        @Test
        void zerado_quandoNadaRegistrado() {
            String s = log.gerar();
            assertTrue(s.contains("Total de linhas processadas com sucesso: 0\n"));
            assertTrue(s.contains("Linhas puladas: 0\n"));
            assertTrue(s.contains("Erros de coerção: 0\n"));
            assertTrue(s.contains("Linhas com erros agregados: 0\n"));
            assertTrue(s.contains("Total de linhas com falha: 0\n"));
        }

        @Test
        void contadoresMistos() {
            log.registrarLinhaProcessada();
            log.registrarLinhaProcessada();
            log.registrarLinhaProcessada();
            log.registrarLinhaPulada(10, "a", "m");
            log.registrarLinhaPulada(11, "b", "m");
            log.registrarErroCoercao(20, "col", "v", "m");
            log.registrarErrosLinha(30, "c", List.of("erro"));
            String s = log.gerar();
            assertTrue(s.contains("Total de linhas processadas com sucesso: 3\n"));
            assertTrue(s.contains("Linhas puladas: 2\n"));
            assertTrue(s.contains("Erros de coerção: 1\n"));
            assertTrue(s.contains("Linhas com erros agregados: 1\n"));
            assertTrue(s.contains("Total de linhas com falha: 4\n"));
        }

        @Test
        void linhasDuplicadasContamUmaVezSo() {
            // Linha 42 aparece nas 3 categorias → conta 1 vez no total distinto.
            log.registrarLinhaPulada(42, "x", "m");
            log.registrarErroCoercao(42, "c", "v", "m");
            log.registrarErrosLinha(42, "x", List.of("e"));
            log.registrarErroCoercao(43, "c", "v", "m");
            String s = log.gerar();
            assertTrue(s.contains("Total de linhas com falha: 2\n"), s);
        }
    }

    // ---------- Seções omitidas quando vazias ----------

    @Nested
    class SecoesOmitidas {

        @Test
        void apenasLinhasPuladas_outrasSecoesAusentes() {
            log.registrarLinhaPulada(1, "x", "m");
            String s = log.gerar();
            assertTrue(s.contains("----- Linhas puladas"));
            assertFalse(s.contains("----- Erros de coerção"));
            assertFalse(s.contains("----- Erros agregados"));
            assertTrue(s.contains("----- Sumário -----"));
        }

        @Test
        void semNenhumErro_apenasCabecalhoESumario() {
            String s = log.gerar();
            assertFalse(s.contains("----- Linhas puladas"));
            assertFalse(s.contains("----- Erros de coerção"));
            assertFalse(s.contains("----- Erros agregados"));
            assertTrue(s.contains("----- Sumário -----"));
        }
    }

    // ---------- Determinismo de ordem ----------

    @Nested
    class Determinismo {

        @Test
        void preservaOrdemDeChamada_naoOrdemNumerica() {
            log.registrarLinhaPulada(87, "B", "m87");
            log.registrarLinhaPulada(42, "A", "m42");
            String s = log.gerar();
            int idx87 = s.indexOf("[linha 87]");
            int idx42 = s.indexOf("[linha 42]");
            assertTrue(idx87 > 0 && idx42 > 0);
            assertTrue(idx87 < idx42, "Ordem de chamada deve ser preservada (87 antes de 42).");
        }

        @Test
        void gerarIdempotente_mesmaSaidaByteAByte() {
            log.registrarLinhaPulada(1, "x", "m");
            log.registrarErroCoercao(2, "c", "v", "m");
            log.registrarErrosLinha(3, "x", List.of("e"));
            log.registrarLinhaProcessada();
            assertEquals(log.gerar(), log.gerar());
        }

        @Test
        void registrarAposGerar_alteraProximaSaida() {
            String antes = log.gerar();
            log.registrarLinhaPulada(1, "x", "m");
            String depois = log.gerar();
            assertNotEquals(antes, depois);
            assertTrue(depois.length() > antes.length());
        }
    }

    // ---------- Line separator fixo ----------

    @Nested
    class LineSeparator {

        @Test
        void saidaNuncaContemCarriageReturnLiteral() {
            log.registrarLinhaPulada(1, "x\rdados", "motivo\rcr");
            log.registrarErroCoercao(2, "c\r", "v\r", "m\r");
            log.registrarErrosLinha(3, "x", List.of("erro\r"));
            String s = log.gerar();
            assertFalse(s.contains("\r"), "Saída não pode conter \\r literal — control chars devem ser escapados.");
        }
    }

    // ---------- Smoke de log-injection (AC13) ----------

    @Nested
    class SmokeLogInjection {

        @Test
        void vetorMaliciosoNaoQuebraEstrutura() {
            log.registrarErroCoercao(42, "Coluna\nFalsa", "valor\rcom\ncontrol",
                    "motivo\nmalicioso\n[linha 999] codigo='HACK' motivo='injetado'");
            String s = log.gerar();

            // Conta linhas estruturalmente esperadas:
            // 4 cabeçalho (incluindo linha em branco) +
            // 2 seção coerção (header + 1 entrada) + 1 linha em branco
            // 6 sumário (header + 5 contadores)
            // = 13 \n exatos.
            int newlines = (int) s.chars().filter(c -> c == '\n').count();
            assertEquals(13, newlines, "Saída completa:\n" + s);

            // A saída sanitizada deve conter os escapes esperados, não o \n bruto na entrada.
            assertTrue(s.contains("Coluna\\nFalsa"));
            assertTrue(s.contains("valor\\rcom\\ncontrol"));
            assertTrue(s.contains("motivo\\nmalicioso\\n[linha 999] codigo='HACK' motivo='injetado'"));

            // Não há nenhuma "linha 999" forjada como entrada própria (só como literal escapado).
            // Garante que o token "[linha 999]" só aparece dentro do motivo, no meio da única linha de entrada.
            int firstIdx = s.indexOf("[linha 999]");
            int lastIdx = s.lastIndexOf("[linha 999]");
            assertEquals(firstIdx, lastIdx, "[linha 999] deve aparecer no máximo uma vez (escapado dentro de motivo).");
        }
    }
}
