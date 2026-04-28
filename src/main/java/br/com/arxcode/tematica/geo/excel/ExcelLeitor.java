package br.com.arxcode.tematica.geo.excel;

import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point para leitura de planilhas {@code .xlsx} em modo streaming.
 *
 * <p>Wrapper estático sobre {@code fastexcel-reader} — escolhido por compatibilidade
 * com GraalVM native-image (evita a carga de reflexão do Apache POI).
 *
 * <p>Leitor é stateless e thread-safe: cada chamada a {@link #abrir(Path)}
 * cria uma nova {@link ExcelSessao} independente.
 *
 * <p>Story: 2.1 — leitura streaming, suporta ≥ 100k linhas sem esgotar memória (NFR-04).
 */
public final class ExcelLeitor {

    private ExcelLeitor() {
        // utility class
    }

    /**
     * Abre uma planilha {@code .xlsx} em modo streaming.
     *
     * <p>Validações realizadas antes de abrir o arquivo:
     * <ul>
     *   <li>Path deve existir (caso contrário lança {@link ImportacaoException}).</li>
     *   <li>Nome do arquivo deve terminar em {@code .xlsx} (case-insensitive).
     *       Outras extensões (incl. {@code .xls}) são rejeitadas.</li>
     * </ul>
     *
     * <p>Corrupção interna do XLSX (zip inválido, structure malformado) é detectada
     * pelo {@code ReadableWorkbook} no construtor da sessão e re-lançada como
     * {@link ImportacaoException} com {@code cause} preservado.
     *
     * @param xlsx caminho do arquivo a ler
     * @return sessão {@link AutoCloseable} pronta para consultar cabeçalhos e linhas
     * @throws ImportacaoException se o arquivo não existir, tiver extensão errada
     *                              ou for um XLSX corrompido
     */
    public static ExcelSessao abrir(Path xlsx) {
        if (xlsx == null) {
            throw new ImportacaoException("Caminho da planilha não informado.");
        }
        if (!Files.exists(xlsx)) {
            throw new ImportacaoException("Arquivo não encontrado: " + xlsx);
        }
        String nome = xlsx.getFileName() == null ? "" : xlsx.getFileName().toString();
        if (!nome.toLowerCase().endsWith(".xlsx")) {
            throw new ImportacaoException("Formato não suportado (esperado .xlsx): " + nome);
        }
        return new ExcelSessao(xlsx);
    }
}
