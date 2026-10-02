-- Gerenciador de Eventos Academicos - schema inicial (secao 4.2 da especificacao tecnica).

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
-- impede inscricao duplicada ativa, mas permite reinscrever apos cancelar (RN-03)
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
