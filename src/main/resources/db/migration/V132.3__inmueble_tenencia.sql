ALTER TABLE activos.inmueble
    ADD COLUMN IF NOT EXISTS es_propio boolean DEFAULT true,
    ADD COLUMN IF NOT EXISTS alquiler_proveedor_id bigint,
    ADD COLUMN IF NOT EXISTS alquiler_monto numeric,
    ADD COLUMN IF NOT EXISTS alquiler_dia_vencimiento integer,
    ADD COLUMN IF NOT EXISTS alquiler_vigencia date;

ALTER TABLE activos.inmueble
    DROP CONSTRAINT IF EXISTS fk_inmueble_alquiler_proveedor;

ALTER TABLE activos.inmueble
    ADD CONSTRAINT fk_inmueble_alquiler_proveedor
        FOREIGN KEY (alquiler_proveedor_id) REFERENCES personas.persona(id);
