ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS situacion_pago character varying;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS proveedor_id bigint;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS moneda_id bigint;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS monto_total numeric;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS monto_ya_pagado numeric;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS cantidad_cuotas integer;
ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS dia_vencimiento integer;

ALTER TABLE activos.inmueble DROP CONSTRAINT IF EXISTS fk_inmueble_proveedor;
ALTER TABLE activos.inmueble ADD CONSTRAINT fk_inmueble_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.persona(id);

ALTER TABLE activos.inmueble DROP CONSTRAINT IF EXISTS fk_inmueble_moneda;
ALTER TABLE activos.inmueble ADD CONSTRAINT fk_inmueble_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
