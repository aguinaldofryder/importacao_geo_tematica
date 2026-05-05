package br.com.arxcode.tematica.geo.mapeamento;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import br.com.arxcode.tematica.geo.dominio.Alternativa;
import br.com.arxcode.tematica.geo.dominio.Campo;
import br.com.arxcode.tematica.geo.dominio.Fluxo;

/**
 * Parameter object com os insumos exigidos pelo {@link AutoMapeador#mapear(EntradaAutoMapeamento)}.
 *
 * <p><strong>Por que parameter object</strong> (em vez de parâmetros soltos no método):
 * <ul>
 *   <li>Assinatura de chamada legível e auto-documentada no orquestrador (Story 3.4).</li>
 *   <li>Permite evolução (ex.: novo campo) sem quebrar binário/assinatura.</li>
 *   <li>Imutabilidade total (record + cópia defensiva em {@code campos}).</li>
 * </ul>
 *
 * <p><strong>Slots e contratos:</strong>
 * <ul>
 *   <li>{@code classificacao} — saída da Fase 1 (Story 2.4); fonte de
 *       {@code colunaCodigoImovel}, {@code colunasFixas} e da lista de headers
 *       dinâmicos a auto-mapear.</li>
 *   <li>{@code nomeArquivoPlanilha} — apenas o nome do arquivo (ex.:
 *       {@code "TABELA_PREDIAL_V001.xlsx"}); o orquestrador 3.4 extrai do
 *       {@code Path} recebido pelo CLI antes de montar a entrada.</li>
 *   <li>{@code fluxo} — {@link Fluxo#TERRITORIAL} ou {@link Fluxo#PREDIAL}.</li>
 *   <li>{@code colunaSequenciaPredial} — nome do header da planilha que carrega o
 *       valor de {@code sequencia} (terceiro componente da PK de
 *       {@code tribimobiliariosegmento}). Presente apenas para
 *       {@link Fluxo#PREDIAL}; {@code null} para TERRITORIAL. O
 *       {@link AutoMapeador} repassa este valor ao {@link Mapeamento} produzido,
 *       sem interpretá-lo.</li>
 *   <li>{@code campos} — lista <strong>já filtrada</strong> pelo fluxo (e por
 *       {@code ativo='S'}). O caller deve obtê-la via
 *       {@code CampoRepository.listarPorFluxo(fluxo)} (Story 2.3 AC1).</li>
 *   <li>{@code alternativasPorCampo} — callback que retorna as alternativas de
 *       um {@code idcampo}. Esperado idempotente.</li>
 *   <li>{@code valoresDistintosPorHeader} — callback que retorna os valores
 *       DISTINCT presentes na coluna {@code header} da planilha. Invocado
 *       apenas para headers cujo match resultou em {@code tipo=MULTIPLA_ESCOLHA}.</li>
 * </ul>
 *
 * <p>Story: 3.2 — AutoMapeador.
 */
public record EntradaAutoMapeamento(
        Classificacao classificacao,
        String nomeArquivoPlanilha,
        Fluxo fluxo,
        String colunaSequenciaPredial,
        List<Campo> campos,
        Function<Long, List<Alternativa>> alternativasPorCampo,
        Function<String, Set<String>> valoresDistintosPorHeader) {

    public EntradaAutoMapeamento {
        if (classificacao == null) {
            throw new IllegalArgumentException("classificacao não pode ser nula em EntradaAutoMapeamento");
        }
        if (nomeArquivoPlanilha == null || nomeArquivoPlanilha.isBlank()) {
            throw new IllegalArgumentException("nomeArquivoPlanilha não pode ser nulo ou em branco em EntradaAutoMapeamento");
        }
        if (fluxo == null) {
            throw new IllegalArgumentException("fluxo não pode ser nulo em EntradaAutoMapeamento");
        }
        if (campos == null) {
            throw new IllegalArgumentException("campos não pode ser nulo em EntradaAutoMapeamento");
        }
        if (alternativasPorCampo == null) {
            throw new IllegalArgumentException("alternativasPorCampo não pode ser nulo em EntradaAutoMapeamento");
        }
        if (valoresDistintosPorHeader == null) {
            throw new IllegalArgumentException("valoresDistintosPorHeader não pode ser nulo em EntradaAutoMapeamento");
        }
        campos = List.copyOf(campos);
    }
}
