-- Agregar campo activo a la tabla sucursal
ALTER TABLE empresarial.sucursal ADD COLUMN activo BOOLEAN DEFAULT TRUE;

-- Actualizar registros existentes para que estén activos por defecto
UPDATE empresarial.sucursal SET activo = TRUE WHERE activo IS NULL;

-- Hacer el campo NOT NULL después de la actualización
ALTER TABLE empresarial.sucursal ALTER COLUMN activo SET NOT NULL;

-- Agregar comentario al campo
COMMENT ON COLUMN empresarial.sucursal.activo IS 'Indica si la sucursal está activa o no'; 