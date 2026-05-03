package br.com.arxcode.tematica.geo.geracao;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Acumulador estruturado de erros de uma execução do comando {@code importar}
 * e produtor do artefato {@code .log} consolidado em texto plano UTF-8.
 *
 * <h2>Papel no épico 4</h2>
 * <p>{@code LogErros} é alimentado pelo orquestrador {@code ImportarCommand}
 * (Story 4.5) com três categorias de erro de dados produzidas pelas Stories
 * 4.1 ({@link Coercionador} — falhas de coerção célula-a-célula), 4.2
 * ({@link SqlGeradorUpdate#gerar} — erros agregados por linha do {@code UPDATE}
 * principal) e 4.3 ({@link SqlGeradorUpsert#gerar} — erros agregados por linha
 * dos {@code UPSERT}s de células dinâmicas), além das <em>linhas puladas</em>
 * por código de imóvel inexistente (FR-10). Após o processamento,
 * {@link #gerar()} devolve a {@code String} consolidada (cabeçalho + seções
 * + sumário) que a Story 4.5 grava em disco via
 * {@code Files.writeString(path, log.gerar(), StandardCharsets.UTF_8, ...)}.
 *
 * <h2>Divergência arquitetural (intencional) vs. 4.1/4.2/4.3</h2>
 * <p>As Stories 4.1, 4.2 e 4.3 entregam funções <strong>puras / stateless</strong>
 * porque cada chamada processa uma unidade independente (uma célula, uma linha).
 * {@code LogErros} é deliberadamente <strong>stateful</strong>: o log é uma
 * coleção crescente que só faz sentido ao final da execução. Mitigações:
 * <ul>
 *   <li>{@link #gerar()} é puro/idempotente sobre o estado acumulado — chamá-lo
 *       N vezes sem novos {@code registrarX} produz a <em>mesma</em> {@code String}
 *       byte-a-byte (AC11).</li>
 *   <li>Contrato <strong>single-thread</strong>: orquestrador 4.5 é sequencial.
 *       <em>Não</em> use a mesma instância concorrentemente.</li>
 * </ul>
 *
 * <h2>Sanitização de control chars (defesa em profundidade — AC3)</h2>
 * <p>Todos os campos de {@code String} provenientes do Excel/usuário são
 * passados por {@link #sanitizar(String)} antes de comporem a saída: {@code \n},
 * {@code \r} e {@code \t} viram literais {@code \\n}, {@code \\r} e {@code \\t};
 * demais caracteres com código {@code < 0x20} viram {@code \\uXXXX}; caracteres
 * {@code >= 0x20} (incluindo aspas, contrabarra e UTF-8 multi-byte) passam
 * intactos. Isso preserva a estrutura por linha do {@code .log} (NFR-08) e
 * impede log-injection a partir de células com {@code Alt+Enter} (AC13).
 * <strong>Fecha ISSUE-4.1-03 LOW</strong>.
 *
 * <h2>Fronteira vs. {@code quarkus.log.file.*}</h2>
 * <p>{@code LogErros} alimenta o <em>artefato</em> {@code .log} de auditoria de
 * dados (FR-17). O canal operacional do Quarkus
 * ({@code quarkus.log.file.path=./saida/importacao-geo.log}) — INFO/WARN/ERROR
 * de execução via {@code org.jboss.logging.Logger} — é independente. Esta
 * classe deliberadamente <strong>não</strong> usa {@code Logger} nem escreve
 * em {@code System.out}.
 *
 * <h2>Decisões de formato</h2>
 * <ul>
 *   <li><strong>Line separator fixo {@code "\n"} (LF)</strong> — não
 *       {@code System.lineSeparator()} — para reprodutibilidade entre Linux e
 *       Windows (CON-05/NFR-02 prevê GraalVM nativo nas duas plataformas).</li>
 *   <li><strong>Timestamp único no cabeçalho</strong> formatado por
 *       {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} (releitura pragmática de
 *       NFR-08 aprovada pelo @po — execuções duram minutos; granularidade por
 *       entrada agregaria pouco valor).</li>
 *   <li><strong>Seções vazias são omitidas</strong>; o sumário é sempre
 *       emitido (AC10).</li>
 *   <li><strong>Sumário é subset operacional</strong> focado em erros — o
 *       resumo executivo rico ({@code respostas.atualizadas}/{@code inseridas},
 *       JSON estruturado) é responsabilidade do Épico 5 (FR-14/FR-15/FR-16),
 *       que requer feedback do banco pós-execução do {@code .sql} pelo DBA.</li>
 * </ul>
 *
 * <p>Story: 4.4 — fecha ISSUE-4.1-03 LOW.
 * Rastreia: FR-10, FR-13, FR-17, NFR-01, NFR-02, NFR-08, CON-03, CON-05.
 */
public final class LogErros {

    private static final String LF = "\n";

    private final Fluxo fluxo;
    private final String nomePlanilha;
    private final OffsetDateTime inicioExecucao;

    private final List<EntradaLinhaPulada> linhasPuladas = new ArrayList<>();
    private final List<EntradaErroCoercao> errosCoercao = new ArrayList<>();
    private final List<EntradaErrosLinha> errosAgregados = new ArrayList<>();
    private int linhasProcessadas = 0;

    /**
     * Constrói um log com {@code OffsetDateTime.now()} como início de execução.
     * Use este construtor em produção (orquestrador 4.5).
     *
     * @param fluxo        fluxo de importação ({@code TERRITORIAL} ou {@code PREDIAL}); não-{@code null}.
     * @param nomePlanilha nome do arquivo {@code .xlsx} de origem; não-{@code null} e não-blank.
     * @throws IllegalArgumentException se algum argumento for inválido.
     */
    public LogErros(Fluxo fluxo, String nomePlanilha) {
        this(fluxo, nomePlanilha, OffsetDateTime.now());
    }

    /**
     * Construtor com visibilidade de pacote para testabilidade determinística
     * do timestamp do cabeçalho (AC15). Não exposto publicamente — orquestrador
     * 4.5 deve usar {@link #LogErros(Fluxo, String)}.
     *
     * @param fluxo          fluxo; não-{@code null}.
     * @param nomePlanilha   nome do {@code .xlsx}; não-{@code null}/blank.
     * @param inicioExecucao timestamp do início da execução; não-{@code null}.
     * @throws IllegalArgumentException se algum argumento for inválido.
     */
    LogErros(Fluxo fluxo, String nomePlanilha, OffsetDateTime inicioExecucao) {
        if (fluxo == null) {
            throw new IllegalArgumentException("Fluxo é obrigatório.");
        }
        if (nomePlanilha == null || nomePlanilha.isBlank()) {
            throw new IllegalArgumentException("Nome da planilha é obrigatório.");
        }
        if (inicioExecucao == null) {
            throw new IllegalArgumentException("Início de execução é obrigatório.");
        }
        this.fluxo = fluxo;
        this.nomePlanilha = nomePlanilha;
        this.inicioExecucao = inicioExecucao;
    }

    // ---------- API pública: registro tipado por categoria ----------

    /**
     * Registra uma linha pulada por código de imóvel inexistente na tabela
     * principal (cobre FR-10).
     *
     * @param numeroLinhaExcel número da linha do Excel (1-based, header conta como 1); deve ser {@code >= 1}.
     * @param codigoImovel     código do imóvel apresentado na planilha; não-{@code null}/blank.
     * @param motivo           motivo em PT (ex.: {@code "Imóvel não encontrado em tribcadastroimobiliario"}); não-{@code null}/blank.
     * @throws IllegalArgumentException se algum argumento for inválido.
     */
    public void registrarLinhaPulada(int numeroLinhaExcel, String codigoImovel, String motivo) {
        validarLinha(numeroLinhaExcel);
        validarObrigatorio(codigoImovel, "código do imóvel");
        validarObrigatorio(motivo, "motivo");
        linhasPuladas.add(new EntradaLinhaPulada(numeroLinhaExcel, codigoImovel, motivo));
    }

    /**
     * Registra uma falha individual de coerção (cobre FR-13). Alimentado pelo
     * orquestrador 4.5 a partir de {@link ResultadoCoercao#falha} retornado por
     * {@link Coercionador}.
     *
     * @param numeroLinhaExcel número da linha do Excel; {@code >= 1}.
     * @param coluna           header da coluna do Excel; não-{@code null}/blank.
     * @param valorOriginal    valor original da célula como string; não-{@code null} (use {@code ""} se ausente).
     * @param motivo           mensagem PT vinda do {@link Coercionador}; não-{@code null}/blank.
     * @throws IllegalArgumentException se algum argumento for inválido.
     */
    public void registrarErroCoercao(int numeroLinhaExcel, String coluna, String valorOriginal, String motivo) {
        validarLinha(numeroLinhaExcel);
        validarObrigatorio(coluna, "coluna");
        if (valorOriginal == null) {
            throw new IllegalArgumentException("Valor original é obrigatório (use string vazia se ausente).");
        }
        validarObrigatorio(motivo, "motivo");
        errosCoercao.add(new EntradaErroCoercao(numeroLinhaExcel, coluna, valorOriginal, motivo));
    }

    /**
     * Registra um conjunto de erros agregados por linha vindos de
     * {@link ResultadoUpdate#erros()} ou {@link ResultadoUpsert#erros()}
     * (uma única lista por linha — pode misturar erros de UPDATE e UPSERT).
     *
     * @param numeroLinhaExcel número da linha do Excel; {@code >= 1}.
     * @param codigoImovel     código do imóvel; não-{@code null}/blank.
     * @param erros            lista não-vazia de mensagens PT já prefixadas (ex.: {@code "Coluna 'X': ..."}); cada elemento não-{@code null}/blank.
     * @throws IllegalArgumentException se algum argumento for inválido.
     */
    public void registrarErrosLinha(int numeroLinhaExcel, String codigoImovel, List<String> erros) {
        validarLinha(numeroLinhaExcel);
        validarObrigatorio(codigoImovel, "código do imóvel");
        if (erros == null || erros.isEmpty()) {
            throw new IllegalArgumentException("Lista de erros é obrigatória e não pode ser vazia.");
        }
        for (String e : erros) {
            if (e == null || e.isBlank()) {
                throw new IllegalArgumentException("Erro individual não pode ser nulo ou em branco.");
            }
        }
        errosAgregados.add(new EntradaErrosLinha(numeroLinhaExcel, codigoImovel, List.copyOf(erros)));
    }

    /**
     * Incrementa o contador de linhas processadas com sucesso (alimenta o
     * sumário). Sem argumentos.
     */
    public void registrarLinhaProcessada() {
        linhasProcessadas++;
    }

    // ---------- Geração da saída consolidada ----------

    /**
     * Renderiza o log consolidado (cabeçalho + seções + sumário) usando line
     * separator fixo {@code "\n"} (LF). Método <strong>puro/idempotente</strong>
     * sobre o estado acumulado: chamá-lo N vezes sem novos {@code registrarX}
     * devolve a mesma {@code String} byte-a-byte (AC11).
     *
     * <p>Seções vazias são omitidas; o sumário é sempre emitido. A
     * codificação UTF-8 é responsabilidade do caller (Story 4.5).
     *
     * @return artefato {@code .log} como {@code String}; nunca {@code null}.
     */
    public String gerar() {
        StringBuilder sb = new StringBuilder(estimarTamanho());

        // Cabeçalho (AC9)
        sb.append("===== Importação georreferenciada — Log de execução =====").append(LF);
        sb.append("Data: ").append(inicioExecucao.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append(LF);
        sb.append("Fluxo: ").append(fluxo.name())
                .append(" (planilha: ").append(sanitizar(nomePlanilha)).append(")").append(LF);
        sb.append(LF);

        // Seção 1 — Linhas puladas (AC5)
        if (!linhasPuladas.isEmpty()) {
            sb.append("----- Linhas puladas (código imobiliário inexistente) -----").append(LF);
            for (EntradaLinhaPulada e : linhasPuladas) {
                sb.append("[linha ").append(e.linha()).append("] codigo='").append(sanitizar(e.codigo()))
                        .append("' motivo='").append(sanitizar(e.motivo())).append("'").append(LF);
            }
            sb.append(LF);
        }

        // Seção 2 — Erros de coerção (AC6)
        if (!errosCoercao.isEmpty()) {
            sb.append("----- Erros de coerção -----").append(LF);
            for (EntradaErroCoercao e : errosCoercao) {
                sb.append("[linha ").append(e.linha()).append(", coluna '")
                        .append(sanitizar(e.coluna())).append("'] valor='")
                        .append(sanitizar(e.valor())).append("' motivo='")
                        .append(sanitizar(e.motivo())).append("'").append(LF);
            }
            sb.append(LF);
        }

        // Seção 3 — Erros agregados por linha (AC7)
        if (!errosAgregados.isEmpty()) {
            sb.append("----- Erros agregados por linha -----").append(LF);
            for (EntradaErrosLinha e : errosAgregados) {
                sb.append("[linha ").append(e.linha()).append("] codigo='").append(sanitizar(e.codigo()))
                        .append("' (").append(e.erros().size()).append(" erros):").append(LF);
                for (String msg : e.erros()) {
                    sb.append("  - ").append(sanitizar(msg)).append(LF);
                }
            }
            sb.append(LF);
        }

        // Sumário (AC10) — sempre emitido
        sb.append("----- Sumário -----").append(LF);
        sb.append("Total de linhas processadas com sucesso: ").append(linhasProcessadas).append(LF);
        sb.append("Linhas puladas: ").append(linhasPuladas.size()).append(LF);
        sb.append("Erros de coerção: ").append(errosCoercao.size()).append(LF);
        sb.append("Linhas com erros agregados: ").append(errosAgregados.size()).append(LF);
        sb.append("Total de linhas com falha: ").append(linhasComFalhaDistintas()).append(LF);

        return sb.toString();
    }

    private int linhasComFalhaDistintas() {
        Set<Integer> distintas = new LinkedHashSet<>();
        for (EntradaLinhaPulada e : linhasPuladas) {
            distintas.add(e.linha());
        }
        for (EntradaErroCoercao e : errosCoercao) {
            distintas.add(e.linha());
        }
        for (EntradaErrosLinha e : errosAgregados) {
            distintas.add(e.linha());
        }
        return distintas.size();
    }

    private int estimarTamanho() {
        return 256 + linhasPuladas.size() * 96 + errosCoercao.size() * 128 + errosAgregados.size() * 96;
    }

    // ---------- Sanitização (AC3 — defesa em profundidade) ----------

    /**
     * Escapa caracteres de controle ({@code < 0x20}) preservando demais bytes:
     * {@code \n}/{@code \r}/{@code \t} viram {@code \\n}/{@code \\r}/{@code \\t};
     * demais control chars viram {@code \\uXXXX}. Caracteres {@code >= 0x20}
     * (incluindo aspas, contrabarra e UTF-8 multi-byte) passam intactos.
     *
     * <p>É <strong>defesa em profundidade</strong>: os métodos públicos
     * {@code registrarX} já validam não-{@code null} e não-blank dos campos
     * obrigatórios. Aceita {@code null} apenas para uso defensivo interno
     * (devolve {@code null}).
     *
     * @param entrada string a sanitizar (pode ser {@code null}).
     * @return string sanitizada; {@code null} se a entrada for {@code null}.
     */
    private static String sanitizar(String entrada) {
        if (entrada == null) {
            return null;
        }
        // Early return: se não há control char, devolve referência original (idempotente).
        boolean precisa = false;
        for (int i = 0; i < entrada.length(); i++) {
            if (entrada.charAt(i) < 0x20) {
                precisa = true;
                break;
            }
        }
        if (!precisa) {
            return entrada;
        }
        StringBuilder out = new StringBuilder(entrada.length() + 8);
        for (int i = 0; i < entrada.length(); i++) {
            char c = entrada.charAt(i);
            if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Acesso package-private ao helper {@link #sanitizar(String)} para testes
     * unitários diretos das categorias de caractere (AC3/AC4). Não usar em
     * produção.
     */
    static String sanitizarParaTeste(String entrada) {
        return sanitizar(entrada);
    }

    // ---------- Validação ----------

    private static void validarLinha(int numeroLinhaExcel) {
        if (numeroLinhaExcel < 1) {
            throw new IllegalArgumentException("Número da linha do Excel deve ser >= 1.");
        }
    }

    private static void validarObrigatorio(String valor, String campo) {
        Objects.requireNonNull(campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Campo '" + campo + "' é obrigatório.");
        }
    }

    // ---------- Records internos (estado) ----------

    private record EntradaLinhaPulada(int linha, String codigo, String motivo) {
    }

    private record EntradaErroCoercao(int linha, String coluna, String valor, String motivo) {
    }

    private record EntradaErrosLinha(int linha, String codigo, List<String> erros) {
    }
}
