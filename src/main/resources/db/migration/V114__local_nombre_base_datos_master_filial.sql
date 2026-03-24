-- Configuración de nombres de base de datos para replicación lógica.
-- nombre_base_datos_master: nombre de la base del servidor central (ej. bodega_fact_test_2).
-- nombre_base_datos_filial: nombre de la base del servidor filial (ej. general_fact_test_2).
-- Si están en NULL, el sistema usa los valores por defecto de aplicación (central.server.dbname y "general").
ALTER TABLE configuraciones.local
    ADD COLUMN IF NOT EXISTS nombre_base_datos_master VARCHAR(255),
    ADD COLUMN IF NOT EXISTS nombre_base_datos_filial VARCHAR(255);

COMMENT ON COLUMN configuraciones.local.nombre_base_datos_master IS 'Nombre de la base de datos del servidor central (master). Usado en replicación lógica.';
COMMENT ON COLUMN configuraciones.local.nombre_base_datos_filial IS 'Nombre de la base de datos del servidor filial. Usado en replicación lógica al conectar a una sucursal.';

-- Ejemplo para entorno de pruebas (central): actualizar la fila existente en configuraciones.local:
-- UPDATE configuraciones.local SET nombre_base_datos_master = 'bodega_fact_test_2', nombre_base_datos_filial = 'general_fact_test_2' WHERE id = (SELECT id FROM configuraciones.local ORDER BY id LIMIT 1);
