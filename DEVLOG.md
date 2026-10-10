# ControlJob — o devlog de uma jornada assistida por IA

> Este documento conta, em formato de "walkthrough" gamificado, como o ControlJob
> saiu de uma planilha mental para uma aplicação web completa. Ele é honesto sobre
> o processo: as decisões de produto e as validações foram humanas, e boa parte do
> código foi escrita com assistência de inteligência artificial, revisada e testada
> passo a passo.

```
INÍCIO DA JORNADA
[█░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%   — "TODO: parar de contar comissão no papel"
```

---

## Nível 0 — A missão

Quem trabalha com conserto de eletrônicos conhece bem a rotina: entra aparelho,
sai aparelho, e cada serviço tem um valor, um tipo e uma regra de comissão
diferente. No fim do mês, a pergunta crucial aparece:

**"Quanto eu tenho a receber, e de onde veio cada centavo?"**

O jeito antigo era anotar conserto por conserto e, na hora do fechamento,
recalcular tudo na mão — misturando três taxas de comissão diferentes, um salário
fixo e a sensação constante de que algum item tinha escapado.

O ControlJob nasceu para resolver exatamente isso: um lugar único onde cada
conserto é registrado, a comissão é calculada sozinha, e o fechamento do mês vira
um arquivo — não um cálculo de cabeça.

### As regras do jogo (requisitos reais)

| Regra | Valor / comportamento |
| --- | --- |
| Consertos em Geral | comissão de **5%** |
| Reparo de Placa | comissão de **50%** |
| Conserto para Lojistas | comissão de **50%** |
| Salário | **fixo mensal + total de comissões** |
| Fechamento | congela os itens pendentes, gera relatório e envia por e-mail |
| Acesso | um administrador edita; leitores só consultam |

O placar final que o sistema precisa mostrar, a qualquer momento:

```
salário_total = salário_fixo + Σ comissão(conserto)
```

---

## Nível 1 — Escolhendo o "engine" (a arquitetura)

Antes da primeira linha de código, a pergunta de arquitetura: **que tipo de
sistema isso é?**

A resposta honesta: é uma aplicação interna, para poucas pessoas, rodando em uma
oficina, sem necessidade de escalar para milhares de usuários simultâneos. Isso
muda tudo. Em vez de desenhar algo grande e complicado, o projeto foi feito para
ser **simples de operar e fácil de entender**.

### Por que Spring Boot (monólito, MVC server-side)?

- **Um único artefato para rodar.** `./mvnw spring-boot:run` em desenvolvimento,
  um `.jar` em produção. Sem orquestrar vários serviços.
- **Thymeleaf + HTML no servidor.** Não existe um segundo projeto de frontend
  (Node, bundler, build separado). Um só lugar para manter. Para um painel
  interno, essa simplicidade vale mais que qualquer framework de UI.
- **Microserviços aqui seriam over-engineering.** Um sistema desse porte ganha
  mais com fronteiras claras dentro de um único processo do que com rede e
  coordenação entre processos.

### Por que o banco H2 em arquivo?

- É **embutido**: não exige instalar e administrar um servidor de banco. Os dados
  ficam em `./data/controljob.mv.db`, persistindo entre reinícios.
- **Backup é copiar uma pasta.** No cenário de oficina, isso é ouro.
- O contrato com o código é JPA/Hibernate, então migrar para PostgreSQL no futuro
  é trocar configuração de datasource, não reescrever as entidades.
- O trade-off é real e assumido: H2 em arquivo brilha para uso de instância única
  e não é a escolha ideal para múltiplas instâncias concorrentes. Para o problema
  em questão, foi a decisão certa.

### Por que as regras de dinheiro vivem no domínio?

Comissão é regra de negócio, não detalhe de tela. Por isso ela mora no enum
`TipoConserto` — fonte única de verdade — e o cálculo é delegado pela própria
entidade `Conserto`:

```
Conserto.getComissao()  →  TipoConserto.calcularComissao(valor)
```

Assim, o dashboard, o relatório, a auditoria e o fechamento **nunca** reimplementam
a conta: todos perguntam a mesma fonte. E o dinheiro usa `BigDecimal` do começo ao
fim, porque `double` não se dá bem com centavos.

### O "estado" de um conserto

A vida de um conserto é binária e cabe em uma coluna: `dataFechamento`.

- `null` → **pendente** (ainda entra no salário do mês)
- preenchida → **fechado** (arquivado, não conta de novo)

Esse único campo é a máquina de estados que impede o clássico bug de "paguei duas
vezes pelo mesmo conserto".

### Mapa do mundo (organização em camadas)

```
src/main/java/com/controljob
├── model/        entidades JPA, enums de regra e "records" de leitura
├── repository/   Spring Data JPA + Specifications para filtros dinâmicos
├── service/      onde a regra de negócio acontece (comissão, fechamento, auditoria)
├── controller/   a camada web (MVC) que conversa com o navegador
├── job/          tarefas agendadas (o fechamento automático)
└── config/       segurança e login
```

