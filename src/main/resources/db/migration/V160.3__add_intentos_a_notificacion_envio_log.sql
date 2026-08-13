-- Presupuesto de reintentos por destino de envio push.
--
-- Hasta ahora el contador de intentos vivia en configuraciones.notificacion
-- (columna intentos_envio), pero se incrementa una vez por cada fila procesada
-- de esa notificacion. Con eso el presupuesto quedaba compartido entre todos
-- los destinos: una notificacion con 100 tokens agotaba los 5 intentos en las
-- primeras 5 filas, y las 95 restantes se descartaban en su primer error
-- transitorio sin haber reintentado nunca.
--
-- Esta columna mueve el contador a la fila, que es el destino real. Cada token
-- agota sus propios reintentos y recien ahi el envio pasa a CANCELADA.
--
-- Nullable con default 0: las filas historicas quedan en 0 y el codigo trata
-- NULL como 0, asi que no hace falta backfill.
--
-- configuraciones.notificacion_envio_log no esta registrada en
-- configuraciones.replication_table ni pertenece a ninguna publicacion, asi que
-- agregar la columna no toca replicacion.
ALTER TABLE configuraciones.notificacion_envio_log
    ADD COLUMN IF NOT EXISTS intentos INTEGER DEFAULT 0;

COMMENT ON COLUMN configuraciones.notificacion_envio_log.intentos IS
    'Intentos de envio de este destino. Al llegar a app.notifications.max-attempts el estado pasa a CANCELADA.';
