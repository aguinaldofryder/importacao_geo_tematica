-- =============================================================================
-- seed-e2e.sql — Seed completo para a suite E2E da Story 6.3
--
-- Deve ser executado em banco limpo (paranacity) antes de cada run do e2e.sh.
-- Cobre: schema aise, DDL de todas as tabelas do domínio, 10 linhas pré-
-- existentes em tribcadastroimobiliario (100001–100010) e tribimobiliariosegmento
-- (200001–200010), catálogo casado com os cabeçalhos das fixtures xlsx, e
-- tabelas de respostas inicialmente vazias.
--
-- Tabelas de fixtures:
--   TERRITORIAL-10linhas.xlsx — colunas: MATRICULA, TESTADA (fixa), AREA_TERRENO
--     (DECIMAL), TIPO_MURO (MULTIPLA_ESCOLHA), OBSERVACAO (TEXTO), DATA_CADASTRO (DATA)
--   PREDIAL-10linhas.xlsx     — colunas: MATRICULA, AREA_CONSTRUIDA (fixa),
--     AREA_PRIVATIVA (DECIMAL), PADRAO_CONSTRUTIVO (MULTIPLA_ESCOLHA),
--     OBSERVACAO_SEG (TEXTO), DATA_VISTORIA (DATA)
-- =============================================================================

-- ── Schema ──────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS aise;
SET search_path TO aise;

-- ── Sequences para respostas ─────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS aise.s_respostaterreno_id  START 1 INCREMENT 1;
CREATE SEQUENCE IF NOT EXISTS aise.s_respostasegmento_id START 1 INCREMENT 1;

