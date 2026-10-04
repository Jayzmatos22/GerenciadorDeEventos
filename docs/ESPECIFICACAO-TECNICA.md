# Gerenciador de Eventos Acadêmicos — Especificação Técnica de Implementação

> Documento de trabalho para desenvolvimento. Deriva da documentação acadêmica do projeto
> (UNISA, 2026) e a traduz em decisões implementáveis.
> **Status:** baseline v1 — as seções marcadas com 🔶 são decisões em aberto e podem mudar
> sem retrabalho estrutural.

---

## 0. Como usar este documento

Instruções para o agente de desenvolvimento:

1. **Implemente por fases** (seção 12). Não comece uma fase sem a anterior rodando e testada.
2. **Contratos são estáveis, implementação é livre.** Nomes de entidades, enums, endpoints e
   códigos de erro desta especificação não devem ser alterados sem confirmação. Estrutura
   interna de services, nomes de métodos privados e estratégia de teste ficam a seu critério.
3. **Não antecipe requisitos.** Nada de cache, mensageria, microsserviços, CQRS ou camada de
   abstração extra. O projeto é um monolito e deve continuar simples.
4. **Quando faltar informação**, escolha o caminho mais simples que atenda à regra de negócio
   e deixe um comentário `// DECISÃO: <o que foi assumido>` no código, em vez de parar.
5. **Regras de negócio são rastreáveis**: cada regra tem um identificador `RN-xx` e deve
   aparecer como comentário no service que a implementa e no nome do teste correspondente.

---

## 1. Escopo

Aplicação web para organização de eventos acadêmicos, cobrindo: cadastro de eventos,
inscrição de participantes com fila de espera, registro de presença, emissão de certificado
em PDF com código de autenticidade e coleta de avaliações pós-evento.

O diferencial é o cadastro assistido por IA: o organizador cola um texto livre descrevendo o
evento e o sistema extrai os campos estruturados via *function calling*, sempre com
confirmação humana antes de persistir.

**Fora de escopo na v1:** pagamento, emissão de nota fiscal, múltiplas sessões/palestras
dentro de um evento, submissão de trabalhos, notificações por e-mail, perfil de avaliador.

---

## 2. Stack

| Camada | Tecnologia | Versão alvo |
|---|---|---|
| Linguagem | Java | 21 (LTS) |
| Framework | Spring Boot | 4.1.x |
| Segurança | Spring Security | 7.x (vem com o Boot 4) |
| IA | Spring AI | 2.0.x |
| Banco | PostgreSQL | 16+ |
| Migrations | Flyway | gerenciado pelo Boot |
| PDF | iText Core | 9.x |
| Build | Maven | wrapper no repo |
| Testes | JUnit 5, Mockito, Testcontainers | gerenciado pelo Boot |

**Atenção a dois pontos na hora de criar o projeto:**

- Spring Boot 4 é modular: use `spring-boot-starter-webmvc`, não `spring-boot-starter-web`.
- Spring AI 2.0.x é a linha compatível com Boot 4.x. Importe o `spring-ai-bom` e use
  `spring-ai-starter-model-openai` (e/ou `spring-ai-starter-model-ollama`).

Confirme as versões exatas em `start.spring.io` no momento do scaffold — se houver
incompatibilidade entre Boot 4.1 e Spring AI, caia para Boot 3.5.x + Spring AI 1.1.x e
registre a mudança aqui.

**Licença do iText:** o iText Core é AGPL. Para um trabalho acadêmico de código aberto está
tudo certo. Se o projeto for fechado depois, trocar por OpenPDF ou Apache PDFBox — por isso a
geração de PDF fica isolada atrás de uma interface (seção 9).

---

## 3. Estrutura do projeto

Organização por funcionalidade, não por camada técnica:

