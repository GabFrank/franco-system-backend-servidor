-- V111.2: Deshabilitar propagación de TRUNCATE en central_pub
-- El TRUNCATE replicado puede borrar datos operativos en filiales de producción
ALTER PUBLICATION central_pub SET (publish = 'insert, update, delete');
