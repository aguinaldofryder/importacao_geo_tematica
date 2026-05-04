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
 * <p>Verifica os dois ramos principais (imóvel presente e ausente) e o
 * comportamento defensivo para código nulo, usando seed JDBC mínimo em
 * {@code aise.tribcadastroimobiliario}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class ExistenciaRepositoryTest {

    @Inject
    ExistenciaRepository repo;

    @Inject
    Instance<DataSource> dataSource;

    // DDL usa NUMERIC para tribcadastrogeral_idkey, espelhando o schema de produção.
    // O teste anterior usava VARCHAR(50), o que mascarava o bug onde ps.setString()
    // falhava contra a coluna numeric real (Story 4.5 bug fix).
    static final String DDL_TABELA =
            "CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario ("
            + "id BIGSERIAL PRIMARY KEY, "
            + "tribcadastrogeral_idkey NUMERIC"
            + ");";

    static final String CODIGO_EXISTENTE = "900001";

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL_TABELA);
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tribcadastrogeral_idkey = " + CODIGO_EXISTENTE);
            s.execute("INSERT INTO aise.tribcadastroimobiliario (tribcadastrogeral_idkey) "
                    + "VALUES (" + CODIGO_EXISTENTE + ")");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.get().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM aise.tribcadastroimobiliario "
                    + "WHERE tribcadastrogeral_idkey = " + CODIGO_EXISTENTE);
        }
    }

    /** AC5 Teste 1: imóvel seeded deve ser encontrado no fluxo TERRITORIAL. */
    @Test
    void deveRetornarTrueParaImovelExistente() {
        assertTrue(repo.existeImovel(CODIGO_EXISTENTE, Fluxo.TERRITORIAL),
                "Imóvel seeded deve ser encontrado");
    }

    /** AC5 Teste 2: código não presente na tabela deve retornar false. */
    @Test
    void deveRetornarFalseParaImovelInexistente() {
        assertFalse(repo.existeImovel("999999999", Fluxo.TERRITORIAL),
                "Código inexistente não deve ser encontrado");
    }

    /**
     * AC5 Teste 3: código nulo — {@code PreparedStatement.setString(1, null)} é válido JDBC;
     * {@code CAST(NULL AS numeric)} retorna NULL e {@code WHERE col = NULL} avalia para
     * UNKNOWN (não TRUE), portanto não casa nenhuma linha.
     */
    @Test
    void deveRetornarFalseParaCodigoNulo() {
        assertFalse(repo.existeImovel(null, Fluxo.TERRITORIAL),
                "Código nulo não deve encontrar imóvel (CAST(NULL AS numeric) não casa nenhuma linha)");
    }
}
