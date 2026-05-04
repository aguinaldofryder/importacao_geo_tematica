# Manual do Operador — Importação GEO

> **Versão:** 1.0 — Épico 6  
> **Idioma:** Português  
> **Público-alvo:** Operador de TI municipal (não é necessário conhecimento de desenvolvimento de software)

---

## Sumário

1. [Pré-requisitos](#1-pré-requisitos)
2. [Configuração](#2-configuração)
3. [Fluxo Completo de Importação](#3-fluxo-completo-de-importação)
4. [Interpretação do Resumo e do `.log`](#4-interpretação-do-resumo-e-do-log)
5. [Exit Codes](#5-exit-codes)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Pré-requisitos

Antes de executar a ferramenta, verifique que os três requisitos abaixo estão atendidos:

### 1.1 Sistema operacional suportado

| Sistema operacional | Binário a usar |
|---|---|
| Linux x64 (Ubuntu, Debian, RHEL, etc.) | `importacao-geo-linux-x64` |
| Windows x64 (10, 11, Server 2019+) | `importacao-geo-windows-x64.exe` |

> O binário é autocontido — não é necessário instalar Java, Maven ou qualquer outro software.

### 1.2 Acesso de rede ao banco de dados PostgreSQL

A ferramenta precisa se conectar ao banco de dados de IPTU durante a execução de todos os subcomandos. Verifique:

- O servidor PostgreSQL está em execução e acessível a partir da máquina onde o binário será executado.
- A porta padrão é **5432** (ou a porta configurada pelo DBA).
- O usuário informado no `application.properties` tem permissão de leitura no schema `aise` (necessário para o mapeamento automático) e, opcionalmente, permissão de escrita (não é usada diretamente — a ferramenta apenas gera arquivos `.sql`).

Em caso de dúvida sobre host, porta ou credenciais, consulte o DBA responsável.

### 1.3 Arquivo de configuração provisionado

O arquivo `config/application.properties` deve estar presente **no mesmo diretório** em que o binário for executado, dentro de uma subpasta chamada `config/`:

```
./
├── importacao-geo-linux-x64        ← binário
└── config/
    └── application.properties      ← arquivo de configuração (obrigatório)
```

Se o arquivo não existir, a ferramenta encerrará imediatamente com erro. Consulte a [Seção 2](#2-configuração) para saber como preenchê-lo.

---

## 2. Configuração

### 2.1 Anatomia do `application.properties`

O arquivo de configuração usa o formato `chave=valor`, um por linha. Linhas iniciadas com `#` são comentários e são ignoradas.

Um modelo completo está disponível no arquivo **`application.properties.exemplo`** que acompanha o pacote de distribuição. Copie esse arquivo para `config/application.properties` e preencha os valores conforme a tabela abaixo.

---

#### Seção: Banco de dados (obrigatórias)

| Chave | Obrigatória | Descrição | Exemplo de valor |
|---|---|---|---|
| `quarkus.datasource.db-kind` | **Sim** | Tipo do banco. Sempre `postgresql`. | `postgresql` |
| `quarkus.datasource.jdbc.url` | **Sim** | Endereço de conexão ao banco no formato JDBC. Substitua `host`, `porta` e `nome-do-banco` pelos valores do seu ambiente. | `jdbc:postgresql://192.168.1.10:5432/iptu` |
| `quarkus.datasource.username` | **Sim** | Nome de usuário do banco de dados. | `operador_geo` |
| `quarkus.datasource.password` | **Sim** | Senha do usuário do banco de dados. | `senha_segura` |
| `quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema` | **Sim** | Schema padrão do banco. Todas as tabelas do domínio estão em `aise`. **Não alterar.** | `aise` |
| `quarkus.datasource.jdbc.acquisition-timeout` | Não | Tempo máximo (em segundos) para obter conexão do pool antes de falhar. Padrão: `5`. Aumente em ambientes com latência alta. | `10` |

> **Atenção:** A URL JDBC segue o padrão `jdbc:postgresql://<host>:<porta>/<banco>`. Exemplo completo: `jdbc:postgresql://servidor-db.prefeitura.gov.br:5432/iptu_municipal`.

---

#### Seção: Saída de arquivos (opcionais)

| Chave | Obrigatória | Descrição | Exemplo de valor |
|---|---|---|---|
| `importacao.saida.diretorio` | Não | Pasta onde os arquivos `.sql` e `.log` serão gravados. Padrão: `./saida` (subpasta `saida` no diretório atual). | `./saida` |
| `importacao.saida.sufixo-timestamp` | Não | Se `true`, adiciona data e hora ao nome dos arquivos gerados (ex.: `saida-territorial-20260420-143447.sql`). Padrão: `true`. | `true` |

---

#### Seção: Comportamento do mapeamento (opcionais)

| Chave | Obrigatória | Descrição | Exemplo de valor |
|---|---|---|---|
| `importacao.mapeamento.case-sensitive` | Não | Se `false` (padrão), o mapeamento automático ignora diferenças entre maiúsculas e minúsculas nos nomes de coluna. | `false` |
| `importacao.mapeamento.trim-espacos` | Não | Se `true` (padrão), espaços em branco nas extremidades dos valores são removidos antes da comparação. | `true` |
| `importacao.codigo-imovel.territorial` | Não | Nome da coluna na planilha territorial que contém o código do imóvel. Padrão: `MATRICULA`. Altere somente se o município usar outro nome. | `MATRICULA` |
| `importacao.codigo-imovel.predial` | Não | Nome da coluna na planilha predial que contém o código do imóvel. Padrão: `MATRICULA`. | `MATRICULA` |
| `importacao.colunas-fixas.territorial` | Não | Lista separada por vírgulas de colunas da planilha territorial que mapeiam diretamente para colunas da tabela principal (UPDATE direto). Quando vazio, todas as colunas que não são código do imóvel são tratadas como campos dinâmicos. | `AREA_TERRENO,TESTADA` |
| `importacao.colunas-fixas.predial` | Não | Equivalente para a planilha predial. | `PADRAO_CONSTRUTIVO` |

---

#### Seção: Registro de log (opcionais)

| Chave | Obrigatória | Descrição | Exemplo de valor |
|---|---|---|---|
| `quarkus.log.level` | Não | Nível de detalhe do log. Use `INFO` para operação normal, `WARN` para ver apenas avisos e erros, `DEBUG` para diagnóstico detalhado. Padrão: `INFO`. | `INFO` |
| `quarkus.log.file.enable` | Não | Se `true` (padrão), o log também é gravado em arquivo. | `true` |
| `quarkus.log.file.path` | Não | Caminho do arquivo de log. Padrão: `./saida/importacao-geo.log`. | `./saida/importacao-geo.log` |

---

### 2.2 Exemplo mínimo de `application.properties`

```properties
# === Banco de dados (obrigatórios) ===
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://192.168.1.10:5432/iptu
quarkus.datasource.username=operador_geo
quarkus.datasource.password=senha_segura
quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema=aise

# === Saída ===
importacao.saida.diretorio=./saida
importacao.saida.sufixo-timestamp=true

# === Logging ===
quarkus.log.level=INFO
quarkus.log.file.enable=true
quarkus.log.file.path=./saida/importacao-geo.log
```

> Para o exemplo completo com todas as opções disponíveis, consulte o arquivo **`application.properties.exemplo`** incluído no pacote.

---

## 3. Fluxo Completo de Importação

O fluxo de importação é composto por **5 passos obrigatórios** que devem ser executados na ordem indicada. **Não pule nenhuma etapa.**

```
validar-conexao → mapear → [editar mapping.json] → validar → importar
```

Os exemplos abaixo usam o **fluxo territorial** (`TABELA_TERRITORIAL_V001.xlsx`). Para o fluxo **predial** (`TABELA_PREDIAL_V001.xlsx`), substitua `territorial` por `predial` e o nome da planilha onde indicado.

---

### 3.1 Passo 1 — Validar conexão

Antes de qualquer outra operação, verifique se a ferramenta consegue se conectar ao banco de dados.

**Linux:**
```bash
./importacao-geo-linux-x64 validar-conexao
```

**Windows:**
```cmd
importacao-geo-windows-x64.exe validar-conexao
```

**Saída esperada em caso de sucesso:**
```
Conexão com o banco de dados estabelecida com sucesso.
```

Se houver erro, consulte a [Seção 6 — Troubleshooting](#6-troubleshooting).

---

### 3.2 Passo 2 — Gerar mapeamento (`mapear`)

Este comando lê a planilha e gera o arquivo `mapping.json` com o mapeamento automático das colunas.

**Linux:**
```bash
./importacao-geo-linux-x64 mapear \
  --arquivo TABELA_TERRITORIAL_V001.xlsx \
  --fluxo territorial
```

**Windows:**
```cmd
importacao-geo-windows-x64.exe mapear --arquivo TABELA_TERRITORIAL_V001.xlsx --fluxo territorial
```

**Saída esperada:**
```
Mapeamento gerado: mapping.json
  Colunas mapeadas    : 18
  Colunas pendentes   :  2
```

O arquivo `mapping.json` será criado no diretório atual. Se houver colunas com status `PENDENTE`, prossiga para o [Passo 3](#33-passo-3--editar-o-mappingjson).

---

### 3.3 Passo 3 — Editar o `mapping.json`

Abra o arquivo `mapping.json` em um editor de texto (Bloco de Notas, Notepad++, etc.).

Localize as entradas com `"status": "PENDENTE"`. Essas são colunas que a ferramenta não conseguiu mapear automaticamente. O campo `"motivo"` explica a razão:

```json
"TIPO_SOLO": {
  "status": "PENDENTE",
  "motivo": "Nenhum campo encontrado com descricao='TIPO_SOLO'",
  "sugestoes": [140, 171]
}
```

**Como resolver:**

1. Consulte o DBA responsável informando o nome da coluna (`TIPO_SOLO` no exemplo acima) e peça o `idcampo` correto no cadastro do sistema.
2. Preencha o campo `"idcampo"` com o número fornecido pelo DBA.
3. Altere `"status": "PENDENTE"` para `"status": "MAPEADO"`.
4. Se o campo for do tipo `MULTIPLA_ESCOLHA`, preencha também as `"alternativas"` (associando cada valor da planilha ao `id` da alternativa correspondente no banco).

**Exemplo após preenchimento:**
```json
"TIPO_SOLO": {
  "status": "MAPEADO",
  "idcampo": 140,
  "tipo": "MULTIPLA_ESCOLHA",
  "alternativas": {
    "Arenoso": 501,
    "Argiloso": 502
  }
}
```

> **Importante:** A importação **não prosseguirá** enquanto houver qualquer item com `"status": "PENDENTE"`. O comando `validar` (Passo 4) confirmará se todos os itens estão resolvidos.

---

### 3.4 Passo 4 — Validar mapeamento (`validar`)

Após editar o `mapping.json`, execute a validação para confirmar que não há itens pendentes e que o arquivo está íntegro.

**Linux:**
```bash
./importacao-geo-linux-x64 validar --mapeamento mapping.json
```

**Windows:**
```cmd
importacao-geo-windows-x64.exe validar --mapeamento mapping.json
```

**Saída esperada em caso de sucesso:**
```
Mapeamento válido. Nenhum item PENDENTE encontrado.
Pronto para importar.
```

Se houver itens `PENDENTE`, a ferramenta listará quais são. Retorne ao Passo 3 e resolva-os antes de continuar.

---

### 3.5 Passo 5 — Executar importação (`importar`)

Com o mapeamento validado, execute a importação. Este comando gera os arquivos `.sql` e `.log` na pasta de saída configurada.

**Linux:**
```bash
./importacao-geo-linux-x64 importar \
  --arquivo TABELA_TERRITORIAL_V001.xlsx \
  --fluxo territorial \
  --mapeamento mapping.json
```

**Windows:**
```cmd
importacao-geo-windows-x64.exe importar --arquivo TABELA_TERRITORIAL_V001.xlsx --fluxo territorial --mapeamento mapping.json
```

Ao final, o terminal exibirá o [bloco de resumo](#41-bloco-de-resumo-no-terminal) com os contadores da importação e os caminhos dos arquivos gerados.

> **Atenção:** Os arquivos `.sql` e `.log` são gerados na pasta `./saida/` (ou no diretório configurado em `importacao.saida.diretorio`). O arquivo `.sql` deve ser entregue ao DBA para aplicação no banco de produção — **a ferramenta não executa os comandos diretamente no banco**.

> **Para o DBA aplicar o SQL gerado:** consulte o [RUNBOOK-DBA.md](RUNBOOK-DBA.md) — guia passo-a-passo com checklist de segurança, procedimento de aplicação em homologação e produção, queries de sanidade e instruções de rollback.

---

### 3.6 Fluxo completo — fluxo predial

Para importar a planilha predial, repita todos os passos acima substituindo:

| Substituir | Por |
|---|---|
| `TABELA_TERRITORIAL_V001.xlsx` | `TABELA_PREDIAL_V001.xlsx` |
| `--fluxo territorial` | `--fluxo predial` |

Execute o mapeamento e a importação em sessões **separadas** (um `mapping.json` por fluxo). Recomenda-se usar subpastas distintas para organizar os artefatos gerados por cada fluxo.

---

## 4. Interpretação do Resumo e do `.log`

### 4.1 Bloco de resumo no terminal

Ao final de cada execução do comando `importar`, a ferramenta exibe um bloco de resumo no terminal. Exemplo real:

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

**Significado de cada campo:**

| Campo | Descrição |
|---|---|
| **Registros lidos** | Total de linhas processadas da planilha (excluindo a linha de cabeçalho). |
| **Registros com sucesso** | Linhas processadas sem erros — geraram ao menos um comando SQL válido. |
| **Registros com erro** | Linhas com algum problema (código de imóvel inexistente, valor inválido, etc.). Detalhes no arquivo `.log`. |
| **UPDATEs gerados** | Quantidade de comandos `UPDATE` gerados para a tabela principal (`tribcadastroimobiliario` ou `tribimobiliariosegmento`). |
| **Atualizados** | Campos dinâmicos que **já existiam** no banco e foram sobrescritos (par `referencia + idcampo` já cadastrado). |
| **Inseridos** | Campos dinâmicos que **não existiam** e serão inseridos na primeira aplicação do SQL. |
| **Total** | Soma de atualizados + inseridos. |
| **Artefatos — SQL** | Caminho do arquivo `.sql` gerado. Entregue ao DBA para aplicação. |
| **Artefatos — LOG** | Caminho do arquivo `.log` com detalhes de erros e o resumo final. |

> Se **Registros com erro > 0**, abra o arquivo `.log` e localize as linhas com `WARN` para identificar os registros problemáticos.

---

### 4.2 Arquivo `.log`

O arquivo `.log` contém o histórico completo da execução. Cada linha segue o formato:

```
<NÍVEL>  [<Componente>] <Mensagem>
```

**Exemplos de linhas típicas:**

```
# Código de imóvel ausente no banco — a linha inteira é ignorada (incluindo campos dinâmicos)
WARN  [ImportarCommand] Código 99999 não encontrado em tribcadastroimobiliario — linha 42 ignorada

# Valor inválido para o tipo do campo — o campo é omitido, mas os demais campos da linha são processados
WARN  [CoercaoTipos] Falha ao converter "N/A" para DECIMAL na coluna AREA_TERRENO, linha 7 — campo omitido

# Linha processada com sucesso (visível apenas com quarkus.log.level=DEBUG)
DEBUG [ImportarCommand] Linha 5 processada: UPDATE tribcadastroimobiliario + 3 UPSERTs em respostaterreno
```

**Ao final do arquivo `.log`**, a ferramenta também anexa o resumo da execução no formato JSON estruturado — útil para integração com outros sistemas de monitoramento:

```json
{"evento":"resumo","fluxo":"territorial","lidos":1204,"sucesso":1187,"erro":17,"principal_updates":1187,"respostas_update":4210,"respostas_insert":893,"respostas_total":5103,"duracao_ms":157000}
```

---

## 5. Exit Codes

O binário encerra com um dos seguintes códigos de saída. Em scripts automatizados, use o código de saída para detectar falhas.

| Código | Situação | Ação recomendada ao operador |
|---|---|---|
| `0` | Execução concluída **sem nenhum erro**. Todos os registros foram processados com sucesso. | Entregue o arquivo `.sql` ao DBA para aplicação no banco. |
| `1` | Falha **antes** de processar qualquer linha: configuração ausente ou inválida, conexão com o banco falhou, `mapping.json` inválido ou com itens `PENDENTE`. | Verifique a mensagem de erro exibida no terminal. Consulte a [Seção 6 — Troubleshooting](#6-troubleshooting). |
| `2` | Execução concluída **com erros parciais**: ao menos uma linha da planilha gerou erro (código de imóvel inexistente, coerção de tipo inválida, etc.), mas as demais linhas foram processadas normalmente. | Abra o `.log`, localize as linhas com `WARN` e corrija ou documente os registros problemáticos. O arquivo `.sql` gerado contém os comandos das linhas com sucesso e pode ser aplicado normalmente. |

---

## 6. Troubleshooting

### Erro 1 — Conexão recusada / Banco inacessível

**Sintoma:**
```
ERRO: Falha ao conectar ao banco de dados.
java.net.ConnectException: Connection refused (Connection refused)
```
ou
```
ERRO: Tempo de conexão esgotado.
```

**Causa:** O servidor PostgreSQL não está acessível a partir desta máquina na porta configurada.

**Solução:**
1. Verifique se o servidor PostgreSQL está em execução.
2. Confirme que `quarkus.datasource.jdbc.url` aponta para o host e a porta corretos.
3. Verifique se há firewall ou VPN bloqueando a porta 5432.
4. Teste a conectividade manualmente:
   - **Linux:** `telnet <host> 5432` ou `nc -zv <host> 5432`
   - **Windows:** `Test-NetConnection -ComputerName <host> -Port 5432` (PowerShell)
5. Se o problema persistir, acione o administrador de rede ou o DBA.

---

### Erro 2 — Credencial inválida (usuário ou senha errada)

**Sintoma:**
```
ERRO: Falha na autenticação com o banco de dados.
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "operador_geo"
```

**Causa:** O usuário ou a senha configurados em `application.properties` estão incorretos.

**Solução:**
1. Abra `config/application.properties`.
2. Verifique `quarkus.datasource.username` e `quarkus.datasource.password`.
3. Confirme com o DBA o usuário e senha corretos para o ambiente.
4. Atenção: senhas com caracteres especiais (`@`, `#`, `$`, `=`) devem ser informadas exatamente como fornecidas pelo DBA — sem aspas adicionais.

---

### Erro 3 — Arquivo `.xlsx` não encontrado

**Sintoma:**
```
ERRO: Arquivo não encontrado: TABELA_TERRITORIAL_V001.xlsx
```

**Causa:** O caminho informado no parâmetro `--arquivo` não existe ou está incorreto.

**Solução:**
1. Verifique se a planilha está no diretório correto.
2. Se estiver em outra pasta, informe o **caminho completo**:
   - **Linux:** `--arquivo /home/operador/planilhas/TABELA_TERRITORIAL_V001.xlsx`
   - **Windows:** `--arquivo "C:\Importacao\Planilhas\TABELA_TERRITORIAL_V001.xlsx"`
3. Confirme que o nome do arquivo está escrito corretamente (incluindo maiúsculas e extensão `.xlsx`).
4. Certifique-se de que o arquivo **não está aberto** no Excel ou em outro programa durante a execução.

---

### Erro 4 — Coerção de tipo falhou

**Sintoma no `.log`:**
```
WARN  [CoercaoTipos] Falha ao converter "N/A" para DECIMAL na coluna AREA_TERRENO, linha 7 — campo omitido
```

**Causa:** Uma célula da planilha contém um valor incompatível com o tipo esperado pelo campo no banco (por exemplo, texto `"N/A"` em uma coluna do tipo `DECIMAL`).

**Solução:**
1. Abra o arquivo `.log` e localize todas as linhas `WARN [CoercaoTipos]`.
2. Identifique a linha e a coluna da planilha com o valor inválido.
3. Corrija o valor na planilha (ou deixe a célula em branco se o campo não for obrigatório).
4. Execute o fluxo novamente a partir do Passo 2 (`mapear`).

> O campo com erro de coerção é omitido, mas os demais campos da mesma linha são processados normalmente. O exit code será `2` se houver ao menos um erro desse tipo.

---

### Erro 5 — `importar` bloqueado por itens `PENDENTE` no `mapping.json`

**Sintoma:**
```
ERRO: O mapeamento contém 2 item(s) com status PENDENTE. Resolva todos os itens antes de importar.
  - TIPO_SOLO: Nenhum campo encontrado com descricao='TIPO_SOLO'
  - PADRAO_PISO: Nenhum campo encontrado com descricao='PADRAO_PISO'
```

**Causa:** O `mapping.json` gerado pelo comando `mapear` tem colunas que não foram mapeadas automaticamente e ainda não foram preenchidas manualmente.

**Solução:**
1. Abra `mapping.json` em um editor de texto.
2. Para cada item com `"status": "PENDENTE"`, siga o procedimento da [Seção 3.3](#33-passo-3--editar-o-mappingjson).
3. Consulte o DBA para obter o `idcampo` correto.
4. Após preencher todos os itens, execute `validar` para confirmar:
   ```bash
   # Linux
   ./importacao-geo-linux-x64 validar --mapeamento mapping.json

   # Windows
   importacao-geo-windows-x64.exe validar --mapeamento mapping.json
   ```
5. Somente após a mensagem `Mapeamento válido. Nenhum item PENDENTE encontrado.` execute `importar` novamente.

---

## Glossário

| Termo | Significado |
|---|---|
| **Fluxo territorial** | Importação da planilha `TABELA_TERRITORIAL_V001.xlsx`, que atualiza dados de terrenos. |
| **Fluxo predial** | Importação da planilha `TABELA_PREDIAL_V001.xlsx`, que atualiza dados de edificações. |
| **Coluna fixa** | Coluna da planilha que corresponde diretamente a uma coluna da tabela principal do banco. |
| **Campo dinâmico** | Coluna da planilha que corresponde a um campo configurável do sistema (tabela `campo` no banco). |
| **MAPEADO** | Status no `mapping.json` indicando que a coluna foi mapeada com sucesso e está pronta para importação. |
| **PENDENTE** | Status no `mapping.json` indicando que a coluna ainda não foi mapeada. A importação é bloqueada até que todos os itens estejam `MAPEADO`. |
| **UPSERT** | Operação de banco que insere um registro se ele não existir, ou atualiza se já existir. Usado nas tabelas de respostas (`respostaterreno`, `respostasegmento`). |
| **DBA** | Administrador de banco de dados — responsável por aplicar o arquivo `.sql` gerado no banco de produção e fornecer os `idcampo` para mapeamentos pendentes. |
| **`.sql`** | Arquivo gerado pela ferramenta contendo os comandos SQL de atualização. **Não é executado automaticamente** — deve ser entregue ao DBA. |
| **`.log`** | Arquivo de registro da execução, contendo avisos, erros por linha e o resumo final. |
