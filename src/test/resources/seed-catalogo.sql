-- Seed mínimo do catálogo para testes da Story 2.3.
-- IDs explícitos para que asserts comparem valores fixos.

INSERT INTO grupocampo (id, funcionalidade) VALUES
  (1, 'TERRENO'),
  (2, 'SEGMENTO');

INSERT INTO campo (id, descricao, tipo, ativo, idgrupo) VALUES
  (10, 'AREA_TERRENO',      'DECIMAL',          'S', 1),
  (11, 'TIPO_MURO',         'MULTIPLA_ESCOLHA', 'S', 1),
  (12, 'OBSERVACAO',        'TEXTO',            'S', 1),
  (13, 'CAMPO_INATIVO',     'TEXTO',            'N', 1),
  (20, 'PADRAO_CONSTRUTIVO','MULTIPLA_ESCOLHA', 'S', 2),
  (21, 'DATA_VISTORIA',     'DATA',             'S', 2);

INSERT INTO alternativa (id, descricao, idcampo) VALUES
  (501, 'Alvenaria', 11),
  (502, 'Madeira',   11),
  (601, 'Alto',      20),
  (602, 'Médio',     20);
