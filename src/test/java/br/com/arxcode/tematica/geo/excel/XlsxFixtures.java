package br.com.arxcode.tematica.geo.excel;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Helper de testes para gerar arquivos {@code .xlsx} sintéticos via fastexcel-writer.
 *
 * <p>Usado pela Story 2.1 para evitar binários versionados em {@code src/test/resources}.
 */
final class XlsxFixtures {

    private XlsxFixtures() {
    }

    /**
     * Gera um XLSX simples a partir de cabeçalhos + linhas de strings.
     */
    static Path criarXlsx(Path destino, List<String> headers, List<List<String>> linhas) throws IOException {
        try (OutputStream os = Files.newOutputStream(destino);
             Workbook wb = new Workbook(os, "importacao-geo-test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < headers.size(); c++) {
                ws.value(0, c, headers.get(c));
            }
            for (int r = 0; r < linhas.size(); r++) {
                List<String> linha = linhas.get(r);
                for (int c = 0; c < linha.size(); c++) {
                    String v = linha.get(c);
                    if (v != null) {
                        ws.value(r + 1, c, v);
                    }
                }
            }
            wb.finish();
        }
        return destino;
    }

    /**
     * Gera um XLSX programaticamente para cenários complexos (uma célula por chamada).
     *
     * @param totalLinhas número total de linhas além do cabeçalho
     * @param headers     cabeçalhos
     * @param escritor    callback {@code (worksheet, rowIndex)} aplicado para cada linha de dados
     *                    (rowIndex é 1-based em relação ao header — ou seja, primeira linha de dados é 1)
     */
    static Path criarXlsxLargo(Path destino, List<String> headers, int totalLinhas,
                               BiConsumer<Worksheet, Integer> escritor) throws IOException {
        try (OutputStream os = Files.newOutputStream(destino);
             Workbook wb = new Workbook(os, "importacao-geo-test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet1");
            for (int c = 0; c < headers.size(); c++) {
                ws.value(0, c, headers.get(c));
            }
            for (int r = 1; r <= totalLinhas; r++) {
                escritor.accept(ws, r);
            }
            wb.finish();
        }
        return destino;
    }
}
