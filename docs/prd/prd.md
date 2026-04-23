# PRD — Ferramenta de Importação GEO

> **Versão:** 1.1
> **Autor:** Morgan (@pm)
> **Status:** Revisado após validação do @po (C1 + H1–H3 + M1–M3 endereçados)
> **Fontes:** [`README.md`](../../README.md), [`docs/architecture/arquitetura.md`](../architecture/arquitetura.md)
> **Rastreabilidade (Constitution Art. IV):** todo requisito abaixo deriva das fontes listadas — nenhum inventado.
>
> **Changelog:**
> - v1.1 — adicionados FR-18 a FR-20 (documentação e distribuição), NFR-09 e NFR-10 (testes), premissa P3 (schema externo estável), seção 14 (estratégia de testes); ajustes nos épicos 1, 3, 4, 5 e 6.

---

## 1. Contexto e problema

A Prefeitura mantém dois cadastros tributários no banco de IPTU:

- **Cadastro Imobiliário (territorial)** — tabela `tribcadastroimobiliario`, com respostas dinâmicas em `respostaterreno`.
- **Cadastro de Segmentos Prediais** — tabela `tribimobiliariosegmento`, com respostas dinâmicas em `respostasegmento`.

A equipe de georreferenciamento realiza levantamentos em campo e entrega **planilhas Excel** (`TABELA_TERRITORIAL_V001.xlsx`, `TABELA_PREDIAL_V001.xlsx`) com dados atualizados sobre imóveis **já cadastrados**. O set de colunas dessas planilhas **varia entre versões** — novas colunas aparecem, outras somem, e os valores das colunas de múltipla escolha flutuam.

Hoje, a aplicação dessas planilhas ao banco é manual, sujeita a erro humano, sem rastreabilidade e sem garantia de que colunas desconhecidas serão corretamente mapeadas. Aplicações incorretas podem corromper cadastros fiscais com impacto direto no lançamento de IPTU.

## 2. Objetivos

| # | Objetivo |
|---|---|
| O1 | Automatizar a geração de comandos SQL a partir das planilhas de campo, eliminando transcrição manual. |
| O2 | Impedir que colunas/valores desconhecidos passem despercebidos — o sistema deve exigir mapeamento explícito antes de gerar SQL. |
| O3 | Preservar a integridade das tabelas principais: **nunca** inserir novas linhas — apenas atualizar registros existentes. |
| O4 | Prover rastreabilidade total (arquivo `.sql` revisável, arquivo `.log` de erros, resumo de execução). |
| O5 | Distribuir a ferramenta como binário nativo único por SO (Linux + Windows), sem necessidade de JVM instalada. |

## 3. Personas e stakeholders

### 3.1. Persona primária: Operador de Importação (TI tributária)

- Equipe de TI da prefeitura responsável por aplicar as planilhas ao banco.
- Conhece SQL e o modelo de dados do IPTU em nível intermediário.
- Trabalha em terminal/linha de comando em ambiente Linux ou Windows.
- Não tem acesso a ambientes de build (consome binário pronto).

### 3.2. Persona secundária: Equipe de Georreferenciamento

- Produz as planilhas de origem.
- Não interage com a ferramenta, mas é fonte das variações de esquema.

### 3.3. Stakeholders

- **Secretaria de Finanças** — dona do dado tributário, patrocinadora.
- **DBA do IPTU** — aplica o `.sql` em produção, audita resultado.
- **TI Corporativa** — valida distribuição do binário e requisitos de ambiente.

## 4. Escopo

### 4.1. Dentro do escopo (IN)

- CLI com subcomandos: `validar-conexao`, `mapear`, `validar`, `importar`.
- Leitura de planilhas `.xlsx` (apenas formato moderno).
- Consulta ao catálogo (`campo`, `alternativa`, `grupocampo`) no banco PostgreSQL de IPTU.
- Geração de `mapping.json` (auto-mapeamento + edição manual).
- Geração de arquivo `.sql` com `UPDATE` (tabelas principais) e `INSERT ... ON CONFLICT` (tabelas de respostas).
- Geração de arquivo `.log` com erros de linha.
- Resumo pós-importação (contadores, duração, exit codes).
- Configuração externa via `./config/application.properties`.
- Binário nativo Linux + Windows.

### 4.2. Fora do escopo (OUT)