```
src/main/java/br/unisa/eventos/
├── EventosApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── OpenApiConfig.java
│   └── AiConfig.java
├── shared/
│   ├── exception/           # RegraDeNegocioException, RecursoNaoEncontradoException, ...
│   ├── GlobalExceptionHandler.java
│   └── dto/ErroResponse.java
├── usuario/
│   ├── Usuario.java  UsuarioPapel.java  Papel.java
│   ├── UsuarioRepository.java
│   ├── AuthController.java  AuthService.java
│   ├── JwtService.java  JwtAuthFilter.java
│   └── dto/
├── evento/
│   ├── Evento.java  EventoFoto.java  StatusEvento.java
│   ├── EventoRepository.java  EventoService.java  EventoController.java
│   └── dto/
├── inscricao/
│   ├── Inscricao.java  StatusInscricao.java
│   ├── InscricaoRepository.java  InscricaoService.java  InscricaoController.java
│   └── dto/
├── presenca/
│   ├── Presenca.java  PresencaRepository.java  PresencaService.java  PresencaController.java
│   └── dto/
├── certificado/
│   ├── Certificado.java  CertificadoRepository.java  CertificadoService.java
│   ├── GeradorCertificado.java        # interface
│   ├── GeradorCertificadoIText.java   # implementação
│   └── CertificadoController.java
├── avaliacao/
│   ├── Avaliacao.java  ResumoAvaliacao.java
│   ├── AvaliacaoRepository.java  AvaliacaoService.java  AvaliacaoController.java
│   └── dto/
└── ia/
    ├── InteracaoIA.java  InteracaoIARepository.java
    ├── ExtratorEvento.java            # interface — porta de saída
    ├── ExtratorEventoSpringAI.java    # implementação
    ├── ResumidorAvaliacoes.java
    └── dto/EventoExtraidoDTO.java

src/main/resources/
├── application.yml
├── application-dev.yml
└── db/migration/V1__schema.sql
```

Regra: **controller não acessa repository**, service não devolve entidade JPA para fora
(sempre DTO/record).

---

## 4. Modelo de dados

### 4.1 Enums

```java
public enum Papel { PARTICIPANTE, ORGANIZADOR }
public enum StatusEvento { RASCUNHO, PUBLICADO, EM_ANDAMENTO, ENCERRADO, CANCELADO }
public enum StatusInscricao { CONFIRMADA, EM_ESPERA, CANCELADA, AUSENTE }
```

`PRESENTE` e `CERTIFICADA` **não** são valores de `StatusInscricao`. São estados derivados da
existência de registro em `presenca` e `certificado`. O diagrama de estados mostra os dois
como estados por clareza didática; a persistência segue o enum acima.

### 4.2 Schema (Flyway `V1__schema.sql`)

```sql
CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(120)  NOT NULL,
    email         VARCHAR(160)  NOT NULL UNIQUE,
    senha_hash    VARCHAR(100)  NOT NULL,
    criado_em     TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE usuario_papel (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    papel       VARCHAR(20) NOT NULL,
    UNIQUE (usuario_id, papel)
);

CREATE TABLE evento (
    id             BIGSERIAL PRIMARY KEY,
    organizador_id BIGINT       NOT NULL REFERENCES usuario(id),
    titulo         VARCHAR(160) NOT NULL,
    descricao      TEXT         NOT NULL,
    local          VARCHAR(200) NOT NULL,
    data_inicio    TIMESTAMP    NOT NULL,
    data_fim       TIMESTAMP    NOT NULL,
    carga_horaria  INT          NOT NULL CHECK (carga_horaria > 0),
    limite_vagas   INT          NOT NULL CHECK (limite_vagas > 0),
    status         VARCHAR(20)  NOT NULL DEFAULT 'RASCUNHO',
    criado_em      TIMESTAMP    NOT NULL DEFAULT now(),
    versao         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_evento_periodo CHECK (data_fim > data_inicio)
);
CREATE INDEX idx_evento_status_inicio ON evento (status, data_inicio);

CREATE TABLE evento_foto (
    id        BIGSERIAL PRIMARY KEY,
    evento_id BIGINT       NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL,
    ordem     INT          NOT NULL DEFAULT 0
);

CREATE TABLE inscricao (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT      NOT NULL REFERENCES usuario(id),
    evento_id      BIGINT      NOT NULL REFERENCES evento(id),
    status         VARCHAR(20) NOT NULL,
    posicao_fila   INT,
    data_inscricao TIMESTAMP   NOT NULL DEFAULT now()
);
-- impede inscrição duplicada ativa, mas permite reinscrever após cancelar
CREATE UNIQUE INDEX ux_inscricao_ativa
    ON inscricao (usuario_id, evento_id)
    WHERE status <> 'CANCELADA';
CREATE INDEX idx_inscricao_fila ON inscricao (evento_id, status, posicao_fila);

CREATE TABLE presenca (
    id                 BIGSERIAL PRIMARY KEY,
    inscricao_id       BIGINT    NOT NULL UNIQUE REFERENCES inscricao(id),
    data_hora_checkin  TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por     BIGINT    NOT NULL REFERENCES usuario(id)
);

CREATE TABLE certificado (
    id                    BIGSERIAL PRIMARY KEY,
    inscricao_id          BIGINT      NOT NULL UNIQUE REFERENCES inscricao(id),
    codigo_autenticidade  VARCHAR(40) NOT NULL UNIQUE,
    data_emissao          TIMESTAMP   NOT NULL DEFAULT now(),
    arquivo_url           VARCHAR(500)
);

CREATE TABLE avaliacao (
    id           BIGSERIAL PRIMARY KEY,
    inscricao_id BIGINT    NOT NULL UNIQUE REFERENCES inscricao(id),
    nota         INT       NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario   TEXT,
    data_envio   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE resumo_avaliacao (
    id             BIGSERIAL PRIMARY KEY,
    evento_id      BIGINT    NOT NULL UNIQUE REFERENCES evento(id),
    texto_resumo   TEXT      NOT NULL,
    nota_media     NUMERIC(3,2),
    total_avaliacoes INT,
    gerado_em      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE interacao_ia (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT      NOT NULL REFERENCES usuario(id),
    tipo           VARCHAR(30) NOT NULL,   -- EXTRACAO_EVENTO | RESUMO_AVALIACAO
    texto_entrada  TEXT        NOT NULL,
    json_extraido  TEXT,
    sucesso        BOOLEAN     NOT NULL,
    criado_em      TIMESTAMP   NOT NULL DEFAULT now()
);
```

