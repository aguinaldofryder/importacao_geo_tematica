package br.com.arxcode.tematica.geo.mapeamento;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.arxcode.tematica.geo.dominio.Tipo;

/**
 * Forma serializada de uma coluna dinâmica no {@code mapping.json} (Arquitetura §5).
 *
 * <p><strong>Tipos boxed e coleções opcionais.</strong> {@link Integer} e
 * {@code Map}/{@code List} são usados (em vez de {@code int} e estruturas obrigatórias)
 * para que ausência seja representável como {@code null} — pré-requisito da política
 * de serialização {@link JsonInclude.Include#NON_NULL} no nível do record:
 * itens {@link StatusMapeamento#MAPEADO MAPEADO} não emitem {@code motivo}/{@code sugestoes};
 * itens {@link StatusMapeamento#PENDENTE PENDENTE} não emitem {@code idcampo}/{@code tipo}/{@code alternativas}.
 *
 * <p><strong>Validação semântica cross-field</strong> (ex.: {@code MAPEADO} exige
 * {@code idcampo} não-nulo; {@link Tipo#MULTIPLA_ESCOLHA} exige {@code alternativas}
 * não-vazio) <strong>não</strong> ocorre aqui — é responsabilidade da Story 3.3
 * ({@code MapeamentoValidador}). Esta fronteira mantém o store como I/O puro,
 * desacoplado de regra de negócio.
 *
 * <p><strong>Nota sobre {@link JsonInclude.Include#NON_NULL}:</strong> a política
 * afeta apenas a serialização. JSON de entrada com {@code "motivo": null} continua
 * aceito e produz {@code motivo=null} no record (comportamento esperado).
 *
 * <p>Story: 3.1 — MapeamentoStore (Jackson JSON I/O do mapping.json).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColunaDinamica(
        StatusMapeamento status,
        Integer idcampo,
        Tipo tipo,
        Map<String, Integer> alternativas,
        String motivo,
        List<Integer> sugestoes) {

    public ColunaDinamica {
        if (status == null) {
            throw new IllegalArgumentException("status não pode ser nulo em ColunaDinamica");
        }
    }
}
