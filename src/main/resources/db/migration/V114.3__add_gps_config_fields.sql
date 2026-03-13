ALTER TABLE vehiculos.dispositivo_gps
ADD COLUMN IF NOT EXISTS modo_sueno BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS intervalo_reporte INTEGER DEFAULT 30,
ADD COLUMN IF NOT EXISTS motor_bloqueado BOOLEAN DEFAULT false;

COMMENT ON COLUMN vehiculos.dispositivo_gps.modo_sueno IS 'Estado real del modo sueño en el dispositivo';
COMMENT ON COLUMN vehiculos.dispositivo_gps.intervalo_reporte IS 'Intervalo de reporte configurado en segundos';
COMMENT ON COLUMN vehiculos.dispositivo_gps.motor_bloqueado IS 'Estado real del bloqueo de motor/combustible';
