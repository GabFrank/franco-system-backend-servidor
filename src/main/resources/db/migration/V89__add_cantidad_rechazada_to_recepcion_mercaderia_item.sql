-- Agregar columna cantidad_rechazada que falta en la tabla recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN cantidad_rechazada NUMERIC DEFAULT 0;
 
-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.cantidad_rechazada IS 'Cantidad rechazada del ítem durante la recepción física'; 