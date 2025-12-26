-- V93.5: Create solicitud_pago_nota_recepcion table
-- Relationship table between solicitud_pago and nota_recepcion
-- This migration comes from the compras branch

CREATE TABLE IF NOT EXISTS operaciones.solicitud_pago_nota_recepcion (
    id BIGSERIAL PRIMARY KEY,
    solicitud_pago_id BIGINT NOT NULL,
    nota_recepcion_id BIGINT NOT NULL,
    monto_incluido DECIMAL(15,2) NOT NULL,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_solicitud_nota_solicitud FOREIGN KEY (solicitud_pago_id) 
        REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitud_nota_nota FOREIGN KEY (nota_recepcion_id) 
        REFERENCES operaciones.nota_recepcion(id) ON DELETE CASCADE,
    
    -- Unique constraint to prevent duplicate relationships
    CONSTRAINT uk_solicitud_nota UNIQUE (solicitud_pago_id, nota_recepcion_id)
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_solicitud_nota_solicitud ON operaciones.solicitud_pago_nota_recepcion(solicitud_pago_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_nota_nota ON operaciones.solicitud_pago_nota_recepcion(nota_recepcion_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_nota_creado_en ON operaciones.solicitud_pago_nota_recepcion(creado_en);