package br.com.arxcode.tematica.geo.cli;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public class PostgresResource implements QuarkusTestResourceLifecycleManager {

    // Credenciais distintas entre si e do nome da base para que o teste
    // de segurança AC5 possa verificar que a senha não vaza no output.
    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geotestdb")
            .withUsername("geouser")
            .withPassword("S3cr3t!Pass#99");

    @Override
    public Map<String, String> start() {
        PG.start();
        return Map.of(
            "quarkus.datasource.active",                   "true",
            "quarkus.datasource.jdbc.url",                 PG.getJdbcUrl(),
            "quarkus.datasource.username",                 PG.getUsername(),
            "quarkus.datasource.password",                 PG.getPassword(),
            "quarkus.datasource.jdbc.acquisition-timeout", "5"
            // currentSchema não é sobrescrito: SET search_path TO aise
            // é ignorado silenciosamente pelo PG se o schema não existir
        );
    }

    @Override
    public void stop() {
        PG.stop();
    }
}
