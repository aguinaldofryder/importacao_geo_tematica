# Arquitetura — Importação GEO

> Sistema de importação de planilhas de levantamento de campo (equipe de georreferenciamento) para o banco de dados de IPTU municipal.
> Documento vivo — ajustar conforme decisões evoluírem.

---

## 1. Visão geral

Ferramenta CLI que lê planilhas `.xlsx` produzidas pela equipe de georreferenciamento e gera artefatos `.sql` + `.log` para atualizar o banco de IPTU. A ferramenta **nunca** insere em tabelas principais — apenas `UPDATE` nas tabelas principais e `UPSERT` nas tabelas de respostas de campos dinâmicos.

Dois fluxos com a mesma forma:

| Fluxo | Planilha | Tabela principal (só UPDATE) | Tabela respostas (UPSERT) | Chave de junção |
|---|---|---|---|---|
| Territorial | `TABELA_TERRITORIAL_V001.xlsx` | `tribcadastroimobiliario` | `respostaterreno` | `tribcadastroimobiliario.tribcadastrogeral_idkey = respostaterreno.referencia` |
| Predial | `TABELA_PREDIAL_V001.xlsx` | `tribimobiliariosegmento` | `respostasegmento` | `tribimobiliariosegmento.idkey = respostasegmento.referencia` |

---

## 2. Stack tecnológica

| Camada | Escolha | Justificativa |
|---|---|---|
| Runtime | **Quarkus 3.x** compilado com **GraalVM native-image** | Produz binário único por SO (Linux + Windows); startup instantâneo; baixo consumo de memória |
| CLI | `quarkus-picocli` | Subcomandos, flags, prompts — maduro e integrado |
| Leitura Excel | **`fastexcel-reader`** (dhatim) | Streaming, XLSX-only, amigável a native-image (evita a carga de reflexão do Apache POI) |
| Banco de dados | **PostgreSQL** via `quarkus-jdbc-postgresql` + `quarkus-agroal` | Driver PG tem excelente suporte a native-image, zero configuração extra |
| Serialização JSON | `quarkus-jackson` | Leitura/escrita do `mapping.json` |
| Configuração externa | **SmallRye Config** lendo `./config/application.properties` | Quarkus já procura lá automaticamente ao lado do binário |
| Logging | `quarkus-logging` com file handler | Gera o `.log` exigido pelo pipeline |
| Build CI | GitHub Actions matrix `ubuntu-latest` + `windows-latest` | `native-image` **não cross-compila** — build por SO é mandatório |
| Distribuição | Binário único por SO (`.exe` no Windows, ELF no Linux) | Futuro: instaladores `.deb` / `.msi` |

### 2.1. Alternativas avaliadas e descartadas

| Alternativa | Motivo do descarte |
|---|---|
| Spring Boot + Spring Native | Mesmo esforço que Quarkus, com menos maturidade nativa |
| Java puro + GraalVM | Exige plumbing manual (DI, config, CLI); Quarkus economiza semanas |
| Bun + TypeScript | Perfil TS do time é menor; ecossistema JDBC mais fraco |
| Go / Rust | Fora do stack atual da equipe |

---

## 3. Fluxo de comandos CLI

```
importacao-geo validar-conexao
  └─► testa conexão com o banco antes de qualquer outra coisa (falha-rápida)

importacao-geo mapear --arquivo X.xlsx --fluxo {territorial|predial}
  ├─► classifica colunas (código da matrícula / colunas fixas / colunas dinâmicas)
  ├─► auto-mapeia: campo.descricao ↔ header, alternativa.descricao ↔ valor distinto
  └─► grava mapping.json com status por item: MAPEADO | PENDENTE

  ← usuário edita o mapping.json manualmente, preenche os itens PENDENTE

importacao-geo validar --mapeamento mapping.json
  └─► valida sintaxe e completude do mapping (sem gerar SQL)

importacao-geo importar --arquivo X.xlsx --fluxo {...} --mapeamento mapping.json
  ├─► valida conexão + mapping (recusa se houver PENDENTE)
  ├─► gera saida-{fluxo}-{timestamp}.sql (UPDATE principal + UPSERT respostas)
  ├─► gera saida-{fluxo}-{timestamp}.log (matrículas ausentes, coerções falhas)
  └─► imprime RESUMO final no terminal e no final do .log
```

### 3.1. Conexão obrigatória

A conexão com PostgreSQL é **obrigatória** em todos os subcomandos que tocam catálogo ou geram SQL. O app falha-rápido se o `.properties` não estiver presente ou se a conexão falhar.

---

## 4. Pipeline de importação (3 fases do README)

