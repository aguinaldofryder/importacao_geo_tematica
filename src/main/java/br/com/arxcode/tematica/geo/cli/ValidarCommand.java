package br.com.arxcode.tematica.geo.cli;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import picocli.CommandLine;

// @Dependent evita o client proxy do CDI, necessário para que picocli possa
// injetar @CommandLine.Spec diretamente na instância real (não num wrapper).
// @Unremovable impede que o ARC elimine o bean em modo prod (ele é resolvido
// via lookup programático pelo PicocliBeansFactory, e o build não detecta o uso).
@Dependent
@Unremovable
@CommandLine.Command(
    name = "validar",
    description = "Valida o mapping.json contra as colunas da planilha.",
    mixinStandardHelpOptions = true
)
public class ValidarCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("[TODO Story 3.5] comando validar ainda não implementado");
    }
}
