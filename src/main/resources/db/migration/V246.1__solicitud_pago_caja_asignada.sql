-- =====================================================================
-- Caja asignada a una solicitud de pago
-- =====================================================================
-- Tesoreria asigna una solicitud de pago a una caja para que la pague el
-- operador de esa caja (rol TESORERIA CAJA, V245.1), que no ve el resto de
-- la deuda con proveedores. NULL = sin asignar: la paga tesoreria, como
-- siempre. La escribe asignarSolicitudPagoACaja y la lee
-- PagoProveedorService en modo acotado.
--
-- Aditiva e idempotente: columna nullable, FK sin cascade (borrar una caja
-- no puede llevarse solicitudes) e indice parcial. operaciones.solicitud_pago
-- no esta publicada a las filiales, asi que no lleva espejo.
-- =====================================================================

ALTER TABLE operaciones.solicitud_pago
    ADD COLUMN IF NOT EXISTS caja_virtual_asignada_id bigint NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'solicitud_pago_caja_virtual_asignada_fk'
          AND conrelid = 'operaciones.solicitud_pago'::regclass
    ) THEN
        ALTER TABLE operaciones.solicitud_pago
            ADD CONSTRAINT solicitud_pago_caja_virtual_asignada_fk
            FOREIGN KEY (caja_virtual_asignada_id) REFERENCES financiero.caja_virtual (id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS solicitud_pago_caja_virtual_asignada_idx
    ON operaciones.solicitud_pago (caja_virtual_asignada_id)
    WHERE caja_virtual_asignada_id IS NOT NULL;
