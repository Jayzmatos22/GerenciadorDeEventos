# Orientações para trabalhar neste repositório

Gerenciador de Eventos Acadêmicos: backend Spring Boot + frontend React, especificado em
[`docs/ESPECIFICACAO-TECNICA.md`](docs/ESPECIFICACAO-TECNICA.md). **Leia a especificação antes
de mudar comportamento** — ela é a fonte de verdade das regras de negócio.

## Comandos

```bash
# Backend (precisa do Postgres: docker compose up -d)
./mvnw verify                                        # 98 testes, Testcontainers exige Docker
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
npm run lint --prefix frontend       # ESLint type-aware
npm run typecheck --prefix frontend
npm run build --prefix frontend      # roda tsc antes do Vite

# Ponta a ponta (sobe Postgres, backend e Vite sozinho)
npm test --prefix e2e

# Produção
docker compose -f docker-compose.prod.yml up -d --build
```

## Convenções

- **Código, nomes e comentários em português.** Termos técnicos consagrados (`Controller`,
  `Service`, `Repository`, `DTO`) ficam em inglês.
- **Toda regra de negócio é rastreável.** As regras `RN-01` a `RN-11` aparecem como comentário
  no service que as implementa e no nome do teste correspondente
  (`deveEntrarNaFilaQuandoEventoLotado_RN01`). Mantenha isso ao mexer em regra.
- **Controller não acessa repository.** Service não devolve entidade JPA para fora da
  aplicação — sempre DTO ou record.
- **Propriedade do recurso se checa dentro do service** (RN-08), nunca só por anotação.
  `@PreAuthorize` cuida do papel; quem é dono do quê é decisão do service.
- Commits em Conventional Commits. Nada de `System.out.println` — use SLF4J.
- **Decisão assumida vira comentário `// DECISÃO:`** no código, como a especificação pede
  quando falta informação.

## Armadilhas que já custaram caro aqui

Cada uma destas gerou um bug real neste projeto. Valem uma segunda leitura antes de repetir o
padrão:

- **Spring Boot 4 é modular.** Web é `spring-boot-starter-webmvc`. O Flyway precisa de
  `spring-boot-flyway` além do `flyway-core` — sem ele as migrations *silenciosamente* não
  rodam. O slice de MockMvc vem em `spring-boot-starter-webmvc-test`.
- **Boot 4 usa Jackson 3.** O bean é `tools.jackson.databind.ObjectMapper`. Injetar o
  `com.fasterxml` **compila** (o Jackson 2 segue no classpath via springdoc e Spring AI) e só
  falha na subida do contexto.
- **O Postgres não infere tipo de parâmetro nulo.** JPQL com `:param is null` quebra com
  `could not determine data type`. Filtros opcionais usam Specifications, e o filtro ausente
  devolve `Specification.unrestricted()`.
- **`LocalDateTime.now()` segue o fuso da JVM**, não o do Jackson nem o do Hibernate. RN-04 e
  RN-06 dependem desse relógio; o fuso é fixado por `app.fuso-horario` em `FusoHorarioConfig`.
  Não remova isso achando que o `application.yml` já resolvia.
- **TypeScript está fixado em 6.x de propósito.** O `typescript-eslint` ainda não suporta a API
  do TS 7 e aborta com erro, não com aviso.
- **O `add_header` do nginx não é herdado** por um `location` que declare o próprio — e nenhum
  deles. Por isso os cabeçalhos de segurança vivem em `frontend/seguranca.conf`, incluído em
  cada bloco que precisa.
- **O Playwright inicia o `webServer` antes do `globalSetup`.** Por isso o banco sobe dentro de
  `e2e/apoio/subir-backend.sh`, e não num setup global.
- **Resposta antiga pode chegar depois da nova.** As buscas do frontend cancelam o pedido
  anterior com `AbortController`; sem isso a lista velha reaparece e desfaz o filtro.

## Ao mexer no frontend

- O contrato da API vive em `frontend/src/types/api.ts` e espelha os DTOs do backend. **Mudou
  DTO no backend, atualize ali.**
- O backend omite campo nulo do JSON (`default-property-inclusion: non_null`), então os
  opcionais são `campo?: T`, nunca `T | null`.
- `react-hooks/set-state-in-effect` está desligada, com a justificativa no `eslint.config.js`.
  É a única regra desligada — não desligue outras sem registrar o motivo ali.

## Antes de dar algo por pronto

Rode as três suítes. O e2e pega o que as outras não pegam, e já flagrou bug de fuso, de corrida
e de ordem de inicialização neste projeto.
