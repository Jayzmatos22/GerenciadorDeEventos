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
| Frontend | React 19 + TypeScript 6, Tailwind 4, build com Vite |
| E2E | Playwright |

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

## Frontend

O frontend fica em [`frontend/`](frontend), é **React + TypeScript** e consome a API pelo proxy
do Vite, então em desenvolvimento não há CORS no caminho.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm run lint       # eslint, com regras que usam o type checker
npm run typecheck  # tsc --noEmit
npm run build      # typecheck + build de produção
```

O `build` roda `tsc --noEmit` antes do Vite, de propósito: o Vite transpila TypeScript sem
checar tipos, então sem esse passo um erro de tipo passaria direto para o bundle.

O contrato da API vive em [`src/types/api.ts`](frontend/src/types/api.ts), espelhando os DTOs do
backend. Duas convenções do backend moldam esses tipos:

- `default-property-inclusion: non_null` faz o campo nulo ser **omitido** do JSON, não vir como
  `null` — por isso os opcionais são `campo?: T` e não `T | null`;
- os códigos de erro são uma união fechada mais `(string & {})`, então um código novo no backend
  não quebra a compilação, mas o editor continua completando os conhecidos.

O `tsconfig.json` está em modo estrito, incluindo `noUncheckedIndexedAccess` e
`exactOptionalPropertyTypes`.

### Lint

O ESLint usa `typescript-eslint` em modo *type-aware* (`recommendedTypeChecked` e
`stylisticTypeChecked`), mais as regras do React Hooks e do React Refresh. Type-aware é o que
pega o que o `tsc` sozinho deixa passar — promessa não aguardada, handler `async` entregue a um
atributo JSX que espera retorno `void`, `await` em valor que não é promessa.

Dois pontos que valem saber antes de mexer:

- **O TypeScript está fixado em 6.x de propósito.** O `typescript-eslint` ainda não suporta a
  API do TypeScript 7 e aborta com erro, não com aviso
  ([issue 10940](https://github.com/typescript-eslint/typescript-eslint/issues/10940)).
- **Uma única regra está desligada**, `react-hooks/set-state-in-effect`, com a justificativa no
  próprio `eslint.config.js`. Ela proíbe chamar de dentro de um efeito qualquer função cujo
  corpo contenha `setState`, mesmo quando o `setState` só acontece depois do `await` — ela não
  consegue provar a fronteira assíncrona. Isso veta o padrão de buscar dados no efeito, que é o
  que se usa sem uma biblioteca de data fetching. O projeto não usa nenhuma, por decisão.

Ainda assim, as buscas de dados foram reescritas para não chamar `setState` de forma síncrona
antes do primeiro `await`: isso evita render em cascata e, de quebra, a lista anterior continua
visível durante uma troca de página em vez de piscar vazia.

Ele cobre os dois perfis:

- **Participante** — catálogo com filtros, detalhe do evento, inscrição (com entrada na fila
  quando lota), cancelamento, download do certificado e avaliação pós-evento.
- **Organizador** — seus eventos, cadastro assistido por IA, edição, transições de status,
  fotos, lista de inscritos com fila, registro de presença, avaliações recebidas e resumo
  por IA.
- **Público** — validação de certificado pelo código de autenticidade, sem login.

O cadastro assistido mostra a RN-10 funcionando na prática: se `/eventos/interpretar` responde
`503 IA_INDISPONIVEL`, o painel da IA se recolhe com um aviso e o formulário manual continua
ali, intacto. Os campos preenchidos pela IA ficam marcados como "confira antes de salvar", e os
que o texto não trazia aparecem numa lista do que falta preencher.

## Testes

```bash
./mvnw test
```

Os testes de integração usam Testcontainers e **precisam de um daemon Docker rodando**. Eles
levantam um PostgreSQL real e aplicam as migrations, então cobrem schema, segurança e
persistência de verdade.

Os cinco testes que a especificação exige estão cobertos:

| Cenário | Onde |
|---|---|
| Última vaga disputada por duas threads | `InscricaoIntegracaoTest` |
| Cancelamento de confirmada promove a primeira da fila | `InscricaoIntegracaoTest` e o unitário |
| Certificado negado sem presença | `CertificadoIntegracaoTest` |
| Reinscrição após cancelamento não viola o índice único | `InscricaoIntegracaoTest` |
| `/eventos/interpretar` com o extrator falhando devolve 503 | `InterpretacaoEventoIntegracaoTest` |

O teste de concorrência não passa por acaso: as duas threads calculam `CONFIRMADA`, uma perde a
disputa pela versão do evento, repete a transação e entra na fila na posição 1.

### Testes de ponta a ponta

Ficam em [`e2e/`](e2e) e cobrem o que a seção 11 da especificação nomeia: criar evento →
inscrever → check-in → certificado, e o caminho da IA indisponível.

```bash
cd e2e
npm install
npm test          # sobe Postgres, backend e frontend sozinho
npm run relatorio # abre o relatório da última execução
```

Um comando só: o Playwright sobe o PostgreSQL pelo `docker-compose`, o backend e o Vite, e
derruba tudo ao terminar. A pilha sobe com `IA_HABILITADA=false` de propósito — sem um modelo
no ar, o caminho de indisponibilidade da RN-10 é o único lado da IA verificável.

Os testes semeiam os próprios dados com e-mails únicos, então rodam em paralelo e repetidas
vezes contra o mesmo banco sem limpar nada entre execuções.

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
- [x] **5** — extração de evento por IA com fallback
- [x] **6** — avaliações e resumo por IA
- [x] **7** — frontend

## Licença

O iText Core é AGPL, o que está adequado para um trabalho acadêmico de código aberto. A geração
de PDF fica isolada atrás da interface `GeradorCertificado`, então trocar por OpenPDF ou Apache
PDFBox é uma mudança local se o projeto virar código fechado.
