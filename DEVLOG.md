# ControlJob — devlog completo: do problema ao deploy, com IA como co-piloto

> Este documento descreve **todo o processo de desenvolvimento** do ControlJob:
> por que ele existe, como o domínio foi modelado, qual arquitetura foi escolhida
> e por quê, e como cada parte foi construída com assistência de inteligência
> artificial — sempre com decisões de produto, revisão e validação humanas.
>
> É um registro honesto. Onde não havia teste automatizado, está dito. Onde a IA
> errou ou precisou de ajuste, está dito. O objetivo é mostrar o método, não vender
> mágica. Nada aqui contém dados reais, credenciais ou informações sensíveis.

## Índice

1. [O problema](#1-o-problema)
2. [Levantamento de requisitos](#2-levantamento-de-requisitos)
3. [Modelagem do domínio](#3-modelagem-do-domínio)
4. [Arquitetura escolhida e por quê](#4-arquitetura-escolhida-e-por-quê)
5. [O processo de desenvolvimento, fase a fase](#5-o-processo-de-desenvolvimento-fase-a-fase)
6. [O método: como a IA foi usada (e como não foi)](#6-o-método-como-a-ia-foi-usada-e-como-não-foi)
7. [Decisões, trade-offs e o que envelheceu bem](#7-decisões-trade-offs-e-o-que-envelheceu-bem)
8. [Segurança e privacidade](#8-segurança-e-privacidade)
9. [Empacotamento e publicação](#9-empacotamento-e-publicação)
10. [Resultado final](#10-resultado-final)
11. [Como rodar](#11-como-rodar)
12. [Próximos níveis (roadmap)](#12-próximos-níveis-roadmap)

```
INÍCIO DA JORNADA
[█░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 0%   — "TODO: parar de contar comissão no papel"
```

---

## 1. O problema

### O dia a dia

Quem trabalha com conserto de eletrônicos vive uma rotina repetitiva e cheia de
detalhes: entra aparelho, sai aparelho. Cada serviço tem um valor, um tipo e uma
regra de comissão diferente. No fim do mês, aparece a pergunta decisiva:

**"Quanto eu tenho a receber, e de onde veio cada centavo?"**

O método antigo era anotar conserto por conserto e, na hora do fechamento,
recalcular tudo à mão — misturando três taxas de comissão, um salário fixo e a
sensação constante de que algum item tinha escapado. Erro de digitação, item
esquecido e retrabalho eram o padrão.

### As regras de negócio

| Tipo de conserto | Comissão |
| --- | --- |
| Consertos em Geral | **5%** |
| Reparo de Placa | **50%** |
| Conserto para Lojistas | **50%** |

O placar que o sistema precisa calcular a qualquer momento:

```
salário_total = salário_fixo + Σ comissão(conserto)
```

### O que precisava existir

- Registrar consertos com modelo, número da OS, IMEI (opcional), tipo, descrição,
  valor e data.
- Calcular a comissão automaticamente, sem ninguém digitar percentual.
- Mostrar, em tempo real, o total de consertos, a comissão acumulada e o salário.
- "Fechar o mês": arquivar os consertos pendentes e gerar um **relatório**.
- Separar o que é conserto de lojista numa visão dedicada.
- Impedir acesso indevido — nem todo mundo deve poder editar.
- Rodar de forma simples, local, sem depender de serviços na nuvem.

```
CHECKPOINT
[██░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 5%   — problema definido
```

---

## 2. Levantamento de requisitos

Antes de qualquer código, o domínio foi interrogado. As perguntas não foram
"qual framework usar?", mas "como o trabalho realmente funciona?". Algumas das
que guiaram tudo:

- **A comissão é sempre a mesma?** Não — depende do tipo (5%, 50%, 50%).
- **O que entra no salário?** Um valor fixo mensal somado às comissões.
- **O que significa "fechar o mês"?** Congelar os itens pendentes, gerar um
  relatório e enviá-lo. Depois disso, o item não deve contar de novo.
- **Como saber se um conserto já foi pago?** Por um marcador de "fechado".
- **Quem usa o sistema?** Um administrador que edita e outros que só consultam.
- **Precisa internet?** Não — os dados ficam na máquina, privados.
- **Como enviar o relatório?** Por e-mail, com um PDF e um arquivo de auditoria.

Esse questionário virou a espinha dorsal do modelo de dados e das telas.

```
CHECKPOINT
[████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 10%  — requisitos claros
```

---

## 3. Modelagem do domínio

Aqui a IA ajudou a transformar as respostas em classes, mas cada nome e cada
campo foi escolhido pensando no vocabulário real do trabalho.

### Entidades

- **`Conserto`** — o coração. Guarda modelo, OS, IMEI, tipo, descrição, valor,
  data e `dataFechamento`.
- **`Usuario`** — nome de usuário, hash da senha e perfil.

### Enums de regra

- **`TipoConserto`** — Consertos em Geral, Reparo de Placa, Conserto para
  Lojistas. Cada um carrega seu percentual de comissão.
- **`PerfilUsuario`** — `ADMIN` ou `LEITOR`.
- **`SituacaoConserto`** — Todos, Pendentes, Fechados (para filtros).
- **`Agrupamento`** — como agrupar a auditoria (mês, tipo, modelo, situação).

### A decisão central: comissão no domínio

Em vez de espalhar "0.05" e "0.50" pelas telas, a regra mora no enum — **fonte
única de verdade**:

```java
public enum TipoConserto {
    CONSERTOS_EM_GERAL("Consertos em Geral", new BigDecimal("0.05")),
    REPARO_DE_PLACA("Reparo de Placa",      new BigDecimal("0.50")),
    CONSERTO_PARA_LOJISTAS("Conserto para Lojistas", new BigDecimal("0.50"));
    // ...
}
```

E a entidade apenas delega:

```java
public BigDecimal getComissao() {
    if (tipoConserto == null || valor == null) return BigDecimal.ZERO;
    return tipoConserto.calcularComissao(valor);
}
```

Assim, dashboard, relatório, auditoria e fechamento **nunca** reimplementam a
conta. Mudar uma taxa no futuro é editar uma linha.

### O estado de um conserto

A vida de um conserto cabe em uma coluna: `dataFechamento`.

- `null` → **pendente** (ainda entra no salário do mês)
- preenchida → **fechado** (arquivado, não conta de novo)

Esse único campo é a máquina de estados que evita o bug clássico de "paguei duas
vezes pelo mesmo conserto".

### Dinheiro nunca é `double`

Todos os valores usam `BigDecimal`, com escala 2 e `RoundingMode.HALF_UP` nas
saídas. Centavos não se perdem no caminho.

```
CHECKPOINT
[██████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 16%  — domínio modelado
```

---

## 4. Arquitetura escolhida e por quê

A pergunta certa não era "qual a arquitetura mais avançada?", e sim "que
arquitetura serve a este problema sem virar peso morto?".

### Por que Spring Boot (monólito, MVC server-side)?

- **Um único artefato.** `./mvnw spring-boot:run` em desenvolvimento, um `.jar`
  em produção. Nada de orquestrar vários serviços.
- **Thymeleaf + HTML renderizado no servidor.** Não existe um segundo projeto de
  frontend (Node, bundler, build separado). Um só lugar para manter.
- **Microserviços seriam over-engineering aqui.** Um sistema para uma oficina
  ganha mais com fronteiras claras dentro de um processo do que com rede e
  coordenação entre serviços.

### Por que o banco H2 em arquivo?

- **Embutido**: não exige instalar e administrar servidor de banco.
- **Backup é copiar uma pasta** (`./data`). No cenário de oficina, isso vale ouro.
- O contrato é **JPA/Hibernate**, então migrar para PostgreSQL no futuro é trocar
  a configuração de datasource, não reescrever as entidades.
- **Trade-off assumido**: H2 em arquivo brilha para uso de instância única e não
  é a escolha ideal para múltiplas instâncias concorrentes. Para o problema em
  questão, foi a decisão certa.

### Organização em camadas

```
src/main/java/com/controljob
├── model/        entidades JPA, enums de regra e "records" de leitura
├── repository/   Spring Data JPA + Specifications para filtros dinâmicos
├── service/      regra de negócio (comissão, fechamento, auditoria, estatística)
├── controller/   camada web (MVC) que conversa com o navegador
├── job/          tarefas agendadas (fechamento automático)
└── config/       segurança e login
```

Cada camada faz uma coisa: controller lida com HTTP, service decide, repository
persiste, model representa. Isso mantém o projeto navegável meses depois.

### DTOs imutáveis

Os dados que vão para a tela não são entidades: são **records** (`DashboardResumo`,
`PainelAnalitico`, `Auditoria`). Imutáveis, focados no que a tela precisa, sem
risco de alterar o banco por acidente.

```
CHECKPOINT
[████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 22%  — arquitetura definida
```

---

## 5. O processo de desenvolvimento, fase a fase

Cada fase abaixo virou código real e verificável no repositório. O padrão de
trabalho foi sempre o mesmo ciclo: **requisito → desenho com IA → código →
revisão humana → validação rodando a aplicação**.

### Fase 0 — Setup

- Projeto gerado como Spring Boot (Java 21), com as dependências escolhidas a
  dedo: `web`, `thymeleaf`, `data-jpa`, `validation`, `security`,
  `thymeleaf-extras-springsecurity6`, `mail`, H2 e OpenPDF.
- Maven Wrapper incluído para rodar sem instalar Maven na máquina.

**Arquivos:** `pom.xml`, `mvnw`, `mvnw.cmd`.

### Fase 1 — Domínio e registro de consertos

- Entidade `Conserto` com validação embutida (Bean Validation): valor maior que
  zero, campos obrigatórios.
- Formulário Thymeleaf único servindo para criar e editar.
- `ConsertoController` + `ConsertoService` para o CRUD.

**Confronto desta fase:** garantir que IMEI em branco virasse `null`, e não uma
string vazia que depois poluiria as estatísticas de recorrência. Resolvido no
`ConsertoService.salvar`.

**Arquivos:** `model/Conserto.java`, `service/ConsertoService.java`,
`controller/ConsertoController.java`, `templates/conserto-form.html`.

### Fase 2 — Cálculo de comissão e dashboard

- `ConsertoService.gerarResumo()` percorre os pendentes e monta o
  `DashboardResumo`: valor total, comissão por tipo, salário fixo, salário total
  e quantidade.
- Somatórios também disponíveis em JPQL no repositório (`somarValorTotal`,
  `somarValorPorTipo`).

**Decisão:** o dashboard lê de um DTO imutável, não da entidade. A tela não
manipula o estado do banco.

**Arquivos:** `model/DashboardResumo.java`,
`repository/ConsertoRepository.java`, `templates/dashboard.html`.

### Fase 3 — Visões dedicadas

- **`/lojistas`**: filtra só os consertos para lojistas (maior comissão, precisa
  de atenção própria).
- **`/relatorio`**: relatório de pagamentos do mês corrente.

**Arquivos:** `service/RelatorioService.java`,
`controller/RelatorioController.java`, `templates/lojistas.html`,
`templates/relatorio.html`.

### Fase 4 — CHEFÃO: o fechamento do mês

O boss da jornada. No fim do mês, ou por um botão, o sistema:

1. Busca os consertos pendentes até o fim do mês (`FechamentoMesService`).
2. Soma valores e comissões por tipo.
3. Gera dois artefatos:
   - **JSON de auditoria** do mês (com tudo detalhado),
   - **PDF do relatório** de pagamentos (tabela completa, via OpenPDF).
4. Anexa os dois arquivos e **envia por e-mail** (JavaMail / SMTP).
5. Marca os consertos como fechados (`marcarFechados`), congelando o mês.

**Decisão de robustez:** se o e-mail falhar, o fechamento **não** desanda — os
arquivos continuam gerados e o erro é registrado de forma clara. Um cálculo
confuso de fim de mês vira um pacote rastreável.

**Arquivos:** `service/FechamentoMesService.java`,
`controller/FechamentoMesController.java`, `templates/admin/painel.html`.

```
BOSS DEFEATED
[██████████████░░░░░░░░░░░░░░░░░░░░░░░░░░] 40%  — o mês agora tem recibo
```

### Fase 5 — Segurança e perfis

Nada de credencial "chumbada" no código nem senha em texto:

- **Primeiro acesso** cria o administrador pela tela `/configurar-admin` (só
  aparece enquanto não existe usuário). Do contrário, a senha inicial seria uma
  vulnerabilidade embutida no projeto.
- Senhas com **BCrypt** (`PasswordEncoder`).
- Dois perfis: `ADMIN` (edita, exclui, fecha o mês, vê auditoria) e `LEITOR`
  (consulta), com regras declaradas no `SecurityConfig`:

```java
.requestMatchers("/usuarios/**", "/admin/**", "/consertos/novo",
                 "/consertos/*/editar", "/consertos/*/excluir").hasRole("ADMIN")
.requestMatchers("/consertos").hasRole("ADMIN")
.anyRequest().authenticated()
```

- Login por formulário, logout configurado, e redirecionamento automático para o
  primeiro acesso quando ainda não há administrador.

**Arquivos:** `config/SecurityConfig.java`, `service/UsuarioService.java`,
`controller/AuthController.java`, `controller/UsuarioController.java`.

### Fase 6 — Os guardiões automáticos (agendamento)

O fechamento não precisa ser lembrado: `FechamentoMesJob` roda sozinho no
**último dia do mês, às 22h**, no fuso `America/Sao_Paulo`.

```java
@Scheduled(cron = "0 0 22 L * ?", zone = "America/Sao_Paulo")
public void executar() { /* ... chama FechamentoMesService ... */ }
```

A automação reaproveita 100% da lógica já validada — não existe uma "versão do
robô" diferente da "versão do humano".

**Arquivos:** `job/FechamentoMesJob.java`, `ControlJobApplication.java`
(`@EnableScheduling`).

### Fase 7 — Sala de troféus: analytics e auditoria

Registrar é o básico; **entender** o padrão é o bônus que os dados permitem.

O `EstatisticaService` monta o `PainelAnalitico` com:

- **Evolução mensal** (quantidade, valor, comissão e ticket médio por mês),
- **Distribuição por tipo** (donut desenhado em SVG puro, com
  `stroke-dasharray` calculado a partir da circunferência),
- **Top modelos de aparelho**,
- **Recorrências por IMEI**: o mesmo aparelho voltando mais de uma vez, com
  intervalo em dias e valor acumulado.

Na auditoria, filtros combináveis e agrupamentos:

```java
public static Specification<Conserto> noPeriodo(LocalDate de, LocalDate ate) { /* ... */ }
public static Specification<Conserto> dosTipos(Collection<TipoConserto> tipos) { /* ... */ }
public static Specification<Conserto> comTexto(String termo) { /* ... */ }
// período + tipo + modelo + texto + faixa de valor + situação, tudo opcional
```

**Por que Specifications?** Porque os filtros são opcionais e combináveis. Em vez
de dezenas de métodos `findByXAndYAndZ`, cada critério é uma peça que se junta só
quando existe. Mais o **exportação CSV** da auditoria.

**Arquivos:** `service/EstatisticaService.java`, `service/AuditoriaService.java`,
`model/PainelAnalitico.java`, `model/Auditoria.java`,
`repository/ConsertoSpecifications.java`, `templates/admin/*.html`.

```
CHECKPOINT
[██████████████████████████░░░░░░░░░░░░░░] 70%  — sistema completo
```

### Fase 8 — Empacotamento e publicação

- **Dockerfile** enxuto sobre `eclipse-temurin:21-jre`.
- **docker-compose** com o banco (`./data`) montado como volume e os segredos
  entrando por `.env`, nunca dentro da imagem.
- Segredos (SMTP, salário, usuário admin) chegam por **variáveis de ambiente**;
  o `application.properties` só referencia nomes, com padrões sensatos.

**Arquivos:** `Dockerfile`, `docker-compose.yml`, `.dockerignore`,
`.env.example`.

```
JORNADA PRINCIPAL CONCLUÍDA
[█████████████████████████████████████████] 100%  — da planilha ao deploy
```

### Fase 9 — Salário fixo editável (atualização pós-lançamento)

Depois que o sistema já estava rodando, surgiu uma necessidade do mundo real: **o
salário fixo pode aumentar de um mês para o outro.** Reajustes acontecem por
força de mudanças externas — reajuste do salário mínimo, alterações na legislação
trabalhista, decisão do empregador — e o valor precisa acompanhar isso sem virar
um parto técnico.

**Como era antes.** O salário vivia na configuração (`controljob.salario-fixo`),
lida só na inicialização. Para mudá-lo era preciso editar o `.env` e **reiniciar a
aplicação** — algo inviável para quem só quer ajustar um número. Pior: o
`RelatorioService` tinha o valor **chumbado no código**, então nem a variável o
afetava.

**Como ficou.** O valor agora é **persistido no banco de dados** e editável por um
botão na área do administrador:

1. Uma entidade `SalarioConfig` guarda um único registro (a configuração vigente).
2. O `SalarioConfigService` resolve o valor atual; se ainda não existir registro,
   ele **semeia** o primeiro com o valor da variável de ambiente/histórico.
3. Um serviço de atualização valida (precisa ser maior que zero) e grava — **só
   muda de novo se o administrador mudar**.
4. Todos os pontos que dependem do salário (dashboard, relatório, auditoria e
   fechamento mensal) passaram a consultar **a mesma fonte**, eliminando o valor
   duplicado.
5. Na tela `Admin → Salário fixo`, há o valor atual, um campo para o novo valor e
   o botão de atualizar.

**Por que uma linha única e não um histórico de vigências?** Porque o pedido era
simples e direto: um valor vigente que o administrador controla. O histórico dos
meses passados **não se perde**: cada fechamento mensal já grava o salário usado
no momento no **JSON de auditoria e no PDF**, que ficam arquivados. Ou seja, o
"recibo" daquele mês fica congelado com o valor da época, mesmo que o parâmetro
mude depois. Se no futuro for necessário recalcular meses antigos por vigência,
`SalarioConfig` é o ponto de extensão natural.

**Impacto da atualização:**

- **Dashboard** e **próximo fechamento**: passam a usar o valor novo.
- **Relatórios já gerados** (PDF/JSON): permanecem com o valor que valia quando
  foram emitidos.
- **Relatório de pagamentos** (`/relatorio`): deixou de ter valor chumbado e agora
  reflete o valor vigente.
- **Reset**: se a base for zerada, o valor volta ao padrão definido na subida.

**Validação:** boot da aplicação confirmado (contexto Spring sobe e a tabela é
criada pelo `ddl-auto`); a verificação funcional do fluxo (trocar o valor e ver o
dashboard/fechamento refletirem) é manual, como no restante do projeto.

```
PATCH APLICADO
[██████████████████████████████████████████] v1.1 — salário agora vive no banco
```

---

### Fase 10 — Endurecimento de segurança (v1.2)

Depois de um bom tempo em uso, o código passou por uma **revisão completa de
segurança**. O objetivo não era só "consertar bugs", e sim responder três
perguntas: *o que está exposto? o que um invasor consegue fazer? e o que vale a
pena corrigir hoje vs. depois?*

**A auditoria encontrou três vetores sérios** (além de itens médios e baixos):

1. **Console do H2 ligado em produção** — o banco em arquivo tem usuário `sa` e
   senha vazia; o console permite executar SQL e código arbitrários (RCE/vazamento).
2. **Login sem proteção contra força bruta** — qualquer pessoa podia tentar senhas
   indefinidamente. Pior em instalações acessíveis por rede.
3. **Bootstrap do admin inseguro** — com o banco vazio, a tela `/configurar-admin`
   está aberta para quem alcançar o app; e havia uma corrida (check-then-save) que
   podia criar usuários duplicados.

**Decisões e correções aplicadas:**

- **Console H2 desligado por padrão** (`H2_CONSOLE_ENABLED=false`) e **bloqueado na
  camada web** (`denyAll`), mesmo que alguém o religue por engano.
- **Lockout por usuário**: após 5 falhas em 15 minutos, a conta fica bloqueada por
  15 minutos (em memória). O reset acontece no login bem-sucedido.
- **Rate limit por IP**: no máximo 60 `POST /login` por IP em 15 minutos; acima
  disso o servidor responde `429`.
- **Senha inicial via ambiente**: `CONTROLJOB_ADMIN_SENHA`, se definida e com o
  banco vazio, cria o admin no startup — o `/configurar-admin` deixa de ser o
  caminho padrão (fica como fallback). Nunca sobrescreve usuário existente.
- **Corrida do bootstrap corrigida**: `configurarAdministrador` virou `synchronized`
  com re-checagem dentro do método.
- **Controle de acesso endurecido**: `/relatorio` passou a exigir o perfil `ADMIN`
  (antes qualquer usuário autenticado acessava pela URL, mesmo sem o link aparecer).

**Validação** (com banco temporário, sem tocar em dados de produção):
boot OK; 5 senhas erradas + a 6ª correta → bloqueada; 70 POSTs → 16 respostas
`429`; console H2 → `403`; bootstrap via env comprovado pelos logs. Detalhes e o
restante da análise estão no **[SEGURANCA.md](SEGURANCA.md)**.

As **medidas médias/baixas** (container não-root, HTTPS ponta a ponta, banco com
senha, atualização de dependências, CSP) foram registradas e seguem como
próximos passos neste repositório.

```
HARDENING APLICADO
[██████████████████████████████████████████] v1.2 — críticos e altos mitigados
```

---

## 6. O método: como a IA foi usada (e como não foi)

Seria desonesto dizer que "a IA fez tudo". Também seria desonesto esconder que ela
foi protagonista técnica. A divisão real do trabalho:

### O ciclo de cada tarefa

1. **Requisito (humano).** Definir o comportamento esperado em linguagem de
   negócio: "ao fechar o mês, os consertos pendentes devem deixar de contar".
2. **Desenho (humano + IA).** Discutir onde a regra deveria morar, quais classes
   surgiriam, quais eram os riscos.
3. **Implementação (IA).** A IA redigiu a maior parte do código e das consultas.
4. **Revisão (humano).** Ler, questionar, renomear, cortar excesso.
5. **Validação (humano).** Rodar a aplicação, criar consertos de teste, conferir
   os números na tela e no relatório.

### O que a IA fez bem

- Reduzir drasticamente o trabalho mecânico (getters/setters, queries, telas).
- Sugerir estruturas coerentes (camadas, Specifications, records).
- Manter consistência de estilo em todo o projeto.

### O que só o humano podia fazer

- Decidir o que é "fechar o mês" no mundo real.
- Perceber que a comissão tinha de viver no domínio, não na tela.
- Garantir que a aplicação fizesse sentido para quem trabalha na oficina.
- Proteger dados sensíveis e validar cada entrega.

> Resumo: a IA foi **parceira de implementação**, não dona do problema. O
> experimento mostra que um único desenvolvedor, com apoio de IA, leva uma
> aplicação de negócio completa do domínio ao deploy — mantendo o código legível.

---

## 7. Decisões, trade-offs e o que envelheceu bem

| Decisão | Motivo | Trade-off assumido |
| --- | --- | --- |
| Monólito Spring Boot | simplicidade de operar e manter | menos "escala" horizontal (não é o problema aqui) |
| H2 em arquivo | zero administração, backup por cópia | não ideal para múltiplas instâncias |
| Comissão no enum | fonte única de verdade | taxa fixa no código (mudança = novo deploy) |
| `dataFechamento` como estado | evita dupla contagem | um único campo binário (simples e suficiente) |
| DTOs como records | imutabilidade e clareza | mais uma classe por visão |
| Specifications | filtros combináveis e legíveis | curva de aprendizado inicial |
| Segredos em `.env` | nada sensível no repositório | exige configuração no deploy |
| Salário fixo no banco, com um valor vigente | ajuste sem redeploy, um clique no admin | sem histórico de vigências (o histórico fica nos PDF/JSON de cada fechamento) |

Todos os trade-offs foram escolhas conscientes para o contexto real: uma oficina,
poucos usuários, prioridade em simplicidade e confiabilidade.

---

## 8. Segurança e privacidade

- **Senhas com BCrypt**; nunca em texto no banco.
- **Primeiro acesso guiado** cria o admin; nada de senha padrão no código.
- **Perfis**: `ADMIN` edita; `LEITOR` apenas consulta.
- **Segredos fora do código**: o `.env` é ignorado pelo `.gitignore`; existe um
  `.env.example` documentando as chaves, sem valores reais.
- **Versionamento limpo**: `data/`, `target/` e `.env` ficam fora do repositório.
- Esta **versão pública** foi sanitizada: sem dados de consertos, comissões,
  salários, e-mails ou credenciais reais.

---

## 9. Empacotamento e publicação

```bash
# build do jar
./mvnw clean package

# subir com Docker
docker compose up -d --build
```

A aplicação sobe em um container, com o banco persistido em volume. O mesmo
`docker-compose.yml` serve de base para publicar em qualquer máquina com Docker,
bastando preencher o `.env`.

---

## 10. Resultado final

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
  + Salário fixo editável no painel do administrador (salvo no banco)
```

**Validação:** a verificação foi **manual** — rodando a aplicação, criando
consertos em todos os tipos, conferindo os totais do dashboard, disparando o
fechamento e inspecionando o PDF, o JSON e o e-mail. Não há (ainda) suíte de
testes automatizados; está no roadmap.

---

## 11. Como rodar

```bash
# 1. Configure o ambiente
cp .env.example .env          # preencha e-mail/SMTP, salário e usuário admin
                              # (sem .env, sobe com valores padrão e sem e-mail)

# 2. Rode em desenvolvimento
./mvnw spring-boot:run        # abra http://localhost:8080

# 3. No primeiro acesso, defina a senha do administrador
#    (a tela /configurar-admin aparece automaticamente)

# 4. Ou empacote e suba com Docker
docker compose up -d --build  # aplicação disponível na porta 8081
```

Os dados ficam em `./data/controljob` (H2 em arquivo).

---

## 12. Próximos níveis (roadmap)

Ideias naturais, ainda **não** implementadas — listadas com transparência para
quem quiser contribuir:

- **Testes automatizados** (unitários para as regras de comissão e fechamento;
  de integração para os repositórios e filtros).
- **Migration de schema** com Flyway/Liquibase no lugar do `ddl-auto`.
- **Banco relacional externo** (PostgreSQL) se o uso crescer.
- **Troca de senha** e recuperação de acesso.
- **Hospedagem atrás de HTTPS** com proxy reverso.

---

## Nota sobre esta versão pública

Este repositório foi preparado para divulgar o trabalho de desenvolvimento
assistido por IA. Ele **não** contém nenhum dado real de consertos, comissões,
salários, e-mails ou credenciais. Usuário administrador e salário padrão são
exemplos genéricos, e todos os segredos são fornecidos por variáveis de ambiente.

Construído com Java, Spring Boot e muita conversa entre pessoa e máquina.
