# Stories — Importação GEO

Índice de stories geradas por River (@sm) a partir do PRD v1.1.

> **Status inicial de todas:** `Draft`. Cada story deve passar por `@po *validate-story-draft X.Y` antes de ir para `@dev`.

## Épico 1 — Fundação da CLI e conexão

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [1.1](./1.1.git-init-ci-minimo.md) | Inicialização de repositório git e pipeline CI mínimo | @devops | 2 h | — |
| [1.2](./1.2.scaffold-quarkus.md) | Scaffold Quarkus + estrutura de pacotes + convenções | @dev | 0,5 h | 1.1 |
| [1.3](./1.3.skeleton-picocli.md) | Esqueleto picocli (subcomandos stub) | @dev | 0,3 h | 1.2 |
| [1.4](./1.4.config-externa-properties.md) ✅ | @ConfigMapping + leitura de `./config/application.properties` | @dev | 0,5 h | 1.3 |
| [1.5](./1.5.datasource-validar-conexao.md) | Datasource PostgreSQL + comando `validar-conexao` | @dev | 1,5 h | 1.4 |

## Épico 2 — Leitura de planilhas e catálogo

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [2.1](./2.1.excel-leitor-streaming.md) | ExcelLeitor com fastexcel-reader (streaming) | @dev | 1 h | 1.2 |
| [2.2](./2.2.dominio-pojos-enums.md) | Domínio: enums e records (Campo, Alternativa, GrupoCampo, Fluxo, Tipo) | @dev | 0,3 h | 1.2 |
| [2.3](./2.3.repositorios-jdbc-catalogo.md) | Repositórios JDBC do catálogo (read-only) | @dev | 1 h | 1.5, 2.2 |
| [2.4](./2.4.classificador-colunas.md) | ClassificadorColunas | @dev | 0,5 h | 2.1, 2.2 |

## Épico 3 — Fase de mapeamento

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [3.1](./3.1.mapeamento-store-json.md) ✅ | MapeamentoStore (Jackson JSON I/O) | @dev | 0,3 h | 1.2, 2.2 |
| [3.2](./3.2.auto-mapeador.md) ✅ | AutoMapeador (header ↔ campo, valor ↔ alternativa) | @dev | 1,5 h | 2.1, 2.3, 2.4, 3.1 |
| [3.3](./3.3.mapeamento-validador.md) ✅ | MapeamentoValidador (gate PENDENTE) | @dev | 1 h | 3.1 |
| [3.4](./3.4.comando-mapear.md) | Comando `mapear` | @dev | 1 h | 2.3, 2.4, 3.2 |
| [3.5](./3.5.comando-validar.md) | Comando `validar` | @dev | 0,3 h | 3.3 |

## Épico 4 — Geração de SQL e execução

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [4.1](./4.1.coercao-tipos.md) ✅ | Coerção de tipos (TEXTO/DECIMAL/DATA/MULTIPLA_ESCOLHA) | @dev | 1 h | 2.2 |
| [4.2](./4.2.sql-gerador-update.md) ✅ | SqlGerador UPDATE (tabela principal) | @dev | 1 h | 4.1 |
| [4.2.1](./4.2.1.tipagem-completa-colunas-fixas.md) 📋 | Tipagem completa das colunas fixas (DECIMAL/DATA) — Backlog, refino pós-4.5 | @dev | 3–5 h | 4.2, 4.3, 4.4, 4.5 |
| [4.3](./4.3.sql-gerador-upsert.md) | SqlGerador UPSERT (tabela de respostas) | @dev | 1 h | 4.1 |
| [4.4](./4.4.log-erros-estruturado.md) | LogErros estruturado | @dev | 0,3 h | 1.2 |
| [4.5](./4.5.comando-importar.md) | Comando `importar` | @dev | 1,5 h | 3.3, 3.4, 4.1, 4.2, 4.3, 4.4 |

## Épico 5 — Resumo pós-importação

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [5.1](./5.1.resumo-execucao-contadores.md) | ResumoExecucao (contadores) | @dev | 0,3 h | 4.5 |
| [5.2](./5.2.resumo-formatacao-exit-codes.md) | Resumo: formatação ASCII + JSON + exit codes | @dev | 0,3 h | 5.1 |

## Épico 6 — Build nativo, testes E2E e distribuição

| ID | Título | Assignee | Estim. | Depende |
|---|---|---|---:|---|
| [6.1](./6.1.perfil-native-reflect-config.md) | Perfil native + reflect-config (build local) | @dev | 1 h | 4.5 |
| [6.2](./6.2.ci-matrix-linux-windows.md) | GitHub Actions matrix Linux + Windows | @devops | 1,5 h | 6.1 |
| [6.3](./6.3.e2e-binario-nativo.md) | Suite E2E sobre o binário nativo | @qa + @dev | 2 h | 5.2, 6.2 |
| [6.4](./6.4.manual-operador.md) | MANUAL.md (operador) | @pm + @architect | 1 h | 5.2 |
| [6.5](./6.5.runbook-dba.md) | RUNBOOK-DBA.md | @pm + @architect + DBA | 1 h | 4.5 |
| [6.6](./6.6.github-release-automation.md) | GitHub Release automation | @devops | 1 h | 6.2, 6.3, 6.4, 6.5 |

---

## Totais

| Épico | Stories | Horas estimadas (IA) |
|---|---:|---:|
| 1 | 5 | 4,8 |
| 2 | 4 | 2,8 |
| 3 | 5 | 3,4 |
| 4 | 5 | 4,8 |
| 5 | 2 | 0,6 |
| 6 | 6 | 7,5 |
| **Total** | **27** | **~24 h** |

> Nota: total das stories ≈ 24 h. O `orcamento.md` ficou em ~41,5 h porque inclui contingência (15%) e a Fase 10 (execução em produção + conferência, 9 h) que está fora das stories de desenvolvimento.

## Grafo de dependências (resumido)

```
1.1 → 1.2 → 1.3 → 1.4 → 1.5
              │         │
              │         └── 2.3 ──┐
              ├── 2.1 ─────┬── 2.4┤
              └── 2.2 ─────┘      │
                                  └──→ 3.2 → 3.4 ┐
                 3.1 → 3.3 → 3.5               │
                          │                    │
                          └─── 4.5 ←─ 4.1 → 4.2┘
                                      │    → 4.3
                                      └────→ 4.4
                                                │
                                         5.1 → 5.2 ┐
                                                   │
                                  6.1 → 6.2 → 6.3 ─┤
                                  6.4 ─────────────┤
                                  6.5 ─────────────┤
                                                   └─→ 6.6
```

## Marcos

| Marco | Fecha quando | Stories envolvidas |
|---|---|---|
| **M1** — CLI JVM + mapeamento | 3.5 verde | 1.1–1.5, 2.1–2.4, 3.1–3.5 |
| **M2** — SQL aplicável com resumo | 5.2 verde | 4.1–4.5, 5.1–5.2 |
| **M3** — Binários nativos + docs | 6.6 verde | 6.1–6.6 |
| **M4** — Importação em produção | (operacional) | — |

## Próximos passos

1. **@po (Pax):** validar cada story via `*validate-story-draft X.Y` na ordem.
2. **@devops (Gage):** assumir Story 1.1.
3. Seguir linearmente respeitando dependências do grafo.
