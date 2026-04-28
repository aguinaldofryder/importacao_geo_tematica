package br.com.arxcode.tematica.geo.excel;

import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários do {@link ExcelLeitor} / {@link ExcelSessao}.
 *
 * <p>Cobre AC1, AC2, AC3, AC5, AC6, AC7 e AC8 da Story 2.1.
 * AC4 (streaming 100k linhas) é coberto em {@link ExcelLeitorStreamingTest}.
 *
 * <p>Fixtures geradas em runtime via {@link XlsxFixtures} (sem binários versionados).
 */
class ExcelLeitorTest {

    @Test
    @DisplayName("AC1+AC2+AC3: lê XLSX simples com cabeçalhos e linhas como Map<header, valor>")
    void lerXlsxSimples(@TempDir Path tmp) throws IOException {
        Path xlsx = XlsxFixtures.criarXlsx(
                tmp.resolve("simples.xlsx"),
                Arrays.asList("nome", "idade", "ativo"),
                Arrays.asList(
                        Arrays.asList("Alice", "30", "S"),
                        Arrays.asList("Bob", "25", "N")));

        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            // AC2: cabeçalhos preservam ordem
            assertEquals(Arrays.asList("nome", "idade", "ativo"), s.cabecalhos());

            // AC1+AC3: linhas como Map<header, String>, ordem das colunas preservada
            List<Map<String, String>> linhas = s.linhas().toList();
            assertEquals(2, linhas.size());

            Map<String, String> linha0 = linhas.get(0);
            assertEquals("Alice", linha0.get("nome"));
            assertEquals("30", linha0.get("idade"));
            assertEquals("S", linha0.get("ativo"));
            // ordem das chaves
            assertEquals(Arrays.asList("nome", "idade", "ativo"),
                    new java.util.ArrayList<>(linha0.keySet()));

            Map<String, String> linha1 = linhas.get(1);
            assertEquals("Bob", linha1.get("nome"));
            assertEquals("25", linha1.get("idade"));
            assertEquals("N", linha1.get("ativo"));
        }
    }

    @Test
    @DisplayName("AC1: planilha sem linhas de dados — cabeçalhos lidos, linhas() vazio")
    void planilhaSomenteHeader(@TempDir Path tmp) throws IOException {
        Path xlsx = XlsxFixtures.criarXlsx(
                tmp.resolve("apenas-header.xlsx"),
                Arrays.asList("a", "b"),
                List.of());

        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            assertEquals(Arrays.asList("a", "b"), s.cabecalhos());
            assertEquals(0L, s.linhas().count());
        }
    }

    @Test
    @DisplayName("AC1: célula vazia retorna string vazia (\"\"), não null")
    void celulaVaziaRetornaStringVazia(@TempDir Path tmp) throws IOException {
        // Linha com a coluna do meio vazia (null no writer = célula não escrita)
        Path xlsx = XlsxFixtures.criarXlsx(
                tmp.resolve("celula-vazia.xlsx"),
                Arrays.asList("a", "b", "c"),
                Arrays.asList(Arrays.asList("x", null, "z")));

        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            Map<String, String> linha = s.linhas().findFirst().orElseThrow();
            assertEquals("x", linha.get("a"));
            assertEquals("", linha.get("b"));
            assertEquals("z", linha.get("c"));
        }
    }

    @Test
    @DisplayName("AC5: arquivo inexistente → ImportacaoException com mensagem em PT")
    void arquivoInexistente(@TempDir Path tmp) {
        Path inexistente = tmp.resolve("nao-existe.xlsx");
        ImportacaoException ex = assertThrows(ImportacaoException.class,
                () -> ExcelLeitor.abrir(inexistente));
        assertTrue(ex.getMessage().contains("não encontrado"),
                "mensagem deve estar em português: " + ex.getMessage());
    }

    @Test
    @DisplayName("AC6: extensão .xls é rejeitada (apenas .xlsx é suportado)")
    void extensaoXlsRejeitada(@TempDir Path tmp) throws IOException {
        Path xls = tmp.resolve("legacy.xls");
        Files.writeString(xls, "qualquer coisa"); // só precisa existir
        ImportacaoException ex = assertThrows(ImportacaoException.class,
                () -> ExcelLeitor.abrir(xls));
        assertTrue(ex.getMessage().toLowerCase().contains(".xlsx")
                        || ex.getMessage().toLowerCase().contains("formato"),
                "mensagem deve indicar formato inválido: " + ex.getMessage());
    }

    @Test
    @DisplayName("AC6: extensão case-insensitive (.XLSX é aceita)")
    void extensaoMaiusculaAceita(@TempDir Path tmp) throws IOException {
        // Gera um xlsx válido e depois renomeia para .XLSX
        Path origem = XlsxFixtures.criarXlsx(
                tmp.resolve("upper.xlsx"),
                Arrays.asList("h"),
                Arrays.asList(Arrays.asList("v")));
        Path destino = tmp.resolve("UPPER.XLSX");
        Files.move(origem, destino);

        try (ExcelSessao s = ExcelLeitor.abrir(destino)) {
            assertEquals(List.of("h"), s.cabecalhos());
        }
    }

    @Test
    @DisplayName("AC7: arquivo XLSX corrompido → ImportacaoException com cause preservado")
    void xlsxCorrompido(@TempDir Path tmp) throws IOException {
        Path corrompido = tmp.resolve("corrompido.xlsx");
        // Bytes arbitrários — não é um zip válido
        Files.write(corrompido, new byte[]{0x00, 0x01, 0x02, 0x03, 0x04});

        ImportacaoException ex = assertThrows(ImportacaoException.class,
                () -> ExcelLeitor.abrir(corrompido));
        assertNotNull(ex.getCause(), "cause original deve estar preservado para diagnóstico");
        assertTrue(ex.getMessage().toLowerCase().contains("inválido")
                        || ex.getMessage().toLowerCase().contains("corrompido")
                        || ex.getMessage().toLowerCase().contains("falha"),
                "mensagem deve indicar falha de leitura: " + ex.getMessage());
    }

    @Test
    @DisplayName("AC8 (out-of-scope): cabeçalhos duplicados — última coluna vence (Map.put)")
    void cabecalhosDuplicadosUltimaColunaVence(@TempDir Path tmp) throws IOException {
        Path xlsx = XlsxFixtures.criarXlsx(
                tmp.resolve("dup.xlsx"),
                Arrays.asList("nome", "valor", "valor"), // "valor" duplicado
                Arrays.asList(Arrays.asList("Alice", "10", "20")));

        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            // cabeçalhos() preserva a duplicata (lista bruta)
            assertEquals(Arrays.asList("nome", "valor", "valor"), s.cabecalhos());
            // mas o Map<String,String> resultante tem só 2 entradas e mantém o último valor
            Map<String, String> linha = s.linhas().findFirst().orElseThrow();
            assertEquals(2, linha.size());
            assertEquals("Alice", linha.get("nome"));
            assertEquals("20", linha.get("valor"), "Map.put: última coluna vence");
        }
    }

    @Test
    @DisplayName("contrato AutoCloseable: try-with-resources fecha workbook sem exceção")
    void closeable(@TempDir Path tmp) throws IOException {
        Path xlsx = XlsxFixtures.criarXlsx(
                tmp.resolve("close.xlsx"),
                Arrays.asList("h"),
                Arrays.asList(Arrays.asList("v")));

        ExcelSessao s = ExcelLeitor.abrir(xlsx);
        // consome stream e fecha manualmente — não deve lançar
        try (Stream<Map<String, String>> stream = s.linhas()) {
            assertEquals(1L, stream.count());
        }
        s.close();
    }
}
