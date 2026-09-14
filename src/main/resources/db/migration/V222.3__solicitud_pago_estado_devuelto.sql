-- Estado DEVUELTO de la solicitud de pago: tesorería no cancela, devuelve a compras la
-- solicitud que no va a pagar (falta la factura, monto mal cargado). Compras la corrige y
-- la reenvía (DEVUELTO → SOLICITADO) o la cancela. Conserva sus notas mientras tanto.
--
-- Idempotente: ADD VALUE IF NOT EXISTS no falla si el valor ya existe (mismo patrón que
-- V194.5, que agregó SOLICITADO a este mismo tipo).
ALTER TYPE operaciones.solicitud_pago_estado ADD VALUE IF NOT EXISTS 'DEVUELTO' AFTER 'SOLICITADO';
