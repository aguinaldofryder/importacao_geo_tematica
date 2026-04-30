package br.com.arxcode.tematica.geo.cli;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.arxcode.tematica.geo.dominio.excecao.MapeamentoIoException;
import br.com.arxcode.tematica.geo.mapeamento.Mapeamento;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoStore;
import br.com.arxcode.tematica.geo.mapeamento.MapeamentoValidador;
import br.com.arxcode.tematica.geo.mapeamento.ResultadoValidacao;

/**
 * Subcomando {@code validar} — gate offline de completude e consistência
 * semântica do {@code mapping.json} (FR-07, FR-08, FR-09).
 *
 * <p><strong>Pipeline (3 etapas — fail-fast):</strong>
 * <ol>
 *   <li>Validação do arquivo — {@link Files#exists(Path, java.nio.file.LinkOption...)};
 *       ausência → exit 1 com caminho absoluto na mensagem (AC3, AC12).</li>
 *   <li>Carga e parsing — {@link MapeamentoStore#carregar(Path)} (Story 3.1);
 *       qualquer {@link MapeamentoIoException} → exit 1 (AC3).</li>
 *   <li>Validação semântica — {@link MapeamentoValidador#validar(Mapeamento)}
 *       (Story 3.3); {@link ResultadoValidacao#valido()} controla relatório
 *       e exit code (AC5, AC6, AC7).</li>
 * </ol>
 *
 * <p><strong>Comando puramente offline</strong> (AC4): não injeta
 * {@code DataSource} nem repositórios; viabiliza revisão de
 * {@code mapping.json} em CI / ambientes sem acesso ao banco.
 *
 * <p><strong>Exit codes</strong> (FR-16): {@code 0} sucesso; {@code 1} arquivo
 * ausente, JSON inválido ou pendências semânticas. Não há exit {@code 2}
 * nesta story.
 *
 * <p>Story: 3.5 — Comando {@code validar} (gate FR-09 offline). Fecha o
 * Marco M1 (mapping completo).
 */
// @Dependent + @Unremovable: evita client proxy CDI (necessário para
// @CommandLine.Spec na instância real) e impede ARC de eliminar o bean
// (registro programático via PicocliBeansFactory). Mesmo padrão das Stories
// 1.4 (ValidarConexaoCommand) e 3.4 (MapearCommand).
@Dependent
@Unremovable
@CommandLine.Command(
    name = "validar",
    description = "Valida a completude e consistência semântica do mapping.json (FR-09). Comando offline — não acessa o banco.",
    mixinStandardHelpOptions = true
)
public class ValidarCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ValidarCommand.class);

    /**
     * Instância única JVM-wide do validador. {@link MapeamentoValidador} é
     * stateless (decisão Story 3.3 AC10: não-CDI; mesma estratégia de
     * {@code AutoMapeador} e {@code ClassificadorColunas}). {@code static final}
     * evita {@code new} a cada invocação do comando (AC10).
     */
    private static final MapeamentoValidador VALIDADOR = new MapeamentoValidador();

    /**
     * Padrão para extrair o header do prefixo {@code "Coluna 'X': motivo"}
     * emitido por {@link MapeamentoValidador} (Story 3.3 AC6). Defesa em
     * profundidade (AC7): pendências que não casarem este padrão são
     * agrupadas sob {@code "(sem header)"} mantendo a mensagem completa.
     */
    private static final Pattern PREFIXO_COLUNA = Pattern.compile("^Coluna '([^']+)':\\s*(.*)$");

    @Option(
        names = {"-m", "--mapeamento"},
        defaultValue = "./mapping.json",
        description = "Caminho do mapping.json a validar (padrão: ./mapping.json)."
    )
    Path mapeamento;

    @Inject
    MapeamentoStore mapeamentoStore;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Path absoluto = mapeamento.toAbsolutePath().normalize();
        LOG.infof("Iniciando validação: arquivo=%s", absoluto);

        // (1) Validação do arquivo ------------------------------------------
        if (!Files.exists(mapeamento)) {
            err.println("✗ Arquivo de mapeamento não encontrado: " + absoluto);
            return 1;
        }

        // (2) Carga e parsing -----------------------------------------------
        Mapeamento m;
        try {
            m = mapeamentoStore.carregar(mapeamento);
        } catch (MapeamentoIoException e) {
            err.println("✗ Mapeamento inválido: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("✗ Falha inesperada: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            LOG.error("Falha inesperada ao carregar mapping.json", e);
            return 1;
        }
        LOG.infof("Mapeamento carregado: fluxo=%s, planilha=%s, %d coluna(s) dinâmica(s), %d coluna(s) fixa(s)",
            m.fluxo(), m.planilha(), m.colunasDinamicas().size(), m.colunasFixas().size());

        // (3) Validação semântica -------------------------------------------
        ResultadoValidacao resultado;
        try {
            resultado = VALIDADOR.validar(m);
        } catch (Exception e) {
            err.println("✗ Falha inesperada: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            LOG.error("Falha inesperada na validação semântica", e);
            return 1;
        }
        LOG.infof("Validação concluída: valido=%s, %d pendência(s)",
            resultado.valido(), resultado.pendencias().size());

        if (resultado.valido()) {
            imprimirSucesso(out);
            return 0;
        }

        imprimirPendencias(err, resultado, absoluto);
        return 1;
    }

    /** AC6: relatório de sucesso em uma única linha ao stdout. */
    private void imprimirSucesso(PrintWriter out) {
        out.println("✓ Mapeamento completo. Pronto para importar.");
    }

    /** AC7: relatório de falha em 3 blocos ao stderr, agrupado por header. */
    private void imprimirPendencias(PrintWriter err, ResultadoValidacao resultado, Path absoluto) {
        int total = resultado.pendencias().size();
        // (a) cabeçalho
        err.println("✗ Mapeamento incompleto. " + total + " pendência(s) encontrada(s):");
        err.println();

        // (b) lista agrupada por header
        Map<String, List<String>> agrupado = agruparPendenciasPorHeader(resultado.pendencias());
        for (Map.Entry<String, List<String>> e : agrupado.entrySet()) {
            err.println("  - " + e.getKey() + ":");
            for (String motivo : e.getValue()) {
                err.println("      • " + motivo);
            }
        }
        err.println();

        // (c) rodapé
        err.println("Edite o arquivo manualmente (FR-08) e rode 'validar' novamente: " + absoluto);
    }

    /**
     * Agrupa as pendências pelo header extraído do prefixo {@code "Coluna 'X': "}
     * (formato canônico do {@link MapeamentoValidador}). Pendências que não
     * casarem o padrão (defesa contra mudança futura no contrato) são
     * agrupadas sob {@code "(sem header)"} preservando a mensagem completa.
     *
     * <p>{@link LinkedHashMap} preserva a ordem de inserção — alinhada à ordem
     * de emissão do {@link MapeamentoValidador} (fixas primeiro, depois
     * dinâmicas).
     */
    private Map<String, List<String>> agruparPendenciasPorHeader(List<String> pendencias) {
        Map<String, List<String>> agrupado = new LinkedHashMap<>();
        for (String p : pendencias) {
            Matcher mt = PREFIXO_COLUNA.matcher(p);
            String header;
            String motivo;
            if (mt.matches()) {
                header = mt.group(1);
                motivo = mt.group(2);
            } else {
                header = "(sem header)";
                motivo = p;
            }
            agrupado.computeIfAbsent(header, k -> new ArrayList<>()).add(motivo);
        }
        return agrupado;
    }
}
