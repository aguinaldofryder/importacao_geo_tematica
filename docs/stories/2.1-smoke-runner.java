import br.com.arxcode.tematica.geo.excel.ExcelLeitor;
import br.com.arxcode.tematica.geo.excel.ExcelSessao;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SmokeRunner {
    public static void main(String[] args) throws Exception {
        Path xlsx = Paths.get(args[0]);
        try (ExcelSessao s = ExcelLeitor.abrir(xlsx)) {
            List<String> headers = s.cabecalhos();
            System.out.println("HEADERS=" + headers);
            long count = s.linhas().peek(linha -> System.out.println("ROW=" + linha)).count();
            System.out.println("TOTAL=" + count);
        }
    }
}
