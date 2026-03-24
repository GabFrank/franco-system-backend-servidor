-- Dirección del servidor central para replicación lógica (conexión desde filiales hacia central).
-- Si están en NULL, el sistema usa los valores de aplicación (central.server.ip y central.server.port).
-- NO usar IP de producción por defecto en código; configurar aquí o en application properties.
ALTER TABLE configuraciones.local
    ADD COLUMN IF NOT EXISTS ip_servidor_central VARCHAR(255),
    ADD COLUMN IF NOT EXISTS puerto_servidor_central INTEGER;

COMMENT ON COLUMN configuraciones.local.ip_servidor_central IS 'IP o hostname del servidor central. Usado en replicación lógica para que las filiales se conecten al central.';
COMMENT ON COLUMN configuraciones.local.puerto_servidor_central IS 'Puerto PostgreSQL del servidor central. Usado en replicación lógica.';

-- Ejemplo para entorno de pruebas (central en localhost):
-- UPDATE configuraciones.local SET ip_servidor_central = 'localhost', puerto_servidor_central = 5551 WHERE id = (SELECT id FROM configuraciones.local ORDER BY id LIMIT 1);