Cada camada faz uma coisa: controller lida com HTTP, service decide, repository
persiste, model representa. Isso mantém o projeto navegável mesmo meses depois.

```
CHECKPOINT
[█████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 12%   — engine escolhida
```

---

## Nível 2 — As fases da construção

Cada fase abaixo é uma peça real e verificável no repositório.

### Fase 2.1 — O núcleo: registrar consertos

- Entidade `Conserto`: modelo do aparelho, número da OS, IMEI opcional, tipo,
  descrição, valor, data e `dataFechamento`.
- Validação com Bean Validation direto na entidade (valor > 0, campos
  obrigatórios) — o formulário nunca grava lixo no banco.
- CRUD completo com `ConsertoController` e um formulário Thymeleaf único que
  serve para criar e editar.
- Página dedicada `/lojistas` filtrando só os consertos para lojistas.

> Confronto desta fase: garantir que IMEI em branco virasse `null` e não uma
> string vazia que poluiria as estatísticas de recorrência depois. Resolvido no
> `ConsertoService.salvar`.

### Fase 2.2 — O painel de bordo (dashboard)

O `ConsertoService.gerarResumo()` caminha pela lista de consertos pendentes e
monta um `DashboardResumo` com:

- valor total dos consertos,
- total de comissão (separado por tipo),
- salário fixo,
- **salário total** (fixo + comissões),
- quantidade de consertos.

As somas também existem em consultas JPQL no repositório
(`somarValorTotal`, `somarValorPorTipo`), então o banco pode fazer o trabalho
pesado quando convém.

> Decisão de leitura: os "records" (`DashboardResumo`, `PainelAnalitico`,
> `Auditoria`) são imutáveis e existem só para transportar dados à tela — não são
> entidades e não vão para o banco.

### Fase 2.3 — CHEFÃO: o fechamento do mês

Este é o boss da jornada. No último dia do mês, ou por um botão no painel, o
sistema precisa "fechar" tudo o que está pendente:

1. Buscar os consertos pendentes até o fim do mês (`FechamentoMesService`).
2. Somar valores e comissões por tipo.
3. Gerar dois artefatos:
   - **JSON de auditoria** do mês (com tudo detalhado),
   - **PDF do relatório** de pagamentos (tabela completa, via OpenPDF).
4. Anexar os dois arquivos e **enviar por e-mail** (JavaMail / SMTP).
5. Marcar os consertos como fechados (`marcarFechados`), congelando o mês.

Se o e-mail falhar, o fechamento **não** desanda: os arquivos continuam gerados e
o erro é registrado de forma clara. Isso transforma um cálculo confuso de fim de
mês em um pacote rastreável.

```
BOSS DEFEATED
[████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 30%   — o mês agora tem recibo
```

### Fase 2.4 — A sala de troféus: painel analítico e auditoria

Registrar conserto é o básico. **Entender** o padrão é o que a IA e os dados
permitem de bônus. O `EstatisticaService` monta um `PainelAnalitico` com:

- **Evolução mensal** (quantidade, valor, comissão e ticket médio por mês),
- **Distribuição por tipo** (donut desenhado em SVG puro, calculando
  `stroke-dasharray` na mão a partir da circunferência),
- **Top modelos de aparelho**,
- **Recorrências por IMEI**: o mesmo aparelho voltando mais de uma vez, com
  intervalo de dias e valor acumulado.

Na área administrativa, o `AuditoriaService` leva isso além: filtros por período,
tipo, modelo, texto livre, faixa de valor e situação — montados dinamicamente com
**JPA Specifications** — além de agrupamentos (por mês, tipo, modelo, situação) e
**exportação CSV**.

> Por que Specifications? Porque os filtros são combináveis e opcionais. Em vez de
> dezenas de métodos `findByXAndYAndZ`, cada critério é uma peça que se junta só
> quando existe. É código que se lê como a pergunta que ele responde.

### Fase 2.5 — As muralhas: segurança e perfis

A aplicação não guarda senhas em texto e não tem credencial "chumbada" no código:

- **Primeiro acesso** cria o administrador pela tela `/configurar-admin` (só
  aparece enquanto não existe usuário). Do contrário, a senha inicial seria uma
  vulnerabilidade embutida no projeto.
- Senhas com **BCrypt** (`PasswordEncoder`).
- Dois perfis: `ADMIN` (edita, exclui, fecha o mês, vê auditoria) e `LEITOR`
  (consulta). Regras de acesso declaradas no `SecurityConfig`.
- Login por formulário e logout configurados; visitas a qualquer rota protegida
  caem no login.

### Fase 2.6 — Os guardiões automáticos: agendamento

`FechamentoMesJob` usa `@Scheduled(cron = "0 0 22 L * ?")` no fuso
`America/Sao_Paulo`: **no último dia do mês, às 22h**, o fechamento roda sozinho.
O mesmo serviço também pode ser disparado por um botão. A automação reaproveita
100% da lógica já testada — não existe uma "versão do robô" diferente da "versão
do humano".

