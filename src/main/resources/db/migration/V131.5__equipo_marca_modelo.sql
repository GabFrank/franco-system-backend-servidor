-- Tablas de catálogo marca/modelo para equipos (mismo patrón que vehiculos.marca / vehiculos.modelo).

CREATE SEQUENCE IF NOT EXISTS equipos.marca_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipos.marca_id_seq OWNER TO franco;

CREATE TABLE IF NOT EXISTS equipos.marca (
    id bigint NOT NULL DEFAULT nextval('equipos.marca_id_seq'::regclass),
    descripcion character varying(255),
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT marca_pkey PRIMARY KEY (id)
);

ALTER TABLE equipos.marca OWNER TO franco;
ALTER SEQUENCE equipos.marca_id_seq OWNED BY equipos.marca.id;

CREATE SEQUENCE IF NOT EXISTS equipos.modelo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipos.modelo_id_seq OWNER TO franco;

CREATE TABLE IF NOT EXISTS equipos.modelo (
    id bigint NOT NULL DEFAULT nextval('equipos.modelo_id_seq'::regclass),
    descripcion character varying(255),
    marca_id bigint,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT modelo_pkey PRIMARY KEY (id)
);

ALTER TABLE equipos.modelo OWNER TO franco;
ALTER SEQUENCE equipos.modelo_id_seq OWNED BY equipos.modelo.id;

ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS modelo_id bigint;

-- Migrar marcas existentes desde texto plano.
INSERT INTO equipos.marca (descripcion, creado_en)
SELECT DISTINCT UPPER(TRIM(e.marca)), CURRENT_TIMESTAMP
FROM equipos.equipo e
WHERE e.marca IS NOT NULL
  AND TRIM(e.marca) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM equipos.marca m WHERE UPPER(TRIM(m.descripcion)) = UPPER(TRIM(e.marca))
  );

-- Migrar modelos existentes vinculados a su marca.
INSERT INTO equipos.modelo (descripcion, marca_id, creado_en)
SELECT DISTINCT UPPER(TRIM(e.modelo)), m.id, CURRENT_TIMESTAMP
FROM equipos.equipo e
JOIN equipos.marca m ON UPPER(TRIM(m.descripcion)) = UPPER(TRIM(e.marca))
WHERE e.modelo IS NOT NULL
  AND TRIM(e.modelo) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM equipos.modelo mod
      WHERE mod.marca_id = m.id
        AND UPPER(TRIM(mod.descripcion)) = UPPER(TRIM(e.modelo))
  );

-- Modelos sin marca: se asocian a marca SIN MARCA.
INSERT INTO equipos.marca (descripcion, creado_en)
SELECT 'SIN MARCA', CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM equipos.equipo e
    WHERE e.modelo IS NOT NULL
      AND TRIM(e.modelo) <> ''
      AND (e.marca IS NULL OR TRIM(e.marca) = '')
)
AND NOT EXISTS (
    SELECT 1 FROM equipos.marca m WHERE UPPER(TRIM(m.descripcion)) = 'SIN MARCA'
);

INSERT INTO equipos.modelo (descripcion, marca_id, creado_en)
SELECT DISTINCT UPPER(TRIM(e.modelo)), m.id, CURRENT_TIMESTAMP
FROM equipos.equipo e
JOIN equipos.marca m ON UPPER(TRIM(m.descripcion)) = 'SIN MARCA'
WHERE e.modelo IS NOT NULL
  AND TRIM(e.modelo) <> ''
  AND (e.marca IS NULL OR TRIM(e.marca) = '')
  AND NOT EXISTS (
      SELECT 1
      FROM equipos.modelo mod
      WHERE mod.marca_id = m.id
        AND UPPER(TRIM(mod.descripcion)) = UPPER(TRIM(e.modelo))
  );

UPDATE equipos.equipo e
SET modelo_id = mod.id
FROM equipos.modelo mod
JOIN equipos.marca m ON mod.marca_id = m.id
WHERE e.modelo_id IS NULL
  AND e.modelo IS NOT NULL
  AND TRIM(e.modelo) <> ''
  AND UPPER(TRIM(mod.descripcion)) = UPPER(TRIM(e.modelo))
  AND (
      (e.marca IS NOT NULL AND TRIM(e.marca) <> '' AND UPPER(TRIM(m.descripcion)) = UPPER(TRIM(e.marca)))
      OR ((e.marca IS NULL OR TRIM(e.marca) = '') AND UPPER(TRIM(m.descripcion)) = 'SIN MARCA')
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_modelo_marca'
    ) THEN
        ALTER TABLE equipos.modelo
            ADD CONSTRAINT fk_modelo_marca FOREIGN KEY (marca_id) REFERENCES equipos.marca(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_modelo_usuario'
    ) THEN
        ALTER TABLE equipos.modelo
            ADD CONSTRAINT fk_modelo_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_marca_usuario'
    ) THEN
        ALTER TABLE equipos.marca
            ADD CONSTRAINT fk_marca_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_modelo'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_modelo FOREIGN KEY (modelo_id) REFERENCES equipos.modelo(id);
    END IF;
END $$;

ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS marca;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS modelo;
