ALTER TABLE configuraciones.notificacion_usuario
    ADD COLUMN estado_tablero varchar(50);

UPDATE configuraciones.notificacion_usuario
SET estado_tablero = 'POR_VERIFICAR'
WHERE estado_tablero IS NULL;