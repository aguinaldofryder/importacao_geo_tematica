# DEVELOPING — Build Nativo (GraalVM)

Guia para compilar e manter o binário nativo do `importacao-geo` com GraalVM `native-image`.

## Pré-requisitos

### GraalVM JDK 21

O build nativo exige GraalVM JDK 21 com o componente `native-image` incluído.
O ambiente de dev usa:

```
GraalVM CE 21.0.2+13.1
Caminho: ~/.local/lib/graalvm-community-openjdk-21.0.2+13.1
```

Opções de instalação:

```bash
# Via SDKMAN (recomendado)
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce

# Ou download manual: https://github.com/graalvm/graalvm-ce-builds/releases
# Extrair e exportar JAVA_HOME antes do build
```

Verificar instalação:

```bash
java -version    # deve reportar "GraalVM CE 21.0.2"
native-image --version
```

### Docker

O banco PostgreSQL é necessário para o smoke test (`validar-conexao`, `mapear`, `importar`).

```bash
docker-compose up -d    # sobe o banco na porta 5432 (db: paranacity, schema: aise)
```

Restaurar o dump de dados (necessário uma única vez):

```bash
./scripts/restore-db.sh
```

---

## Compilar o binário nativo

```bash
export JAVA_HOME=~/.local/lib/graalvm-community-openjdk-21.0.2+13.1
export PATH=$JAVA_HOME/bin:$PATH

./mvnw package -Pnative -DskipTests
```

O binário é gerado em `target/importacao-geo-1.0.0-SNAPSHOT-runner`.

### Métricas (medidas em 2026-05-03, Linux x86-64)

| Métrica | Valor | Limite NFR |
|---|---|---|
| Tamanho do binário | **50 MB** | ≤ 100 MB (NFR-05) |
| Startup (`validar-conexao`) | **~7 ms** | ≤ 500 ms (NFR-03) |
| Tempo de build nativo | ~24 s | — |

---

## Smoke test manual

```bash
RUNNER=./target/importacao-geo-1.0.0-SNAPSHOT-runner

# Todos os subcomandos devem responder sem stack trace nativo
$RUNNER --help
$RUNNER validar-conexao                      # Requer Docker
$RUNNER mapear --arquivo <planilha.xlsx> --fluxo territorial
$RUNNER validar --mapeamento mapping.json    # Offline — sem banco
$RUNNER importar --arquivo <planilha.xlsx> --fluxo territorial
```

---

## Procedimento `native-image-agent`

O agente registra reflexão, recursos e proxies usados em runtime. Deve ser executado toda vez que
uma nova classe serializada por Jackson ou acessada por reflexão for adicionada ao projeto.

```bash
# 1. Build JVM
./mvnw package -DskipTests

# 2. Primeira execução — cria o diretório de saída
mkdir -p target/native-image-agent

java -agentlib:native-image-agent=config-output-dir=target/native-image-agent \
     -jar target/quarkus-app/quarkus-run.jar \
     validar-conexao

# 3. Execuções adicionais — ACUMULA com config-merge-dir
java -agentlib:native-image-agent=config-merge-dir=target/native-image-agent \
     -jar target/quarkus-app/quarkus-run.jar \
     mapear --arquivo src/test/resources/fixtures/TABELA_TERRITORIAL_V001.xlsx \
            --fluxo territorial

java -agentlib:native-image-agent=config-merge-dir=target/native-image-agent \
     -jar target/quarkus-app/quarkus-run.jar \
     validar --mapeamento mapping.json

java -agentlib:native-image-agent=config-merge-dir=target/native-image-agent \
     -jar target/quarkus-app/quarkus-run.jar \
     importar --arquivo src/test/resources/fixtures/TABELA_TERRITORIAL_V001.xlsx \
              --fluxo territorial --mapeamento mapping.json

# Repetir os três últimos com --fluxo predial para cobrir o segundo fluxo

# 4. Inspecionar saída
ls target/native-image-agent/
# reflect-config.json  resource-config.json  proxy-config.json  serialization-config.json
```

> **Triagem obrigatória antes de copiar:** o agente captura reflexão do JDK e de todas
> as extensões Quarkus. Essas entradas **não devem** ser copiadas — já estão cobertas.
> Manter apenas entradas dos namespaces `org.dhatim.*` (fastexcel), `br.com.arxcode.*`
> (domínio próprio sem `@RegisterForReflection`) e `com.fasterxml.jackson.*` não cobertos
> por `quarkus-jackson`.

---

## Como adicionar entradas novas ao `reflect-config.json`

### Quando usar `@RegisterForReflection` (preferido)

Para classes do próprio projeto serializadas por Jackson ou instanciadas por reflexão,
adicione a anotação diretamente na classe ou em uma classe marker de pacote:

```java
// Opção A — anotação direta na classe (1–2 classes)
@RegisterForReflection
public class MinhaClasse { ... }

// Opção B — classe marker de pacote (≥ 3 classes do mesmo pacote)
@RegisterForReflection(targets = {
    MinhaClasse.class,
    OutraClasse.class,
    MaisUma.class
})
public class MeuPacoteReflection {}
```

Classes marker existentes:

| Classe | Pacote | Tipos cobertos |
|---|---|---|
| `DominioReflection` | `dominio` | `Campo`, `Alternativa`, `GrupoCampo` |
| `MapeamentoReflection` | `mapeamento` | `Classificacao`, `Mapeamento`, `ColunaDinamica`, `EntradaAutoMapeamento` |
| `ResumoSnapshot` | `geracao` | (anotação direta) |

### Quando usar `reflect-config.json`

Para **classes de terceiros** que não podem receber `@RegisterForReflection`:

```
src/main/resources/META-INF/native-image/reflect-config.json
```

O arquivo atual contém apenas `[]` (array vazio) — nenhuma entrada de terceiros foi
necessária além das cobertas pelas extensões Quarkus.

Formato de entrada:

```json
[
  {
    "name": "com.exemplo.Classe",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  }
]
```

---

## Dependências opcionais do `commons-compress` (Leia antes de atualizar versões)

O `fastexcel-reader` usa `commons-compress` para leitura de ZIP (`.xlsx`). O `commons-compress`
referencia construtores de todos os compressores suportados durante o parsing do native-image,
mesmo que nenhum seja usado em runtime para `.xlsx`.

As seguintes dependências foram adicionadas ao `pom.xml` para resolver referências de método
durante o build nativo (Story 6.1):

| Dependência | Tipo | Razão |
|---|---|---|
| `org.tukaani:xz:1.10` | Pure Java | Resolve `XZCompressorInputStream` no parsing |
| `com.github.luben:zstd-jni:1.5.7-3` | JNI | Resolve `ZstdInputStream` no parsing |

A inicialização JNI do `zstd-jni` é diferida para runtime via:
```
quarkus.native.additional-build-args=--initialize-at-run-time=com.github.luben.zstd
```
(configurado no perfil `native` do `pom.xml`)

Ao atualizar o `commons-compress`, verificar se surgem novos compressores opcionais com
o padrão "unresolved method during parsing" e aplicar a mesma abordagem.
