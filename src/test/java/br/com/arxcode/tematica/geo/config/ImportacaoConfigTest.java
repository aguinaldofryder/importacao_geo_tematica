package br.com.arxcode.tematica.geo.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ImportacaoConfigTest.OverrideProfile.class)
class ImportacaoConfigTest {

    public static class OverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "importacao.saida.diretorio", "/tmp/saida-teste",
                "importacao.saida.sufixo-timestamp", "false"
            );
        }
    }

    private static final List<LogRecord> logsCapturados = new ArrayList<>();
    private static Handler captureHandler;

    @BeforeAll
    static void instalarCapturaDeLogs() {
        captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (logsCapturados) {
                    logsCapturados.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger.getLogger("").addHandler(captureHandler);
    }

    @AfterAll
    static void removerCapturaDeLogs() {
        java.util.logging.Logger.getLogger("").removeHandler(captureHandler);
    }

    @Inject
    ImportacaoConfig config;

    @Test
    void AC1_interfaceInjetadaComSubInterfaces() {
        assertNotNull(config.saida(), "saida() não deve ser nulo");
        assertNotNull(config.mapeamento(), "mapeamento() não deve ser nulo");
    }

    @Test
    void AC5_overrideViaTestProfileAplicado() {
        assertEquals("/tmp/saida-teste", config.saida().diretorio(),
            "diretorio deve ser o valor do TestProfile");
        assertFalse(config.saida().sufixoTimestamp(),
            "sufixoTimestamp deve ser false (override)");
    }

    @Test
    void AC5_defaultsParaPropriedadesNaoSobrescritas() {
        assertFalse(config.mapeamento().caseSensitive(),
            "caseSensitive deve ser false (@WithDefault)");
        assertTrue(config.mapeamento().trimEspacos(),
            "trimEspacos deve ser true (@WithDefault)");
    }

    @Test
    void AC6_NFR07_logNaoContemPasswordEmTextoPlano() {
        // Emite log de uso típico do config, como a aplicação faria
        java.util.logging.Logger.getLogger("br.com.arxcode.tematica.geo")
            .info("Config carregado: saida.diretorio=" + config.saida().diretorio());

        List<LogRecord> registros;
        synchronized (logsCapturados) {
            registros = new ArrayList<>(logsCapturados);
        }

        for (LogRecord registro : registros) {
            String mensagem = registro.getMessage() != null ? registro.getMessage() : "";
            assertFalse(
                mensagem.toLowerCase().contains("password"),
                "Log não deve conter 'password' em texto plano. Encontrado: " + mensagem
            );
        }
    }
}
