# Convenções de Código — importacao-geo

## Nomenclatura

| Elemento | Convenção | Exemplo |
|---|---|---|
| Classes e interfaces | `UpperCamelCase` | `CampoRepository`, `MapeamentoValidador` |
| Métodos e variáveis | `lowerCamelCase` | `buscarPorId()`, `idCampo` |
| Constantes | `SCREAMING_SNAKE_CASE` | `MAX_LINHAS_PLANILHA`, `SCHEMA_PADRAO` |
| Pacotes | `lowercase` sem separadores | `br.com.arxcode.tematica.geo.catalogo` |
| Arquivos de recurso | `kebab-case` | `mapping-territorial.json` |

## Imports

- **Imports absolutos obrigatórios** — nunca usar `import br.com.arxcode.tematica.geo.*`
- Um import por linha
- Ordem: Java standard → Jakarta/Quarkus → terceiros → projeto

## Native Image

- Todo POJO de domínio que for serializado/deserializado por Jackson **deve estar registrado** em `@RegisterForReflection`. Duas formas equivalentes são aceitas:
  - **(a) Anotação direta** sobre cada POJO: `@RegisterForReflection` na classe/record.
  - **(b) Classe marker package-private** com `@RegisterForReflection(targets = { ClasseA.class, ClasseB.class, ... })`. Recomendada quando há ≥ 3 tipos correlatos no mesmo pacote (ex.: `dominio/DominioReflection.java`) — centraliza a lista, evita anotações repetidas e facilita revisão. Justificada pela documentação Quarkus para casos com múltiplos alvos.
- **Enums não precisam** de `@RegisterForReflection`: native-image trata enums nativamente (constantes resolvidas em compile time). Não incluir enums no array `targets` da classe marker.
- Classes acessadas via reflexão fora desses padrões (fastexcel, mapeamentos dinâmicos, drivers que escaneiam classpath) devem ser registradas em `src/main/resources/META-INF/native-image/reflect-config.json`.

## Geral

- Encoding: **UTF-8** em todos os arquivos
- Mensagens ao usuário (logs, erros, prompts): **português** (NFR-01)
- Nenhum `System.out.println` em produção — usar `org.jboss.logging.Logger`
