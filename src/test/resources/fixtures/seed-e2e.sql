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
-- Coluna física `testada` usada pelo fluxo TERRITORIAL (colunas-fixas.territorial=TESTADA)
CREATE TABLE IF NOT EXISTS aise.tribcadastroimobiliario (
    id                      BIGSERIAL    PRIMARY KEY,
    tribcadastrogeral_idkey NUMERIC,
    testada                 VARCHAR(50)
);

-- tribimobiliariosegmento: apenas UPDATE (nunca INSERT em produção — CON-03)
-- Coluna física `area_construida` usada pelo fluxo PREDIAL (colunas-fixas.predial=AREA_CONSTRUIDA)
CREATE TABLE IF NOT EXISTS aise.tribimobiliariosegmento (
    id              BIGSERIAL   PRIMARY KEY,
    idkey           NUMERIC,
    area_construida VARCHAR(50)
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

-- Fluxo Territorial: tribcadastrogeral_idkey 100001–100010
INSERT INTO aise.tribcadastroimobiliario (tribcadastrogeral_idkey, testada) VALUES
    (100001, NULL),
    (100002, NULL),
    (100003, NULL),
    (100004, NULL),
    (100005, NULL),
    (100006, NULL),
    (100007, NULL),
    (100008, NULL),
    (100009, NULL),
    (100010, NULL);

-- Fluxo Predial: idkey 200001–200010
INSERT INTO aise.tribimobiliariosegmento (idkey, area_construida) VALUES
    (200001, NULL),
    (200002, NULL),
    (200003, NULL),
    (200004, NULL),
    (200005, NULL),
    (200006, NULL),
    (200007, NULL),
    (200008, NULL),
    (200009, NULL),
    (200010, NULL);

-- =============================================================================
-- Respostas vazias — exercita INSERT (primeiro run) e UPDATE (re-run)
-- Nenhuma linha inserida aqui intencionalmente: primeiro run do E2E
-- produzirá apenas INSERTs (respostas_insert > 0, conforme AC5).
-- =============================================================================
