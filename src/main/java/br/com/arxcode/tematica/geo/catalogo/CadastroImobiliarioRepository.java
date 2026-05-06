package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositório JDBC somente-leitura para busca do identificador interno
 * {@code tribcadastrogeral_idkey} na tabela {@code aise.tribcadastroimobiliario}.
 *
 * <p>O {@code idkey} retornado é o valor correto da coluna {@code referencia}
 * nas tabelas {@code aise.respostaterreno} (FK para {@code tribcadastrogeral_idkey}).
 * Usar {@code cadastrogeral} diretamente como {@code referencia} violaria a
 * integridade referencial — veja Story 4.7 §Contexto.
 *
 * <p><strong>Somente-leitura:</strong> nenhum método de escrita (INSERT/UPDATE/DELETE)
 * existe nesta classe (AC8).
 *
 * <p>Story: 4.7 — Repositórios de {@code idkey} e correção da {@code referencia} no UPSERT.
 */
@ApplicationScoped
public class CadastroImobiliarioRepository {

    private static final Logger LOG = Logger.getLogger(CadastroImobiliarioRepository.class);

    private static final String SQL_BUSCAR =
            "SELECT tribcadastrogeral_idkey FROM aise.tribcadastroimobiliario"
            + " WHERE tipocadastro = 1 AND cadastrogeral = CAST(? AS numeric)";

    /**
     * {@code Instance<DataSource>} — padrão do projeto para datasource opcionalmente
     * inativo em profiles sem banco (evita {@code InactiveBeanException} no startup do ARC).
     */
    @Inject
    Instance<DataSource> dataSourceInstance;

    /**
     * Busca o {@code tribcadastrogeral_idkey} correspondente ao {@code cadastrogeral}
     * informado no schema {@code aise}.
     *
     * <p>Executa {@code SELECT tribcadastrogeral_idkey FROM aise.tribcadastroimobiliario
     * WHERE tipocadastro = 1 AND cadastrogeral = CAST(? AS numeric)} via
     * {@code PreparedStatement} com {@code cadastrogeral} sempre parametrizado.
     *
     * @param cadastrogeral valor de {@code cadastrogeral} lido da planilha (parametrizado — nunca concatenado)
     * @return {@code Optional.of(idkey)} se encontrado; {@code Optional.empty()} se o imóvel não existir
     * @throws ImportacaoException em falha de I/O JDBC (mensagem em PT)
     */
    public Optional<Long> buscarIdKey(String cadastrogeral) {
        LOG.debugf("Buscando idkey territorial: cadastrogeral=%s", cadastrogeral);
        try (Connection c = dataSourceInstance.get().getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_BUSCAR)) {
            ps.setString(1, cadastrogeral);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong(1));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                    "Falha ao buscar idkey do imóvel territorial '" + cadastrogeral + "': " + e.getMessage(), e);
        }
    }
}