- GUI (interface gráfica).
- Aplicação direta do SQL no banco (a ferramenta **apenas gera** — a aplicação é feita por DBA externamente).
- Suporte ao formato `.xls` antigo.
- Instaladores `.deb` / `.msi` (v1 entrega binário solto).
- Tela de administração de `campo` / `alternativa`.
- Importação de dados **novos** para tabelas principais.
- Integração com ferramentas de ETL existentes.
- Suporte a outros SGBDs além de PostgreSQL.

## 5. Requisitos funcionais (FR)

| ID | Requisito |
|---|---|
| **FR-01** | A ferramenta deve oferecer CLI com subcomandos `validar-conexao`, `mapear`, `validar` e `importar`. |
| **FR-02** | Todo subcomando que acessa o banco deve exigir conexão PostgreSQL válida antes de prosseguir, falhando-rápido se a configuração for ausente ou inválida. |
| **FR-03** | A configuração de conexão e comportamento deve ser lida de `./config/application.properties`, externa ao binário. |
| **FR-04** | `mapear` deve classificar as colunas da planilha em: coluna de código do imóvel, colunas fixas (tabela principal) e colunas dinâmicas (tabela `campo`). |
| **FR-05** | `mapear` deve tentar auto-mapeamento comparando cabeçalhos da planilha com `campo.descricao`, filtrado por `grupocampo.funcionalidade` conforme o fluxo (`TERRENO` ou `SEGMENTO`) e por `campo.ativo = 'S'`. |
| **FR-06** | Para colunas do tipo `MULTIPLA_ESCOLHA`, `mapear` deve extrair valores distintos da planilha e tentar casá-los com `alternativa.descricao` (filtro `alternativa.idcampo`). |
| **FR-07** | Itens não resolvidos automaticamente devem ser marcados como `PENDENTE` no `mapping.json`, com motivo e, quando possível, sugestões. |
| **FR-08** | O usuário deve poder editar `mapping.json` manualmente para preencher itens `PENDENTE`. |
| **FR-09** | `validar` e `importar` devem recusar execução se houver qualquer item `PENDENTE` no `mapping.json`. |
| **FR-10** | `importar` deve, para cada linha da planilha, verificar se o código do imóvel existe na tabela principal. Se não existir, registrar erro no `.log` e **não** gerar SQL para essa linha. |
| **FR-11** | `importar` deve gerar `UPDATE` nas tabelas principais **somente para colunas fixas mapeadas**, sem nunca emitir `INSERT` para `tribcadastroimobiliario` ou `tribimobiliariosegmento`. |
| **FR-12** | `importar` deve gerar `INSERT ... ON CONFLICT (referencia, idcampo) DO UPDATE` para os campos dinâmicos na tabela de respostas correspondente ao fluxo. |
| **FR-13** | `importar` deve aplicar coerção de valores conforme `campo.tipo` (`TEXTO`, `DECIMAL`, `DATA`, `MULTIPLA_ESCOLHA`), registrando erro no `.log` em falhas. |
| **FR-14** | `importar` deve gerar resumo final com: linhas lidas, com sucesso, com erro, `UPDATE`s na tabela principal, respostas atualizadas, respostas inseridas, total de respostas, duração. |
| **FR-15** | O resumo deve ser impresso no terminal (ASCII) e anexado ao `.log` (texto + linha JSON estruturada). |
| **FR-16** | Exit codes: `0` = sem erros, `2` = execução completa com erros por linha, `1` = falha de configuração / conexão / mapping inválido. |
| **FR-17** | Os artefatos de saída (`.sql` e `.log`) devem ser gravados no diretório configurado, com nome incluindo fluxo e timestamp. |
| **FR-18** | Cada release deve incluir `MANUAL.md` em português cobrindo: configuração do `application.properties`, fluxo completo `validar-conexao` → `mapear` → edição manual → `validar` → `importar`, interpretação do resumo e do `.log`, e tabela de exit codes. |
| **FR-19** | Cada release deve incluir `RUNBOOK-DBA.md` em português, cobrindo: checklist de conferência do `.sql` antes da aplicação, procedimento de aplicação em homologação, procedimento em produção, queries de sanidade pós-aplicação, e procedimento de rollback. |
| **FR-20** | Os binários nativos (Linux + Windows) devem ser publicados automaticamente como **GitHub Release versionada** a cada tag `vX.Y.Z`, acompanhados de `MANUAL.md`, `RUNBOOK-DBA.md` e `application.properties.exemplo`. |

