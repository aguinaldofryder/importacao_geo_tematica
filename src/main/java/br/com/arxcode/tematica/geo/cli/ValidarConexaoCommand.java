package br.com.arxcode.tematica.geo.cli;

import picocli.CommandLine;

@CommandLine.Command(
    name = "validar-conexao",
    description = "Valida a conexão com o banco de dados PostgreSQL.",
    mixinStandardHelpOptions = true
)
public class ValidarConexaoCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("[TODO Story 1.5] comando validar-conexao ainda não implementado");
    }
}
