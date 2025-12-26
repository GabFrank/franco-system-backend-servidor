-- ===============================================
-- MIGRACIÓN: AGREGAR CAMPOS FALTANTES A NOTA_RECEPCION
-- ===============================================
-- Esta migración agrega los campos faltantes en las tablas nota_recepcion y nota_recepcion_item
-- para que coincidan con el modelo Java y el flujo de trabajo del manual

-- Step 1: Create the nota_recepcion_estado enum type
CREATE TYPE operaciones.nota_recepcion_estado AS ENUM (
    'PENDIENTE_CONCILIACION',
    'CONCILIADA', 
    'EN_RECEPCION',
    'RECEPCION_PARCIAL',
    'RECEPCION_COMPLETA',
    'CERRADA'
);

-- Step 2: Add missing fields to nota_recepcion table
ALTER TABLE operaciones.nota_recepcion 
ADD COLUMN moneda_id BIGINT NOT NULL,
ADD COLUMN cotizacion DOUBLE PRECISION,
ADD COLUMN estado operaciones.nota_recepcion_estado DEFAULT 'PENDIENTE_CONCILIACION';

-- Step 3: Add missing fields to nota_recepcion_item table
ALTER TABLE operaciones.nota_recepcion_item
ADD COLUMN cantidad_en_nota DOUBLE PRECISION,
ADD COLUMN precio_unitario_en_nota DOUBLE PRECISION,
ADD COLUMN producto_id BIGINT;

-- Step 4: Add foreign key constraints for the new fields
-- Add foreign key for moneda_id in nota_recepcion
ALTER TABLE operaciones.nota_recepcion 
ADD CONSTRAINT fk_nota_recepcion_moneda 
FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);

-- Add foreign key for producto_id in nota_recepcion_item
ALTER TABLE operaciones.nota_recepcion_item
ADD CONSTRAINT fk_nota_recepcion_item_producto 
FOREIGN KEY (producto_id) REFERENCES productos.producto(id);

-- Step 5: Add indexes for better performance
CREATE INDEX idx_nota_recepcion_estado ON operaciones.nota_recepcion(estado);
CREATE INDEX idx_nota_recepcion_moneda ON operaciones.nota_recepcion(moneda_id);
CREATE INDEX idx_nota_recepcion_item_producto ON operaciones.nota_recepcion_item(producto_id);
CREATE INDEX idx_nota_recepcion_item_cantidad ON operaciones.nota_recepcion_item(cantidad_en_nota);

-- Step 6: Add comments for documentation
COMMENT ON COLUMN operaciones.nota_recepcion.moneda_id IS 'Moneda de la nota de recepción (referencia a financiero.moneda)';
COMMENT ON COLUMN operaciones.nota_recepcion.cotizacion IS 'Cotización de la moneda al momento de la nota';
COMMENT ON COLUMN operaciones.nota_recepcion.estado IS 'Estado actual de la nota de recepción en el flujo de trabajo';
COMMENT ON COLUMN operaciones.nota_recepcion_item.cantidad_en_nota IS 'Cantidad del producto según la factura del proveedor (en unidades base)';
COMMENT ON COLUMN operaciones.nota_recepcion_item.precio_unitario_en_nota IS 'Precio unitario según la factura del proveedor (en unidades base)';
COMMENT ON COLUMN operaciones.nota_recepcion_item.producto_id IS 'Producto específico de la nota (referencia a productos.producto)';

-- Step 7: Update existing records to have default values
-- Set default estado for existing notas
UPDATE operaciones.nota_recepcion 
SET estado = 'PENDIENTE_CONCILIACION' 
WHERE estado IS NULL;

-- Step 8: Add NOT NULL constraints after setting default values
-- Make estado NOT NULL since it has a default value
ALTER TABLE operaciones.nota_recepcion 
ALTER COLUMN estado SET NOT NULL;

-- ===============================================
-- EXPLICACIÓN DE LOS CAMBIOS
-- ===============================================
-- 
-- 1. ENUM nota_recepcion_estado:
--    - PENDIENTE_CONCILIACION: Nota registrada, pendiente de conciliar ítems
--    - CONCILIADA: Todos los ítems han sido conciliados o rechazados
--    - EN_RECEPCION: Nota en proceso de recepción física
--    - RECEPCION_PARCIAL: Recepción física parcialmente completada
--    - RECEPCION_COMPLETA: Recepción física completamente finalizada
--    - CERRADA: Nota cerrada y procesada
--
-- 2. Campos agregados a nota_recepcion:
--    - moneda_id: Para cálculos de montos y conversiones
--    - cotizacion: Cotización de la moneda al momento de la nota
--    - estado: Control del flujo de trabajo de la nota
--
-- 3. Campos agregados a nota_recepcion_item:
--    - cantidad_en_nota: Cantidad según factura del proveedor (unidades base)
--    - precio_unitario_en_nota: Precio según factura del proveedor (unidades base)
--    - producto_id: Identificación específica del producto
--
-- Estos campos son CRÍTICOS para el flujo de conciliación documental
-- según el manual de implementación frontend. 