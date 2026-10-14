-- =====================================================================
-- Caja que recibe envios de otras cajas
-- =====================================================================
-- Una transferencia entre cajas exige escritura sobre las dos. Con esta
-- marca, quien puede mover la caja de origen puede ENVIAR a la caja
-- marcada sin tener ningun acceso sobre ella: no ve su saldo ni sus
-- movimientos y no puede sacarle plata. Es lo que usa el operador
-- acotado (rol TESORERIA CAJA, V245.1) para mandar efectivo a la caja
-- mayor principal.
--
-- La escribe marcarCajaRecibeEnvios (TESORERIA GESTIONAR + responsable de
-- la caja) y la lee TesoreriaSecurityService.requireEnvioACaja.
--
-- Aditiva e idempotente; false por defecto, asi que nada cambia hasta que
-- tesoreria marque una caja. financiero.caja_virtual no se publica a las
-- filiales: no lleva espejo.
-- =====================================================================

ALTER TABLE financiero.caja_virtual
    ADD COLUMN IF NOT EXISTS recibe_envios boolean NOT NULL DEFAULT false;
