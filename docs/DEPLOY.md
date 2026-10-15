# Deploy: Neon + Render + Vercel

Três serviços, todos no plano gratuito. Banco no **Neon**, backend no **Render** (a partir do
`Dockerfile` que já existe), frontend estático na **Vercel**.

A ordem importa: o Render precisa da URL do banco, e a Vercel precisa da URL do backend.

---

## 1. Neon (banco)

1. <https://neon.tech> → novo projeto, **na mesma região do serviço no Render** — para o
   `render.yaml` como está, isso é **AWS US West (Oregon)**.

   Soa errado escolher Oregon morando no Brasil, mas quem conversa com o banco é o backend,
   não o navegador. Uma requisição HTTP vira várias idas e voltas ao Postgres (JPA, Flyway na
   subida, a transação de inscrição com retry otimista); o navegador faz **uma** chamada ao
   backend. Banco em São Paulo com backend em Oregon põe ~180 ms em cada uma dessas idas,
   multiplicado por consulta. O Render não tem região na América do Sul — são Oregon, Ohio,
   Virginia, Frankfurt e Singapura — então o par fica nos EUA e a distância até você é paga
   uma vez só, no salto do navegador.

   **As duas escolhas são permanentes.** Nem o Neon nem o Render deixam mudar a região de um
   projeto depois de criado; para trocar, é recriar. Se preferir Ohio ou Virginia (mais perto
   do Brasil que Oregon), mude o `region` no `render.yaml` e crie o Neon na mesma.
2. Copie a connection string. Ela vem no formato do `psql`:

   ```
   postgresql://usuario:senha@ep-algo-123.sa-east-1.aws.neon.tech/neondb?sslmode=require
   ```

3. **Traduza para JDBC**, que é o que o Spring espera. Três mudanças: prefixo `jdbc:`, usuário e
   senha saem da URL e viram variáveis próprias.

   | Variável | Valor |
   | --- | --- |
   | `DB_URL` | `jdbc:postgresql://ep-algo-123.sa-east-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USER` | `usuario` |
   | `DB_PASSWORD` | `senha` |

   **Mantenha o `?sslmode=require`.** O Neon só aceita conexão TLS; sem isso o driver tenta em
   texto puro e a conexão é recusada na subida, com erro que parece credencial errada.

Não precisa criar tabela nenhuma: o Flyway roda as migrations no primeiro start.

---

## 2. Render (backend)

O `render.yaml` na raiz já descreve o serviço, então não precisa preencher formulário.

1. <https://dashboard.render.com> → **New** → **Blueprint** → conecte o repositório e escolha a
   branch. O Render lê o `render.yaml` e monta o serviço `eventos-api` a partir do `Dockerfile`.
2. Ele vai pedir os valores marcados como `sync: false`. Preencha:

   | Variável | Valor | Observação |
   | --- | --- | --- |
   | `JWT_SECRET` | `openssl rand -base64 48` | mínimo 32 bytes |
   | `GROQ_API_KEY` | `gsk_...` | <https://console.groq.com/keys> |
   | `DB_URL` / `DB_USER` / `DB_PASSWORD` | do passo 1 | |
   | `APP_URL` | **URL da Vercel** | não a do Render — ver abaixo |
   | `CORS_ORIGENS` | **URL da Vercel** | sem barra no final |

   As duas últimas você ainda não tem. Deixe um valor qualquer agora e volte depois do passo 3 —
   trocar variável no Render dispara redeploy automático.

3. Anote a URL que o Render dá: `https://eventos-api-xxxx.onrender.com`.

### Dois detalhes do plano gratuito

**O serviço hiberna** depois de ~15 minutos sem requisição, e o próximo acesso espera o
container subir — perto de um minuto, somado ao start da JVM. **Antes de uma apresentação, abra
a aplicação uns minutos antes** para acordar tudo. O Neon também suspende, mas acorda em
segundos.

