package br.com.arxcode.tematica.geo.cli;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@QuarkusMain
@Command(
    name = "importacao-geo",
    mixinStandardHelpOptions = true,
    version = "1.0.0-SNAPSHOT",
    description = "Ferramenta de importação de dados de georreferenciamento para o IPTU municipal.",
    subcommands = { CommandLine.HelpCommand.class }
)
public class MainCommand implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this).execute(args);
    }
}
