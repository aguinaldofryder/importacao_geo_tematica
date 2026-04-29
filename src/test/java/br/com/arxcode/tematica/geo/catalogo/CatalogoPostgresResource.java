package br.com.arxcode.tematica.geo.catalogo;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resource Testcontainers que sobe um PostgreSQL 16-alpine, cria o schema {@code aise}
 * com o subset de tabelas do catálogo ({@code grupocampo}, {@code campo}, {@code alternativa})
 * e carrega o seed {@code /seed-catalogo.sql}.
 *
 * <p>Story: 2.3 — Repositórios JDBC do catálogo (read-only).
 */
public class CatalogoPostgresResource implements QuarkusTestResourceLifecycleManager {

    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geotestdb")
            .withUsername("geouser")
            .withPassword("S3cr3t!Pass#99");

    private static final String DDL_AISE =
        "CREATE SCHEMA IF NOT EXISTS aise;";

    private static final String DDL_GRUPOCAMPO =
        "CREATE TABLE IF NOT EXISTS aise.grupocampo ("
        + "  id BIGINT PRIMARY KEY,"
        + "  funcionalidade VARCHAR(20) NOT NULL"
        + ");";

    private static final String DDL_CAMPO =
        "CREATE TABLE IF NOT EXISTS aise.campo ("
        + "  id BIGINT PRIMARY KEY,"
        + "  descricao VARCHAR(200) NOT NULL,"
        + "  tipo VARCHAR(30) NOT NULL,"
        + "  ativo CHAR(1) NOT NULL,"
        + "  idgrupo BIGINT NOT NULL REFERENCES aise.grupocampo(id)"
        + ");";

    private static final String DDL_ALTERNATIVA =
        "CREATE TABLE IF NOT EXISTS aise.alternativa ("
        + "  id BIGINT PRIMARY KEY,"
        + "  descricao VARCHAR(200) NOT NULL,"
        + "  idcampo BIGINT NOT NULL REFERENCES aise.campo(id)"
        + ");";

    @Override
    public Map<String, String> start() {
        PG.start();
        try (Connection conn = DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute(DDL_AISE);
            stmt.execute("SET search_path TO aise;");
            stmt.execute(DDL_GRUPOCAMPO);
            stmt.execute(DDL_CAMPO);
            stmt.execute(DDL_ALTERNATIVA);

            String seed = lerRecurso("/seed-catalogo.sql");
            stmt.execute(seed);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Falha ao inicializar CatalogoPostgresResource", e);
        }

        return Map.of(
            "quarkus.datasource.active",                                                 "true",
            "quarkus.datasource.jdbc.url",                                               PG.getJdbcUrl(),
            "quarkus.datasource.username",                                               PG.getUsername(),
            "quarkus.datasource.password",                                               PG.getPassword(),
            "quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema",          "aise"
        );
    }

    @Override
    public void stop() {
        PG.stop();
    }

    private static String lerRecurso(String path) {
        try (InputStream in = CatalogoPostgresResource.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler recurso " + path, e);
        }
    }
}
