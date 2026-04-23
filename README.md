# Objetivo
O objetivo geral deste projeto é importar as duas planilhas que estão na raiz do projeto para um banco de dados em forma de update. 
O banco de dados é de um sistema de controle tributário municipal que controla o lançamento de IPTU. 
A planilha é um levantamento de campo feito pela equipe de georeferenciamento. 
Agora os dados dos cadastros imobiliários precisam ser atualizados conforme o levantamento de campo.


# Estrutura de dados

## Territorial
Planilha - TABELA_TERRITORIAL_V001.xlsx

Para fazer a conversão será necessário fazer alguns mapementos conforme descrito abaixo.
Todas as colunas da tabela podem variar, ser acrescentadas/removidas novas colunas, portanto será necssário fazer um mapemento prévio antes da importação.


* Código do imóvel - Esta coluna referencia a chave primária do imóvel na tabela tribcadastroimobiliario e deve ser utilizada na clausula WHERE. 
* As demais colunas podem ser uma coluna na tabela tribcadastroimobiliário, ou um campo dinâmico, portanto o primeiro passo da migração deve ser definir, qual coluna é o código do imóvel, depois identificar quais colunas são fixas e quais são de campos dinâmicos. As colunas fixas devem estar relacionadas com uma coluna na tabela. 
Já as colunas dinâmicas deverão ser mapeadas com o ID do campo e ID da alternativa, já que o campo pode ser TEXTO, DECIMAL, DATA ou MULTIPLA_ESCOLHA.

Abaixo a estrutura do cadastro de campos dinâmicos. 
Tabela: campo
Colunas: 
    * id - ID do campo utilizado no mapeamento (Nome da coluna = ID do campo)
    * identificador - Identificador único do campo (Não será utilizado neste mapeamento)
    * descricao - Descrição do campo - Utilizado para fazer match com o nome da coluna no Excel
    * tipo - TEXTO, DECIMAL, DATA ou MULTIPLA_ESCOLHA - Utilizado para fazer a conversão de tipo
    * obrigatorio - Define se o campo é obrigatório ou não - Não será utilizado neste mapeamento
    * idgrupo - ID do grupo - Não será utilizado neste mapeamento
    * ordem - Ordem do campo - Não será utilizado neste mapeamento
    * codigo - Código do campo - Não será utilizado neste mapeamento
    * armazenaresultadocalculo - Define se armazena o resultado do cálculo - Não será utilizado neste mapeamento
    * ativo - Situação do campo - Deverá ignorar campos = N

Os campos do tipo MULTIPLA_ESCOLHA possuem uma lista de alternativas válidas. 
Abaixo a estrutura da tabela de alternativas. 
Tabela: alternativa
Colunas: 
    * id - ID da alternativa
    * descricao - Descrição da alternativa
    * idcampo - ID do campo
    * codigo - Código da alternativa - Não será utilizado neste mapeamento
    * valor - Valor da alternativa - Não será utilizado neste mapeamento

Identificado todas as colunas, código do imóvel, colunas fixas e campos dinâmicos, será necessário fazer o de-para das colunas. 
Colunas fixas devem receber o nome da coluna a partir de uma lista que representa as colunas da tabela tribcadastroimobiliario.
As colunas que são campos variáveis devem buscar os dados da tabela campo, pelo campo "descricao" e fazendo mapeamento para a valor da coluna ID do campo.
Para as colunas que forem do tipo MULTIPLA_ESCOLHA, também será necessário mapear o ID da alternativa, que está na tabela alternativa. Para fazer esse mapeamento, será necessário fazer um distinct na coluna do exel e fazer mach com a coluna "descricao" da tabela "alternativa" para identificar o ID da alternativa.
A importação não pode prosseguir até que todas as colunas e alternativas estejam mapeadas.

O terceiro passo é fazer a importação de fato.
Cada linha pode virar um update na tabela "tribcadastroimobiliario" e cada coluna pode virar um insert/update na tabela "respostaterreno".
A tabela "respostaterreno" se relaciona com a tabela "tribcadastroimobiliario" da seguinte forma "tribcadastroimobiliario"."tribcadastrogeral_idkey" = "respostaterreno".referencia.
A tabela tribcadastroimobiliario nunca deve receber insert. Caso um código imobiliário não exista na tabela, um log de erro deve ser registrado dizendo que o imóvel não foi encontrado e os campos dinâmicos desse código também devem ser ignorados.
Já a tabela "respostaterreno" deve ser validada pela chave "referencia" + "idcampo". Caso já exista um registro com essa chave, este deve ser atualizado, caso não exista deve ser inserido.

## Predial
A importação da planilha predial segue a mesma premissa da planilha territorial, com as seguintes diferenças.
* A tabela principal onde pode ser mapeadas colunas fixas é tribimobiliariosegmento. 
* A resposta dos campos dinâmicos ficam na tabela "respostasegmento"
* O relationcamento da tabela "tribimobiliariosegmento" com a "respostasegmento" é feito pelos campos "tribimobiliariosegmento"."idkey" = "respostasegmento"."referencia"

# Requisitos não funcionais
* A importação só deve começar depois de todas os mapeamentos concluídos
* O sistema deve utilizar um arquivo JSON para fazer o mapeamento
* O sistema deve salvar um arquivo .sql com os inserts/updates
* O sistema deve salvar um arquivo .log com o erros
* O sistema pode funcionar por linha de comando, não é necessário interface gráfica
* Para definifr se um campo deve ser utilizado para predial ou territorial deve ser feito um JOIN da tabela campo com a grupocamo. Na tabela grupocamo, a coluna funcionalidade define 'TERRENO' ou 'SEGMENTO'. 
* O sistema pode evoluir para uma API com interface web.