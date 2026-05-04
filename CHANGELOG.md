# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

> **Regras de versionamento:**
> - **MAJOR** — quebra de compatibilidade (novo formato de planilha, remoção de subcomando, alteração de schema SQL gerado)
> - **MINOR** — nova funcionalidade retro-compatível (novo subcomando, novo fluxo, nova opção)
> - **PATCH** — correção de bug retro-compatível

---

## [Unreleased]

### Adicionado
- Automação de GitHub Release via workflow `native-build.yml` (Story 6.6)

---

## [1.0.0] — Previsto para fechamento do Épico 6

### Adicionado
- CLI `importacao-geo` com subcomandos: `validar-conexao`, `mapear`, `validar`, `importar`
- Fluxo Territorial: leitura de `TABELA_TERRITORIAL_V001.xlsx`, UPDATE em `tribcadastroimobiliario`, UPSERT em `respostaterreno`
- Fluxo Predial: leitura de `TABELA_PREDIAL_V001.xlsx`, UPDATE em `tribimobiliariosegmento`, UPSERT em `respostasegmento`
- Mapeamento dinâmico de colunas via tabela `campo` (tipos: `TEXTO`, `DECIMAL`, `DATA`, `MULTIPLA_ESCOLHA`)
- Geração de arquivos `.sql` e `.log` sem execução direta de DML em produção
- Build nativo GraalVM para Linux x86-64 e Windows x86-64
- Suite E2E offline validando todos os subcomandos contra o binário nativo
- `MANUAL.md` — manual completo do operador
- `RUNBOOK-DBA.md` — procedimentos de banco de dados
- `application.properties.exemplo` — template de configuração

[Unreleased]: https://github.com/arxcode/importacao-geo/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/arxcode/importacao-geo/releases/tag/v1.0.0