```
┌──────────────────────────────────────────────────────────────────┐
│  FASE 1 — Classificação de colunas                               │
│    Identifica: (a) coluna de código do imóvel                    │
│                (b) colunas fixas → mapeiam para colunas da       │
│                    tabela principal                              │
│                (c) colunas dinâmicas → mapeiam para `campo`      │
├──────────────────────────────────────────────────────────────────┤
│  FASE 2 — Mapeamento (JSON)                                      │
│    - Auto-mapeia o que conseguir (header ↔ campo.descricao;      │
│      valor distinto ↔ alternativa.descricao).                    │
│    - Marca PENDENTE o que não casou.                             │
│    - Usuário edita o JSON manualmente.                           │
│    - Importação NÃO prossegue enquanto houver PENDENTE.          │
├──────────────────────────────────────────────────────────────────┤
│  FASE 3 — Execução                                               │
│    - Itera linhas da planilha.                                   │
│    - Para cada linha:                                            │
│        • Valida que o código do imóvel existe na tabela          │
│          principal (SELECT). Se não existe: log e pula.          │
│        • Gera UPDATE das colunas fixas.                          │
│        • Gera UPSERT (referencia, idcampo) nas tabelas           │
│          de respostas.                                           │
│    - Escreve .sql + .log.                                        │
│    - Gera RESUMO final (ver seção 7).                            │
└──────────────────────────────────────────────────────────────────┘
```

---

## 5. Forma do `mapping.json`

```json
{
  "fluxo": "TERRITORIAL",
  "planilha": "TABELA_TERRITORIAL_V001.xlsx",
  "colunaCodigoImovel": "MATRICULA",
  "colunasFixas": {
    "AREA_TERRENO": "area_terreno",
    "TESTADA": "testada_principal"
  },
  "colunasDinamicas": {
    "TIPO_MURO": {
      "status": "MAPEADO",
      "idcampo": 142,
      "tipo": "MULTIPLA_ESCOLHA",
      "alternativas": {
        "Alvenaria": 501,
        "Madeira": 502
      }
    },
    "COLUNA_NOVA": {
      "status": "PENDENTE",
      "motivo": "Nenhum campo encontrado com descricao='COLUNA_NOVA'",
      "sugestoes": [140, 171]
    }
  }
}
```

- **`status`** é o gate: `importar` recusa rodar enquanto qualquer item estiver `PENDENTE`.
- Campos dinâmicos com `campo.ativo = 'N'` são ignorados na geração automática (e marcados com motivo explícito se aparecerem na planilha).
- O filtro `grupocampo.funcionalidade ∈ {TERRENO, SEGMENTO}` é aplicado na auto-mapeamento conforme o fluxo.

---

## 6. Configuração externa (`config/application.properties`)

Arquivo lido automaticamente por Quarkus a partir do diretório onde o binário é executado. **Obrigatório** em toda execução.

```properties
# === Banco de dados (obrigatórios) ===
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://host:5432/iptu
quarkus.datasource.username=usuario
quarkus.datasource.password=senha

# === Saída ===
importacao.saida.diretorio=./saida
importacao.saida.sufixo-timestamp=true

# === Comportamento do mapeamento ===
importacao.mapeamento.case-sensitive=false
importacao.mapeamento.trim-espacos=true

# === Logging ===
quarkus.log.file.enable=true
quarkus.log.file.path=./saida/importacao-geo.log
quarkus.log.level=INFO
```

---

## 7. Resumo pós-importação (obrigatório)

Ao final de cada execução de `importar`, o sistema **deve** gerar um bloco de resumo com os seguintes contadores. O resumo é impresso no terminal **e** anexado ao final do arquivo `.log`.

| Métrica | Descrição |
|---|---|
| `registros.lidos` | Total de linhas lidas da planilha (excluindo cabeçalho) |
| `registros.sucesso` | Linhas processadas sem erro (geraram ao menos um SQL válido) |
| `registros.erro` | Linhas que falharam — ex.: código de imóvel inexistente, falha de coerção de tipo, valor sem alternativa |
| `tabelaPrincipal.atualizados` | Quantidade de `UPDATE` gerados na tabela principal (`tribcadastroimobiliario` ou `tribimobiliariosegmento`) |
| `respostas.atualizadas` | Quantidade de registros **atualizados** na tabela de respostas (par `(referencia, idcampo)` já existia) |
| `respostas.inseridas` | Quantidade de registros **inseridos** na tabela de respostas (par `(referencia, idcampo)` novo) |
| `respostas.total` | Soma de atualizadas + inseridas |
| `duracao` | Tempo total de execução (formato `HH:MM:SS`) |

### 7.1. Formato no terminal

```
========================================================
  RESUMO DA IMPORTAÇÃO — territorial
  Planilha : TABELA_TERRITORIAL_V001.xlsx
  Início   : 2026-04-20 14:32:10
  Fim      : 2026-04-20 14:34:47
  Duração  : 00:02:37
--------------------------------------------------------
  Registros lidos        : 1.204
  Registros com sucesso  : 1.187
  Registros com erro     :    17
--------------------------------------------------------
  Tabela principal (tribcadastroimobiliario)
    UPDATEs gerados      : 1.187
--------------------------------------------------------
  Tabela de respostas (respostaterreno)
    Atualizados          : 4.210
    Inseridos            :   893
    Total                : 5.103
========================================================
  Artefatos:
    SQL : ./saida/saida-territorial-20260420-143447.sql
    LOG : ./saida/saida-territorial-20260420-143447.log
========================================================
```

