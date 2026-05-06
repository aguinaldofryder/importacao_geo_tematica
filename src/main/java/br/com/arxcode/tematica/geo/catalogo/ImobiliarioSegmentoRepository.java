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
 * {@code idkey} na tabela {@code aise.tribimobiliariosegmento}.
 *
 * <p>O {@code idkey} retornado é o valor correto da coluna {@code referencia}
 * nas tabelas {@code aise.respostasegmento} (FK para {@code idkey}).
 * Usar {@code cadastrogeral} diretamente como {@code referencia} violaria a
 * integridade referencial — veja Story 4.7 §Contexto.
 *
 * <p><strong>Somente-leitura:</strong> nenhum método de escrita (INSERT/UPDATE/DELETE)
 * existe nesta classe (AC8).
 *
 * <p>Story: 4.7 — Repositórios de {@code idkey} e correção da {@code referencia} no UPSERT.
 */
@ApplicationScoped
public class ImobiliarioSegmentoRepository {

    private static final Logger LOG = Logger.getLogger(ImobiliarioSegmentoRepository.class);

    private static final String SQL_BUSCAR =
            "SELECT idkey FROM aise.tribimobiliariosegmento"
            + " WHERE tipocadastro = 1 AND cadastrogeral = CAST(? AS numeric)"
            + " AND sequencia = CAST(? AS numeric)";

    /**
     * {@code Instance<DataSource>} — padrão do projeto para datasource opcionalmente
     * inativo em profiles sem banco (evita {@code InactiveBeanException} no startup do ARC).
     */
    @Inject
    Instance<DataSource> dataSourceInstance;

    /**
     * Busca o {@code idkey} correspondente ao par {@code (cadastrogeral, sequencia)}
     * informado no schema {@code aise}.
     *
     * <p>Executa {@code SELECT idkey FROM aise.tribimobiliariosegmento
     * WHERE tipocadastro = 1 AND cadastrogeral = CAST(? AS numeric) AND sequencia = CAST(? AS numeric)}
     * via {@code PreparedStatement} com ambos os parâmetros sempre parametrizados.
     *
     * @param cadastrogeral valor de {@code cadastrogeral} lido da planilha (parametrizado — nunca concatenado)
     * @param sequencia     valor de {@code sequencia} lido da planilha (parametrizado — nunca concatenado)
     * @return {@code Optional.of(idkey)} se encontrado; {@code Optional.empty()} se o segmento não existir
     * @throws ImportacaoException em falha de I/O JDBC (mensagem em PT)
     */
    public Optional<Long> buscarIdKey(String cadastrogeral, String sequencia) {
        LOG.debugf("Buscando idkey predial: cadastrogeral=%s, sequencia=%s", cadastrogeral, sequencia);
        try (Connection c = dataSourceInstance.get().getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_BUSCAR)) {
            ps.setString(1, cadastrogeral);
            ps.setString(2, sequencia);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong(1));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                    "Falha ao buscar idkey do segmento predial '" + cadastrogeral
                    + "', sequencia '" + sequencia + "': " + e.getMessage(), e);
        }
    }
}
