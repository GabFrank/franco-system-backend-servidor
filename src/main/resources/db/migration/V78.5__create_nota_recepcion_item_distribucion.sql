-- ===============================================
-- MIGRACIÓN V78: CREAR TABLA NOTA_RECEPCION_ITEM_DISTRIBUCION
-- ===============================================
-- Esta migración crea la tabla para registrar la distribución de ítems
-- en la etapa de conciliación documental (NotaRecepcionItem)

-- V78.5: Create nota_recepcion_item_distribucion table
-- This migration comes from the compras branch

CREATE TABLE IF NOT EXISTS operaciones.nota_recepcion_item_distribucion (
    id BIGSERIAL PRIMARY KEY,
    nota_recepcion_item_id BIGINT NOT NULL,
    sucursal_entrega_id BIGINT NOT NULL,
    cantidad DOUBLE PRECISION NOT NULL,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    -- Foreign keys
    CONSTRAINT fk_nota_recepcion_item_distribucion_nota_recepcion_item 
        FOREIGN KEY (nota_recepcion_item_id) 
        REFERENCES operaciones.nota_recepcion_item(id),
    
    CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_entrega 
        FOREIGN KEY (sucursal_entrega_id) 
        REFERENCES empresarial.sucursal(id),
    
    CONSTRAINT fk_nota_recepcion_item_distribucion_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id)
);

-- Índices para optimizar consultas
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_distribucion_nota_recepcion_item 
    ON operaciones.nota_recepcion_item_distribucion(nota_recepcion_item_id);

CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_distribucion_sucursal_entrega 
    ON operaciones.nota_recepcion_item_distribucion(sucursal_entrega_id);

CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_distribucion_usuario 
    ON operaciones.nota_recepcion_item_distribucion(usuario_id);

-- Comentarios para documentación
COMMENT ON TABLE operaciones.nota_recepcion_item_distribucion IS 
    'Tabla que registra la distribución de ítems de nota de recepción a sucursales específicas durante la etapa de conciliación documental';

COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.nota_recepcion_item_id IS 
    'Referencia al ítem de nota de recepción que se está distribuyendo';

COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.sucursal_entrega_id IS 
    'Sucursal a la que se asigna esta cantidad del ítem';

COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.cantidad IS 
    'Cantidad del ítem asignada a esta sucursal según la documentación del proveedor';

COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.creado_en IS 
    'Fecha y hora de creación del registro';

COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.usuario_id IS 
    'Usuario que creó este registro de distribución'; 