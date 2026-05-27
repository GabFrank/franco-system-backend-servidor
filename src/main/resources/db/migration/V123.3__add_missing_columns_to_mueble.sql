ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS situacion_pago varchar(255);
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS proveedor_id int8;
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS moneda_id int8;
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS monto_total numeric(19, 2);
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS monto_ya_pagado numeric(19, 2);
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS cantidad_cuotas int4;
ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS dia_vencimiento int4;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_mueble_proveedor'
    ) THEN
        ALTER TABLE activos.mueble
            ADD CONSTRAINT fk_mueble_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_mueble_moneda'
    ) THEN
        ALTER TABLE activos.mueble
            ADD CONSTRAINT fk_mueble_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END $$;
