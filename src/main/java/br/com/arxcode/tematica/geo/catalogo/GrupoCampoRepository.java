package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.GrupoCampo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório JDBC <strong>read-only</strong> para a tabela {@code grupocampo}
 * do catálogo dinâmico (schema {@code aise}).
 *
 * <p><strong>Invariante read-only:</strong> esta classe não expõe nenhum método de escrita
 * — verificado por reflexão em {@code RepositoriosReadOnlyTest} (Story 2.3 AC7).
 *
 * <p>Aceita o literal cru de {@code funcionalidade} ({@code "TERRENO"} ou
 * {@code "SEGMENTO"}); a tradução {@link br.com.arxcode.tematica.geo.dominio.Fluxo}
 * → {@code funcionalidade} é responsabilidade do chamador (vide Story 2.2 AC1).
 *
 * <p>Story: 2.3 — Repositórios JDBC do catálogo (read-only).
 */
@ApplicationScoped
public class GrupoCampoRepository {

    private static final String SQL_LISTAR_POR_FUNCIONALIDADE =
        "SELECT id, funcionalidade FROM grupocampo WHERE funcionalidade = ?";

    private final Instance<DataSource> dataSource;

    @Inject
    public GrupoCampoRepository(Instance<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Lista os grupos de campo associados à funcionalidade informada.
     *
     * @param funcionalidade literal {@code "TERRENO"} ou {@code "SEGMENTO"} (não nulo)
     * @return lista (possivelmente vazia) de {@link GrupoCampo}
     * @throws ImportacaoException em caso de falha de I/O JDBC
     */
    public List<GrupoCampo> listarPorFuncionalidade(String funcionalidade) {
        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_POR_FUNCIONALIDADE)) {
            ps.setString(1, funcionalidade);
            try (ResultSet rs = ps.executeQuery()) {
                List<GrupoCampo> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                "Falha ao listar grupos de campo por funcionalidade " + funcionalidade, e);
        }
    }

    private GrupoCampo mapear(ResultSet rs) throws SQLException {
        return new GrupoCampo(
            rs.getLong("id"),
            rs.getString("funcionalidade")
        );
    }
}
