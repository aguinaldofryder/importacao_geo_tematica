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
 * <h2>WHERE do UPDATE — chave composta</h2>
 * <p>A tabela principal de ambos os fluxos é identificada por chave composta:
 * <ul>
 *   <li>TERRITORIAL: {@code tipocadastro = 1 AND cadastrogeral = ?}</li>
 *   <li>PREDIAL:     {@code tipocadastro = 1 AND cadastrogeral = ? AND sequencia = ?}</li>
 * </ul>
 * O {@code SqlGeradorUpdate} constrói esse WHERE diretamente; não há coluna-chave única.
 *
 * <h2>Coluna de referência FK (tabelas de respostas)</h2>
 * <p>{@link #colunaReferencia()} retorna a coluna da tabela principal cujo valor é
 * gravado na coluna {@code referencia} das tabelas de respostas
 * ({@code respostaterreno} / {@code respostasegmento}).
 *
 * <p>Consumidores: Story 2.3 (repositórios JDBC), Story 2.4 (classificador de colunas),
 * Story 3.2 (auto-mapeador), Stories 4.2 / 4.3 / 4.5 (geração e orquestração de SQL).
 *
 * <p>Story: 2.2 — Domínio: enums e records.
 * Story 4.2 — adicionado {@code colunaReferencia()} (ex-{@code colunaChave}).
 */
public enum Fluxo {

    /**
     * Fluxo Territorial — planilha {@code TABELA_TERRITORIAL_V001.xlsx}.
     * Tabela principal: {@code tribcadastroimobiliario}; respostas: {@code respostaterreno};
     * funcionalidade no catálogo: {@code "TERRENO"};
     * coluna de referência FK: {@code tribcadastrogeral_idkey}.
     */
    TERRITORIAL("TERRENO", "tribcadastroimobiliario", "respostaterreno", "tribcadastrogeral_idkey", "s_respostaterreno_id"),

    /**
     * Fluxo Predial — planilha {@code TABELA_PREDIAL_V001.xlsx}.
     * Tabela principal: {@code tribimobiliariosegmento}; respostas: {@code respostasegmento};
     * funcionalidade no catálogo: {@code "SEGMENTO"};
     * coluna de referência FK: {@code idkey}.
     */
    PREDIAL("SEGMENTO", "tribimobiliariosegmento", "respostasegmento", "idkey", "s_respostasegmento_id");

    private final String funcionalidade;
    private final String tabelaPrincipal;
    private final String tabelaRespostas;
    private final String colunaReferencia;
    private final String sequenceRespostas;

    Fluxo(String funcionalidade, String tabelaPrincipal, String tabelaRespostas, String colunaReferencia, String sequenceRespostas) {
        this.funcionalidade = funcionalidade;
        this.tabelaPrincipal = tabelaPrincipal;
        this.tabelaRespostas = tabelaRespostas;
        this.colunaReferencia = colunaReferencia;
        this.sequenceRespostas = sequenceRespostas;
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

    /**
     * Coluna física da tabela principal cujo valor é armazenado na coluna
     * {@code referencia} das tabelas de respostas, servindo como chave estrangeira:
     * {@code tribcadastrogeral_idkey} (territorial) ou {@code idkey} (predial).
     *
     * <p><strong>Não</strong> é usada no {@code WHERE} do {@code UPDATE} da tabela
     * principal — esse WHERE usa a chave composta real
     * ({@code tipocadastro}, {@code cadastrogeral}, e {@code sequencia} para PREDIAL).
     * O único consumidor desta coluna é o {@code SqlGeradorUpsert} (Story 4.3), que
     * a referencia ao gravar {@code respostaterreno.referencia} /
     * {@code respostasegmento.referencia}.
     */
    public String colunaReferencia() {
        return colunaReferencia;
    }

    /**
     * Nome físico da sequence Postgres usada para alimentar a coluna {@code id}
     * da tabela de respostas no schema {@code aise} —
     * {@code s_respostaterreno_id} (territorial) ou
     * {@code s_respostasegmento_id} (predial). Consumida pela Story 4.3
     * ({@code SqlGeradorUpsert}) na cláusula {@code VALUES (nextval('aise.<seq>'), ...)}
     * dos {@code INSERT}s de células dinâmicas (sintaxe PG-específica — CON-04).
     *
     * <p>Sequences confirmadas em {@code information_schema.sequences} do banco
     * IPTU em 2026-04-30. Story: 4.3 — adicionado AC16.
     */
    public String sequenceRespostas() {
        return sequenceRespostas;
    }
}
