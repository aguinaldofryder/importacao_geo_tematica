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
 * Testes de integração do {@link ImobiliarioSegmentoRepository} com Testcontainers (Story 4.7 AC9).
 *
 * <p>Cobre: registro encontrado → {@code Optional.of(idkey)}, registro não encontrado →
 * {@code Optional.empty()}, código/sequência nulos → {@code Optional.empty()}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class ImobiliarioSegmentoRepositoryTest {

    @Inject
    ImobiliarioSegmentoRepository repo;

    @Inject
    Instance<DataSource> dataSource;

    static final String CADASTROGERAL_EXISTENTE = "900002";
    static final String SEQUENCIA_EXISTENTE = "1";
    static final long IDKEY_ESPERADO = 888001L;

    static final String DDL =
            "CREATE TABLE IF NOT EXISTS aise.tribimobiliariosegmento ("
            + "tipocadastro SMALLINT NOT NULL DEFAULT 1, "
            + "cadastrogeral NUMERIC NOT NULL, "
            + "sequencia NUMERIC NOT NULL, "
            + "idkey NUMERIC(15,0), "
            + "PRIMARY KEY (tipocadastro, cadastrogeral, sequencia)"
            + ");";

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL);
            s.execute("DELETE FROM aise.tribimobiliariosegmento "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CADASTROGERAL_EXISTENTE
                    + " AND sequencia = " + SEQUENCIA_EXISTENTE);
            s.execute("INSERT INTO aise.tribimobiliariosegmento (tipocadastro, cadastrogeral, sequencia, idkey) "
                    + "VALUES (1, " + CADASTROGERAL_EXISTENTE + ", " + SEQUENCIA_EXISTENTE + ", " + IDKEY_ESPERADO + ")");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.tribimobiliariosegmento "
                    + "WHERE tipocadastro = 1 AND cadastrogeral = " + CADASTROGERAL_EXISTENTE);
        }
    }

    @Test
    void segmentoExistente_retornaOptionalComIdkey() {
        Optional<Long> resultado = repo.buscarIdKey(CADASTROGERAL_EXISTENTE, SEQUENCIA_EXISTENTE);
        assertTrue(resultado.isPresent(), "Segmento seeded deve ser encontrado");
        assertEquals(IDKEY_ESPERADO, resultado.get());
    }

    @Test
    void sequenciaInexistente_retornaOptionalVazio() {
        Optional<Long> resultado = repo.buscarIdKey(CADASTROGERAL_EXISTENTE, "99");
        assertFalse(resultado.isPresent(), "Sequência inexistente não deve retornar idkey");
    }

    @Test
    void codigoInexistente_retornaOptionalVazio() {
        Optional<Long> resultado = repo.buscarIdKey("999999999", SEQUENCIA_EXISTENTE);
        assertFalse(resultado.isPresent(), "Código inexistente não deve retornar idkey");
    }

    @Test
    void codigoNulo_retornaOptionalVazio() {
        // CAST(NULL AS numeric) = NULL → WHERE cadastrogeral = NULL → 0 linhas
        Optional<Long> resultado = repo.buscarIdKey(null, SEQUENCIA_EXISTENTE);
        assertFalse(resultado.isPresent(), "Código nulo não deve retornar idkey");
    }
}
