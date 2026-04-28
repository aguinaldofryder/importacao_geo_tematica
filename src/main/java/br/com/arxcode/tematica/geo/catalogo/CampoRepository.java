package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
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
 * Repositório JDBC <strong>read-only</strong> para a tabela {@code campo} do catálogo
 * dinâmico (schema {@code aise}).
 *
 * <p>Carrega os campos (com FK para {@code grupocampo}) usados pelo auto-mapeador
 * (Story 3.2), pelo validador (Story 3.3) e pelo gerador de SQL (Story 4.5).
 *
 * <p><strong>Invariante read-only:</strong> esta classe não expõe nenhum método de escrita
 * — verificado por reflexão em {@code RepositoriosReadOnlyTest} (Story 2.3 AC7).
 *
 * <p>Schema esperado: {@code aise} (configurado via
 * {@code quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema=aise}).
 *
 * <p>Story: 2.3 — Repositórios JDBC do catálogo (read-only).
 */
@ApplicationScoped
public class CampoRepository {

    private static final String SQL_LISTAR_POR_FLUXO =
        "SELECT c.id, c.descricao, c.tipo, c.ativo, c.idgrupocampo "
        + "FROM campo c "
        + "JOIN grupocampo g ON c.idgrupocampo = g.id "
        + "WHERE g.funcionalidade = ? AND c.ativo = 'S'";

    private static final String SQL_LISTAR_TODOS =
        "SELECT c.id, c.descricao, c.tipo, c.ativo, c.idgrupocampo "
        + "FROM campo c "
        + "WHERE c.ativo = 'S'";

    private final Instance<DataSource> dataSource;

    @Inject
    public CampoRepository(Instance<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Lista os campos ativos de um fluxo (filtrando por
     * {@code grupocampo.funcionalidade = fluxo.funcionalidade()}).
     *
     * @param fluxo fluxo da CLI/planilha (não nulo)
     * @return lista (possivelmente vazia) de {@link Campo}
     * @throws ImportacaoException em caso de falha de I/O JDBC
     */
    public List<Campo> listarPorFluxo(Fluxo fluxo) {
        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_POR_FLUXO)) {
            ps.setString(1, fluxo.funcionalidade());
            try (ResultSet rs = ps.executeQuery()) {
                List<Campo> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                "Falha ao listar campos por fluxo " + fluxo.name(), e);
        }
    }

    /**
     * Lista todos os campos ativos (sem filtro de fluxo). Útil para diagnósticos
     * e testes que exercem ambos os fluxos no mesmo seed.
     *
     * @return lista (possivelmente vazia) de {@link Campo}
     * @throws ImportacaoException em caso de falha de I/O JDBC
     */
    public List<Campo> listarTodos() {
        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_TODOS);
             ResultSet rs = ps.executeQuery()) {
            List<Campo> resultado = new ArrayList<>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw new ImportacaoException("Falha ao listar todos os campos do catálogo", e);
        }
    }

    private Campo mapear(ResultSet rs) throws SQLException {
        return new Campo(
            rs.getLong("id"),
            rs.getString("descricao"),
            Tipo.valueOf(rs.getString("tipo")),
            "S".equals(rs.getString("ativo")),
            rs.getLong("idgrupocampo")
        );
    }
}
