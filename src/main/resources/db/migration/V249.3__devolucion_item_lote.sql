-- Lote del maestro en el item de devolucion, y lote con el que reingresa el canje.
--
-- Hasta ahora devolucion_item.lote era texto libre y la baja de stock de una devolucion no
-- tocaba el ledger por lote (operaciones.movimiento_stock_lote): para un producto con control de
-- lote, el saldo por lote quedaba por encima de la existencia real.
--
--   lote_id              lote del maestro elegido al cargar el item; la baja al separar sale de ahi.
--   lote_reingreso       numero del lote que entrega el proveedor en un canje.
--   movimiento_baja_id   movimiento de stock de la baja al separar. Revertir o cancelar devuelve a
--                        los mismos lotes espejando SU desglose, sin adivinar cual fue.
--   movimiento_canje_id  movimiento de stock del reingreso del canje, para revertirlo igual.
--
-- Los dos ids de movimiento no llevan FK: la PK de movimiento_stock es (id, sucursal_id).
--
-- Aditiva y nullable. devolucion_item no se replica a las filiales: no lleva espejo.

ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS lote_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS lote_reingreso VARCHAR(255);
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS movimiento_baja_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS movimiento_canje_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = 'operaciones' AND table_name = 'devolucion_item'
                     AND constraint_name = 'devolucion_item_lote_fk') THEN
        ALTER TABLE operaciones.devolucion_item
            ADD CONSTRAINT devolucion_item_lote_fk FOREIGN KEY (lote_id) REFERENCES operaciones.lote (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_devolucion_item_lote ON operaciones.devolucion_item (lote_id);
