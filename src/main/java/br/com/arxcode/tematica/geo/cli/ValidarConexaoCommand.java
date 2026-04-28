package br.com.arxcode.tematica.geo.cli;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// @Dependent evita o client proxy do CDI, necessário para que picocli possa
// injetar @CommandLine.Spec diretamente na instância real (não num wrapper).
// Instance<DataSource> permite startup com datasource inativo (quarkus.datasource.active=false).
@Dependent
@CommandLine.Command(
    name = "validar-conexao",
    description = "Valida a conexão com o banco de dados PostgreSQL.",
    mixinStandardHelpOptions = true
)
public class ValidarConexaoCommand implements Callable<Integer> {

    private static final Pattern JDBC_HOST_PATTERN =
        Pattern.compile("jdbc:postgresql://([^/?]+)/([^?]*)");

    // SQLState → mensagem amigável em PT (AC3, AC5)
    static final Map<String, String> SQLSTATE_PT = Map.of(
        "08001", "Servidor indisponível: verifique host e porta",
        "08006", "Conexão encerrada inesperadamente",
        "28P01", "Usuário ou senha incorretos",
        "3D000", "Base de dados não encontrada",
        "57014", "Tempo limite de conexão esgotado"
    );

    @Inject
    Instance<DataSource> dataSourceInstance;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        try (Connection conn = dataSourceInstance.get().getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("SELECT 1");

            DatabaseMetaData meta = conn.getMetaData();
            String hostPort = extrairHostPort(meta.getURL());
            String base     = extrairBase(meta.getURL());
            String usuario  = meta.getUserName();
            String versao   = meta.getDatabaseProductVersion();

            spec.commandLine().getOut().println(
                "✓ Conexão OK (" + hostPort + ", base=" + base
                + ", usuário=" + usuario + ", versão PG=" + versao + ")"
            );
            return 0;

        } catch (Exception e) {
            // Nunca exibir e.getMessage() diretamente — pode conter senha (AC5)
            spec.commandLine().getErr().println("✗ Falha: " + mensagemAmigavel(e));
            return 1;
        }
    }

    // package-protected para testes unitários de mapeamento de erros
    static String mensagemAmigavel(Exception e) {
        if (e instanceof SQLException sqlEx) {
            String state = sqlEx.getSQLState();
            if (state != null && !state.isBlank()) {
                return SQLSTATE_PT.getOrDefault(state, "Falha na conexão: " + state);
            }
        }
        // Desempacota um nível — Agroal pode encapsular PSQLException
        if (e.getCause() instanceof Exception cause && cause != e) {
            return mensagemAmigavel(cause);
        }
        return "Falha inesperada ao conectar ao banco de dados";
    }

    private String extrairHostPort(String jdbcUrl) {
        if (jdbcUrl == null) return "desconhecido";
        Matcher m = JDBC_HOST_PATTERN.matcher(jdbcUrl);
        return m.find() ? m.group(1) : "desconhecido";
    }

    private String extrairBase(String jdbcUrl) {
        if (jdbcUrl == null) return "desconhecida";
        Matcher m = JDBC_HOST_PATTERN.matcher(jdbcUrl);
        if (!m.find()) return "desconhecida";
        String base = m.group(2);
        int sep = base.indexOf('?');
        return sep >= 0 ? base.substring(0, sep) : base;
    }
}
