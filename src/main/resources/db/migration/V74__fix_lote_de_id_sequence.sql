-- 1. Crear la secuencia si no existe
CREATE SEQUENCE IF NOT EXISTS financiero.lote_de_id_seq;

-- 2. Asignar la secuencia como valor por defecto para la columna id
ALTER TABLE financiero.lote_de ALTER COLUMN id SET DEFAULT nextval('financiero.lote_de_id_seq');

-- 3. Actualizar la secuencia con el valor máximo actual del id en la tabla
-- Esto previene errores de clave duplicada al insertar nuevos registros
SELECT setval('financiero.lote_de_id_seq', COALESCE((SELECT MAX(id) FROM financiero.lote_de), 1));
