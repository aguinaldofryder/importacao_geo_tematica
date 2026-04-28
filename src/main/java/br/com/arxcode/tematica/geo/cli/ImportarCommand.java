package br.com.arxcode.tematica.geo.cli;

import picocli.CommandLine;

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
