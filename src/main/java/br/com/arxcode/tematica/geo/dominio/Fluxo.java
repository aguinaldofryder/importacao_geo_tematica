package br.com.arxcode.tematica.geo.dominio;

/**
 * Identifica o fluxo de importação ({@code TERRITORIAL} ou {@code PREDIAL}) e expõe
 * o contrato com o banco IPTU municipal: o discriminador da funcionalidade no catálogo
 * e os nomes físicos das tabelas envolvidas.
 *
 * <h2>Assimetria intencional dos vocabulários</h2>
 * <p>O nome do <em>fluxo</em> (CLI / planilha) <strong>não</strong> coincide com o valor de
 * {@code grupocampo.funcionalidade} no banco. A correspondência é:
 * <ul>
 *   <li>{@link #TERRITORIAL} → {@code funcionalidade() == "TERRENO"}</li>
 *   <li>{@link #PREDIAL}     → {@code funcionalidade() == "SEGMENTO"}</li>
 * </ul>
 * <p>Ambos os vocabulários vêm de fontes externas estáveis e já documentadas
 * (CLI/planilha no PRD §3.1/FR-05; banco no AGENTS.md §Domínio). Renomear qualquer
 * um quebraria comunicação com usuários ou com DBA. Por isso a assimetria é mantida
 * e formalizada em código.
 *
 * <h2>Tabelas físicas — parte do contrato</h2>
 * <p>Os nomes retornados por {@link #tabelaPrincipal()} e {@link #tabelaRespostas()}
 * são parte do contrato com o banco IPTU e <strong>não</strong> devem ser configuráveis.
 * Alterá-los exige mudança de código com revisão de DBA.
 *
 * <p>Consumidores: Story 2.3 (repositórios JDBC), Story 2.4 (classificador de colunas),
 * Story 3.2 (auto-mapeador), Stories 5.x (geração de SQL).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 */
public enum Fluxo {

    /**
     * Fluxo Territorial — planilha {@code TABELA_TERRITORIAL_V001.xlsx}.
     * Tabela principal: {@code tribcadastroimobiliario}; respostas: {@code respostaterreno};
     * funcionalidade no catálogo: {@code "TERRENO"}.
     */
    TERRITORIAL("TERRENO", "tribcadastroimobiliario", "respostaterreno"),

    /**
     * Fluxo Predial — planilha {@code TABELA_PREDIAL_V001.xlsx}.
     * Tabela principal: {@code tribimobiliariosegmento}; respostas: {@code respostasegmento};
     * funcionalidade no catálogo: {@code "SEGMENTO"}.
     */
    PREDIAL("SEGMENTO", "tribimobiliariosegmento", "respostasegmento");

    private final String funcionalidade;
    private final String tabelaPrincipal;
    private final String tabelaRespostas;

    Fluxo(String funcionalidade, String tabelaPrincipal, String tabelaRespostas) {
        this.funcionalidade = funcionalidade;
        this.tabelaPrincipal = tabelaPrincipal;
        this.tabelaRespostas = tabelaRespostas;
    }

    /**
     * Valor esperado em {@code grupocampo.funcionalidade} no banco
     * ({@code "TERRENO"} ou {@code "SEGMENTO"}). Usado pelo classificador de colunas
     * (Story 2.4) para discriminar campos do fluxo.
     */
    public String funcionalidade() {
        return funcionalidade;
    }

    /**
     * Nome físico da tabela principal do fluxo no schema {@code aise} —
     * {@code tribcadastroimobiliario} (territorial) ou {@code tribimobiliariosegmento} (predial).
     * Esta tabela é alvo apenas de {@code UPDATE} (nunca {@code INSERT}).
     */
    public String tabelaPrincipal() {
        return tabelaPrincipal;
    }

    /**
     * Nome físico da tabela de respostas do fluxo no schema {@code aise} —
     * {@code respostaterreno} (territorial) ou {@code respostasegmento} (predial).
     * Esta tabela é alvo de {@code UPSERT} por {@code (referencia, idcampo)}.
     */
    public String tabelaRespostas() {
        return tabelaRespostas;
    }
}