-- ── Catálogo ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS aise.grupocampo (
    id             BIGINT       PRIMARY KEY,
    funcionalidade VARCHAR(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS aise.campo (
    id       BIGINT       PRIMARY KEY,
    descricao VARCHAR(200) NOT NULL,
    tipo      VARCHAR(30)  NOT NULL,
    ativo     CHAR(1)      NOT NULL,
    idgrupo   BIGINT       NOT NULL REFERENCES aise.grupocampo(id)
);

CREATE TABLE IF NOT EXISTS aise.alternativa (
    id       BIGINT        PRIMARY KEY,
    descricao VARCHAR(200) NOT NULL,
    idcampo  BIGINT        NOT NULL REFERENCES aise.campo(id)
);

-- ── Tabelas principais ───────────────────────────────────────────────────────
-- tribcadastroimobiliario: apenas UPDATE (nunca INSERT em produção — CON-03)
-- PK composta real: (tipocadastro, cadastrogeral)
-- Coluna física `testada` usada pelo fluxo TERRITORIAL (colunas-fixas.territorial=TESTADA)
CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario (
    tipocadastro  SMALLINT    NOT NULL DEFAULT 1,
    cadastrogeral NUMERIC     NOT NULL,
    testada       VARCHAR(50),
    PRIMARY KEY (tipocadastro, cadastrogeral)
);

-- tribimobiliariosegmento: apenas UPDATE (nunca INSERT em produção — CON-03)
-- PK composta real: (tipocadastro, cadastrogeral, sequencia)
-- Coluna física `area_construida` usada pelo fluxo PREDIAL (colunas-fixas.predial=AREA_CONSTRUIDA)
CREATE TABLE IF NOT EXISTS aise.tribimobiliariosegmento (
    tipocadastro    SMALLINT    NOT NULL DEFAULT 1,
    cadastrogeral   NUMERIC     NOT NULL,
    sequencia       NUMERIC     NOT NULL,
    area_construida VARCHAR(50),
    PRIMARY KEY (tipocadastro, cadastrogeral, sequencia)
);

-- ── Tabelas de respostas ─────────────────────────────────────────────────────
-- Upsert por (referencia, idcampo) via padrão DELETE+INSERT do SqlGeradorUpsert
CREATE TABLE IF NOT EXISTS aise.respostaterreno (
    id           BIGINT  PRIMARY KEY,
    referencia   TEXT    NOT NULL,
    valor        TEXT,
    idcampo      BIGINT  NOT NULL,
    idalternativa BIGINT
);

CREATE TABLE IF NOT EXISTS aise.respostasegmento (
    id            BIGINT  PRIMARY KEY,
    referencia    TEXT    NOT NULL,
    valor         TEXT,
    idcampo       BIGINT  NOT NULL,
    idalternativa BIGINT
);

-- =============================================================================
-- DML — Catálogo
-- =============================================================================

INSERT INTO aise.grupocampo (id, funcionalidade) VALUES
    (1, 'TERRENO'),
    (2, 'SEGMENTO');

-- Campos Territorial (idgrupo=1 / funcionalidade=TERRENO)
-- Cobrem todos os 4 tipos para exercitar os caminhos de coerção do Coercionador
INSERT INTO aise.campo (id, descricao, tipo, ativo, idgrupo) VALUES
    (10, 'AREA_TERRENO',      'DECIMAL',          'S', 1),
    (11, 'TIPO_MURO',         'MULTIPLA_ESCOLHA', 'S', 1),
    (12, 'OBSERVACAO',        'TEXTO',            'S', 1),
    (14, 'DATA_CADASTRO',     'DATA',             'S', 1);

-- Campos Predial (idgrupo=2 / funcionalidade=SEGMENTO)
INSERT INTO aise.campo (id, descricao, tipo, ativo, idgrupo) VALUES
    (20, 'PADRAO_CONSTRUTIVO', 'MULTIPLA_ESCOLHA', 'S', 2),
    (21, 'DATA_VISTORIA',      'DATA',             'S', 2),
    (22, 'AREA_PRIVATIVA',     'DECIMAL',          'S', 2),
    (23, 'OBSERVACAO_SEG',     'TEXTO',            'S', 2);

-- Alternativas para TIPO_MURO (idcampo=11) — mesmos valores nas linhas fixture
INSERT INTO aise.alternativa (id, descricao, idcampo) VALUES
    (501, 'Alvenaria', 11),
    (502, 'Madeira',   11);

-- Alternativas para PADRAO_CONSTRUTIVO (idcampo=20) — mesmos valores nas linhas fixture
INSERT INTO aise.alternativa (id, descricao, idcampo) VALUES
    (601, 'Alto',  20),
    (602, 'Médio', 20);

-- =============================================================================
-- DML — Tabelas principais pré-populadas (chaves casadas com os xlsx)
-- =============================================================================

-- Fluxo Territorial: cadastrogeral 100001–100010
INSERT INTO aise.tribcadastroimobiliario (tipocadastro, cadastrogeral, testada) VALUES
    (1, 100001, NULL),
    (1, 100002, NULL),
    (1, 100003, NULL),
    (1, 100004, NULL),
    (1, 100005, NULL),
    (1, 100006, NULL),
    (1, 100007, NULL),
    (1, 100008, NULL),
    (1, 100009, NULL),
    (1, 100010, NULL);

-- Fluxo Predial: cadastrogeral 200001–200010, sequencia 1
INSERT INTO aise.tribimobiliariosegmento (tipocadastro, cadastrogeral, sequencia, area_construida) VALUES
    (1, 200001, 1, NULL),
    (1, 200002, 1, NULL),
    (1, 200003, 1, NULL),
    (1, 200004, 1, NULL),
    (1, 200005, 1, NULL),
    (1, 200006, 1, NULL),
    (1, 200007, 1, NULL),
    (1, 200008, 1, NULL),
    (1, 200009, 1, NULL),
    (1, 200010, 1, NULL);

-- =============================================================================
-- Respostas vazias — exercita INSERT (primeiro run) e UPDATE (re-run)
-- Nenhuma linha inserida aqui intencionalmente: primeiro run do E2E
-- produzirá apenas INSERTs (respostas_insert > 0, conforme AC5).
-- =============================================================================
