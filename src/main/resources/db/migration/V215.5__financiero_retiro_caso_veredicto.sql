-- Veredicto del caso de retiro: la conclusión del que investiga.
--
-- Hasta acá el caso se cerraba con `resolucion`, texto libre. Eso alcanza para dejar constancia
-- y no alcanza para nada más: no se puede contar cuántos faltantes tuvo una sucursal, ni cuántas
-- veces contó mal el mismo receptor, que es exactamente el dato por el que este circuito existe.
--
-- El veredicto NO reemplaza a `retiro_verificacion_detalle.categoria`: esa es lo que cree el que
-- recibe, por moneda y sin haber averiguado nada. El veredicto es uno solo por caso y nombra el
-- lado responsable después de investigar.
--
-- Central-only: la tabla no está replicada (ver V214.5).

ALTER TABLE financiero.retiro_caso
    ADD COLUMN IF NOT EXISTS veredicto VARCHAR(40);

-- A quién se le atribuye la diferencia. Es el funcionario del PDV cuando el veredicto apunta al
-- lado que entrega, y el usuario que contó cuando apunta a tesorería — por eso apunta a persona,
-- que es lo único que ambos comparten.
ALTER TABLE financiero.retiro_caso
    ADD COLUMN IF NOT EXISTS responsable_persona_id BIGINT
    REFERENCES personas.persona (id);

-- Cuando el veredicto es REINTEGRADO, el retiro por el que volvió la plata. Sin esto, "se repuso
-- después" es una afirmación sin respaldo.
--
-- Va con su sucursal por el mismo motivo que origen_sucursal_id en movimiento_caja_virtual
-- (ver V214.5): el id de un retiro no es global — cada filial numera desde 1, así que el id
-- suelto no identifica nada. Sin la sucursal, dentro de un año nadie sabe a qué retiro apunta.
ALTER TABLE financiero.retiro_caso
    ADD COLUMN IF NOT EXISTS reintegro_retiro_id BIGINT;
ALTER TABLE financiero.retiro_caso
    ADD COLUMN IF NOT EXISTS reintegro_sucursal_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_retiro_caso_veredicto
    ON financiero.retiro_caso (veredicto);
