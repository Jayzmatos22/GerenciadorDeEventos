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

# 3. roda a aplicação (o perfil dev já vem configurado no pom)
./mvnw spring-boot:run        # Linux e macOS
.\mvnw.cmd spring-boot:run    # Windows
```

A API sobe em `http://localhost:8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html` (só no perfil `dev`; em produção a
  documentação fica desligada, porque descreve a superfície inteira da API)
- Health: `http://localhost:8080/actuator/health`

### Problemas comuns ao subir pela primeira vez

**`APPLICATION FAILED TO START` falando de `app.jwt.segredo`.** A aplicação subiu sem o perfil
`dev` e sem `JWT_SECRET`. Pelo `spring-boot:run` isso não acontece — o perfil `dev` já vem
configurado no `pom.xml`. Acontece ao rodar pela IDE: defina `SPRING_PROFILES_ACTIVE=dev` na
configuração de execução.

Não há segredo padrão fora do `dev` de propósito: um valor que funcionasse sozinho
acompanharia a aplicação até produção.

**No PowerShell, o executável precisa do prefixo `.\`** — `.\mvnw.cmd`, não `mvnw`. E evite
passar `-Dalguma.coisa=valor`: o PowerShell quebra o argumento no `=` antes de o Maven ver.
Se precisar de um, ponha entre aspas simples.

**`'vite' não é reconhecido como um comando`** (ou `vite: command not found`). As dependências
do frontend não estão instaladas na sua máquina — `node_modules/` não vai para o repositório.
Rode `npm install` dentro de `frontend/` uma vez.

**`Connection to localhost:5432 refused`.** O PostgreSQL não está no ar. Com o Docker Desktop
aberto, rode `docker compose up -d` na raiz do projeto.

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

### Provedores de modelo

A troca é só de perfil — nenhum código muda (seção 8 da especificação):

| Perfil | Provedor | Quando usa |
|---|---|---|
| `dev` | Ollama local | padrão; não gasta crédito, mas exige o Ollama instalado |
| `groq` | Groq | **o caminho mais curto para rodar de graça**; cota por conta, chave sem cartão |
| `openrouter` | OpenRouter | gateway para modelos de vários fornecedores, alguns gratuitos — mas a cota gratuita é compartilhada |
| `openai` | OpenAI | apresentação |

### Groq: a opção gratuita que não depende de fila alheia

A diferença entre o Groq e os modelos `:free` do OpenRouter não é velocidade, é **a natureza da
cota**. No OpenRouter, um endpoint `:free` é capacidade compartilhada entre todos os usuários do
gateway: o `429 Provider returned error` chega na primeira requisição da sua conta, e não há o
que fazer além de esperar a fila de estranhos andar. No Groq a cota é da sua conta — um `429`
quer dizer que foi você que passou do limite, e o limite aparece no console.

```properties
spring.profiles.active=groq
GROQ_API_KEY=gsk_sua-chave
```

A chave sai de <https://console.groq.com/keys>, sem cartão. O perfil já vem com rotação de três
modelos *instruct*, porque no Groq o limite por minuto é **por modelo** — enquanto um espera, o
próximo atende. Para ver os ids ativos na sua conta:

```bash
curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY" \
  | jq -r '.data[].id'
```

Se algum dos três não estiver disponível para a sua conta, a rotação passa adiante e **tira o
modelo da lista até o próximo restart** — o `404` é permanente para a conta, ao contrário de um
`429`, então repeti-lo em cada extração seria uma ida e volta jogada fora. No log:

```
llama-3.3-70b-versatile nao esta disponivel para esta conta e sai da rotacao: HTTP 404, corpo: {code=model_not_found, ...}
A extracao por IA respondeu com openai/gpt-oss-120b depois de 1 recusa(s): [...]
```

A marca não é persistida de propósito: reiniciar testa de novo, porque modelo volta ao catálogo
e conta muda de plano. Para evitar até esse primeiro 404, fixe só o que funciona no `.env`:

```properties
GROQ_MODELOS=openai/gpt-oss-120b,llama-3.1-8b-instant
```

Outra alternativa de cota própria é o **Google AI Studio**, também compatível com a API da
OpenAI: `base-url` `https://generativelanguage.googleapis.com/v1beta/openai`. Não ganhou perfil
próprio porque os ids dos modelos Gemini mudam com frequência e as fontes divergem sobre quais
estão no plano gratuito hoje; para usar, aponte `spring.ai.openai.base-url` e a chave do AI
Studio e confirme o id em <https://ai.google.dev/gemini-api/docs/openai>.