## 6. Requisitos não-funcionais (NFR)

| ID | Requisito |
|---|---|
| **NFR-01** | O conteúdo voltado ao usuário (logs, prompts, mensagens de erro, resumo) deve estar em **português**. |
| **NFR-02** | A ferramenta deve rodar nativamente em **Linux x64** e **Windows x64**, sem exigir JVM instalada. |
| **NFR-03** | Startup da ferramenta (tempo até primeiro comando executável) deve ser inferior a 500 ms no binário nativo. |
| **NFR-04** | A leitura de planilhas deve ser **streaming** (linha-a-linha), suportando arquivos com pelo menos 100.000 linhas sem exceder 512 MB de memória. |
| **NFR-05** | O binário nativo não deve exceder 100 MB por plataforma. |
| **NFR-06** | O `.sql` gerado deve ser **idempotente** na tabela de respostas (reexecutar produz o mesmo estado final). |
| **NFR-07** | Todas as credenciais de banco devem ser lidas exclusivamente do arquivo de configuração — nunca hardcoded ou logadas. |
| **NFR-08** | O `.log` deve ser parseável linha-a-linha e incluir timestamp ISO-8601 em cada entrada. |
| **NFR-09** | Cobertura de testes unitários ≥ **70%** nos módulos críticos: coerção de tipos, geração de SQL (UPDATE + UPSERT), auto-mapeador e validador de `mapping.json`. Build de release falha se a cobertura nesses módulos cair abaixo do mínimo. |
| **NFR-10** | Suite E2E obrigatória antes de cada release: executa o **binário nativo** (não a build JVM) contra amostra reduzida das duas planilhas (`territorial` e `predial`) e um banco PostgreSQL de teste, validando que o `.sql` gerado é sintaticamente válido e que o resumo tem os contadores esperados. |

## 7. Restrições (CON)

| ID | Restrição |
|---|---|
| **CON-01** | Banco de dados alvo é **exclusivamente PostgreSQL**. |
| **CON-02** | A ferramenta **nunca** aplica SQL diretamente no banco — apenas gera arquivo. A aplicação é responsabilidade do DBA. |
| **CON-03** | A ferramenta **nunca** insere novas linhas em `tribcadastroimobiliario` ou `tribimobiliariosegmento`. |
| **CON-04** | Esquema das planilhas **não é estável**: cada execução deve refazer a fase de mapeamento. |
| **CON-05** | Stack técnica: Quarkus + GraalVM native-image + `fastexcel-reader` + picocli (definido em `arquitetura.md`). |
| **CON-06** | Distribuição v1: binário único por SO; instaladores ficam para v2. |

## 8. Épicos propostos

Proposta de quebra em épicos. Cada épico é independentemente entregável e atravessa um marco técnico claro. `@sm` irá desmembrar cada um em stories.

### Épico 1 — Fundação da CLI e conexão
**Objetivo:** scaffold do projeto Quarkus + picocli, inicialização do repositório git, definição de convenções de código, carregamento de configuração externa, conexão PostgreSQL validável.
**Entrega:** binário JVM com `validar-conexao` funcional, repositório git inicializado com CI mínimo (lint + testes), documento de convenções de código.
**Inclui:** (a) `*environment-bootstrap` via @devops para inicialização do repo; (b) definição dos pacotes base e convenções (naming, estilo, imports absolutos); (c) suite inicial de testes unitários configurada.
**Rastreia:** FR-01, FR-02, FR-03, NFR-07, NFR-09 (infra de testes).

### Épico 2 — Leitura de planilhas e catálogo
**Objetivo:** leitura streaming de `.xlsx`, acesso read-only ao catálogo (`campo`, `alternativa`, `grupocampo`), classificação de colunas.
**Entrega:** comando interno que classifica colunas de uma planilha arbitrária.
**Rastreia:** FR-04, NFR-04.

### Épico 3 — Fase de mapeamento
**Objetivo:** auto-mapeamento, persistência do `mapping.json`, comando `validar`, gate `PENDENTE`.
**Entrega:** comandos `mapear` e `validar` funcionais; fluxo de edição manual documentado; suite de testes unitários cobrindo o `AutoMapeador` e o `MapeamentoValidador` (atende NFR-09).
**Rastreia:** FR-05, FR-06, FR-07, FR-08, FR-09, NFR-09.

