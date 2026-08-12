-- Nuevo estado PAGADO para PreGasto: terminal, alcanzado cuando su SolicitudPago
-- (tipo=GASTO) se paga por tesorería. Distinto de COMPLETADO (rendición de adelanto).
-- Aditivo, idempotente.
ALTER TYPE financiero.estado_pre_gasto ADD VALUE IF NOT EXISTS 'PAGADO' AFTER 'ENVIADO_A_TESORERIA';
