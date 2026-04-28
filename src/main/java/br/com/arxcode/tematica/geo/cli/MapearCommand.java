package br.com.arxcode.tematica.geo.cli;

import picocli.CommandLine;

@CommandLine.Command(
    name = "mapear",
    description = "Gera o arquivo mapping.json com o mapeamento de colunas.",
    mixinStandardHelpOptions = true
)
public class MapearCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("[TODO Story 3.4] comando mapear ainda não implementado");
    }
}
