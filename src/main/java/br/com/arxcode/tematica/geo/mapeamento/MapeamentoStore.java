package br.com.arxcode.tematica.geo.mapeamento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import br.com.arxcode.tematica.geo.dominio.excecao.MapeamentoIoException;

/**
 * Fronteira de I/O JSON do {@code mapping.json} (FR-07, FR-08; Arquitetura §5).
 *
 * <p>Componente CDI {@link ApplicationScoped} cujo construtor recebe o
 * {@link ObjectMapper} configurado pelo Quarkus ({@code quarkus-jackson}).
 * O padrão "construtor + injeção via parâmetro" permite testes JUnit 5 puros
 * com {@code new MapeamentoStore(new ObjectMapper())} — alinhado à decisão
 * da Story 2.2 de evitar {@code @QuarkusTest} em camadas sem CDI real.
 *
 * <p>Pretty-print é obrigatório na escrita (FR-08): o usuário edita o
 * {@code mapping.json} manualmente entre {@code mapear} e {@code importar}.
 *
 * <p>Story: 3.1 — MapeamentoStore (Jackson JSON I/O do mapping.json).
 */
@ApplicationScoped
public class MapeamentoStore {

    private static final Logger LOG = Logger.getLogger(MapeamentoStore.class);

    private final ObjectMapper objectMapper;

    public MapeamentoStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa {@code m} em JSON pretty-printed UTF-8 e grava em {@code arquivo}.
     *
     * @throws MapeamentoIoException em caso de falha de I/O ou serialização.
     */
    public void salvar(Mapeamento m, Path arquivo) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
            Files.writeString(arquivo, json, StandardCharsets.UTF_8);
            LOG.infof("Mapeamento salvo em %s", arquivo);
        } catch (JsonProcessingException e) {
            throw new MapeamentoIoException(
                    "Falha ao serializar o mapeamento para JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new MapeamentoIoException(
                    "Falha ao gravar o arquivo de mapeamento em " + arquivo + ": " + e.getMessage(), e);
        }
    }

    /**
     * Lê e desserializa o {@code mapping.json} de {@code arquivo} para um
     * {@link Mapeamento} tipado.
     *
     * <p>Ordem dos catches é deliberada (mais específico → mais genérico):
     * {@link InvalidFormatException} (enum desconhecido) →
     * {@link ValueInstantiationException} (validação do construtor do record) →
     * {@link MismatchedInputException} / {@link JsonMappingException}
     * (campo obrigatório ausente) → {@link JsonParseException} (JSON corrompido) →
     * {@link AccessDeniedException} → {@link IOException}.
     *
     * @throws MapeamentoIoException com mensagem em PT identificando o problema.
     */
    public Mapeamento carregar(Path arquivo) {
        if (!Files.exists(arquivo)) {
            throw new MapeamentoIoException("Arquivo de mapeamento não encontrado: " + arquivo);
        }
        String json;
        try {
            json = Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (AccessDeniedException e) {
            throw new MapeamentoIoException(
                    "Arquivo de mapeamento sem permissão de leitura: " + arquivo, e);
        } catch (IOException e) {
            throw new MapeamentoIoException(
                    "Falha ao ler o arquivo de mapeamento em " + arquivo + ": " + e.getMessage(), e);
        }
        try {
            return objectMapper.readValue(json, Mapeamento.class);
        } catch (InvalidFormatException e) {
            String campo = caminhoDoCampo(e);
            Object valor = e.getValue();
            throw new MapeamentoIoException(
                    "Valor inválido para " + campo + ": '" + valor + "'", e);
        } catch (ValueInstantiationException e) {
            // Construtor canônico de record lançou IllegalArgumentException
            // (campo obrigatório nulo, valor inválido, etc.).
            Throwable cause = e.getCause();
            String motivo = cause != null && cause.getMessage() != null
                    ? cause.getMessage()
                    : e.getOriginalMessage();
            throw new MapeamentoIoException(
                    "Campo obrigatório ausente no mapeamento: " + motivo, e);
        } catch (MismatchedInputException e) {
            String campo = caminhoDoCampo(e);
            throw new MapeamentoIoException(
                    "Campo obrigatório ausente no mapeamento: " + campo, e);
        } catch (JsonParseException e) {
            throw new MapeamentoIoException(
                    "JSON do mapeamento inválido em " + arquivo + ": " + e.getOriginalMessage(), e);
        } catch (JsonMappingException e) {
            // Fallback defensivo — todas as subclasses específicas já foram tratadas acima.
            throw new MapeamentoIoException(
                    "Erro ao mapear JSON do mapeamento em " + arquivo + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new MapeamentoIoException(
                    "Falha de I/O ao carregar o mapeamento em " + arquivo + ": " + e.getMessage(), e);
        }
    }

    private static String caminhoDoCampo(JsonMappingException e) {
        var path = e.getPath();
        if (path == null || path.isEmpty()) {
            return "(raiz)";
        }
        StringBuilder sb = new StringBuilder();
        for (var ref : path) {
            String nome = ref.getFieldName();
            if (nome != null) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(nome);
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.length() == 0 ? "(raiz)" : sb.toString();
    }
}