**OpenRouter** não é um starter novo: a API dele é compatível com a da OpenAI, então usa o
mesmo `spring-ai-starter-model-openai` que já está no projeto, mudando apenas a `base-url`.

Crie um arquivo `.env` na raiz do projeto (ele já está no `.gitignore`):

```properties
spring.profiles.active=openrouter
OPENROUTER_API_KEY=sk-or-v1-sua-chave
```

E suba normalmente, sem flag nenhuma:

```bash
./mvnw spring-boot:run        # Linux e macOS
.\mvnw.cmd spring-boot:run    # Windows
```

A aplicação lê o `.env` na subida. Sem ele, o perfil é o `dev`.

### Rodando de graça: rotação de modelos

Um modelo `:free` sozinho não é utilizável na prática. Esses endpoints são **capacidade
compartilhada entre todos os usuários do OpenRouter**, então recusam com `429 Provider returned
error` mesmo na primeira requisição da sua conta — não é a sua cota, é a fila de quem hospeda o
modelo. A resposta para isso é ter vários: `app.ia.modelos` aceita uma lista, e a chamada
percorre a rotação até um responder.

Monte a lista a partir do catálogo de **hoje**. No PowerShell:

```powershell
$ids = (Invoke-RestMethod https://openrouter.ai/api/v1/models).data |
  Where-Object { $_.id -like '*:free' } | ForEach-Object { $_.id }
"OPENROUTER_MODELOS=$($ids -join ',')"
```

No Linux e macOS, com `jq`:

```bash
curl -s https://openrouter.ai/api/v1/models \
  | jq -r '"OPENROUTER_MODELOS=" + ([.data[] | select(.id | endswith(":free")) | .id] | join(","))'
```

Cole a linha impressa no `.env`. **Não precisa separar os modelos de chat dos outros:** um slug
de *embedding*, de *rerank* ou de áudio recusa a chamada, e a rotação segue para o próximo —
errar um item da lista custa alguns milissegundos, não a feature.

Prefira modelos **instruct** no começo da lista, se for ordenar. A extração usa saída
estruturada (`.entity(...)`), que espera JSON e nada mais; um modelo de raciocínio que escreva
antes da resposta derruba a leitura. Isso também não trava nada — é tratado como recusa e a
rotação passa adiante — mas gasta uma tentativa.

Para fixar um modelo só (uma apresentação em que você não quer surpresa, por exemplo), use
`OPENROUTER_MODEL` e deixe `OPENROUTER_MODELOS` vazio:

```properties
OPENROUTER_MODEL=fornecedor/modelo:free
```

O modelo padrão, se você não definir nenhum dos dois, é `meta-llama/llama-3.3-70b-instruct` —
**pago de propósito**. As variantes gratuitas entram e saem do catálogo: a desse mesmo modelo
foi descontinuada e hoje responde `404 This model is unavailable for free`. Fixar uma no
`application-openrouter.yml` entregaria um projeto que quebra sozinho quando a lista muda, com
um erro que parece defeito de configuração. O custo do padrão é irrisório aqui — uma extração
gasta por volta de 500 tokens de entrada e 150 de saída.

#### Orçamento de tempo

`app.ia.timeout-segundos` (20s) é o orçamento do **pedido inteiro**, não de cada tentativa:
quem espera é o navegador do organizador, e o que ele aguenta não muda por existirem oito
modelos na lista. Recusa de provedor saturado volta em menos de um segundo, então a rotação
cabe de sobra; já um modelo que aceitou e está gerando recebe todo o tempo restante, porque
interromper um que está funcionando para tentar outro seria trocar resposta por tentativa.

Quando a chamada falha, o cliente recebe sempre o mesmo `503 IA_INDISPONIVEL` (é o desenho da
RN-10), mas **o motivo real aparece no log do backend** em `WARN`, com o status HTTP e o corpo
da resposta:

