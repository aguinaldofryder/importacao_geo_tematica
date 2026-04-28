package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.Alternativa;
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
 * Repositório JDBC <strong>read-only</strong> para a tabela {@code alternativa}
 * (alternativas de campos {@link br.com.arxcode.tematica.geo.dominio.Tipo#MULTIPLA_ESCOLHA})
 * no schema {@code aise}.
 *
 * <p><strong>Invariante read-only:</strong> esta classe não expõe nenhum método de escrita
 * — verificado por reflexão em {@code RepositoriosReadOnlyTest} (Story 2.3 AC7).
 *
 * <p>A tabela {@code alternativa} não possui coluna {@code ativo} no schema atual
 * (Story 2.3 AC3); por isso este repositório não filtra por ativação.
 *
 * <p>Story: 2.3 — Repositórios JDBC do catálogo (read-only).
 */
@ApplicationScoped
public class AlternativaRepository {

    private static final String SQL_LISTAR_POR_CAMPO =
        "SELECT id, descricao, idcampo FROM alternativa WHERE idcampo = ?";

    private final Instance<DataSource> dataSource;

    @Inject
    public AlternativaRepository(Instance<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Lista as alternativas de um campo {@code MULTIPLA_ESCOLHA}.
     *
     * @param idCampo identificador do {@link br.com.arxcode.tematica.geo.dominio.Campo}
     * @return lista (possivelmente vazia) de {@link Alternativa}
     * @throws ImportacaoException em caso de falha de I/O JDBC
     */
    public List<Alternativa> listarPorCampo(long idCampo) {
        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_POR_CAMPO)) {
            ps.setLong(1, idCampo);
            try (ResultSet rs = ps.executeQuery()) {
                List<Alternativa> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                "Falha ao listar alternativas do campo " + idCampo, e);
        }
    }

    private Alternativa mapear(ResultSet rs) throws SQLException {
        return new Alternativa(
            rs.getLong("id"),
            rs.getString("descricao"),
            rs.getLong("idcampo")
        );
    }
}
