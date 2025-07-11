ALTER TABLE operaciones.pedido_item
ADD COLUMN es_bonificacion BOOLEAN DEFAULT FALSE;

CREATE TYPE operaciones.nota_recepcion_item_estado AS ENUM (
    'PENDIENTE_CONCILIACION',
    'CONCILIADO',
    'RECHAZADO',
    'DISCREPANCIA'
);

ALTER TABLE operaciones.nota_recepcion_item
ADD COLUMN estado operaciones.nota_recepcion_item_estado DEFAULT 'PENDIENTE_CONCILIACION',
ADD COLUMN motivo_rechazo TEXT; 