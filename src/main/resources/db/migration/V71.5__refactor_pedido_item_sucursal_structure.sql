-- V71.5__refactor_pedido_item_sucursal_structure.sql
-- Esta migración viene de la rama compras
-- Refactor pedido_item_sucursal table with new structure

-- Drop the existing table
DROP TABLE IF EXISTS operaciones.pedido_item_sucursal CASCADE;

-- Recreate the table with the new structure
CREATE TABLE IF NOT EXISTS operaciones.pedido_item_sucursal (
    id BIGSERIAL PRIMARY KEY,
    
    -- Foreign Keys
    pedido_item_id BIGINT NOT NULL REFERENCES operaciones.pedido_item(id) ON DELETE CASCADE,
    sucursal_id BIGINT NOT NULL REFERENCES empresarial.sucursal(id) ON DELETE CASCADE,
    sucursal_entrega_id BIGINT REFERENCES empresarial.sucursal(id) ON DELETE SET NULL,
    
    -- 📦 Pedido inicial
    cantidad_por_unidad DOUBLE PRECISION,
    
    -- ✅ Recepción efectiva
    cantidad_por_unidad_recibida DOUBLE PRECISION,
    cantidad_por_unidad_rechazada DOUBLE PRECISION,
    
    -- 🛑 Rechazo y modificación
    rechazado BOOLEAN DEFAULT FALSE,
    motivo_rechazo VARCHAR(255),
    modificado BOOLEAN DEFAULT FALSE,
    motivo_modificacion VARCHAR(255),
    
    -- 👤 Usuario y trazabilidad
    usuario_recepcion_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL,
    fecha_recepcion TIMESTAMP,
    
    verificado BOOLEAN DEFAULT FALSE,
    observacion TEXT,
    
    -- Audit fields
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_pedido_item_id ON operaciones.pedido_item_sucursal(pedido_item_id);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_sucursal_id ON operaciones.pedido_item_sucursal(sucursal_id);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_sucursal_entrega_id ON operaciones.pedido_item_sucursal(sucursal_entrega_id);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_usuario_recepcion_id ON operaciones.pedido_item_sucursal(usuario_recepcion_id);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_fecha_recepcion ON operaciones.pedido_item_sucursal(fecha_recepcion);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_rechazado ON operaciones.pedido_item_sucursal(rechazado);
CREATE INDEX IF NOT EXISTS idx_pedido_item_sucursal_verificado ON operaciones.pedido_item_sucursal(verificado);

-- Comments for documentation
COMMENT ON TABLE operaciones.pedido_item_sucursal IS 'Distribución y seguimiento de items de pedido por sucursal con control de recepción';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.cantidad_por_unidad IS 'Cantidad inicial solicitada por unidad';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.cantidad_por_unidad_recibida IS 'Cantidad efectivamente recibida por unidad';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.cantidad_por_unidad_rechazada IS 'Cantidad rechazada por unidad';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.rechazado IS 'Indica si el item fue rechazado';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.motivo_rechazo IS 'Motivo del rechazo (ej: VENCIDO, FALTANTE, AVERÍADO)';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.modificado IS 'Indica si el item fue modificado durante la recepción';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.motivo_modificacion IS 'Motivo de la modificación (ej: PRESENTACION INCORRECTA, CANTIDAD INCONSISTENTE)';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.verificado IS 'Indica si el item ha sido verificado por el usuario';
COMMENT ON COLUMN operaciones.pedido_item_sucursal.observacion IS 'Observaciones adicionales sobre el item'; 