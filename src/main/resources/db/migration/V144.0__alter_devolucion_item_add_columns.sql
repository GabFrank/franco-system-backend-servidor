-- Modulo Devoluciones: extension del detalle operaciones.devolucion_item (aditivo).

ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS presentacion_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS motivo_averia_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS vencimiento DATE;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS costo_unitario DOUBLE PRECISION;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS cantidad_reingresada DOUBLE PRECISION;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS vencimiento_reingreso DATE;

-- FK a presentacion (la tabla ya existe). El FK a motivo_averia se agrega en V145 (tras crear la tabla).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = 'operaciones' AND table_name = 'devolucion_item'
                   AND constraint_name = 'fk_devolucion_item_presentacion') THEN
        ALTER TABLE operaciones.devolucion_item
            ADD CONSTRAINT fk_devolucion_item_presentacion
            FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_devolucion_item_presentacion ON operaciones.devolucion_item(presentacion_id);
CREATE INDEX IF NOT EXISTS idx_devolucion_item_motivo_averia ON operaciones.devolucion_item(motivo_averia_id);
