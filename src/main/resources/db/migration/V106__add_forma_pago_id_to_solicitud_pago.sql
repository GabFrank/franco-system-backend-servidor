-- Add forma_pago_id to solicitud_pago (nullable; entity has @JoinColumn nullable = true)
-- Required by SolicitudPago.formaPago mapping

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'forma_pago_id') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN forma_pago_id int8 NULL;
            ALTER TABLE operaciones.solicitud_pago
                ADD CONSTRAINT solicitud_pago_forma_pago_fk
                FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id) ON DELETE SET NULL;
        END IF;
    END IF;
END $$;