**O disco é efêmero.** As fotos de evento são gravadas em `/app/uploads` e **desaparecem a cada
deploy ou restart**. O resto não sofre: certificado é gerado na hora, em memória, e nada do
banco passa pelo disco do Render. Para fotos permanentes seria preciso disco pago no Render ou
um object storage (S3, Cloudflare R2) — está fora do escopo do plano gratuito. Para a defesa,
suba as fotos no dia.

---

## 3. Vercel (frontend)

1. <https://vercel.com> → **Add New** → **Project** → importe o repositório.
2. **Root Directory: `frontend`.** Este é o passo que mais se esquece; sem ele a Vercel tenta
   buildar a raiz, não acha `package.json` e falha.
   O resto (`npm run build`, saída em `dist`) já vem do `frontend/vercel.json`.
3. Em **Environment Variables**:

   | Variável | Valor |
   | --- | --- |
   | `VITE_API_URL` | `https://eventos-api-xxxx.onrender.com` |

   Sem barra no final. É build-time: mudar depois exige **Redeploy**, não basta salvar.

4. Edite `frontend/vercel.json` e troque as duas ocorrências de
   `https://TROQUE-PELO-SEU-BACKEND.onrender.com` pela URL real do Render. Commite e dê push.

   Isso é obrigatório, não cosmético. O `Content-Security-Policy` tem `connect-src` e
   `img-src`, e com o backend em outro domínio o navegador bloqueia **toda** chamada à API
   antes de ela sair — o erro aparece no console, não na resposta HTTP, e parece bug do
   frontend.

5. Anote a URL: `https://seu-projeto.vercel.app`.

---

## 4. Fechar o círculo

Volte ao Render e corrija as duas variáveis com a URL real da Vercel:

```
APP_URL=https://seu-projeto.vercel.app
CORS_ORIGENS=https://seu-projeto.vercel.app
```

**Por que as duas apontam para o frontend, e não para a API:**

- `CORS_ORIGENS` é a lista de origens que o navegador pode usar para chamar a API. Quem chama é
  a página na Vercel. Faltando, toda requisição morre no preflight.
- `APP_URL` monta o link de validação impresso no certificado
  (`CertificadoService:109` → `%s/certificados/validar/%s`), e `/certificados/validar/:codigo`
  é **rota do React**. Apontando para o Render, o certificado sai com um link que dá 404 — e só
  se descobre quando alguém tenta validar.

Se quiser domínio próprio depois, inclua os dois na `CORS_ORIGENS` separados por vírgula.

---

## Conferindo

```bash
# 1. Backend no ar e banco conectado (o health inclui o datasource)
curl https://eventos-api-xxxx.onrender.com/actuator/health

# 2. Migrations rodaram: o catálogo responde em vez de estourar
curl https://eventos-api-xxxx.onrender.com/api/eventos
```

Depois, no navegador: registre um organizador, crie um evento pela IA, publique, inscreva-se com
outra conta, registre presença e emita o certificado. É o mesmo roteiro do teste local.

## Quando algo não sobe

| Sintoma | Causa |
| --- | --- |
| `no open ports detected` | a `PORT` do Render não chegou ao Spring; `server.port` é `${PORT:8080}` desde o commit de deploy |
| `app.jwt.segredo ausente ou curto` | falta `JWT_SECRET`. **É proposital**: nenhum perfil de produção tem segredo padrão, então a aplicação recusa subir em vez de assinar token com segredo público |
| `could not determine data type` / conexão recusada | falta `?sslmode=require` no `DB_URL` |
| Tela carrega mas nada busca | `CORS_ORIGENS` sem o domínio da Vercel, ou a CSP do `vercel.json` sem a URL do backend. Veja o console do navegador |
| Imagens de evento quebradas | esperado após redeploy do Render — disco efêmero |
| Refresh em `/eventos/5` dá 404 | o `rewrites` do `vercel.json` não entrou: confirme que o Root Directory é `frontend` |
