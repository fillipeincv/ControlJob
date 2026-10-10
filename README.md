# ControlJob

Aplicação Java para controle de consertos, comissões e salário.

## Funcionalidades

- Cadastro de consertos com: modelo, número da OS, IMEI (opcional), tipo, descrição, valor e data
- Dashboard com:
  - valor total dos consertos
  - comissão acumulada
  - salário total (fixo + comissão)
- Salário fixo editável pelo painel do administrador (salvo no banco, com histórico preservado nos relatórios já gerados)
- Tipos de conserto e comissão:
  - **Consertos em Geral** → 5%
  - **Reparo de Placa** → 50%
  - **Conserto para Lojistas** → 50%
- Página dedicada em `/lojistas` com os consertos feitos para lojistas

## Como rodar

```bash
./mvnw spring-boot:run
```

Abra: [http://localhost:8080](http://localhost:8080)

## Configuração

O salário fixo padrão é **R$ 1.500,00**. Esse valor é apenas o ponto de partida: depois de configurado, ele fica **salvo no banco de dados** e pode ser alterado pela tela **Admin → Salário fixo**, sem editar código nem reiniciar a aplicação. A mudança passa a valer para o dashboard e para os próximos fechamentos; relatórios já gerados mantêm o valor da época.

O valor inicial ainda pode ser definido na subida, via `CONTROLJOB_SALARIO_FIXO` (usado apenas quando ainda não existe valor salvo).

Os dados ficam salvos em `./data/controljob` (banco H2 em arquivo).

## Configuração de ambiente

Copie o `.env.example` para `.env` e preencha os valores (e-mail/SMTP, salário fixo e usuário administrador). O `.env` não é versionado.

```bash
cp .env.example .env
```

## Docker

```bash
docker compose up -d --build
```

A aplicação fica disponível na porta `8081`.

## Sobre o projeto

Este projeto foi desenvolvido com apoio de inteligência artificial, combinando
programação assistida por IA com revisão e decisões de arquitetura humanas.

Leia o passo a passo do desenvolvimento em [DEVLOG.md](DEVLOG.md).
