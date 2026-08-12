-- =====================================================================
-- Tesorería — Fase 3: puente Retiro (caja PDV) → Caja Mayor
-- =====================================================================
-- El retiro nace en la filial y llega a central por replicación lógica
-- (BRANCH_TO_MAIN), no por Spring. Un poller @Scheduled en central reconcilia:
-- por cada retiro destinado a una caja mayor (caja_virtual_id NOT NULL) que
-- todavía no fue posteado (movimiento_caja_virtual_id NULL) y está concluido,
-- postea un INGRESO en la caja mayor y marca la fila.
--
-- caja_virtual_id ya existe (V114.1). Se agrega solo el marcador de procesado.
-- Aditivo e idempotente.
-- =====================================================================

ALTER TABLE financiero.retiro
    ADD COLUMN IF NOT EXISTS movimiento_caja_virtual_id bigint;

-- Índice para el poller (retiros pendientes de ingresar a caja mayor)
CREATE INDEX IF NOT EXISTS ix_retiro_pendiente_caja_mayor
    ON financiero.retiro (caja_virtual_id)
    WHERE caja_virtual_id IS NOT NULL AND movimiento_caja_virtual_id IS NULL;