### 7.2. Formato no `.log`

O mesmo bloco é anexado ao final do `.log` em formato texto. Adicionalmente, o resumo é emitido como linha JSON estruturada para facilitar parsing por outras ferramentas:

```json
{"evento":"resumo","fluxo":"territorial","lidos":1204,"sucesso":1187,"erro":17,"principal_updates":1187,"respostas_update":4210,"respostas_insert":893,"respostas_total":5103,"duracao_ms":157000}
```

### 7.3. Exit code

| Situação | Exit code |
|---|---|
| Execução completa sem erros | `0` |
| Execução completa com erros por linha (parciais) | `2` |
| Falha de configuração / conexão / mapping inválido | `1` |

---

## 8. Layout do projeto

```
importacao-geo/
├── pom.xml
├── src/main/java/br/gov/.../importacaogeo/
│   ├── cli/               ValidarConexaoCommand, MapearCommand,
│   │                      ValidarCommand, ImportarCommand
│   ├── excel/             ExcelLeitor (fastexcel)
│   ├── catalogo/          CampoRepository, AlternativaRepository,
│   │                      GrupoCampoRepository (JDBC read-only)
│   ├── mapeamento/        AutoMapeador, MapeamentoStore,
│   │                      MapeamentoValidador
│   ├── geracao/           SqlGerador (UPDATE + UPSERT),
│   │                      LogErros, ResumoExecucao
│   ├── dominio/           Campo, Alternativa, GrupoCampo,
│   │                      Fluxo (enum TERRITORIAL | PREDIAL),
│   │                      Tipo (enum TEXTO | DECIMAL | DATA |
│   │                            MULTIPLA_ESCOLHA)
│   └── config/            ImportacaoConfig (@ConfigMapping)
├── src/main/resources/
│   ├── application.properties             (defaults embutidos)
│   └── META-INF/native-image/
│       └── reflect-config.json            (se fastexcel exigir)
├── config/
│   └── application.properties             (copiado ao lado do binário)
└── .github/workflows/
    └── build-native.yml                   (matrix Linux + Windows)
```

---

## 9. Build e distribuição

### 9.1. Build local (desenvolvimento)

```bash
./mvnw quarkus:dev                 # modo dev (JVM, hot reload)
./mvnw package                     # JAR executável (JVM)
./mvnw package -Pnative            # binário nativo do SO atual
```

### 9.2. Build nativo no CI

`.github/workflows/build-native.yml` — matrix `ubuntu-latest` + `windows-latest`, cada job roda `./mvnw package -Pnative` e publica o artefato:

- Linux: `importacao-geo-linux-x64`
- Windows: `importacao-geo-windows-x64.exe`

### 9.3. Tamanhos esperados

| Artefato | Tamanho aproximado |
|---|---|
| JAR (JVM) | 15–25 MB |
| Binário nativo | 60–90 MB |

---

## 10. Riscos conhecidos e mitigação

| Risco | Mitigação |
|---|---|
| Reflexão em native-image quebrando Jackson / fastexcel | Usar `@RegisterForReflection` nos POJOs; rodar `-agentlib:native-image-agent` em dev para capturar configuração |
| Dependência de `.xls` antigo (não suportado por fastexcel) | Especificação atual é `.xlsx`; reavaliar se aparecer `.xls` — eventual fallback para Apache POI com reflect-config manual |
| Build nativo demorado (~3–5 min por SO) | Cachear `~/.m2` e instalação do GraalVM no GitHub Actions |
| Tamanho do binário (~60–90 MB) | Aceitável para ferramenta interna; opcionalmente usar `upx` |
| Planilha muito grande (memória) | `fastexcel-reader` é streaming; processar linha-a-linha em vez de carregar tudo |
| Alternativas de `MULTIPLA_ESCOLHA` com variações de grafia | Normalização configurável (`case-sensitive`, `trim-espacos`); restante vai para PENDENTE e o usuário resolve manualmente |

---

## 11. Regras não-funcionais (do README)

- **Nunca** executar `INSERT` em `tribcadastroimobiliario` ou `tribimobiliariosegmento`. Código de imóvel ausente → log de erro + skip de todos os campos dinâmicos daquela linha.
- Conjunto de colunas da planilha **não é estável** entre versões; cada execução deve re-executar a fase de mapeamento contra o arquivo corrente.
- Conteúdo voltado ao usuário (logs, prompts, mensagens de erro) deve estar em **português**.
- A importação **não deve prosseguir** enquanto houver qualquer item `PENDENTE` no `mapping.json`.
- Toda execução de `importar` **deve** emitir o resumo descrito na seção 7.

---

## 12. Próximos passos

1. Confirmar este documento com o time.
2. Inicializar o repositório Git e o esqueleto Quarkus (`mvn io.quarkus.platform:quarkus-maven-plugin:create`).
3. Criar as stories de implementação (fase-por-fase) via `@sm *create-story`.
4. Configurar o workflow de CI para build nativo Linux + Windows.
