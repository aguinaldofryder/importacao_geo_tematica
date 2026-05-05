package br.com.arxcode.tematica.geo.mapeamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.arxcode.tematica.geo.dominio.Fluxo;
import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.MapeamentoIoException;

/**
 * Testes da fronteira de I/O JSON do {@code mapping.json}.
 *
 * <p>Cobertura AC10 itens (a)–(f).
 */
class MapeamentoStoreTest {

    private MapeamentoStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        store = new MapeamentoStore(objectMapper);
    }

    @Test
    void roundTrip_preservaConteudo(@TempDir Path tmp) {
        // AC10 (a) — round-trip salvar→carregar preserva conteúdo
        Mapeamento original = new Mapeamento(
                Fluxo.TERRITORIAL,
                "TABELA_TERRITORIAL_V001.xlsx",
                "MATRICULA",
                null,
                Map.of("AREA_TERRENO", "area_terreno", "TESTADA", "testada_principal"),
                Map.of(
                        "TIPO_MURO", new ColunaDinamica(
                                StatusMapeamento.MAPEADO, 142, Tipo.MULTIPLA_ESCOLHA,
                                Map.of("Alvenaria", 501, "Madeira", 502), null, null),
                        "COLUNA_NOVA", new ColunaDinamica(
                                StatusMapeamento.PENDENTE, null, null, null,
                                "Nenhum campo encontrado com descricao='COLUNA_NOVA'",
                                List.of(140, 171))));

        Path arquivo = tmp.resolve("mapping.json");
        store.salvar(original, arquivo);

        Mapeamento recarregado = store.carregar(arquivo);
        assertEquals(original, recarregado);
    }

    @Test
    void carregar_arquivoAusente_lancaMapeamentoIoExceptionEmPt(@TempDir Path tmp) {
        // AC10 (b) — arquivo ausente
        Path arquivo = tmp.resolve("nao-existe.json");

        MapeamentoIoException ex = assertThrows(MapeamentoIoException.class,
                () -> store.carregar(arquivo));
        assertTrue(ex.getMessage().contains("Arquivo de mapeamento não encontrado"),
                "mensagem deve indicar arquivo ausente em PT: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(arquivo.toString()),
                "mensagem deve conter o caminho: " + ex.getMessage());
    }

    @Test
    void carregar_jsonCorrompido_lancaMapeamentoIoExceptionEmPt(@TempDir Path tmp) throws IOException {
        // AC10 (c) — JSON sintaticamente inválido
        Path arquivo = tmp.resolve("corrompido.json");
        Files.writeString(arquivo, "{ isto não é JSON válido", StandardCharsets.UTF_8);

        MapeamentoIoException ex = assertThrows(MapeamentoIoException.class,
                () -> store.carregar(arquivo));
        assertTrue(ex.getMessage().contains("JSON do mapeamento inválido"),
                "mensagem deve indicar JSON inválido em PT: " + ex.getMessage());
    }

    @Test
    void carregar_campoFluxoAusente_lancaMapeamentoIoExceptionEmPt(@TempDir Path tmp) throws IOException {
        // AC10 (d) — campo obrigatório `fluxo` ausente
        Path arquivo = tmp.resolve("sem-fluxo.json");
        Files.writeString(arquivo,
                "{ \"planilha\": \"X.xlsx\", \"colunaCodigoImovel\": \"MAT\", \"colunasFixas\": {}, \"colunasDinamicas\": {} }",
                StandardCharsets.UTF_8);

        MapeamentoIoException ex = assertThrows(MapeamentoIoException.class,
                () -> store.carregar(arquivo));
        assertTrue(ex.getMessage().contains("Campo obrigatório ausente no mapeamento"),
                "mensagem deve indicar campo ausente em PT: " + ex.getMessage());
    }

    @Test
    void carregar_statusInvalido_lancaMapeamentoIoExceptionEmPtComValor(@TempDir Path tmp) throws IOException {
        // AC10 (e) — enum desconhecido
        Path arquivo = tmp.resolve("status-invalido.json");
        Files.writeString(arquivo,
                "{ \"fluxo\": \"TERRITORIAL\", \"planilha\": \"X.xlsx\", \"colunaCodigoImovel\": \"MAT\","
                        + "\"colunasFixas\": {},"
                        + "\"colunasDinamicas\": { \"X\": { \"status\": \"INVALIDO\" } } }",
                StandardCharsets.UTF_8);

        MapeamentoIoException ex = assertThrows(MapeamentoIoException.class,
                () -> store.carregar(arquivo));
        assertTrue(ex.getMessage().contains("Valor inválido"),
                "mensagem deve indicar valor inválido em PT: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("INVALIDO"),
                "mensagem deve conter o valor inválido: " + ex.getMessage());
    }

    @Test
    void fixtureCanonica_roundTripPreservaSemanticaJson(@TempDir Path tmp) throws IOException {
        // AC10 (f) — fidelidade ao §5 via comparação semântica JsonNode
        Path fixture = Path.of("src/test/resources/mapeamento/mapping-exemplo-territorial.json");
        Mapeamento parseado = store.carregar(fixture);

        Path saida = tmp.resolve("re-serializado.json");
        store.salvar(parseado, saida);

        JsonNode arvoreOriginal = objectMapper.readTree(fixture.toFile());
        JsonNode arvoreReserializada = objectMapper.readTree(saida.toFile());
        assertEquals(arvoreOriginal, arvoreReserializada);
    }
}
