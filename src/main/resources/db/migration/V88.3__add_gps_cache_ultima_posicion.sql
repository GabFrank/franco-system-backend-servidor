ALTER TABLE vehiculos.dispositivo_gps
ADD COLUMN IF NOT EXISTS ultima_latitud DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS ultima_longitud DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS ultima_fecha_reporte TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS ultima_ignicion BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS ultima_velocidad INTEGER DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_dispositivo_gps_activo_posicion 
ON vehiculos.dispositivo_gps (activo) 
WHERE ultima_latitud IS NOT NULL AND activo = true;
CREATE INDEX IF NOT EXISTS idx_dispositivo_gps_imei 
ON vehiculos.dispositivo_gps (imei);

UPDATE vehiculos.dispositivo_gps gps
SET 
    ultima_latitud = t.latitud,
    ultima_longitud = t.longitud,
    ultima_fecha_reporte = t.fecha_gps,
    ultima_ignicion = t.ignicion,
    ultima_velocidad = t.velocidad
FROM (
    SELECT DISTINCT ON (dispositivo_id) 
        dispositivo_id, latitud, longitud, fecha_gps, ignicion, velocidad
    FROM vehiculos.telemetria
    WHERE latitud IS NOT NULL AND latitud != 0
    ORDER BY dispositivo_id, fecha_gps DESC
) t
WHERE gps.id = t.dispositivo_id;

COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_latitud IS 'Caché de última latitud conocida';
COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_longitud IS 'Caché de última longitud conocida';
COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_fecha_reporte IS 'Fecha/hora del último reporte GPS';
COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_ignicion IS 'Último estado de ignición conocido';
COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_velocidad IS 'Última velocidad reportada en km/h';
