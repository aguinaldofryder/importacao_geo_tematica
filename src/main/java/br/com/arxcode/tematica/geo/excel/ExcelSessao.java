package br.com.arxcode.tematica.geo.excel;

import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sessão de leitura de uma planilha {@code .xlsx} em modo streaming.
 *
 * <p>Encapsula o {@link ReadableWorkbook} do fastexcel-reader e a {@link Sheet}
 * (sempre a primeira aba — {@link ReadableWorkbook#getFirstSheet()}). Os cabeçalhos
 * são lidos eagerly no construtor (primeira linha consumida); o restante das linhas
 * é exposto via {@link #linhas()} como {@link Stream}, mantendo a leitura linha-a-linha.
 *
 * <p>É {@link AutoCloseable} — uso recomendado:
 * <pre>{@code
 * try (ExcelSessao s = ExcelLeitor.abrir(Paths.get("planilha.xlsx"))) {
 *     List<String> headers = s.cabecalhos();
 *     s.linhas().forEach(row -> { ... });
 * }
 * }</pre>
 *
 * <p><b>Coerção de tipos:</b> os valores são retornados como {@code String} bruta
 * via {@link Row#getCellRawValue(int)} — preserva o texto original do XML, sem aplicar
 * formatação ou coerção. A coerção (TEXTO/DECIMAL/DATA/MULTIPLA_ESCOLHA) é responsabilidade
 * da Story 4.1. Células ausentes ou vazias são representadas como {@code ""} (string vazia).
 *
 * <p><b>Cabeçalhos duplicados:</b> caso a primeira linha contenha valores repetidos,
 * o {@link Map} resultante de {@link #linhas()} mantém apenas o último valor
 * (comportamento natural do {@link Map#put}). Vide AC8 da Story 2.1 — saneamento
 * fica fora de escopo.
 *
 * <p>Story: 2.1 — leitura streaming, suporta ≥ 100k linhas sem esgotar memória (NFR-04).
 */
public final class ExcelSessao implements AutoCloseable {

    private final ReadableWorkbook workbook;
    private final Sheet sheet;
    private final List<String> cabecalhos;

    ExcelSessao(Path xlsx) {
        try {
            this.workbook = new ReadableWorkbook(xlsx.toFile());
        } catch (IOException e) {
            throw new ImportacaoException(
                    "Falha ao abrir a planilha (arquivo inválido ou corrompido): " + xlsx, e);
        }
        try {
            this.sheet = workbook.getFirstSheet();
            this.cabecalhos = lerCabecalhos(sheet);
        } catch (IOException e) {
            // qualquer erro de IO ao ler cabeçalhos: fechar workbook e propagar
            try {
                workbook.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new ImportacaoException(
                    "Falha ao ler cabeçalhos da planilha: " + xlsx, e);
        }
    }

    /**
     * Cabeçalhos da planilha (primeira linha da primeira aba).
     *
     * @return lista imutável; ordem preservada conforme as colunas no XLSX
     */
    public List<String> cabecalhos() {
        return List.copyOf(cabecalhos);
    }

    /**
     * Stream de linhas (excluindo o cabeçalho) como {@code Map<header, valor-bruto>}.
     *
     * <p>O stream é <b>uma vez consumível</b> — não tente iterar duas vezes. Para
     * liberar recursos, feche a sessão (try-with-resources).
     *
     * <p><b>Nota de implementação:</b> internamente, este método chama
     * {@code sheet.openStream()} e descarta a primeira linha (cabeçalho) via {@code skip(1)}.
     * O fastexcel-reader precisa varrer o XML do início mesmo para chegar à linha 2,
     * então existe um custo fixo equivalente a re-parsear o cabeçalho. Como a Story 2.1
     * só lê uma vez por sessão (cabeçalhos no construtor + linhas aqui), este custo é
     * desprezível na prática (~poucos ms para arquivos típicos).
     *
     * @return stream lazy; cada elemento é um {@link LinkedHashMap} preservando ordem
     *         dos cabeçalhos
     * @throws ImportacaoException se a leitura do stream falhar
     */
    public Stream<Map<String, String>> linhas() {
        Stream<Row> stream;
        try {
            stream = sheet.openStream();
        } catch (IOException e) {
            throw new ImportacaoException("Falha ao abrir stream de linhas da planilha.", e);
        }
        // primeira linha (cabeçalho) já foi consumida em lerCabecalhos? Não — openStream()
        // sempre começa da linha 1 (rowNum 1, 1-indexed). Precisamos pular o header.
        return stream.skip(1).map(this::linhaParaMapa);
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException e) {
            throw new ImportacaoException("Falha ao fechar a planilha.", e);
        }
    }

    // ----- helpers -----

    private List<String> lerCabecalhos(Sheet sheet) throws IOException {
        try (Stream<Row> stream = sheet.openStream()) {
            return stream.findFirst()
                    .map(this::linhaParaListaDeStrings)
                    .orElseGet(ArrayList::new);
        }
    }

    private List<String> linhaParaListaDeStrings(Row row) {
        int n = row.getCellCount();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(row.getCellRawValue(i).orElse(""));
        }
        return out;
    }

    private Map<String, String> linhaParaMapa(Row row) {
        Map<String, String> mapa = new LinkedHashMap<>(cabecalhos.size());
        for (int i = 0; i < cabecalhos.size(); i++) {
            String header = cabecalhos.get(i);
            String valor = row.getCellRawValue(i).orElse("");
            mapa.put(header, valor);
        }
        return mapa;
    }
}
