-- Add proveedor_id, moneda_id, monto_total to solicitud_pago (required by SolicitudPago entity)
-- These were missing in DB; entity expects them NOT NULL.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago') THEN
        -- proveedor_id
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'proveedor_id') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN proveedor_id int8 NULL;
            UPDATE operaciones.solicitud_pago SET proveedor_id = (SELECT id FROM personas.proveedor LIMIT 1) WHERE proveedor_id IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN proveedor_id SET NOT NULL;
            ALTER TABLE operaciones.solicitud_pago
                ADD CONSTRAINT solicitud_pago_proveedor_fk
                FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id) ON DELETE RESTRICT;
        END IF;
        -- moneda_id
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'moneda_id') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN moneda_id int8 NULL;
            UPDATE operaciones.solicitud_pago SET moneda_id = (SELECT id FROM financiero.moneda LIMIT 1) WHERE moneda_id IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN moneda_id SET NOT NULL;
            ALTER TABLE operaciones.solicitud_pago
                ADD CONSTRAINT solicitud_pago_moneda_fk
                FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE RESTRICT;
        END IF;
        -- monto_total
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'monto_total') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN monto_total numeric NULL;
            UPDATE operaciones.solicitud_pago SET monto_total = 0 WHERE monto_total IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN monto_total SET NOT NULL;
        END IF;
    END IF;
END $$;
