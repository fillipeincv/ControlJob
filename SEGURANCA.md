# Segurança do ControlJob

Documento que registra a **análise de segurança** do projeto, as **decisões**
tomadas para corrighir falhas e o que **ainda está em andamento**. Este é um
documento vivo: à medida que novas medidas entram em produção, elas são movidas
da seção "em andamento" para "aplicadas" e registradas aqui.

> Nota: este repositório é a versão pública (sanitizada) do projeto. Nada aqui
> contém dados reais de produção — consertos, salários, e-mails, endereços de
> servidor ou credenciais.

---

## 1. Superfície de exposição

A aplicação é um monólito Spring Boot (MVC server-side + Thymeleaf) com:

- **H2 em arquivo** como banco (sem autenticação por padrão).
- **Autenticação por formulário** (Spring Security) com dois perfis: `ADMIN` e
  `LEITOR`.
- **Envio de e-mail** (SMTP) e arquivos (PDF/JSON) gerados no fechamento mensal.
- Execução em **container Docker**, com banco em volume e segredos no `.env`.

Superfície mais crítica: **qualquer ponto alcançável com uma sessão autenticada**,
porque o banco de dados (H2) era acessível a qualquer usuário autenticado quando
o console estava ligado.

---

## 2. Achados da auditoria

| Severidade | Achado | Status |
|------------|--------|--------|
| **Crítica** | Console do H2 habilitado em produção (RCE / exfiltração de todo o banco) | ✅ aplicado |
| **Alta** | Login sem proteção contra força bruta (nenhum rate limit / lockout) | ✅ aplicado |
| **Alta** | Bootstrap do admin aberto (`/configurar-admin`) + corrida TOCTOU | ✅ aplicado |
| **Média** | `/relatorio` acessível a qualquer autenticado (controle de acesso inconsistente) | ✅ aplicado |
| **Média** | Container Docker roda como root (exploração → impacto máximo) | 🔜 em andamento |
| **Média** | Sem HTTPS ponta a ponta; cookie de sessão sem `Secure` | 🔜 em andamento |
| **Média** | Banco H2 sem senha + `AUTO_SERVER=TRUE` local | 🔜 em andamento |
| **Média** | Erros internos expostos ao usuário (`e.getMessage()`) | 🔜 em andamento |
| **Baixa** | CSV formula injection ao abrir auditoria no Excel | 🔜 em andamento |
| **Baixa** | Mass assignment ao vincular a entidade `Conserto` direto do formulário | 🔜 em andamento |
| **Baixa** | Sem `Content-Security-Policy` / HSTS explícitos | 🔜 em andamento |
| **Baixa** | Dependências precisam de atualização / varredura OWASP | 🔜 em andamento |
| **Baixa** | `thymeleaf.cache` desligado; `spring.h2.console.enabled` por profile | 🔜 em andamento |

---

## 3. Decisões tomadas (e por quê)

### 3.1 Desligar o console do H2 (crítica)

O console do H2 permite executar SQL arbitrário e, via `CREATE ALIAS`, **código
Java arbitrário no processo**. Com banco `sa`/senha vazia, qualquer usuário
autenticado — inclusive o perfil de leitura — conseguiria ler/alterar/apagar
tudo ou executar comandos no container.

**Decisão:** desligado por padrão (`H2_CONSOLE_ENABLED=false`) e, para não
depender só da convenção, **bloqueado na camada web** com `denyAll()`. Se um dia
alguém religar o console por engano, ele continua inacessível pela rede.

### 3.2 Proteção contra força bruta (alta)

A aplicação tem poucos usuários e um ponto único de autenticação. Dois
mecanismos complementares:

- **Lockout por usuário** (em memória): 5 falhas em 15 minutos bloqueiam a conta
  por 15 minutos; o contador zera no login bem-sucedido.
- **Rate limit por IP** no `POST /login`: 60 requisições em 15 minutos; acima
  disso, `429`.

**Por quê em memória e não no banco?** A aplicação roda em uma única instância; o
estado em memória é simples, suficiente e recomeça a zero em cada reinício (um
bloqueio nunca é permanente). A tela de login continua mostrando apenas
"Usuário ou senha inválidos" — não revela se o problema é senha errada ou conta
bloqueada.

### 3.3 Bootstrap do administrador (alta)

O `/configurar-admin` precisa existir para o primeiro acesso, mas não pode ser
aberto para qualquer um enquanto o banco estiver vazio — senão o primeiro a
chegar "compra" a conta do admin.

**Decisão:** a senha inicial agora vem do ambiente (`CONTROLJOB_ADMIN_SENHA`).
Se definida e o banco estiver vazio, o admin é criado no *startup*. Assim uma
instalação nova **já nasce com o admin configurado** e a tela de bootstrap vira
apenas o fallback para quem não define a variável. A corrida que permitia criar
dois usuários (check-then-save) foi eliminada com `synchronized` + re-checagem.

### 3.4 Controle de acesso do relatório (média)

O relatório de pagamentos contém valores e comissões. Ele estava apenas
`authenticated` — um perfil `LEITOR` acessava pela URL mesmo sem o link aparecer.
**Decisão:** `ADMIN` only, fechando a incoerência entre regra de navegação e
regra de autorização.

---

## 4. O que está em andamento (próximos passos)

Ordem sugerida, uma a uma:

1. **Container não-root** — criar usuário no `Dockerfile`, `read-only` no
   filesystem e `tmpfs` para escrita; reduz o impacto se qualquer RCE ocorrer.
2. **HTTPS ponta a ponta** — expor o app só atrás de TLS (ex.: proxy reverso ou
   exposição gerenciada da rede privada); então marcar o cookie de sessão como
   `Secure` e adicionar HSTS.
3. **Banco com autenticação** — definir senha do H2 (ou migrar para PostgreSQL)
   e desativar `AUTO_SERVER` em produção.
4. **Erros amigáveis** — não devolver `e.getMessage()` na tela; logar o detalhe.
5. **CSV/Excel hardening** — prefixar células que começam com `= + - @`.
6. **DTOs de formulário** — impedir *mass assignment* no `Conserto`.
7. **Dependências** — manter Spring Boot/OpenPDF atualizados e rodar varredura
   OWASP no pipeline.
8. **CSP** — adicionar `Content-Security-Policy` compatível com os estilos
   atuais da aplicação.

---

## 5. Como validar as correções já aplicadas

Com a aplicação rodando (ex.: `./mvnw spring-boot:run`):

| Teste | Resultado esperado |
|-------|--------------------|
| `GET /h2-console` (com sessão autenticada) | `403` (bloqueado) |
| 5 logins com senha errada + 1 login com a senha correta | a 6ª tentativa **também falha** (conta bloqueada); página segue genérica |
| 70 `POST /login` rapidamente (mesmo IP) | uma parte responde `429` |
| `CONTROLJOB_ADMIN_SENHA` definida com banco vazio | no boot, o log mostra o admin criado via env |
| `GET /relatorio` com perfil `LEITOR` | `403` |
| `GET /relatorio` com perfil `ADMIN` | `200` |