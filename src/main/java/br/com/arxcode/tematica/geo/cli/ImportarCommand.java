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
    name = "importar",
    description = "Executa a importação da planilha para o banco de dados.",
    mixinStandardHelpOptions = true
)
public class ImportarCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("[TODO Story 4.5] comando importar ainda não implementado");
    }
}
