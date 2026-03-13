ALTER TABLE vehiculos.dispositivo_gps
ADD COLUMN IF NOT EXISTS alerta_velocidad BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS velocidad_limite INTEGER DEFAULT 100,
ADD COLUMN IF NOT EXISTS alerta_vibracion BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS alerta_bateria_baja BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS alerta_acc BOOLEAN DEFAULT true;

COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_velocidad IS 'Enviar push notification al detectar exceso de velocidad';
COMMENT ON COLUMN vehiculos.dispositivo_gps.velocidad_limite IS 'Límite de velocidad en km/h para alerta de exceso';
COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_vibracion IS 'Enviar push notification al detectar choque/vibración';
COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_bateria_baja IS 'Enviar push notification al detectar batería baja';
COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_acc IS 'Enviar push notification al detectar cambio de ignición (ACC)';
