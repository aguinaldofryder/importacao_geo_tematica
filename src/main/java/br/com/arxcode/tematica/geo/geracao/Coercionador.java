package br.com.arxcode.tematica.geo.geracao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.regex.Pattern;

import br.com.arxcode.tematica.geo.dominio.Tipo;

/**
 * Converte strings cruas vindas do Excel em <strong>literais SQL tipados</strong>
 * para concatenação direta nos arquivos {@code .sql} gerados pelas Stories
 * 4.2 ({@code SqlGeradorUpdate}) e 4.3 ({@code SqlGeradorUpsert}).
 *
 * <p><strong>Função pura.</strong> Sem estado mutável, sem CDI, sem efeitos
 * colaterais — pode ser instanciado uma vez e compartilhado entre threads.
 * O consumidor decide o ciclo de vida.
 *
 * <p><strong>Por que literal e não {@code PreparedStatement}?</strong> O pipeline
 * produz arquivo {@code .sql} para o DBA aplicar (CON-03); não há conexão JDBC
 * em tempo de geração. A segurança contra SQL injection é responsabilidade
 * desta classe — em particular o escape de aspas simples no branch {@code TEXTO}
 * (PostgreSQL com {@code standard_conforming_strings=on}, default desde 9.1).
 *
 * <p><strong>Locale fixo.</strong> Aceita formatos brasileiros (vírgula decimal,
 * data {@code dd/MM/yyyy}) e ISO ({@code yyyy-MM-dd}). Outros locales são
 * out-of-scope para v1 (escopo IPTU municipal brasileiro).
 *
 * <p><strong>Política de erro.</strong> Falhas são devolvidas via
 * {@link ResultadoCoercao#falha(String)} (não exceção), porque o pipeline
 * processa linha-a-linha e precisa continuar quando uma célula é inválida —
 * o erro vai para o {@code .log} (Story 4.4) e o {@code SqlGerador} pula a
 * linha (PRD FR-13).
 *
 * <p>Story: 4.1 — Coerção de tipos.
 */
public final class Coercionador {

    private static final DateTimeFormatter DATA_PT_BR = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter DATA_ISO = DateTimeFormatter
            .ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Regex que aceita um número decimal canônico (após normalização pt-BR → ISO):
     * sinal negativo opcional, dígitos, opcionalmente ponto + dígitos.
     * Rejeita explicitamente notação científica ({@code 1e3}), espaços,
     * letras, sinais positivos explícitos ({@code +1.5}) e ponto trailing
     * ({@code 1.}).
     */
    private static final Pattern DECIMAL_CANONICO = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /** Literal SQL para célula vazia/nula (qualquer tipo). */
    private static final String NULL_LITERAL = "NULL";

    public Coercionador() {
        // Construtor explícito para clareza — função pura, sem dependências.
    }

    /**
     * Coage {@code valor} para o literal SQL apropriado ao {@code tipo}.
     *
     * @param valor         conteúdo cru da célula (pode ser {@code null} ou
     *                      vazio — vira literal {@code NULL})
     * @param tipo          tipo do campo conforme catálogo (Story 2.2)
     * @param alternativas  mapa {@code descrição → idAlternativa} vindo de
     *                      {@code ColunaDinamica.alternativas()} (Story 3.1).
     *                      Aceita {@code null}/vazio para tipos
     *                      {@code != MULTIPLA_ESCOLHA}; obrigatório para
     *                      {@code MULTIPLA_ESCOLHA}.
     * @return resultado pronto para concatenação no SQL ou falha com mensagem PT
     */
    public ResultadoCoercao coagir(String valor, Tipo tipo, Map<String, Integer> alternativas) {
        if (valor == null || valor.trim().isEmpty()) {
            return ResultadoCoercao.ok(NULL_LITERAL);
        }
        return switch (tipo) {
            case TEXTO -> coagirTexto(valor);
            case DECIMAL -> coagirDecimal(valor);
            case DATA -> coagirData(valor);
            case MULTIPLA_ESCOLHA -> coagirMultiplaEscolha(valor, alternativas);
        };
    }

    // ---------- Branches por tipo ----------

    private static ResultadoCoercao coagirTexto(String valor) {
        String t = valor.trim();
        return ResultadoCoercao.ok("'" + SqlEscape.aspas(t) + "'");
    }

    private static ResultadoCoercao coagirDecimal(String valor) {
        String t = valor.trim();
        String normalizado;
        if (t.contains(",")) {
            // Formato pt-BR: ponto é milhar (descartar), vírgula é decimal (vira ponto).
            normalizado = t.replace(".", "").replace(",", ".");
        } else {
            // Formato ISO ou inteiro: ponto já é decimal.
            normalizado = t;
        }
        if (!DECIMAL_CANONICO.matcher(normalizado).matches()) {
            return ResultadoCoercao.falha("valor '" + valor + "' não é decimal válido");
        }
        // Canonicaliza via BigDecimal para evitar notação científica e zeros redundantes.
        BigDecimal bd = new BigDecimal(normalizado);
        return ResultadoCoercao.ok(bd.toPlainString());
    }

    private static ResultadoCoercao coagirData(String valor) {
        String t = valor.trim();
        DateTimeFormatter formatter;
        if (t.contains("/")) {
            formatter = DATA_PT_BR;
        } else if (t.contains("-")) {
            formatter = DATA_ISO;
        } else {
            return ResultadoCoercao.falha(mensagemDataInvalida(valor));
        }
        try {
            LocalDate data = LocalDate.parse(t, formatter);
            return ResultadoCoercao.ok("DATE '" + data.format(DateTimeFormatter.ISO_LOCAL_DATE) + "'");
        } catch (DateTimeParseException e) {
            return ResultadoCoercao.falha(mensagemDataInvalida(valor));
        }
    }

    private static ResultadoCoercao coagirMultiplaEscolha(String valor, Map<String, Integer> alternativas) {
        if (alternativas == null || alternativas.isEmpty()) {
            return ResultadoCoercao.falha("mapeamento ausente para tipo MULTIPLA_ESCOLHA");
        }
        String chave = valor.trim();
        for (Map.Entry<String, Integer> entry : alternativas.entrySet()) {
            String chaveMapa = entry.getKey() == null ? "" : entry.getKey().trim();
            if (chave.equalsIgnoreCase(chaveMapa)) {
                Integer id = entry.getValue();
                if (id == null) {
                    // AutoMapeador.java:213 pode inserir put(valor, null) para alternativas
                    // não resolvidas; Story 3.3 deveria bloquear, mas defendemos contra NPE.
                    return ResultadoCoercao.falha(
                            "alternativa '" + valor + "' encontrada no mapeamento mas com id nulo (mapeamento corrompido)");
                }
                return ResultadoCoercao.ok(String.valueOf(id));
            }
        }
        return ResultadoCoercao.falha("alternativa '" + valor + "' não mapeada");
    }

    // ---------- Helpers ----------

    private static String mensagemDataInvalida(String valor) {
        return "valor '" + valor + "' não é data válida (formatos aceitos: dd/MM/yyyy, yyyy-MM-dd)";
    }
}
