# Revisão de segurança

Revisão do sistema inteiro, feita com a aplicação no ar e cada hipótese verificada contra ela
— não só por leitura de código. Os achados viraram correção ou risco aceito explícito; os que
viraram correção têm teste de regressão.

## Corrigido

### 1. Canal de tempo no login expunha quem tem conta

**Era:** o login só executava a comparação BCrypt quando o e-mail existia. Medido na aplicação
rodando: **118 ms** para e-mail cadastrado contra **12 ms** para e-mail inexistente. Uma
diferença de dez vezes, estável o bastante para alguém de fora levantar a lista de quem tem
conta no sistema sem acertar uma única senha.

**Agora:** a comparação roda nos dois caminhos, contra um hash descartável quando o e-mail não
existe. Medido depois: **115 ms** contra **108 ms**, dentro do ruído.

Teste: `SegurancaIntegracaoTest.naoDeveVazarExistenciaDeContaPeloTempoDeResposta`.

### 2. Senha acima de 72 bytes virava erro 500

**Era:** o BCrypt opera sobre no máximo 72 **bytes** e o encoder do Spring Security recusa o
que passa disso. A validação limitava por quantidade de **caracteres**, e em UTF-8 um acento
ocupa dois bytes. Uma senha de 72 caracteres acentuados — perfeitamente razoável — estourava o
limite e devolvia `500 ERRO_INTERNO` no registro.

**Agora:** a restrição `@SenhaCompativelComBCrypt` mede em bytes e devolve `422 VALIDACAO` com
o campo e a mensagem, como qualquer outro erro de formulário.

Testes: `deveRecusarSenhaAcimaDoLimiteDoBCrypt` e `deveAceitarSenhaLongaDentroDoLimite`.

### 3. Cabeçalhos de segurança sumiam justamente da página HTML

**Era:** os cabeçalhos estavam declarados no bloco `server` do nginx. Só que o `add_header` do
nginx **não é herdado** por um `location` que declare o seu próprio — e nenhum deles, não só o
repetido. Como `/` cai em `/index.html` por `try_files`, e esse bloco define o próprio
`Cache-Control`, o documento HTML era servido **sem nenhum cabeçalho de segurança**. As
respostas da API tinham os do Spring Security; a página, nenhum.

**Agora:** os cabeçalhos vivem em `frontend/seguranca.conf`, incluído no `server` e em cada
bloco que tem `add_header` próprio. Verificado em `/`, numa rota de fundo e num asset com hash.

A política inclui uma CSP com `script-src 'self'` — o bundle do Vite não usa script inline.
Verificada dirigindo a interface inteira sob ela (catálogo, detalhe, login, área do
organizador, assistente de IA, validação de certificado): nenhuma violação.

### 4. Documentação da API ficava pública (endurecimento)

O Swagger descreve a superfície inteira da API, inclusive as rotas restritas. Passou a ficar
desligado por padrão, ligado só no perfil `dev` ou por `SWAGGER_HABILITADO`.

Teste: `naoDeveExporDocumentacaoForaDoPerfilDev`.

## Verificado e correto

Hipóteses levantadas e descartadas, cada uma checada contra a aplicação rodando:

| Hipótese | Resultado |
|---|---|
| Upload disfarçado viraria XSS armazenado | **Não.** O arquivo é servido como `image/png` com `X-Content-Type-Options: nosniff`, então o navegador não interpreta o conteúdo como HTML. |
| Token com `alg: none` seria aceito | **Não.** O jjwt valida contra a chave HMAC e recusa. Travado em `deveRecusarTokenComAlgoritmoNone`. |
| Actuator vazaria configuração | **Não.** Só `health` e `info` expostos, sem detalhes; `env`, `beans` e `mappings` respondem 401. |
| Nome de arquivo permitiria travessia de diretório | **Não.** O nome vem de um UUID gerado no servidor e o destino é normalizado e conferido contra a raiz. |
| Injeção de SQL | **Não há superfície.** Tudo por JPA e Criteria; a única consulta nativa usa parâmetro nomeado. |
| CSRF | **Não se aplica.** Sessão `STATELESS` e credencial em header `Authorization`, não em cookie. |
| IDOR nas rotas de dono | **Não.** A propriedade do recurso é checada dentro de cada service (RN-08), nunca só por anotação. |
| Segredos no repositório | **Não.** `.env` ignorado, `JWT_SECRET` e `DB_PASSWORD` sem valor padrão no compose de produção — a pilha falha na subida em vez de subir insegura. |

## Riscos aceitos

Nenhum destes é descuido: são decisões, com o motivo registrado.

**Token não tem revogação.** Sair da aplicação apaga o token no navegador, mas o que já foi
emitido continua válido até expirar. Trocar o papel de alguém, ou remover o acesso, só vale na
prática depois de 8 horas. A especificação define v1 sem refresh token; revogação de verdade
pede uma lista de tokens invalidados e consulta por requisição, o que contraria a decisão de
não acrescentar camada que nenhum requisito pede. **Mitigação se virar problema:** encurtar a
expiração.

**Qualquer pessoa pode se registrar como ORGANIZADOR.** É o que a seção 6 da especificação
define: o corpo do registro escolhe o papel. Num sistema real da universidade isso viria de
aprovação ou de um domínio de e-mail institucional.

**O registro revela se um e-mail já tem conta.** Responder `EMAIL_EM_USO` é o que permite ao
formulário orientar quem errou o cadastro. Esconder isso exige confirmação por e-mail, que está
fora do escopo da v1.

**Não há limite de tentativas de login.** Força bruta depende só da rede. Fora do escopo da v1;
num deploy real, resolve-se na borda.

**O conteúdo das imagens não é validado.** A extensão está em lista fechada e o arquivo é
servido com `nosniff`, então não há execução. Validar os bytes iniciais do arquivo impediria
armazenar lixo com cara de imagem, mas não muda a exposição.
