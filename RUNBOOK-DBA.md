# Runbook DBA — Importação GEO

> **Produto:** importacao-geo  
> **Público-alvo:** DBA responsável pela aplicação do `.sql` gerado no banco de IPTU  
> **Idioma:** Português  
> **Versão:** 1.0 — Épico 6

---

> **Aprovado por:** `[PREENCHER — nome do DBA responsável]`, `[PREENCHER — data da aprovação]`

---

## Sumário

1. [Checklist pré-aplicação](#1-checklist-pré-aplicação)
2. [Aplicação em homologação](#2-aplicação-em-homologação)
3. [Aplicação em produção](#3-aplicação-em-produção)
4. [Queries de sanidade pós-aplicação](#4-queries-de-sanidade-pós-aplicação)
5. [Rollback](#5-rollback)
6. [Contatos de escalonamento](#6-contatos-de-escalonamento)

---

## Contexto: o que o arquivo `.sql` contém

O arquivo `.sql` gerado pela ferramenta **nunca executa DML direto no banco em produção** — ele é produzido pela máquina do operador de TI e entregue ao DBA para aplicação controlada.

O `.sql` contém **exclusivamente**:

| Tipo de operação | Tabela afetada | Fluxo |
|---|---|---|
| `UPDATE` (atualiza colunas fixas) | `aise.tribcadastroimobiliario` | Territorial |
| `DELETE` + `INSERT` (UPSERT de respostas) | `aise.respostaterreno` | Territorial |
| `UPDATE` (atualiza colunas fixas) | `aise.tribimobiliariosegmento` | Predial |
| `DELETE` + `INSERT` (UPSERT de respostas) | `aise.respostasegmento` | Predial |

**O `.sql` nunca contém `INSERT INTO tribcadastroimobiliario` ou `INSERT INTO tribimobiliariosegmento`.** A checagem desta regra é o primeiro passo obrigatório antes de qualquer aplicação.

> **UPSERT via DELETE+INSERT:** o sistema implementa o UPSERT nas tabelas de respostas como um par `DELETE WHERE referencia = X AND idcampo = Y` seguido de `INSERT`. Isso significa que linhas existentes em `respostaterreno`/`respostasegmento` com o mesmo par `(referencia, idcampo)` da planilha são excluídas e reinseridas. Se o banco possuir triggers de auditoria que registram `DELETE`, eles serão acionados — comportamento esperado.

> **Transação:** o arquivo `.sql` não contém `BEGIN`/`COMMIT`. O DBA deve sempre aplicá-lo com `--single-transaction` (via `psql`) para garantir atomicidade — veja as seções 2 e 3.

---

## 1. Checklist pré-aplicação

Execute os quatro itens abaixo **antes de qualquer aplicação**, seja em homologação ou em produção.

### 1.1 Validar timestamp e identificar o fluxo

O nome do arquivo segue o padrão `saida-{fluxo}-{timestamp}.sql`:

```
saida-territorial-20260420-143447.sql
     ^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^
     fluxo         data e hora da geração (AAAAMMDD-HHmmss)
```

- Confirme que o timestamp corresponde à importação solicitada pelo operador.
- Confirme o fluxo: `territorial` → planilha de terrenos; `predial` → planilha de edificações.
- Se receber dois arquivos (um por fluxo), aplique-os em sessões **separadas**, nunca no mesmo `psql`.

### 1.2 Conferir que o arquivo não está vazio

```bash
# Linux / macOS
ls -lh saida-territorial-20260420-143447.sql

# Windows (PowerShell)
Get-Item saida-territorial-20260420-143447.sql | Select-Object Length
```

Um arquivo com `0 bytes` indica falha na geração — acione o operador antes de prosseguir.

### 1.3 Verificação de segurança obrigatória (CON-03)

Execute o `grep` abaixo para confirmar que o arquivo **não** contém inserções proibidas nas tabelas principais:

```bash
# Linux / macOS
grep -iE "insert into (tribcadastroimobiliario|tribimobiliariosegmento)" saida-territorial-20260420-143447.sql

# Windows (PowerShell)
Select-String -Path saida-territorial-20260420-143447.sql -Pattern "insert into (tribcadastroimobiliario|tribimobiliariosegmento)" -CaseSensitive:$false
```

**Resultado esperado:** nenhuma linha impressa (exit code `1` no `grep` = padrão não encontrado = seguro prosseguir).

> ⚠️ **Se qualquer linha for impressa: NÃO APLIQUE o arquivo.** Acione imediatamente o desenvolvedor da ferramenta (ver [Seção 6](#6-contatos-de-escalonamento)).

### 1.4 Verificar contadores no arquivo `.log`

O operador deve ter fornecido o arquivo `.log` junto com o `.sql`. Localize a linha de resumo JSON ao final do `.log`:

```bash
# Linux / macOS
grep '"evento":"resumo"' saida-territorial-20260420-143447.log

# Windows (PowerShell)
Select-String -Path saida-territorial-20260420-143447.log -Pattern '"evento":"resumo"'
```

Exemplo de saída:
```json
{"evento":"resumo","fluxo":"territorial","lidos":1204,"sucesso":1187,"erro":17,"principal_updates":1187,"respostas_update":4210,"respostas_insert":893,"respostas_total":5103,"duracao_ms":157000}
```

Anote os valores de `principal_updates`, `respostas_update` e `respostas_insert` — eles serão usados para validação pós-aplicação na [Seção 4](#4-queries-de-sanidade-pós-aplicação).

Se `"erro"` > 0, as linhas problemáticas constam no `.log` (linhas com `WARN`). Isso não impede a aplicação — o `.sql` contém apenas os comandos das linhas processadas com sucesso.

---

## 2. Aplicação em homologação

Execute este procedimento **obrigatoriamente antes de aplicar em produção**.

### 2.1 Aplicar o arquivo

O arquivo `.sql` não contém `BEGIN`/`COMMIT`. Use sempre `--single-transaction` para garantir atomicidade:

```bash
psql \
  --host=<host-homologacao> \
  --port=5432 \
  --username=<usuario> \
  --dbname=<banco> \
  --single-transaction \
  --set ON_ERROR_STOP=on \
  -f saida-territorial-20260420-143447.sql
```

> **`--single-transaction`**: envolve todo o arquivo em uma única transação. Em caso de qualquer erro, o PostgreSQL faz rollback automático de tudo.  
> **`--set ON_ERROR_STOP=on`**: interrompe a execução imediatamente ao primeiro erro, em vez de continuar e aplicar comandos parciais.

### 2.2 Conferência amostral pós-aplicação

Após a execução sem erros, execute as queries de sanidade da [Seção 4](#4-queries-de-sanidade-pós-aplicação) usando 2–3 matrículas retiradas da planilha original.

### 2.3 Decisão: confirmar ou reverter

| Resultado da conferência | Ação |
|---|---|
| Dados corretos, contadores batem com o `.log` | **Confirmar:** a transação já foi confirmada pelo `--single-transaction` ao término sem erros. Nenhuma ação adicional. |
| Dados incorretos ou discrepância nos contadores | **Reverter:** execute o procedimento de rollback da [Seção 5](#5-rollback) e acione o desenvolvedor. |

> Em homologação, não há dump pré-aplicação obrigatório — o ambiente pode ser restaurado a qualquer momento pelo script `scripts/restore-db.sh` do repositório.

---

## 3. Aplicação em produção

### 3.1 Agendamento e janela de manutenção

- Agende uma janela de manutenção com prazo suficiente para a aplicação **e** para um eventual rollback.
- Referência de tempo: arquivos com ~1.200 linhas levam menos de 1 minuto de aplicação, mas reserve ao menos **30 minutos** para o ciclo completo (backup + aplicação + sanidade + confirmação).
- Notifique os usuários do sistema que possam estar conectados ao banco durante a janela.
- **Mantenha a janela aberta até a confirmação final** na Seção 4 — não encerre a manutenção antes de validar.

### 3.2 Backup pré-aplicação (`pg_dump`)

Execute o backup das quatro tabelas afetadas **imediatamente antes** de aplicar o arquivo:

```bash
pg_dump \
  --host=<host-producao> \
  --port=5432 \
  --username=<usuario> \
  --format=custom \
  --file=backup-geo-pre-aplicacao-$(date +%Y%m%d-%H%M%S).dump \
  -t aise.tribcadastroimobiliario \
  -t aise.respostaterreno \
  -t aise.tribimobiliariosegmento \
  -t aise.respostasegmento \
  <banco>
```

> **Windows (PowerShell):**
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd-HHmmss"
> pg_dump --host <host> --port 5432 --username <usuario> --format custom `
>   --file "backup-geo-pre-aplicacao-$ts.dump" `
>   -t aise.tribcadastroimobiliario -t aise.respostaterreno `
>   -t aise.tribimobiliariosegmento -t aise.respostasegmento `
>   <banco>
> ```

Confirme que o arquivo de dump foi criado e não tem `0 bytes` antes de prosseguir.

### 3.3 Aplicação do `.sql`

Idêntica à homologação — use sempre `--single-transaction` e `ON_ERROR_STOP`:

```bash
psql \
  --host=<host-producao> \
  --port=5432 \
  --username=<usuario> \
  --dbname=<banco> \
  --single-transaction \
  --set ON_ERROR_STOP=on \
  -f saida-territorial-20260420-143447.sql
```

### 3.4 Monitoramento durante a execução

Em uma sessão separada, monitore eventuais bloqueios:

```sql
-- Sessões aguardando lock (executar em outra conexão durante a aplicação)
SELECT pid, wait_event_type, wait_event, state, query
FROM pg_stat_activity
WHERE wait_event IS NOT NULL
  AND datname = current_database();
```

Se houver bloqueios prolongados (> 2 minutos), avalie interromper a sessão `psql` (Ctrl+C) — o `--single-transaction` garantirá rollback automático.

### 3.5 Critério de rollback em produção

| Situação | Ação |
|---|---|
| `psql` encerrou sem erros | Prosseguir para a [Seção 4](#4-queries-de-sanidade-pós-aplicação) |
| `psql` exibiu `ERROR` e abortou | O `--single-transaction` + `ON_ERROR_STOP` já reverteu tudo automaticamente. Execute rollback manual da [Seção 5](#5-rollback) apenas se houver dúvida sobre o estado do banco. |
| Execução bem-sucedida mas sanidade falhou | Execute o rollback da [Seção 5](#5-rollback) a partir do dump gerado na Seção 3.2. |

---

## 4. Queries de sanidade pós-aplicação

Execute estas queries **após a aplicação** (homologação ou produção) para validar que os dados foram gravados corretamente. Todas são somente leitura — nenhuma modifica o banco.

Substitua `<fluxo>` por `territorial` ou `predial` conforme o arquivo aplicado.

### 4.1 Contagem de respostas inseridas/atualizadas

Compare o `COUNT(*)` com os valores `respostas_update + respostas_insert` anotados na Seção 1.4:

```sql
-- Fluxo territorial: contagem total de respostas
SELECT COUNT(*) AS total_respostas FROM aise.respostaterreno;

-- Fluxo predial: contagem total de respostas
SELECT COUNT(*) AS total_respostas FROM aise.respostasegmento;
```

O valor absoluto depende do histórico do banco — o que importa é que o delta em relação ao estado anterior seja consistente com `respostas_insert` (novos registros) e `respostas_update` (sobrescritas).

### 4.2 Contagem de UPDATEs na tabela principal

Verifique que o número de registros com dados georreferenciados aumentou ou foi atualizado:

```sql
-- Fluxo territorial
SELECT COUNT(*) AS registros_atualizados
FROM aise.tribcadastroimobiliario
WHERE tribcadastrogeral_idkey IN (
  SELECT DISTINCT referencia FROM aise.respostaterreno
);

-- Fluxo predial
SELECT COUNT(*) AS registros_atualizados
FROM aise.tribimobiliariosegmento
WHERE idkey IN (
  SELECT DISTINCT referencia FROM aise.respostasegmento
);
```

### 4.3 Spot-check por matrícula (amostra)

Escolha 2–3 matrículas retiradas diretamente da planilha original e verifique se os dados foram gravados corretamente:

```sql
-- Fluxo territorial: substituir '12345' pela matrícula da planilha
SELECT
  r.referencia,
  c.descricao AS campo,
  r.valor,
  r.idalternativa
FROM aise.respostaterreno r
JOIN aise.campo c ON c.idkey = r.idcampo
WHERE r.referencia = '12345'
ORDER BY c.descricao;

-- Fluxo predial
SELECT
  r.referencia,
  c.descricao AS campo,
  r.valor,
  r.idalternativa
FROM aise.respostasegmento r
JOIN aise.campo c ON c.idkey = r.idcampo
WHERE r.referencia = '12345'
ORDER BY c.descricao;
```

Compare o resultado com os valores correspondentes na planilha original.

### 4.4 Confirmar ausência de INSERTs proibidos no histórico

Se o banco possuir log de auditoria, confirme que nenhum `INSERT` nas tabelas principais foi registrado durante a janela de manutenção. Se não houver auditoria, a verificação da Seção 1.3 (grep) é suficiente.

---

## 5. Rollback

Execute este procedimento se a sanidade falhar ou se o operador solicitar reversão após a aplicação.

### 5.1 Restaurar a partir do dump

Use o dump gerado na Seção 3.2:

```bash
pg_restore \
  --host=<host-producao> \
  --port=5432 \
  --username=<usuario> \
  --dbname=<banco> \
  --clean \
  --if-exists \
  --single-transaction \
  backup-geo-pre-aplicacao-20260420-143000.dump
```

> **`--clean`**: apaga e recria os objetos antes de restaurar.  
> **`--if-exists`**: evita erro se o objeto não existir (seguro usar sempre).  
> **`--single-transaction`**: envolve o restore em uma transação.

### 5.2 Verificação pós-restore

Após o restore, execute a query de contagem da Seção 4.1 e confirme que os valores retornaram ao estado anterior.

```sql
-- Confirmar que o restore reestabeleceu o estado anterior
SELECT COUNT(*) FROM aise.respostaterreno;
SELECT COUNT(*) FROM aise.respostasegmento;
```

Compare com contagens registradas antes da aplicação (se disponível no log de auditoria ou anotadas previamente).

### 5.3 Notificação após rollback

Após confirmar que o restore foi bem-sucedido:

1. Notifique o operador de TI responsável pela importação (ver [Seção 6](#6-contatos-de-escalonamento)).
2. Registre o ocorrido (data, hora, motivo do rollback, quem executou).
3. Acione o desenvolvedor da ferramenta para análise da causa raiz antes de nova tentativa.

---

## 6. Contatos de escalonamento

Preencha esta tabela antes de colocar em produção:

| Papel | Nome | Contato |
|---|---|---|
| Operador de TI (gerou o `.sql`) | `[PREENCHER]` | `[PREENCHER — ramal / e-mail / celular]` |
| DBA supervisor | `[PREENCHER]` | `[PREENCHER — ramal / e-mail / celular]` |
| Desenvolvedor da ferramenta (importacao-geo) | `[PREENCHER]` | `[PREENCHER — e-mail / canal de suporte]` |

> Esta tabela deve ser preenchida pelo responsável técnico do projeto antes do primeiro uso em produção.

---

## Referências

- [MANUAL.md](MANUAL.md) — Manual do operador de TI (uso do CLI para geração do `.sql`)
- `docs/architecture/arquitetura.md` — Arquitetura técnica do sistema
- `scripts/restore-db.sh` — Script de restauração do banco de homologação