| Status | O que é |
| --- | --- |
| `401` | chave inválida ou ausente |
| `402` | sem crédito, ou saldo negativo (acontece até em modelo `:free`) |
| `404` | modelo que não existe, ou variante `:free` descontinuada |
| `429` | limite estourado — **o corpo diz de quem** |

O `429` merece atenção porque a mensagem engana: o OpenRouter devolve `429: Provider returned
error`, que parece cota sua, mas `Provider returned error` significa que **o gateway está
repassando o erro de quem hospeda o modelo**. Os endpoints `:free` são capacidade compartilhada
entre todos os usuários do OpenRouter, então dá 429 mesmo na sua primeira requisição. O
`metadata.provider_name` e o `metadata.raw` no corpo do log apontam o responsável. Para
comparação, quando o limite é realmente seu a mensagem cita o limite da conta — 20 requisições
por minuto nos modelos `:free`, e 50 por dia enquanto a conta não tiver comprado 10 créditos.

Para a apresentação com OpenAI, o perfil é `openai`.

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
derruba tudo ao terminar. O banco sobe dentro do próprio script do backend
(`e2e/apoio/subir-backend.sh`), e não num `globalSetup` — o Playwright inicia o `webServer`
antes do `globalSetup`, então o backend tentaria conectar num banco que ainda não existe. A pilha sobe com `IA_HABILITADA=false` de propósito — sem um modelo
no ar, o caminho de indisponibilidade da RN-10 é o único lado da IA verificável.

Os testes semeiam os próprios dados com e-mails únicos, então rodam em paralelo e repetidas
vezes contra o mesmo banco sem limpar nada entre execuções.

## Rodando em produção

Dois caminhos. **Hospedado em PaaS gratuito** (Neon + Render + Vercel) está em
[`docs/DEPLOY.md`](docs/DEPLOY.md) — é o mais curto para pôr no ar com URL pública. Nesse
arranjo o frontend fica em outro domínio que o backend, então CORS e CSP passam a valer de
verdade, e o guia trata disso.

**Auto-hospedado**, a pilha inteira sobe com Docker numa origem só (seção 13.4 da
especificação):

```bash
cp .env.example .env    # e preencha JWT_SECRET e DB_PASSWORD
docker compose -f docker-compose.prod.yml up -d --build
```

O sistema fica em `http://localhost:8081`. Decisões que valem conhecer:

- **O nginx serve o frontend e faz o proxy de `/api` e `/uploads`**, então em produção tudo
  está na mesma origem e não há CORS no caminho. Rotas do React Router caem em `index.html`,
  senão recarregar a página em `/organizador` devolveria 404.
- **O nginx resolve o backend a cada requisição**, não na subida. Sem isso ele morre no boot
  com `host not found in upstream` quando o backend ainda não existe, e fica com um IP velho
  quando o contêiner reinicia.
- **O backend roda sem root** e a imagem é montada em camadas separadas para dependências e
  código: trocar o código não invalida a camada das dependências.
- **As fotos ficam num volume** (`uploads`). Sem ele, somem a cada recriação do contêiner.
- **O Postgres não publica porta**: só o backend o alcança.
- `JWT_SECRET` e `DB_PASSWORD` não têm valor padrão — o compose falha na subida se faltarem,
  em vez de subir com algo inseguro.

## Integração contínua

O workflow [`ci.yml`](.github/workflows/ci.yml) roda a cada push na `main` e em todo Pull
Request, em três etapas:

| Etapa | O que faz |
|---|---|
| `backend` | `./mvnw verify` — unitários e integração com Testcontainers |
| `frontend` | `npm ci`, lint, typecheck e build |
| `e2e` | Playwright contra a pilha inteira, só depois das duas anteriores passarem |

Isso é o que dá sentido à convenção da seção 14 da especificação, de `main` protegida
recebendo só via Pull Request: o PR chega com as três etapas verificadas.

## Documentação

A especificação técnica de implementação está em
[`docs/ESPECIFICACAO-TECNICA.md`](docs/ESPECIFICACAO-TECNICA.md), e a revisão de segurança —
o que foi corrigido, o que foi verificado e quais riscos foram aceitos e por quê — em
[`docs/REVISAO-DE-SEGURANCA.md`](docs/REVISAO-DE-SEGURANCA.md). As regras de negócio têm
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
