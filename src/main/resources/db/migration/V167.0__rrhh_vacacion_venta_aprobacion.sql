-- =====================================================================
-- RRHH — Aprobacion de la venta de dias de vacaciones.
-- =====================================================================
-- Vender dias de vacaciones se cobra como HABER en la liquidacion, pero se
-- creaba directamente en PENDIENTE (= listo para cobrar) sin que nadie lo
-- autorizara ni quedara constancia de quien. Se agrega el estado SOLICITADA
-- (via codigo, la columna es varchar) y el autorizante, igual que ya hace
-- vacacion_periodo.
--
-- Aditiva: columna nullable. Las ventas existentes quedan como estaban —
-- siguen en PENDIENTE y se pagan normal; solo las nuevas nacen SOLICITADA.
-- Idempotente.
-- =====================================================================

ALTER TABLE rrhh.vacacion_venta
    ADD COLUMN IF NOT EXISTS autorizado_por_id BIGINT;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vacacion_venta_autorizado_por') THEN
        ALTER TABLE rrhh.vacacion_venta
            ADD CONSTRAINT fk_vacacion_venta_autorizado_por
            FOREIGN KEY (autorizado_por_id) REFERENCES personas.usuario (id);
    END IF;
END $$;
