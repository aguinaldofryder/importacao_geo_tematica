package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.cli.PostgresResource;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração do {@link CadastroImobiliarioRepository} com Testcontainers (Story 4.7 AC9).
 *
 * <p>Cobre: registro encontrado → {@code Optional.of(idkey)}, registro não encontrado →
 * {@code Optional.empty()}, código nulo → {@code Optional.empty()}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class CadastroImobiliarioRepositoryTest {

    @Inject
    CadastroImobiliarioRepository repo;

    @Inject
    Instance<DataSource> dataSource;

    static final String CADASTROGERAL_EXISTENTE = "900001";
    static final long IDKEY_ESPERADO = 777001L;

    static final String DDL =
            "CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario ("
            + "tipocadastro SMALLINT NOT NULL DEFAULT 1, "
            + "cadastrogeral NUMERIC NOT NULL, "
            + "tribcadastrogeral_idkey NUMERIC(15,0), "
            + "PRIMARY KEY (tipocadastro, cadastrogeral)"
            + ");";

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL);
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CADASTROGERAL_EXISTENTE);
            s.execute("INSERT INTO aise.tribcadastroimobiliario (tipocadastro, cadastrogeral, tribcadastrogeral_idkey) "
                    + "VALUES (1, " + CADASTROGERAL_EXISTENTE + ", " + IDKEY_ESPERADO + ")");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CADASTROGERAL_EXISTENTE);
        }
    }

    @Test
    void imovelExistente_retornaOptionalComIdkey() {
        Optional<Long> resultado = repo.buscarIdKey(CADASTROGERAL_EXISTENTE);
        assertTrue(resultado.isPresent(), "Imóvel seeded deve ser encontrado");
        assertEquals(IDKEY_ESPERADO, resultado.get());
    }

    @Test
    void imovelInexistente_retornaOptionalVazio() {
        Optional<Long> resultado = repo.buscarIdKey("999999999");
        assertFalse(resultado.isPresent(), "Código inexistente não deve retornar idkey");
    }

    @Test
    void codigoNulo_retornaOptionalVazio() {
        // CAST(NULL AS numeric) = NULL → WHERE cadastrogeral = NULL → 0 linhas
        Optional<Long> resultado = repo.buscarIdKey(null);
        assertFalse(resultado.isPresent(), "Código nulo não deve retornar idkey");
    }
}
