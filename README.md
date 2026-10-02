# Gerenciador de Eventos Acadêmicos

Aplicação web para organização de eventos acadêmicos: cadastro de eventos, inscrição com fila
de espera, registro de presença, certificado em PDF com código de autenticidade e avaliações
pós-evento. O diferencial é o **cadastro assistido por IA**: o organizador cola um texto livre
e o sistema extrai os campos estruturados, sempre com confirmação humana antes de persistir.

Projeto acadêmico — UNISA, Análise e Desenvolvimento de Sistemas.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 4.1.1 |
| Segurança | Spring Security 7 + JWT (HS256) |
| IA | Spring AI 2.0.1 (Ollama no dev, OpenAI na apresentação) |
| Banco | PostgreSQL 16 |
| Migrations | Flyway |
| PDF | iText Core 9.8 |
| Build | Maven (wrapper no repo) |
| Testes | JUnit 5, Mockito, Testcontainers |

### Notas de compatibilidade

Spring Boot 4 é modular e a autoconfiguração saiu do `spring-boot-autoconfigure` monolítico.
Três consequências práticas no `pom.xml`:

- web é `spring-boot-starter-webmvc`, não `spring-boot-starter-web`;
- o Flyway exige `org.springframework.boot:spring-boot-flyway` além do `flyway-core` — sem ele
  as migrations simplesmente não rodam, e o erro aparece só em runtime;
- o slice de MockMvc vem em `spring-boot-starter-webmvc-test`, e a anotação mudou de pacote
  para `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.

O serializador padrão também mudou: Boot 4 usa **Jackson 3**, então o bean autoconfigurado é
`tools.jackson.databind.ObjectMapper`, não o `com.fasterxml.jackson.databind.ObjectMapper`.
Injetar o pacote antigo compila (o Jackson 2 continua no classpath via springdoc e Spring AI)
e falha só na subida do contexto.

Spring Boot 4.1.1 e Spring AI 2.0.1 são compatíveis, então **não** foi necessário o fallback
para Boot 3.5 + Spring AI 1.1 previsto na especificação.

## Rodando localmente

Pré-requisitos: Java 21, Docker e Docker Compose.

```bash
# 1. sobe o PostgreSQL
docker compose up -d

# 2. configura o ambiente
cp .env.example .env    # e preencha JWT_SECRET

# 3. roda a aplicação no perfil de desenvolvimento
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

A API sobe em `http://localhost:8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### Variáveis de ambiente

Estão documentadas em `.env.example`. As obrigatórias são `JWT_SECRET`, `DB_URL`, `DB_USER` e
`DB_PASSWORD`. `OPENAI_API_KEY` só é necessária no perfil `openai`.

### IA no desenvolvimento

O perfil `dev` fala com um [Ollama](https://ollama.com) local:

```bash
ollama pull llama3.1
```

Sem Ollama no ar, `POST /api/eventos/interpretar` responde `503 IA_INDISPONIVEL` e o fluxo
manual de cadastro continua funcionando — esse é o comportamento esperado (RN-10).

Para a apresentação, troque só o perfil: `-Dspring-boot.run.profiles=openai`.

## Testes

```bash
./mvnw test
```

Os testes de integração usam Testcontainers e **precisam de um daemon Docker rodando**. Eles
levantam um PostgreSQL real e aplicam as migrations, então cobrem schema, segurança e
persistência de verdade.

## Documentação

A especificação técnica de implementação está em
[`docs/ESPECIFICACAO-TECNICA.md`](docs/ESPECIFICACAO-TECNICA.md). As regras de negócio têm
identificadores `RN-xx` que aparecem como comentário no service que as implementa e no nome do
teste correspondente.

## Fases de implementação

- [x] **0** — scaffold, `docker-compose`, Flyway `V1__schema.sql`, `/actuator/health`
- [x] **1** — usuário, papéis, registro, login, JWT, `SecurityConfig`, tratamento de erro
- [x] **2** — CRUD de evento, fotos, listagem pública com filtros, transições de status
- [x] **3** — inscrição, fila de espera, cancelamento, promoção automática, concorrência
- [x] **4** — presença e certificado em PDF com validação pública
- [ ] **5** — extração de evento por IA com fallback
- [ ] **6** — avaliações e resumo por IA
- [ ] **7** — frontend

## Licença

O iText Core é AGPL, o que está adequado para um trabalho acadêmico de código aberto. A geração
de PDF fica isolada atrás da interface `GeradorCertificado`, então trocar por OpenPDF ou Apache
PDFBox é uma mudança local se o projeto virar código fechado.
