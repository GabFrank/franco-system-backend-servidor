-- Nuevo estado SOLICITADO para solicitud de pago: entre PENDIENTE (borrador) y
-- CONCLUIDO (pagada). SOLICITADO = validada y lista para pagar (lo que ven los
-- diálogos de pago). Aditivo, idempotente. AFTER 'PENDIENTE' para orden lógico.
ALTER TYPE operaciones.solicitud_pago_estado ADD VALUE IF NOT EXISTS 'SOLICITADO' AFTER 'PENDIENTE';