### Épico 4 — Geração de SQL e execução
**Objetivo:** coerção de tipos, gerador de `UPDATE` e `UPSERT`, log de erros por linha, comando `importar`.
**Entrega:** comando `importar` produz `.sql` e `.log` aplicáveis contra base de homologação; suite de testes unitários cobrindo coerção de tipos e gerador de SQL (atende NFR-09).
**Rastreia:** FR-10, FR-11, FR-12, FR-13, FR-17, NFR-06, NFR-08, NFR-09, CON-02, CON-03.

### Épico 5 — Resumo pós-importação
**Objetivo:** contadores, formatação ASCII + JSON, exit codes.
**Entrega:** todo `importar` emite resumo completo e exit code apropriado; testes unitários sobre `ResumoExecucao` garantem contadores corretos.
**Rastreia:** FR-14, FR-15, FR-16, NFR-09.

### Épico 6 — Build nativo, testes E2E e distribuição
**Objetivo:** perfil `native`, `reflect-config.json`, pipeline CI (GitHub Actions) com matrix Linux + Windows, suite E2E sobre o binário nativo, publicação dos binários como GitHub Release, entrega de `MANUAL.md` e `RUNBOOK-DBA.md`.
**Entrega:** binários nativos Linux e Windows publicados automaticamente como GitHub Release a cada tag `vX.Y.Z`, acompanhados da documentação do operador (`MANUAL.md`) e do DBA (`RUNBOOK-DBA.md`), precedidos da suite E2E verde.
**Rastreia:** FR-18, FR-19, FR-20, NFR-02, NFR-03, NFR-05, NFR-10, CON-05, CON-06.

### Sequenciamento recomendado

```
Épico 1 → Épico 2 → Épico 3 → Épico 4 → Épico 5
                                         │
                                         └──► Épico 6 (paralelizável após Épico 4)
```

O Épico 6 pode começar em paralelo ao Épico 5 porque o build nativo precisa apenas de código estável — o resumo é um ajuste no final do comando `importar`.

## 9. Critérios de sucesso

| # | Critério | Métrica |
|---|---|---|
| S1 | A ferramenta substitui a aplicação manual das planilhas | 100% das rodadas de importação passam a ser feitas via CLI |
| S2 | Nenhuma linha indevida é inserida nas tabelas principais | Zero `INSERT` em `tribcadastroimobiliario` ou `tribimobiliariosegmento` nos `.sql` gerados (auditável) |
| S3 | Colunas/valores novos não são aplicados silenciosamente | 100% dos itens novos aparecem como `PENDENTE` antes da primeira execução |
| S4 | O resumo permite auditoria operacional | Todo `.log` contém bloco de resumo + linha JSON estruturada |
| S5 | Distribuição sem fricção | Operador consegue executar o binário em Linux e Windows sem instalar dependências |

## 10. Riscos

| # | Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|---|
| R1 | Reflexão em native-image quebra componentes (Jackson, fastexcel) em produção | Média | Alto | Testes E2E contra binário nativo antes de cada release; `-agentlib:native-image-agent` em dev |
| R2 | Variações de grafia em alternativas inflam o número de `PENDENTE` | Alta | Médio | Normalização configurável (case, trim) já prevista em FR-05/FR-06 |
| R3 | Planilhas > 100k linhas esgotam memória | Baixa | Alto | Streaming mandatório (NFR-04) |
| R4 | Operador aplica `.sql` errado em produção (ex.: do ambiente errado) | Média | Muito alto | Nome do arquivo inclui timestamp + fluxo; documentação de procedimento com checklist DBA |
| R5 | Catálogo do banco (IDs de `campo`/`alternativa`) muda entre `mapear` e `importar` | Baixa | Médio | `importar` re-valida a existência dos `idcampo`/`idalternativa` mapeados antes de gerar SQL |
| R6 | Novas versões de planilha trazem colunas fixas não previstas | Média | Médio | Fluxo de edição manual do `mapping.json` já cobre; documentar em runbook |

## 11. Dependências e premissas