Notas de mapeamento JPA:

- `evento.versao` → `@Version` (lock otimista, usado na concorrência de vagas — RN-02).
- Use `FetchType.LAZY` em todo `@ManyToOne`.
- Datas em `LocalDateTime`; o sistema opera em `America/Sao_Paulo`, fixado em `application.yml`.

---

## 5. Regras de negócio

| ID | Regra | RF |
|---|---|---|
| RN-01 | Ao inscrever: se `vagasConfirmadas < limiteVagas` → `CONFIRMADA`; senão → `EM_ESPERA` com `posicaoFila = max(posicaoFila)+1` do evento. | RF-05, RF-06 |
| RN-02 | Ao cancelar uma inscrição `CONFIRMADA`, promover automaticamente a primeira `EM_ESPERA` (menor `posicaoFila`) para `CONFIRMADA`, na mesma transação. Cancelar uma `EM_ESPERA` não promove ninguém, mas reordena a fila. | RF-06, RF-07 |
| RN-03 | Um usuário não pode ter duas inscrições ativas no mesmo evento. Reinscrição após cancelamento é permitida e entra no fim da fila. | RF-05 |
| RN-04 | Check-in só é aceito entre `dataInicio` e `dataFim + 24h`, apenas pelo organizador dono do evento, e apenas para inscrições `CONFIRMADA`. | RF-08 |
| RN-05 | Certificado só é emitido se existir `presenca` para a inscrição. Código de autenticidade = UUID sem hífens, em maiúsculas. Emissão é idempotente: chamar duas vezes devolve o mesmo certificado. | RF-09 |
| RN-06 | Avaliação só é aceita se houver presença registrada e `dataFim` já tiver passado. Uma avaliação por inscrição. | RF-10 |
| RN-07 | O resumo por IA só é gerado com no mínimo 3 avaliações (`app.avaliacao.minimo-para-resumo`). Regerar substitui o resumo anterior. | RF-10 |
| RN-08 | Apenas o organizador criador pode editar, publicar, cancelar, registrar presença e ver inscritos do seu evento. | RNF-04 |
| RN-09 | Evento `ENCERRADO` ou `CANCELADO` não aceita edição nem novas inscrições. Evento só aceita inscrição em `PUBLICADO`. | — |
| RN-10 | Falha na IA nunca bloqueia o fluxo: `/eventos/interpretar` devolve `503` com `codigo: IA_INDISPONIVEL`, e o frontend abre o formulário manual. | RNF-02 |
| RN-11 | Senha: mínimo 8 caracteres, armazenada com BCrypt (força 10). | RNF-03 |

