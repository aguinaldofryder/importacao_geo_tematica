package br.com.arxcode.tematica.geo.mapeamento;

import java.util.Map;

import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Forma serializada do {@code mapping.json} consumido entre os subcomandos
 * {@code mapear}, {@code validar} e {@code importar} (FR-07, FR-08).
 * Espelha o exemplo canônico da Arquitetura §5.
 *
 * <p><strong>Fronteira de I/O, não de validação semântica.</strong> O construtor
 * canônico valida apenas presença de campos top-level obrigatórios
 * ({@code fluxo}, {@code planilha}, {@code colunaCodigoImovel}); coerência
 * cross-field entre {@link ColunaDinamica#status()} e demais campos opcionais
 * é responsabilidade da Story 3.3 ({@code MapeamentoValidador}).
 *
 * <p><strong>Campo {@code colunaSequenciaPredial}:</strong> presente apenas para
 * o fluxo {@link Fluxo#PREDIAL}, onde a PK de {@code tribimobiliariosegmento}
 * é composta por {@code (tipocadastro, cadastrogeral, sequencia)}. Contém o
 * nome do header da planilha que carrega o valor de {@code sequencia}.
 * Para {@link Fluxo#TERRITORIAL} este campo é {@code null}.
 * Jackson desserializa JSON sem a chave como {@code null} — o construtor
 * canônico não valida nem impõe default, deixando a semântica ao {@code ImportarCommand}.
 *
 * <p><strong>Defesas do construtor:</strong>
 * <ul>
 *   <li>{@code fluxo}, {@code planilha} e {@code colunaCodigoImovel} não-nulos
 *       (mensagem PT em {@link IllegalArgumentException}).</li>
 *   <li>{@code planilha} e {@code colunaCodigoImovel} recebem {@code trim()}
 *       (consistência com a Story 2.2).</li>
 *   <li>{@code colunasFixas} e {@code colunasDinamicas} {@code null}
 *       são substituídos por {@link Map#of()} — Jackson pode produzir {@code null}
 *       em campos JSON ausentes, e código consumidor (Stories 3.2/3.3/4.5)
 *       iteraria sobre {@code null}. Imutabilidade preservada.</li>
 * </ul>
 *
 * <p>Story: 3.1 — MapeamentoStore (Jackson JSON I/O do mapping.json).
 */
public record Mapeamento(
        Fluxo fluxo,
        String planilha,
        String colunaCodigoImovel,
        String colunaSequenciaPredial,
        Map<String, String> colunasFixas,
        Map<String, ColunaDinamica> colunasDinamicas) {

    public Mapeamento {
        if (fluxo == null) {
            throw new IllegalArgumentException("fluxo não pode ser nulo em Mapeamento");
        }
        if (planilha == null) {
            throw new IllegalArgumentException("planilha não pode ser nula em Mapeamento");
        }
        if (colunaCodigoImovel == null) {
            throw new IllegalArgumentException("colunaCodigoImovel não pode ser nula em Mapeamento");
        }
        planilha = planilha.trim();
        colunaCodigoImovel = colunaCodigoImovel.trim();
        colunasFixas = colunasFixas == null ? Map.of() : colunasFixas;
        colunasDinamicas = colunasDinamicas == null ? Map.of() : colunasDinamicas;
    }
}
