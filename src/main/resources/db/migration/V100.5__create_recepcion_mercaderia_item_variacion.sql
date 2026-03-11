-- V100.5: Create recepcion_mercaderia_item_variacion table
-- This migration comes from the compras branch

CREATE TABLE IF NOT EXISTS operaciones.recepcion_mercaderia_item_variacion (
    id BIGINT PRIMARY KEY,
    recepcion_mercaderia_item_id BIGINT NOT NULL,
    presentacion_id BIGINT,
    cantidad DOUBLE PRECISION,
    vencimiento TIMESTAMP,
    lote VARCHAR(255),
    rechazado BOOLEAN DEFAULT FALSE,
    motivo_rechazo VARCHAR(255),
    CONSTRAINT fk_recepcion_mercaderia_item
        FOREIGN KEY (recepcion_mercaderia_item_id)
        REFERENCES operaciones.recepcion_mercaderia_item(id),
    CONSTRAINT fk_presentacion
        FOREIGN KEY (presentacion_id)
        REFERENCES productos.presentacion(id)
);

-- Add indexes if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'recepcion_mercaderia_item_variacion' AND indexname = 'idx_recepcion_mercaderia_item_variacion_recepcion_item') THEN
        CREATE INDEX idx_recepcion_mercaderia_item_variacion_recepcion_item ON operaciones.recepcion_mercaderia_item_variacion(recepcion_mercaderia_item_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'recepcion_mercaderia_item_variacion' AND indexname = 'idx_recepcion_mercaderia_item_variacion_presentacion') THEN
        CREATE INDEX idx_recepcion_mercaderia_item_variacion_presentacion ON operaciones.recepcion_mercaderia_item_variacion(presentacion_id);
    END IF;
END $$;
