-- Agregar columnas de estimación en PYG y BRL para Mueble
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS valor_tasacion_pyg NUMERIC;
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS valor_tasacion_brl NUMERIC;

-- Agregar columnas de estimación en PYG y BRL para Inmueble
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS valor_tasacion_pyg NUMERIC;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS valor_tasacion_brl NUMERIC;

-- Agregar columnas de estimación en PYG y BRL para Vehiculo
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_pyg NUMERIC;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_brl NUMERIC;
