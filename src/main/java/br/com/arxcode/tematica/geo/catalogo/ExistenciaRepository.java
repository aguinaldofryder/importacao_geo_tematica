package br.com.arxcode.tematica.geo.catalogo;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
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

/**
 * Repositório JDBC de verificação de existência de imóvel na tabela principal
 * do fluxo de importação (schema {@code aise}).
 *
 * <p>Consumido pelo {@code ImportarCommand} (Story 4.5) para verificar se o
 * código de imóvel lido da planilha existe na tabela principal antes de gerar
 * os SQLs de UPDATE e UPSERT. Imóvel ausente → linha inteira é pulada (FR-10).
 *
 * <p><strong>Segurança da concatenação SQL:</strong> os literais de tabela e coluna
 * ({@link Fluxo#tabelaPrincipal()} e {@link Fluxo#colunaChave()}) derivam
 * exclusivamente das constantes do enum {@link Fluxo}, nunca de input externo ou
 * do Excel. SQL injection contra esses valores é impossível. O parâmetro de valor
 * ({@code codigo}) é sempre parametrizado via {@code PreparedStatement.setString},
 * nunca concatenado na query. Consulte {@code docs/architecture/arquitetura.md §4}
 * e a decisão arquitetural da Story 4.5 Dev Notes §SQL com concatenação de
 * tabela/coluna.
 *
 * <p>Story: 4.5 — Comando {@code importar}. Cobre AC5.
 */
@ApplicationScoped
public class ExistenciaRepository {

    private static final Logger LOG = Logger.getLogger(ExistenciaRepository.class);

    /**
     * {@code Instance<DataSource>} — padrão do projeto para datasource opcionalmente
     * inativo em profiles sem banco (ex.: testes unitários sem Testcontainers).
     * Evita {@code InactiveBeanException} no startup do ARC quando
     * {@code quarkus.datasource.active=false}.
     */
    @Inject
    Instance<DataSource> dataSourceInstance;

    /**
     * Verifica se o imóvel identificado por {@code codigo} existe na tabela
     * principal do {@code fluxo} no schema {@code aise}.
     *
     * <p>Executa {@code SELECT 1 FROM aise.<tabelaPrincipal> WHERE <colunaChave> = ?}
     * via {@code PreparedStatement} com {@code codigo} sempre parametrizado.
     * Os nomes de tabela e coluna derivam exclusivamente das constantes do enum
     * {@link Fluxo} — nunca de input do usuário ou da planilha.
     *
     * @param codigo código do imóvel a verificar (sempre parametrizado — nunca concatenado);
     *               {@code null} resulta em {@code false} (WHERE {@code = NULL} não casa
     *               linhas com NULL em SQL padrão)
     * @param fluxo  fluxo de importação que determina a tabela e coluna de verificação;
     *               não nulo
     * @return {@code true} se o imóvel existir; {@code false} caso contrário
     * @throws ImportacaoException em caso de falha de I/O JDBC (mensagem em PT)
     */
    public boolean existeImovel(String codigo, Fluxo fluxo) {
        String sql = "SELECT 1 FROM aise." + fluxo.tabelaPrincipal()
                + " WHERE " + fluxo.colunaChave() + " = ?";
        LOG.debugf("Verificando existência: fluxo=%s, codigo=%s", fluxo.name(), codigo);
        try (Connection c = dataSourceInstance.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new ImportacaoException(
                    "Falha ao verificar existência do imóvel '" + codigo + "': " + e.getMessage(), e);
        }
    }
}
