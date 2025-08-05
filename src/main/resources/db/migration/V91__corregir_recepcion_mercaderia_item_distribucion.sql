-- ===============================================
-- MIGRACIÓN V91: CORREGIR RELACIÓN RECEPCION_MERCADERIA_ITEM
-- ===============================================
-- Esta migración corrige la relación de RecepcionMercaderiaItem para que
-- esté vinculado a NotaRecepcionItemDistribucion en lugar de PedidoItemDistribucion
-- Esto mantiene la trazabilidad correcta: Plan → Documento → Realidad Física

-- 1. Eliminar la columna pedido_item_distribucion_id
ALTER TABLE operaciones.recepcion_mercaderia_item 
DROP COLUMN IF EXISTS pedido_item_distribucion_id;

-- 2. Eliminar el índice asociado si existe
DROP INDEX IF EXISTS idx_recepcion_mercaderia_item_pedido_item_distribucion_id;

-- 3. Eliminar la foreign key constraint si existe
ALTER TABLE operaciones.recepcion_mercaderia_item 
DROP CONSTRAINT IF EXISTS fk_recepcion_mercaderia_item_pedido_item_distribucion;

-- 4. Agregar la nueva columna nota_recepcion_item_distribucion_id
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN nota_recepcion_item_distribucion_id BIGINT;

-- 5. Agregar foreign key constraint para la nueva relación
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD CONSTRAINT fk_recepcion_mercaderia_item_nota_recepcion_item_distribucion 
FOREIGN KEY (nota_recepcion_item_distribucion_id) 
REFERENCES operaciones.nota_recepcion_item_distribucion(id);

-- 6. Crear índice para mejorar performance
CREATE INDEX idx_recepcion_mercaderia_item_nota_recepcion_item_distribucion_id 
ON operaciones.recepcion_mercaderia_item(nota_recepcion_item_distribucion_id);

-- 7. Comentarios para documentación
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.nota_recepcion_item_distribucion_id IS 
'Referencia a la distribución documental del ítem de nota de recepción asociada a esta recepción física';

-- 8. Comentario sobre la corrección
COMMENT ON TABLE operaciones.recepcion_mercaderia_item IS 
'Tabla corregida: RecepcionMercaderiaItem ahora está vinculado a NotaRecepcionItemDistribucion para mantener trazabilidad: Plan → Documento → Realidad Física'; 