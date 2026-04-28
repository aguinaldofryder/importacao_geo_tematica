package br.com.arxcode.tematica.geo.cli;

import picocli.CommandLine;

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
