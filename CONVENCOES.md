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

- Todo POJO de domínio que for serializado/deserializado por Jackson **deve ter** `@RegisterForReflection`
- Classes acessadas via reflexão (fastexcel, mapeamentos dinâmicos) devem ser registradas em `src/main/resources/META-INF/native-image/reflect-config.json`

## Geral

- Encoding: **UTF-8** em todos os arquivos
- Mensagens ao usuário (logs, erros, prompts): **português** (NFR-01)
- Nenhum `System.out.println` em produção — usar `org.jboss.logging.Logger`