**Concorrência (RN-01/RN-02):** a verificação de vagas e a inserção da inscrição ocorrem na
mesma transação, com `@Version` no `Evento` e retry em `OptimisticLockException` (até 3
tentativas). Alternativa aceitável: `SELECT ... FOR UPDATE` no evento via
`@Lock(LockModeType.PESSIMISTIC_WRITE)`. Escolha uma e teste com duas threads concorrendo
pela última vaga — esse teste é obrigatório.

---

## 6. API REST

Prefixo: `/api`. Autenticação: `Authorization: Bearer <jwt>`.
Datas em ISO-8601. Paginação: `?page=0&size=20` devolvendo o envelope padrão do Spring Data.

### Autenticação

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/auth/registrar` | público | cria usuário; corpo define o papel (`PARTICIPANTE` por padrão) |
| POST | `/auth/login` | público | devolve `{ token, expiraEm, usuario }` |
| GET | `/auth/eu` | autenticado | dados do usuário logado |

### Eventos

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| GET | `/eventos` | público | lista `PUBLICADO`; filtros `q`, `dataInicio`, `dataFim` (RF-04) |
| GET | `/eventos/{id}` | público | detalhe + fotos + vagas restantes |
| POST | `/eventos` | ORGANIZADOR | cria em `RASCUNHO` (RF-02) |
| PUT | `/eventos/{id}` | dono | edita (RN-08, RN-09) |
| PATCH | `/eventos/{id}/status` | dono | `{ "status": "PUBLICADO" }` |
| POST | `/eventos/{id}/fotos` | dono | upload multipart |
| DELETE | `/eventos/{id}/fotos/{fotoId}` | dono | remove foto |
| POST | `/eventos/interpretar` | ORGANIZADOR | `{ "texto": "..." }` → rascunho extraído (RF-03) |

`POST /eventos/interpretar` **não persiste evento**. Devolve um `EventoExtraidoDTO` com os
campos preenchidos e uma lista `camposNaoIdentificados`, para o organizador revisar e então
chamar `POST /eventos`.

### Inscrições

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/eventos/{id}/inscricoes` | PARTICIPANTE | RN-01; devolve `201` com status resultante |
| DELETE | `/inscricoes/{id}` | dono da inscrição | cancela, RN-02 |
| GET | `/inscricoes/minhas` | autenticado | histórico do participante |
| GET | `/eventos/{id}/inscricoes` | dono do evento | lista de inscritos e fila |

### Presença

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/eventos/{id}/presencas` | dono do evento | `{ "inscricaoId": 1 }` — RN-04 |
| GET | `/eventos/{id}/presencas` | dono do evento | lista de presentes |

### Certificado

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/inscricoes/{id}/certificado` | dono da inscrição | emite, RN-05 |
| GET | `/inscricoes/{id}/certificado` | dono da inscrição | baixa PDF (`application/pdf`) |
| GET | `/certificados/validar/{codigo}` | público | devolve nome, evento, carga horária e data |

