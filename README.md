# ControlJob

Aplicação Java para controle de consertos, comissões e salário.

## Funcionalidades

- Cadastro de consertos com: modelo, número da OS, IMEI (opcional), tipo, descrição, valor e data
- Dashboard com:
  - valor total dos consertos
  - comissão acumulada
  - salário total (fixo + comissão)
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

O salário fixo padrão é **R$ 1.500,00**. Para alterar, edite em `src/main/resources/application.properties`:

```properties
controljob.salario-fixo=1500.00
```

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
