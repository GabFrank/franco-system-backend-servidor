-- Crear el enum motivo_rechazo_fisico
CREATE TYPE operaciones.motivo_rechazo_fisico AS ENUM (
    'PRODUCTO_DANADO',
    'PRODUCTO_VENCIDO', 
    'CANTIDAD_INCORRECTA',
    'PRODUCTO_DIFERENTE',
    'EMBALAJE_DANADO',
    'OTRO'
);

-- Agregar comentario al enum
COMMENT ON TYPE operaciones.motivo_rechazo_fisico IS 'Motivos de rechazo físico de mercadería durante la recepción';

-- Agregar la columna motivo_rechazo a recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN motivo_rechazo operaciones.motivo_rechazo_fisico;

-- Agregar comentario a la columna
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.motivo_rechazo IS 'Motivo del rechazo físico del ítem'; 