### Avaliação

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/inscricoes/{id}/avaliacao` | dono da inscrição | `{ nota, comentario }` — RN-06 |
| GET | `/eventos/{id}/avaliacoes` | dono do evento | lista bruta |
| POST | `/eventos/{id}/avaliacoes/resumo` | dono do evento | gera resumo por IA — RN-07 |
| GET | `/eventos/{id}/avaliacoes/resumo` | dono do evento | último resumo |

### Formato de erro

Resposta única para todo erro tratado:

```json
{
  "timestamp": "2026-10-02T14:03:11",
  "status": 422,
  "codigo": "EVENTO_LOTADO",
  "mensagem": "O evento não possui vagas disponíveis.",
  "caminho": "/api/eventos/12/inscricoes",
  "campos": [ { "campo": "limiteVagas", "erro": "deve ser maior que zero" } ]
}
```

Códigos previstos: `CREDENCIAIS_INVALIDAS` (401), `ACESSO_NEGADO` (403),
`RECURSO_NAO_ENCONTRADO` (404), `INSCRICAO_DUPLICADA` (409), `VALIDACAO` (422),
`REGRA_DE_NEGOCIO` (422), `PRESENCA_NAO_REGISTRADA` (422), `IA_INDISPONIVEL` (503).

---

## 7. Segurança

- JWT HS256, segredo em variável de ambiente, expiração 8h, sem refresh token na v1.
- `SecurityConfig` com `SecurityFilterChain`; sessão `STATELESS`; CSRF desabilitado.
- Autorização em dois níveis: papel via `@PreAuthorize("hasRole('ORGANIZADOR')")` e
  propriedade do recurso verificada **dentro do service** (RN-08) — não tente resolver
  ownership só com anotação.
- Senhas com `BCryptPasswordEncoder`.
- Chaves de API da IA só no servidor, lidas de variável de ambiente, nunca logadas (RNF-03).
- Spring Security 7 tem defaults mais restritos que a versão 6; se um endpoint público
  retornar 401 inesperadamente, revise o `SecurityFilterChain` antes de afrouxar regra.

Rotas públicas: `POST /auth/**`, `GET /eventos`, `GET /eventos/{id}`,
`GET /certificados/validar/**`, Swagger e `/actuator/health`.

---

## 8. Integração com IA

### Extração de evento (RF-03)

```java
public record EventoExtraidoDTO(
    String titulo,
    String descricao,
    String local,
    LocalDateTime dataInicio,
    LocalDateTime dataFim,
    Integer cargaHoraria,
    Integer limiteVagas,
    List<String> camposNaoIdentificados
) {}
```

Implementação: `ChatClient` do Spring AI com saída estruturada (`.entity(EventoExtraidoDTO.class)`),
`temperature` baixa (0.2), prompt de sistema instruindo a **não inventar valores** — campo
ausente no texto volta `null` e entra em `camposNaoIdentificados`.

Requisitos da implementação:

- Timeout de 20s na chamada ao modelo.
- Toda chamada é registrada em `interacao_ia` (entrada, JSON de saída, sucesso/falha).
- Qualquer exceção (timeout, 429, resposta não parseável) → `IA_INDISPONIVEL` 503 (RN-10).
  Nada de stacktrace para o cliente.
- A interface `ExtratorEvento` isola o Spring AI do resto do código. Deve existir um
  `ExtratorEventoFake` para os testes, sem chamada de rede.

### Resumo de avaliações (RF-10)

Entrada: lista de comentários. Saída: texto corrido de 3 a 5 frases, em português, sem citar
participantes nominalmente. Mesma política de erro e registro em `interacao_ia`.

🔶 **Provedor em aberto:** desenvolver contra Ollama local (`llama3.1` ou similar) para não
gastar crédito, e manter o perfil `openai` configurado para a apresentação. A troca é só de
propriedade, sem mexer em código.

---

## 9. Certificado em PDF

Interface `GeradorCertificado` com um método
`byte[] gerar(DadosCertificado dados)`. Implementação com iText.

Conteúdo mínimo: nome do participante, título do evento, carga horária, período, data de
emissão, código de autenticidade e a URL pública de validação
(`{app.url-base}/certificados/validar/{codigo}`).

🔶 **Armazenamento em aberto:** na v1, gerar sob demanda e não persistir o arquivo
(`arquivo_url` fica nulo). Se depois quiserem guardar, o campo já existe.

---

## 10. Configuração

`application.yml` com propriedades da aplicação sob o prefixo `app`:

```yaml
app:
  jwt:
    segredo: ${JWT_SECRET}
    expiracao-horas: 8
  url-base: ${APP_URL:http://localhost:8080}
  avaliacao:
    minimo-para-resumo: 3
  checkin:
    tolerancia-horas: 24
  ia:
    habilitada: ${IA_HABILITADA:true}
    timeout-segundos: 20
```

Variáveis de ambiente: `JWT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `OPENAI_API_KEY`
(opcional), `IA_HABILITADA`. Entregue um `.env.example` no repositório e **nunca** um `.env`
preenchido.

`docker-compose.yml` sobe apenas o PostgreSQL para desenvolvimento local.

---

## 11. Testes

| Nível | O que cobrir | Ferramenta |
|---|---|---|
| Unitário | RN-01 a RN-11, isoladamente, com repositórios mockados | JUnit 5 + Mockito |
| Integração | endpoints REST, persistência real, segurança, migrations | `@SpringBootTest` + Testcontainers |
| E2E | criar evento → inscrever → check-in → certificado; e o caminho IA indisponível | **Playwright** (`e2e/`) |

Testes obrigatórios na v1, por serem onde o projeto realmente pode quebrar:

1. Última vaga disputada por duas threads — uma confirma, a outra entra na fila.
2. Cancelamento de confirmada promove a primeira da fila.
3. Certificado negado sem presença.
4. Reinscrição após cancelamento não viola o índice único.
5. `/eventos/interpretar` com o extrator falhando devolve 503 e o fluxo manual segue.

Nomeie os testes citando a regra: `deveEntrarNaFilaQuandoEventoLotado_RN01`.

---

## 12. Fases de implementação

Cada fase termina com a aplicação rodando, migrations aplicadas e testes verdes.

| Fase | Entrega |
|---|---|
| 0 | Scaffold Maven, `docker-compose` com Postgres, Flyway `V1__schema.sql`, `/actuator/health` respondendo |
| 1 | Usuário, papéis, registro, login, JWT, `SecurityConfig`, `GlobalExceptionHandler` |
| 2 | CRUD de evento, fotos, listagem pública com filtros, transições de status (RN-08, RN-09) |
| 3 | Inscrição, fila de espera, cancelamento, promoção automática, concorrência (RN-01 a RN-03) |
| 4 | Presença e certificado em PDF com validação pública (RN-04, RN-05) |
| 5 | Extração de evento por IA com fallback (RF-03, RN-10) |
| 6 | Avaliações e resumo por IA (RN-06, RN-07) |
| 7 | Frontend |

Fases 1 a 4 são o núcleo: se o prazo apertar, o projeto é defensável sem as fases 5 e 6
(cadastro manual puro), mas não sem a 3.

---

## 13. Decisões em aberto 🔶

Nenhuma destas bloqueia o início do desenvolvimento. Todas foram isoladas no desenho para que
a mudança seja local.

1. **Frontend.** ~~Não definido na documentação original. Recomendação: React + Tailwind,
   consumindo a API já especificada.~~ **Decidido:** React + TypeScript + Tailwind, consumindo a
   API já especificada. Como o backend é stateless e só fala JSON, a escolha não afeta nada das
   fases 0 a 6.
2. **Provedor de LLM.** Ollama local no desenvolvimento, OpenAI na apresentação. Troca por
   propriedade.
3. **Upload de imagens.** v1: salvar em disco local e servir por `/uploads/**`. Se precisar de
   deploy em plataforma efêmera, trocar por Cloudinary ou S3 — só a implementação do serviço de
   storage muda.
4. **Deploy.** ~~Não definido. O `docker-compose` cobre o desenvolvimento; produção fica para
   depois da fase 4.~~ **Decidido:** imagens Docker multi-stage para backend e frontend, com
   `docker-compose.prod.yml` subindo a pilha inteira. O frontend é servido por nginx, que também
   faz o proxy de `/api` e `/uploads` para o backend — mesma origem, sem CORS em produção.
5. **Papel de avaliador.** Removido do escopo porque nenhum requisito funcional o utiliza. Se
   voltar, é um valor novo no enum `Papel` e uma entidade de parecer — sem impacto no que já
   estiver pronto.
6. **Sessões/palestras dentro de um evento.** Fora do escopo; a presença é no evento inteiro.
   Caso volte, entra como entidade `Sessao` entre `Evento` e `Presenca`.

---

## 14. Convenções

- Commits: Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`).
- Branches: `feature/`, `fix/`, `refactor/`, `docs/`, sempre a partir da `main`.
- `main` protegida, só recebe via Pull Request com uma aprovação além do autor.
- Código, nomes de classes e comentários em português, seguindo os nomes das entidades deste
  documento. Palavras técnicas consagradas (`Controller`, `Service`, `Repository`, `DTO`)
  ficam em inglês.
- Nada de `System.out.println` — use SLF4J.
