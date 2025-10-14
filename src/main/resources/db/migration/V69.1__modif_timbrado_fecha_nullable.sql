-- Hacer fecha_inicio y fecha_fin nullable para timbrados electrónicos
-- V68.2__make_timbrado_dates_nullable_for_electronico.sql

-- Primero, hacer que las columnas puedan ser NULL
ALTER TABLE financiero.timbrado 
ALTER COLUMN fecha_inicio DROP NOT NULL;

ALTER TABLE financiero.timbrado 
ALTER COLUMN fecha_fin DROP NOT NULL;

-- Agregar constraint para que las fechas sean obligatorias SOLO si no es electrónico
ALTER TABLE financiero.timbrado 
ADD CONSTRAINT check_fechas_requeridas_no_electronico 
CHECK (
    (is_electronico = true) OR 
    (is_electronico = false AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL) OR
    (is_electronico IS NULL AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL)
);

-- Comentar la lógica del constraint
COMMENT ON CONSTRAINT check_fechas_requeridas_no_electronico ON financiero.timbrado 
IS 'Fechas inicio y fin son obligatorias solo para timbrados no electrónicos (físicos)';
