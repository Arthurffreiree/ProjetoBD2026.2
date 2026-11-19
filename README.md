# Sistema de Gerenciamento de Hotel (versão final, Etapa 06)

Aplicação final do Projeto de Banco de Dados: interface web com dashboard estatístico,
integrada a um banco MySQL com consultas, visões, funções, procedimentos e triggers.

## Integrantes

- Arthur Freire
- Luís Melo

## Tecnologias

- **Java 17** com o servidor HTTP do próprio JDK (sem frameworks)
- **MySQL 8** acessado por **JDBC** (`mysql-connector-j`)
- **HTML, CSS e JavaScript** puros na interface
- **Chart.js 4.4.1** para os gráficos (arquivo salvo em `static/vendor`, funciona sem internet)

**Não há ORM.** Todo `INSERT`, `UPDATE`, `DELETE`, `SELECT` e `CALL` está escrito em SQL
nas classes DAO e é enviado ao banco com `PreparedStatement` e `CallableStatement`.

## Como executar

Requisitos: JDK 17 ou mais novo, Maven 3.9 ou mais novo e MySQL 8.

1. **Banco:** no MySQL Workbench ou no DBeaver, execute `database/banco_completo.sql`
   como script (no DBeaver, **Alt+X**). Ele cria o banco `hotel_db` com tudo: tabelas,
   dados, índices, visões, funções, procedimentos e triggers.
   Se preferir por partes, rode os arquivos `01` a `06` da pasta `database`, nessa ordem.
2. **Credenciais:** copie `db.properties.example` para `db.properties` (na raiz do projeto)
   e informe usuário e senha do MySQL. Também dá para usar as variáveis de ambiente
   `DB_URL`, `DB_USER` e `DB_PASSWORD`.
3. **Aplicação:** na raiz do projeto,

   ```bash
   mvn clean package
   java -jar target/hotel-dashboard.jar
   ```

   ou rode `executar.bat` (Windows) / `./executar.sh` (Linux e macOS). Numa IDE, importe
   como projeto Maven e execute `br.edu.cesar.hotel.HotelApplication`.
4. Acesse <http://localhost:8080>. O selo da barra lateral deve mostrar **MySQL conectado**.

## O que a interface tem

### 1. Dashboard estatístico (tela "Dashboard")

Tudo é calculado no MySQL a partir dos dados do banco (`DashboardDAO`).

- **Filtros interativos:** período (data de entrada das reservas), tipo de quarto e
  atalhos de período. Clicar numa barra do gráfico "Reservas por tipo" filtra o
  dashboard por aquele tipo.
- **Indicadores:** reservas no período, taxa de cancelamento, receita recebida com
  tendência (variação do último mês sobre o anterior), ticket médio, ocupação atual,
  hóspedes cadastrados, quartos disponíveis, médias de noites e de hóspedes por reserva.
- **Estatística descritiva do valor das reservas:** n, média, mediana, moda, variância,
  desvio padrão, coeficiente de variação, mínimo, máximo, moda de noites e coeficiente de
  correlação de Pearson (noites × valor e hóspedes × valor). Média com `AVG`, variância
  com `VAR_SAMP`, desvio com `STDDEV_SAMP`, mediana com `ROW_NUMBER() OVER`, moda com
  `GROUP BY ... ORDER BY COUNT(*)` e correlação com a fórmula de Pearson em SQL.
- **8 gráficos:**

  | Gráfico | Tipo | Análise |
  |---|---|---|
  | Reservas por status | rosca | distribuição de frequência |
  | Distribuição do valor das reservas | histograma | distribuição de frequência por faixa |
  | Tendência mensal | linha + barras | tendência temporal da receita e das reservas |
  | Reservas por tipo de quarto | barras horizontais | comparativo por categoria |
  | Noites × valor | dispersão | correlação (r de Pearson) |
  | Serviços com maior receita | barras | ranking |
  | Entradas por dia da semana | radar | sazonalidade semanal |
  | Formas de pagamento | pizza | participação no total |

### 2. Cadastros (CRUD de 4 tabelas)

Inserção, alteração, exclusão e listagem com busca de **QUARTO**, **SERVICO**,
**TIPO_QUARTO** e **RESERVA**. Os erros do banco aparecem com mensagem clara:
chave estrangeira (ex.: excluir quarto com reservas), `UNIQUE`, `CHECK` e as mensagens
dos triggers (capacidade excedida e overbooking ao cadastrar ou alterar reserva).

### 3. Consultas e visões

As 9 consultas da Etapa 03, as 4 da Etapa 04 e as 2 visões. Cada uma mostra filtros
(quando tem parâmetros), indicadores (linhas, soma, média, maior valor), um gráfico,
a tabela de resultado e o SQL executado.

