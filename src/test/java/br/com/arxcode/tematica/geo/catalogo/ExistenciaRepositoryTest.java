package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.cli.PostgresResource;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração do {@link ExistenciaRepository} com Testcontainers (Story 4.5 AC5).
 *
 * <p>Cobre TERRITORIAL (PK de 2 colunas) e PREDIAL (PK de 3 colunas: tipocadastro,
 * cadastrogeral, sequencia).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class ExistenciaRepositoryTest {

    @Inject
    ExistenciaRepository repo;

    @Inject
    Instance<DataSource> dataSource;

    static final String CODIGO_EXISTENTE = "900001";
    static final String SEQUENCIA_EXISTENTE = "1";

    static final String DDL_TERRITORIAL =
            "CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario ("
            + "tipocadastro SMALLINT NOT NULL DEFAULT 1, "
            + "cadastrogeral NUMERIC NOT NULL, "
            + "PRIMARY KEY (tipocadastro, cadastrogeral)"
            + ");";

    static final String DDL_PREDIAL =
            "CREATE TABLE IF NOT EXISTS aise.tribimobiliariosegmento ("
            + "tipocadastro SMALLINT NOT NULL DEFAULT 1, "
            + "cadastrogeral NUMERIC NOT NULL, "
            + "sequencia NUMERIC NOT NULL, "
            + "PRIMARY KEY (tipocadastro, cadastrogeral, sequencia)"
            + ");";

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL_TERRITORIAL);
            s.execute(DDL_PREDIAL);
            // seed territorial
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CODIGO_EXISTENTE);
            s.execute("INSERT INTO aise.tribcadastroimobiliario (tipocadastro, cadastrogeral) "
                    + "VALUES (1, " + CODIGO_EXISTENTE + ")");
            // seed predial
            s.execute("DELETE FROM aise.tribimobiliariosegmento "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CODIGO_EXISTENTE
                    + " AND sequencia = " + SEQUENCIA_EXISTENTE);
            s.execute("INSERT INTO aise.tribimobiliariosegmento (tipocadastro, cadastrogeral, sequencia) "
                    + "VALUES (1, " + CODIGO_EXISTENTE + ", " + SEQUENCIA_EXISTENTE + ")");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CODIGO_EXISTENTE);
            s.execute("DELETE FROM aise.tribimobiliariosegmento "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CODIGO_EXISTENTE);
        }
    }

    // ── TERRITORIAL ──────────────────────────────────────────────────────────

    @Test
    void territorial_imovelExistente_retornaTrue() {
        assertTrue(repo.existeImovel(CODIGO_EXISTENTE, null, Fluxo.TERRITORIAL),
                "Imóvel seeded deve ser encontrado");
    }

    @Test
    void territorial_imovelInexistente_retornaFalse() {
        assertFalse(repo.existeImovel("999999999", null, Fluxo.TERRITORIAL),
                "Código inexistente não deve ser encontrado");
    }

    @Test
    void territorial_codigoNulo_retornaFalse() {
        assertFalse(repo.existeImovel(null, null, Fluxo.TERRITORIAL),
                "Código nulo não deve encontrar imóvel");
    }

    // ── PREDIAL ───────────────────────────────────────────────────────────────

    @Test
    void predial_imovelExistente_retornaTrue() {
        assertTrue(repo.existeImovel(CODIGO_EXISTENTE, SEQUENCIA_EXISTENTE, Fluxo.PREDIAL),
                "Segmento seeded deve ser encontrado");
    }

    @Test
    void predial_imovelInexistente_retornaFalse() {
        assertFalse(repo.existeImovel(CODIGO_EXISTENTE, "99", Fluxo.PREDIAL),
                "Sequência inexistente não deve ser encontrada");
    }

    @Test
    void predial_codigoNulo_retornaFalse() {
        assertFalse(repo.existeImovel(null, SEQUENCIA_EXISTENTE, Fluxo.PREDIAL),
                "Código nulo não deve encontrar segmento");
    }
}
