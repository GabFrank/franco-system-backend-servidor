-- V84: Corregir campo presentacion_en_nota_id en nota_recepcion_item
-- Fecha: 2024-01-XX
-- Descripción: Actualizar registros existentes donde presentacion_en_nota_id es NULL
-- copiando la presentación del pedido_item correspondiente

-- Actualizar nota_recepcion_item donde presentacion_en_nota_id es NULL
-- copiando la presentación del pedido_item relacionado
UPDATE operaciones.nota_recepcion_item 
SET presentacion_en_nota_id = (
    SELECT pi.presentacion_creacion_id 
    FROM operaciones.pedido_item pi 
    WHERE pi.id = nota_recepcion_item.pedido_item_id
)
WHERE presentacion_en_nota_id IS NULL 
AND pedido_item_id IS NOT NULL;

-- Comentario para documentar la corrección
COMMENT ON COLUMN operaciones.nota_recepcion_item.presentacion_en_nota_id IS 'Presentación que el proveedor especifica en su factura/nota de recepción (corregido en V84)';