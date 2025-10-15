CREATE TYPE operaciones.nota_recepcion_agrupada_estado AS ENUM (
    'EN_RECEPCION',
    'CONCLUIDO',
    'CANCELADO'
);

ALTER TABLE operaciones.nota_recepcion_agrupada
ADD COLUMN estado operaciones.nota_recepcion_agrupada_estado;

