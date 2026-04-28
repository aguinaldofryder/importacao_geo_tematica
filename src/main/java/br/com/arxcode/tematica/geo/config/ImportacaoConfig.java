package br.com.arxcode.tematica.geo.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "importacao")
public interface ImportacaoConfig {

    Saida saida();

    Mapeamento mapeamento();

    interface Saida {

        @WithDefault("./saida")
        String diretorio();

        @WithDefault("true")
        boolean sufixoTimestamp();
    }

    interface Mapeamento {

        @WithDefault("false")
        boolean caseSensitive();

        @WithDefault("true")
        boolean trimEspacos();
    }
}
