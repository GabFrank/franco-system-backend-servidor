-- Migración V83: Actualizar estructura de nota_recepcion_item según manual
-- Fecha: 2024-01-XX
-- Descripción: Agregar campos faltantes y eliminar campo obsoleto en nota_recepcion_item

-- 1. Eliminar campo obsoleto sucursal_id (no está en el manual ni en la entidad Java)
ALTER TABLE operaciones.nota_recepcion_item DROP COLUMN IF EXISTS sucursal_id;

-- 2. Agregar campo es_bonificacion (Boolean)
ALTER TABLE operaciones.nota_recepcion_item 
ADD COLUMN es_bonificacion BOOLEAN DEFAULT FALSE;

-- Comentario para el campo
COMMENT ON COLUMN operaciones.nota_recepcion_item.es_bonificacion IS 'Indica si el ítem es una bonificación sin costo';

-- 3. Agregar campo vencimiento_en_nota (LocalDate)
ALTER TABLE operaciones.nota_recepcion_item 
ADD COLUMN vencimiento_en_nota DATE;

-- Comentario para el campo
COMMENT ON COLUMN operaciones.nota_recepcion_item.vencimiento_en_nota IS 'Fecha de vencimiento indicada en la nota del proveedor';

-- 4. Agregar campo observacion (String)
ALTER TABLE operaciones.nota_recepcion_item 
ADD COLUMN observacion TEXT;

-- Comentario para el campo
COMMENT ON COLUMN operaciones.nota_recepcion_item.observacion IS 'Notas adicionales sobre el ítem de la nota';

-- 5. Actualizar comentarios de campos existentes para documentación
COMMENT ON COLUMN operaciones.nota_recepcion_item.cantidad_en_nota IS 'Cantidad que figura en el documento del proveedor';
COMMENT ON COLUMN operaciones.nota_recepcion_item.precio_unitario_en_nota IS 'Precio que figura en el documento del proveedor';
COMMENT ON COLUMN operaciones.nota_recepcion_item.presentacion_en_nota_id IS 'Presentación del producto según la nota del proveedor';
COMMENT ON COLUMN operaciones.nota_recepcion_item.producto_id IS 'Producto según la factura del proveedor';
COMMENT ON COLUMN operaciones.nota_recepcion_item.pedido_item_id IS 'Vínculo al ítem de pedido original (opcional para productos no solicitados)';
COMMENT ON COLUMN operaciones.nota_recepcion_item.estado IS 'Estado del ítem de nota de recepción';
COMMENT ON COLUMN operaciones.nota_recepcion_item.motivo_rechazo IS 'Motivo del rechazo documental del ítem';

-- 6. Crear índices para optimizar consultas frecuentes
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_pedido_item_id 
ON operaciones.nota_recepcion_item(pedido_item_id);

CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_producto_id 
ON operaciones.nota_recepcion_item(producto_id);

CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_estado 
ON operaciones.nota_recepcion_item(estado);

-- 7. Verificar que la estructura final coincida con el manual
-- Campos según manual:
-- - id ✅
-- - nota_recepcion_id ✅
-- - pedido_item_id ✅
-- - producto_id ✅
-- - presentacion_en_nota_id ✅
-- - cantidad_en_nota ✅
-- - precio_unitario_en_nota ✅
-- - es_bonificacion ✅ (AGREGADO)
-- - vencimiento_en_nota ✅ (AGREGADO)
-- - observacion ✅ (AGREGADO)
-- - estado ✅
-- - motivo_rechazo ✅
-- - creado_en ✅
-- - usuario_id ✅ 