### Fase 2.7 — Portal de saída: empacotar e publicar

- **Dockerfile** enxuto sobre `eclipse-temurin:21-jre`.
- **docker-compose** montando o banco (`./data`) como volume e injetando segredos
  por `.env`, nunca dentro da imagem.
- Segredos (SMTP, salário, usuário admin) chegam por **variáveis de ambiente**;
  o `application.properties` só referencia nomes de variável, com valores
  sensatos como padrão.

```
JORNADA PRINCIPAL CONCLUÍDA
[█████████████████████████████████████████] 100%  — da planilha ao deploy
```

---

## Inventário (o que o projeto usa e por quê)

| Peça | Papel na jornada |
| --- | --- |
| Java 21 + Spring Boot 3.4 | base da aplicação; jar único |
| Spring MVC + Thymeleaf | telas renderizadas no servidor, sem build de frontend |
| Spring Data JPA + Hibernate | persistência; migrar de banco é trocar config |
| JPA Specifications | filtros dinâmicos da auditoria |
| Spring Security + BCrypt | login, papéis ADMIN/LEITOR, sem senha fraca embutida |
| H2 (arquivo) | banco embutido, zero administração, backup por cópia |
| OpenPDF | geração do relatório em PDF |
| Spring Mail (JavaMail) | envio do fechamento mensal por e-mail |
| Jackson | serialização do JSON de auditoria |
| Bean Validation | integridade dos dados na entrada |
| Spring Scheduling | fechamento automático no fim do mês |
| Docker / Compose | empacotamento e execução isolada |

---

## Como a IA entrou na party

Seria desonesto dizer que "a IA fez tudo". Também seria desonesto esconder que ela
foi protagonista técnica. A divisão real do trabalho foi mais ou menos assim:

- **Humano (produto e julgamento):** definiu o problema, as três faixas de
  comissão, a regra de salário, o que é "fechar o mês", quem pode editar e o que
  conta como dado sensível. Testou o fluxo, apontou o que não fazia sentido e
  validou cada entrega.
- **IA (execução técnica):** ajudou a desenhar as classes e os "records", escreveu
  a maior parte do código e das consultas, sugeriu a estrutura em camadas, as
  Specifications, a divisão por perfis e o desenho das telas — tudo isso revisado,
  ajustado e testado antes de entrar.
- **Humano + IA (iteração):** cada função foi lida, questionada e refinada. A IA
  acelerou a escrita; o humano garantiu que o resultado correspondesse ao mundo
  real da oficina.

Em outras palavras: a IA foi **parceira de implementação**, não a dona do
problema. O valor do experimento foi mostrar que um único desenvolvedor, com
assistência de IA, consegue levar uma aplicação de negócio completa — do domínio
ao PDF enviado por e-mail e ao deploy em container — mantendo o código legível.

---

## Chefes enfrentados (e o que ficou de aprendizado)

1. **Comissão espalhada pelo código.** Venceu-se centralizando a regra no enum e
   delegando pela entidade.
2. **Contar conserto duas vezes.** Resolvido com o estado `dataFechamento` e o
   `marcarFechados` transacional.
3. **Dinheiro com arredondamento errado.** `BigDecimal` com escala 2 e
   `RoundingMode.HALF_UP` em todos os pontos de saída.
4. **Filtro de auditoria "impossível".** Especificações componíveis em vez de
   dezenas de métodos.
5. **Segredo vazando para o repositório.** Credenciais fora do código, via `.env`,
   com `.env.example` documentando o formato.
6. **Falha de e-mail derrubando o fechamento.** Arquivos gerados primeiro, erro de
   SMTP tratado à parte.

---

## Como jogar

```bash
# 1. Configure o ambiente
cp .env.example .env          # preencha e-mail/SMTP, salário e usuário admin

# 2. Rode em desenvolvimento
./mvnw spring-boot:run        # abra http://localhost:8080

# 3. No primeiro acesso, defina a senha do administrador
#    (a tela /configurar-admin aparece automaticamente)

# 4. Ou empacote e suba com Docker
docker compose up -d --build  # aplicação disponível na porta 8081
```

Os dados ficam em `./data/controljob` (H2 em arquivo).

---

## Placar final

```
[█████████████████████████████████████████] Done

Conquistas desbloqueadas:
  + Registro de consertos com comissão automática
  + Dashboard de valores e salário
  + Página dedicada a lojistas
  + Fechamento mensal com PDF, JSON e e-mail
  + Painel analítico (mensal, por tipo, top modelos, recorrências)
  + Auditoria com filtros combináveis e exportação CSV
  + Login com perfis de acesso
  + Fechamento agendado no último dia do mês
  + Deploy em Docker
```

O ControlJob é prova de um jeito de construir software: menos digitação mecânica,
mais pensamento sobre o problema — com a IA como co-piloto do desenvolvimento.

> **Versão pública:** este repositório não contém dados reais de consertos,
> comissões, salários, e-mails ou quaisquer credenciais. Os nomes de usuário e
> valores padrão foram trocados por exemplos genéricos.
