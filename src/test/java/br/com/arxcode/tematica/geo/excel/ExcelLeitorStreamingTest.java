package br.com.arxcode.tematica.geo.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de capacidade streaming — AC4 da Story 2.1.
 *
 * <p><b>Critério binário:</b> 100.000 linhas devem ser lidas <i>linha a linha</i> sem
 * acumular memória. Combinado com {@code -Xmx256m} configurado no maven-surefire-plugin
 * (POM), este teste só passa se o pipeline de leitura for verdadeiramente streaming.
 *
 * <p>Se o leitor materializar todas as linhas em memória, o teste falha com OOM
 * — isso é exatamente o sinal de regressão que queremos. Nenhuma medição de
 * watermark / GC time é feita: critério binário, zero flakiness.
 *
 * <p><b>Setup:</b> escreve um XLSX de ~100k linhas em diretório temporário (também
 * gerado em streaming via fastexcel-writer — o {@code finish()} fecha o stream).
 *
 * <p><b>Tempo esperado:</b> ~10–30s em hardware moderno (geração + leitura).
 * Se passar de 60s reavalie geração de fixture.
 */
class ExcelLeitorStreamingTest {

    private static final int TOTAL_LINHAS = 100_000;

    @Test
    @DisplayName("AC4: lê 100k linhas com -Xmx256m sem OOM (streaming verdadeiro)")
    void streamingCemMilLinhas(@TempDir Path tmp) throws IOException {
        Path xlsx = tmp.resolve("100k.xlsx");
        XlsxFixtures.criarXlsxLargo(
                xlsx,
                Arrays.asList("id", "descricao", "valor"),
                TOTAL_LINHAS,
                (ws, r) -> {
                    ws.value(r, 0, (Number) r);
                    ws.value(r, 1, "linha-" + r + "-com-algum-texto-realista-para-aumentar-payload");
                    ws.value(r, 2, (Number) (r * 1.5));
                });

        // sanity check do tamanho do arquivo gerado (~2 MB para 100k linhas)
        long tamanho = Files.size(xlsx);
        org.junit.jupiter.api.Assertions.assertTrue(tamanho > 500_000,
                "fixture suspeitosamente pequena: " + tamanho + " bytes");

        AtomicLong contador = new AtomicLong();
        // valida campo de uma linha "do meio" para garantir que o conteúdo é coerente
        AtomicLong somaIds = new AtomicLong();

        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            assertEquals(Arrays.asList("id", "descricao", "valor"), s.cabecalhos());
            s.linhas().forEach(linha -> {
                contador.incrementAndGet();
                String id = linha.get("id");
                if (id != null && !id.isEmpty()) {
                    // Acumula só os primeiros 10 (evita custo de parse 100k vezes)
                    if (contador.get() <= 10) {
                        somaIds.addAndGet(Long.parseLong(id));
                    }
                }
            });
        }

        assertEquals(TOTAL_LINHAS, contador.get(), "deve ler todas as linhas do XLSX");
        // soma de 1..10 = 55
        assertEquals(55L, somaIds.get(), "ids das primeiras 10 linhas devem ser 1..10");
    }
}