- **Dependência:** acesso de rede do operador ao PostgreSQL de IPTU (mesmo que read-only para catálogo).
- **Dependência:** catálogo (`campo`, `alternativa`, `grupocampo`) populado e mantido pela equipe fiscal.
- **Premissa P1:** a equipe de georreferenciamento continuará entregando planilhas `.xlsx` (não `.xls` nem CSV).
- **Premissa P2:** DBA da prefeitura é quem aplica o `.sql` final em produção.
- **Premissa P3:** o schema das tabelas externas (`tribcadastroimobiliario`, `tribimobiliariosegmento`, `respostaterreno`, `respostasegmento`, `campo`, `alternativa`, `grupocampo`) permanece **estável** entre versões da ferramenta. Alterações estruturais nessas tabelas são fora do controle deste projeto e exigem nova versão da ferramenta.
- **Premissa P4:** os IDs de `campo` e `alternativa` só são **adicionados** entre execuções — nunca renumerados ou removidos. A ferramenta protege-se com re-validação antes de gerar SQL (vide R5).

## 12. Plano de entrega sugerido

| Marco | Entrega | Épicos |
|---|---|---|
| **M1** | CLI JVM com conexão validada + fase de mapeamento completa | Épicos 1, 2, 3 |
| **M2** | CLI JVM gera SQL aplicável com resumo | Épicos 4, 5 |
| **M3** | Binários nativos Linux + Windows publicados | Épico 6 |
| **M4** | Primeira importação real aplicada em produção | (operacional) |

---

## 13. Estratégia de testes

Esta seção formaliza **como** a ferramenta será validada e complementa NFR-09 e NFR-10.

### 13.1. Pirâmide de testes

| Camada | Ferramenta | Escopo | Critério |
|---|---|---|---|
| **Unitários** | JUnit 5 + AssertJ | `AutoMapeador`, `MapeamentoValidador`, `SqlGerador`, coerção de tipos, `ResumoExecucao` | ≥ 70% de cobertura de linhas nos módulos críticos (NFR-09) |
| **Integração (JVM)** | `@QuarkusTest` + Testcontainers (PostgreSQL) | Repositórios de catálogo, pipeline completo em JVM contra banco efêmero | Todos os cenários verdes em PR |
| **E2E (nativo)** | Script de CI executando o binário nativo | Binário compilado contra amostra reduzida das planilhas + banco efêmero; valida `.sql` sintático e contadores do resumo | Mandatório antes de tag de release (NFR-10) |

### 13.2. Casos de teste mínimos

**Unitários:**
- Coerção `TEXTO`, `DECIMAL`, `DATA`, `MULTIPLA_ESCOLHA` — happy path e falhas de formato.
- `AutoMapeador` com alternativa normalizada vs case-sensitive vs trim.
- `MapeamentoValidador` recusa execução com qualquer item `PENDENTE`.
- `SqlGerador` produz `UPDATE` sem `INSERT` em tabela principal (invariante CON-03 testado explicitamente).
- `SqlGerador` produz `INSERT ... ON CONFLICT (referencia, idcampo)` idempotente.
- `ResumoExecucao` soma corretamente lidos / sucesso / erro / updates / respostas.

**Integração:**
- Pipeline completo `mapear` → `validar` → `importar` contra banco com catálogo seed.
- `importar` com linha cujo código de imóvel não existe → entrada no `.log` + 0 UPDATEs para ela + resumo marca erro.
- Re-aplicação do `.sql` gerado é idempotente (NFR-06).

**E2E (binário nativo):**
- Amostra de 10 linhas de `TABELA_TERRITORIAL_V001.xlsx` + catálogo mínimo → `.sql` sintaticamente válido + resumo com contadores esperados.
- Mesmo caso para `TABELA_PREDIAL_V001.xlsx`.
- Teste de falha rápida: binário com `application.properties` inválido retorna exit code 1.

### 13.3. Gating de release

```
tag vX.Y.Z
  ├─► unit tests (ambos SOs)        .. falha = bloqueia
  ├─► cobertura módulos críticos    .. < 70% = bloqueia
  ├─► integration tests (Linux)     .. falha = bloqueia
  ├─► native build (Linux + Win)    .. falha = bloqueia
  ├─► E2E sobre binário nativo      .. falha = bloqueia
  └─► publica GitHub Release        .. com MANUAL.md + RUNBOOK-DBA.md
```

## 14. Próximos passos

1. **@po (Pax):** re-validar este PRD v1.1 via `*validate-prd`.
2. **@sm (River):** após aprovação, desmembrar Épico 1 em stories via `*draft`.
3. **@architect (Aria):** já entregou `arquitetura.md` — disponível como referência técnica para cada story.
4. **@devops (Gage):** inicializar repositório git + remote como primeira story do Épico 1 (`*environment-bootstrap`).

— Morgan, planejando o futuro 📊
