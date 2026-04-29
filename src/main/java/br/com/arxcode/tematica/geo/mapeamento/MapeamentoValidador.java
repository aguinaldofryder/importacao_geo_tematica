package br.com.arxcode.tematica.geo.mapeamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import br.com.arxcode.tematica.geo.dominio.Tipo;
import br.com.arxcode.tematica.geo.dominio.excecao.ImportacaoException;

/**
 * Validação semântica do {@code mapping.json} carregado pelo
 * {@link MapeamentoStore} (FR-09).
 *
 * <p><strong>Fail-all:</strong> todas as pendências são coletadas antes de
 * retornar o resultado — nunca fail-fast — para que o operador veja a lista
 * completa de problemas de uma só vez.
 *
 * <p><strong>Lógica por tipo de coluna:</strong>
 * <ul>
 *   <li>Colunas fixas (AC4): {@code colunasFixas} com destino null/blank → pendência.</li>
 *   <li>Colunas dinâmicas PENDENTE (AC2): motivo ou {@code "status PENDENTE sem motivo"}.</li>
 *   <li>Colunas dinâmicas MAPEADO (AC5a): idcampo nulo → pendência.</li>
 *   <li>Colunas dinâmicas MAPEADO + MULTIPLA_ESCOLHA (AC3): alternativas null, vazias
 *       ou com valores null → pendência por item.</li>
 * </ul>
 *
 * <p>Story: 3.3 — MapeamentoValidador (gate PENDENTE).
 * Consumidores: Story 3.5 (subcomando {@code validar}),
 * Story 4.5 (subcomando {@code importar}).
 */
public class MapeamentoValidador {

    private static final Logger LOG = Logger.getLogger(MapeamentoValidador.class);

    public ResultadoValidacao validar(Mapeamento mapeamento) {
        if (mapeamento == null) {
            throw new ImportacaoException("Mapeamento não pode ser nulo em MapeamentoValidador.validar");
        }
        LOG.debugf("Validando mapeamento: fluxo=%s planilha=%s", mapeamento.fluxo(), mapeamento.planilha());

        List<String> pendencias = new ArrayList<>();

        validarColunasFixas(mapeamento.colunasFixas(), pendencias);
        validarColunasDinamicas(mapeamento.colunasDinamicas(), pendencias);

        LOG.debugf("Validação concluída: %d pendência(s)", pendencias.size());
        return new ResultadoValidacao(pendencias.isEmpty(), pendencias);
    }

    private void validarColunasFixas(Map<String, String> colunasFixas, List<String> pendencias) {
        for (Map.Entry<String, String> e : colunasFixas.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                pendencias.add("Coluna '" + e.getKey() + "': coluna fixa sem coluna-destino definida");
            }
        }
    }

    private void validarColunasDinamicas(Map<String, ColunaDinamica> colunasDinamicas, List<String> pendencias) {
        for (Map.Entry<String, ColunaDinamica> e : colunasDinamicas.entrySet()) {
            String header = e.getKey();
            ColunaDinamica col = e.getValue();

            if (col.status() == StatusMapeamento.PENDENTE) {
                String motivo = col.motivo() != null ? col.motivo() : "status PENDENTE sem motivo";
                pendencias.add("Coluna '" + header + "': " + motivo);
                continue;
            }

            // MAPEADO
            if (col.idcampo() == null) {
                pendencias.add("Coluna '" + header + "': status MAPEADO mas idcampo ausente");
            }

            if (col.tipo() == Tipo.MULTIPLA_ESCOLHA) {
                if (col.alternativas() == null) {
                    pendencias.add("Coluna '" + header + "': tipo MULTIPLA_ESCOLHA sem mapa de alternativas definido");
                } else if (col.alternativas().isEmpty()) {
                    pendencias.add("Coluna '" + header + "': tipo MULTIPLA_ESCOLHA com mapa de alternativas vazio");
                } else {
                    for (Map.Entry<String, Integer> alt : col.alternativas().entrySet()) {
                        if (alt.getValue() == null) {
                            pendencias.add("Coluna '" + header + "': alternativa '" + alt.getKey()
                                    + "' sem idAlternativa mapeado");
                        }
                    }
                }
            }
        }
    }
}
