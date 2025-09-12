-- V98: Añadir campo estado_verificacion a recepcion_mercaderia_item
-- Este campo permitirá rastrear el estado de verificación de cada ítem de recepción

-- Crear el tipo enum para estado_verificacion
CREATE TYPE operaciones.estado_verificacion AS ENUM (
    'PENDIENTE',
    'VERIFICADO', 
    'VERIFICADO_CON_DIFERENCIA',
    'RECHAZADO'
);

-- Añadir la columna estado_verificacion a la tabla recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN estado_verificacion operaciones.estado_verificacion NOT NULL DEFAULT 'PENDIENTE';

-- Crear índice para mejorar el rendimiento de consultas por estado
CREATE INDEX idx_recepcion_mercaderia_item_estado_verificacion 
ON operaciones.recepcion_mercaderia_item(estado_verificacion);

-- Comentario en la columna
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.estado_verificacion IS 
'Estado de verificación del ítem: PENDIENTE, VERIFICADO, VERIFICADO_CON_DIFERENCIA, RECHAZADO';

-- Comentario en el tipo enum
COMMENT ON TYPE operaciones.estado_verificacion IS 
'Enum que representa el estado de verificación de un ítem de recepción de mercadería';
