-- Ensure FK constraints exist for associations now loaded lazily.
-- This migration is defensive: it recreates constraints only if missing or misconfigured.

DO
$$
DECLARE
    def text;
BEGIN
    -- activos.ente_sucursal(ente_id) -> activos.ente(id)
    SELECT pg_get_constraintdef(c.oid)
    INTO def
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE c.conname = 'ente_sucursal_ente_id_fkey'
      AND n.nspname = 'activos'
      AND t.relname = 'ente_sucursal';

    IF def IS NULL OR position('REFERENCES activos.ente(id)' IN def) = 0 THEN
        IF def IS NOT NULL THEN
            EXECUTE 'ALTER TABLE activos.ente_sucursal DROP CONSTRAINT ente_sucursal_ente_id_fkey';
        END IF;
        EXECUTE 'ALTER TABLE activos.ente_sucursal ADD CONSTRAINT ente_sucursal_ente_id_fkey FOREIGN KEY (ente_id) REFERENCES activos.ente(id)';
    END IF;

    -- vehiculos.modelo(marca_id) -> vehiculos.marca(id)
    SELECT pg_get_constraintdef(c.oid)
    INTO def
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE c.conname = 'modelo_marca_id_fkey'
      AND n.nspname = 'vehiculos'
      AND t.relname = 'modelo';

    IF def IS NULL OR position('REFERENCES vehiculos.marca(id)' IN def) = 0 THEN
        IF def IS NOT NULL THEN
            EXECUTE 'ALTER TABLE vehiculos.modelo DROP CONSTRAINT modelo_marca_id_fkey';
        END IF;
        EXECUTE 'ALTER TABLE vehiculos.modelo ADD CONSTRAINT modelo_marca_id_fkey FOREIGN KEY (marca_id) REFERENCES vehiculos.marca(id)';
    END IF;

    -- vehiculos.vehiculo(modelo_id) -> vehiculos.modelo(id)
    SELECT pg_get_constraintdef(c.oid)
    INTO def
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE c.conname = 'vehiculo_modelo_fk'
      AND n.nspname = 'vehiculos'
      AND t.relname = 'vehiculo';

    IF def IS NULL OR position('REFERENCES vehiculos.modelo(id)' IN def) = 0 THEN
        IF def IS NOT NULL THEN
            EXECUTE 'ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT vehiculo_modelo_fk';
        END IF;
        EXECUTE 'ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT vehiculo_modelo_fk FOREIGN KEY (modelo_id) REFERENCES vehiculos.modelo(id)';
    END IF;

    -- vehiculos.vehiculo(tipo_vehiculo) -> vehiculos.tipo_vehiculo(id)
    SELECT pg_get_constraintdef(c.oid)
    INTO def
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE c.conname = 'vehiculo_tipo_vehiculo_fkey'
      AND n.nspname = 'vehiculos'
      AND t.relname = 'vehiculo';

    IF def IS NULL OR position('REFERENCES vehiculos.tipo_vehiculo(id)' IN def) = 0 THEN
        IF def IS NOT NULL THEN
            EXECUTE 'ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT vehiculo_tipo_vehiculo_fkey';
        END IF;
        EXECUTE 'ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT vehiculo_tipo_vehiculo_fkey FOREIGN KEY (tipo_vehiculo) REFERENCES vehiculos.tipo_vehiculo(id)';
    END IF;
END
$$;
