ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS situacion_pago character varying;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS proveedor_id bigint;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS moneda_id bigint;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS monto_total numeric;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS monto_ya_pagado numeric;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS cantidad_cuotas integer;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS dia_vencimiento integer;

ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS fk_vehiculo_proveedor;
ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT fk_vehiculo_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.persona(id);

ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS fk_vehiculo_moneda;
ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT fk_vehiculo_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