| Grupo | Consulta | Conceitos |
|---|---|---|
| Etapa 03 | 01 a 09 (ocupação, receita mensal, parcelas em atraso, serviços acima da média, quartos disponíveis por período, ranking de hóspedes, check-ins por funcionário, reservas acima do ticket médio, conta parcial) | JOINs, subconsultas, HAVING, NOT EXISTS, self join |
| Etapa 04 | Quartos nunca reservados | anti join (LEFT JOIN + IS NULL) |
| Etapa 04 | Hóspedes × funcionários | FULL OUTER JOIN emulado (LEFT JOIN UNION RIGHT JOIN) |
| Etapa 04 | Quartos acima da média do andar | CTE + subconsulta correlacionada |
| Etapa 04 | Reservas futuras de devedores | subconsulta com IN + índices |
| Visões | `vw_reserva_completa` | filtros por situação e período |
| Visões | `vw_contas_a_receber` | filtro por situação (atrasada / a vencer) |

### 4. Funções, procedimentos e triggers (Etapa 05)

| Rotina | Como aparece na interface |
|---|---|
| `fn_categoria_hospede` (IF/ELSEIF) | gráfico e tabela com a categoria de fidelidade de cada hóspede |
| `fn_valor_conta` | valor da conta de cada hospedagem em andamento |
| `sp_realizar_checkout` | botão "Fazer check-out": encerra a hospedagem, finaliza a reserva e põe o quarto em LIMPEZA |
| `sp_registrar_pagamento_hospede` (CURSOR) | formulário de pagamento; mostra parcelas quitadas e o saldo (parâmetro OUT) |
| `trg_reserva_log_status` | tabela `LOG_RESERVA` atualizada a cada mudança de status (cadastro de reservas ou check-out) |
| `trg_reserva_valida_insert` / `_update` | botões "Testar capacidade" e "Testar overbooking" e o próprio cadastro de reservas |

## Estrutura do código

```text
src/main/java/br/edu/cesar/hotel/
├── HotelApplication.java      inicia o servidor e registra as rotas
├── util/                      ConnectionFactory (conexão JDBC) e JdbcUtils
├── model/                     Quarto, Servico, TipoQuarto, Reserva
├── dao/
│   ├── QuartoDAO, ServicoDAO, TipoQuartoDAO, ReservaDAO   CRUD
│   ├── ConsultaDAO            13 consultas + 2 visões (SQL, filtros e gráfico)
│   ├── DashboardDAO           indicadores, estatística e dados dos gráficos
│   └── RotinaDAO              funções, CALL dos procedimentos e LOG_RESERVA
└── web/                       handlers HTTP (um por rota), Json e HttpUtils
src/main/resources/static/
├── index.html, styles.css
├── js/                        app.js (base), dashboard.js, cadastros.js,
│                              consultas.js, rotinas.js
└── vendor/chart.umd.js        Chart.js
database/
├── banco_completo.sql         tudo em um arquivo
├── 01_criacao_e_insercao.sql  tabelas e dados
├── 02_indices.sql             índices (Etapa 04)
├── 03_visoes.sql              visões (Etapa 04)
├── 04_funcoes.sql             funções (Etapa 05)
├── 05_procedimentos.sql       procedimentos (Etapa 05)
├── 06_triggers.sql            triggers e tabela LOG_RESERVA (Etapa 05)
├── consultas_etapa03.sql      SQL das 9 consultas da Etapa 03
└── consultas_etapa04.sql      SQL das 4 consultas da Etapa 04
```

Rotas da API: `/api/dashboard`, `/api/quartos`, `/api/servicos`, `/api/tipos-quarto`,
`/api/reservas`, `/api/consultas`, `/api/rotinas` e `/api/health`.

## Ajustes em relação às etapas anteriores

- **Dados de exemplo** com datas em torno de novembro de 2026 (histórico, hospedagens em
  andamento e reservas futuras), para as consultas que usam a data de hoje fazerem sentido.
  Todas as tabelas têm 30 ou mais tuplas.
- **Trigger `trg_reserva_valida_update`:** a versão para `UPDATE` do trigger de validação
  de reservas, necessária porque a tela agora permite alterar reservas.
- **`SET NAMES utf8mb4`** no início de cada script, para os acentos das mensagens dos
  triggers ficarem corretos em qualquer cliente MySQL.
- A regra "funcionário não supervisiona a si mesmo", que saiu do `CHECK` por causa do
  erro 3823 do MySQL 8, é garantida pelos triggers `trg_funcionario_supervisor_*`.

## Uso de IA e validação

Conforme orientação do professor, registramos que usamos IA (Claude) como apoio no
desenvolvimento: sugestão de consultas, visões, rotinas, organização do código e da
interface. Validamos executando os scripts do zero no MySQL 8, conferindo o resultado de
cada consulta e testando pela interface o CRUD das 4 tabelas, os filtros, o check-out,
o pagamento com cursor e as mensagens dos triggers.
