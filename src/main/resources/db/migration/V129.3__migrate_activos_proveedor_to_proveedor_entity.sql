-- Migra proveedor_id de activos/vehiculos: antes referenciaba personas.persona(id),
-- ahora debe referenciar personas.proveedor(id).

-- 1) Guardar valores actuales (persona_id), convertir a proveedor.id y limpiar no mapeables.
-- Se ejecuta de forma condicional para ambientes donde alguna tabla no tenga proveedor_id.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'activos'
      AND table_name = 'mueble'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE activos.mueble ADD COLUMN IF NOT EXISTS proveedor_persona_id_tmp BIGINT;
    UPDATE activos.mueble SET proveedor_persona_id_tmp = proveedor_id;
    UPDATE activos.mueble m
    SET proveedor_id = p.id
    FROM personas.proveedor p
    WHERE p.persona_id = m.proveedor_persona_id_tmp;
    UPDATE activos.mueble m
    SET proveedor_id = NULL
    WHERE m.proveedor_persona_id_tmp IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM personas.proveedor p WHERE p.persona_id = m.proveedor_persona_id_tmp
      );
    ALTER TABLE activos.mueble DROP COLUMN IF EXISTS proveedor_persona_id_tmp;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'activos'
      AND table_name = 'inmueble'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE activos.inmueble ADD COLUMN IF NOT EXISTS proveedor_persona_id_tmp BIGINT;
    UPDATE activos.inmueble SET proveedor_persona_id_tmp = proveedor_id;
    UPDATE activos.inmueble i
    SET proveedor_id = p.id
    FROM personas.proveedor p
    WHERE p.persona_id = i.proveedor_persona_id_tmp;
    UPDATE activos.inmueble i
    SET proveedor_id = NULL
    WHERE i.proveedor_persona_id_tmp IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM personas.proveedor p WHERE p.persona_id = i.proveedor_persona_id_tmp
      );
    ALTER TABLE activos.inmueble DROP COLUMN IF EXISTS proveedor_persona_id_tmp;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'vehiculos'
      AND table_name = 'vehiculo'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS proveedor_persona_id_tmp BIGINT;
    UPDATE vehiculos.vehiculo SET proveedor_persona_id_tmp = proveedor_id;
    UPDATE vehiculos.vehiculo v
    SET proveedor_id = p.id
    FROM personas.proveedor p
    WHERE p.persona_id = v.proveedor_persona_id_tmp;
    UPDATE vehiculos.vehiculo v
    SET proveedor_id = NULL
    WHERE v.proveedor_persona_id_tmp IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM personas.proveedor p WHERE p.persona_id = v.proveedor_persona_id_tmp
      );
    ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS proveedor_persona_id_tmp;
  END IF;
END $$;

-- 2) Reemplazar FK para que apunte a personas.proveedor(id).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'activos'
      AND table_name = 'mueble'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE activos.mueble DROP CONSTRAINT IF EXISTS fk_mueble_proveedor;
    ALTER TABLE activos.mueble DROP CONSTRAINT IF EXISTS mueble_proveedor_id_fkey;
    ALTER TABLE activos.mueble
      ADD CONSTRAINT mueble_proveedor_id_fkey
      FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'activos'
      AND table_name = 'inmueble'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE activos.inmueble DROP CONSTRAINT IF EXISTS fk_inmueble_proveedor;
    ALTER TABLE activos.inmueble DROP CONSTRAINT IF EXISTS inmueble_proveedor_id_fkey;
    ALTER TABLE activos.inmueble
      ADD CONSTRAINT inmueble_proveedor_id_fkey
      FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'vehiculos'
      AND table_name = 'vehiculo'
      AND column_name = 'proveedor_id'
  ) THEN
    ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS fk_vehiculo_proveedor;
    ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_proveedor_id_fkey;
    ALTER TABLE vehiculos.vehiculo
      ADD CONSTRAINT vehiculo_proveedor_id_fkey
      FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
  END IF;
END $$;
