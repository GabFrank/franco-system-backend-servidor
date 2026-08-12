-- Modulo Devoluciones: extension de la cabecera operaciones.devolucion (aditivo).

ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS tipo operaciones.tipo_devolucion;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS identificador VARCHAR(60);
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS resolucion operaciones.tipo_resolucion_devolucion;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS nro_nota_credito VARCHAR(60);
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS monto_acreditado DOUBLE PRECISION;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS gasto_id BIGINT;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS gasto_sucursal_id BIGINT;
-- caja_virtual_id: sin FK a proposito (financiero.caja_virtual proviene de fd-93, puede no existir aun).
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS caja_virtual_id BIGINT;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS observacion TEXT;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS creado_en TIMESTAMP;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS finalizado BOOLEAN DEFAULT FALSE;

-- proveedor_id pasa a ser nullable (las devoluciones SIN_PROVEEDOR no tienen proveedor).
-- DROP NOT NULL es una relajacion retrocompatible (no rompe filas ni codigo existente).
ALTER TABLE operaciones.devolucion ALTER COLUMN proveedor_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_devolucion_tipo ON operaciones.devolucion(tipo);
CREATE INDEX IF NOT EXISTS idx_devolucion_identificador ON operaciones.devolucion(identificador);
