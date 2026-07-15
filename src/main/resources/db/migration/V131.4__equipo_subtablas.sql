-- Normaliza equipos.equipo separando datos financieros en equipos.equipo_financiero (relación 1:1).

CREATE SEQUENCE IF NOT EXISTS equipos.equipo_financiero_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipos.equipo_financiero_id_seq OWNER TO franco;

CREATE TABLE IF NOT EXISTS equipos.equipo_financiero (
    id bigint NOT NULL DEFAULT nextval('equipos.equipo_financiero_id_seq'::regclass),
    equipo_id bigint NOT NULL,
    costo numeric(19, 2),
    valor_tasacion numeric(19, 2),
    valor_tasacion_pyg numeric(19, 2),
    valor_tasacion_brl numeric(19, 2),
    situacion_pago character varying(255),
    proveedor_id bigint,
    moneda_id bigint,
    monto_total numeric(19, 2),
    monto_ya_pagado numeric(19, 2),
    cantidad_cuotas integer,
    cantidad_cuotas_pagadas integer,
    dia_vencimiento integer,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT equipo_financiero_pkey PRIMARY KEY (id),
    CONSTRAINT equipo_financiero_equipo_id_key UNIQUE (equipo_id)
);

ALTER TABLE equipos.equipo_financiero OWNER TO franco;
ALTER SEQUENCE equipos.equipo_financiero_id_seq OWNED BY equipos.equipo_financiero.id;

-- Migrar datos existentes desde equipos.equipo (columnas de V0 y V131.3).
-- SQL dinámico: tolera re-ejecución si flyway.repair() ya eliminó algunas columnas fuente.
DO $$
DECLARE
    financial_columns text[] := ARRAY[
        'costo', 'valor_tasacion', 'valor_tasacion_pyg', 'valor_tasacion_brl',
        'situacion_pago', 'proveedor_id', 'moneda_id', 'monto_total',
        'monto_ya_pagado', 'cantidad_cuotas', 'cantidad_cuotas_pagadas', 'dia_vencimiento'
    ];
    col_name text;
    insert_columns text := 'equipo_id';
    select_expressions text := 'e.id';
    not_null_conditions text := '';
BEGIN
    FOREACH col_name IN ARRAY financial_columns LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns c
            WHERE c.table_schema = 'equipos'
              AND c.table_name = 'equipo'
              AND c.column_name = col_name
        ) THEN
            insert_columns := insert_columns || ', ' || col_name;
            select_expressions := select_expressions || ', e.' || col_name;

            IF not_null_conditions <> '' THEN
                not_null_conditions := not_null_conditions || ' OR ';
            END IF;
            not_null_conditions := not_null_conditions || 'e.' || col_name || ' IS NOT NULL';
        END IF;
    END LOOP;

    IF not_null_conditions <> '' THEN
        EXECUTE format(
            'INSERT INTO equipos.equipo_financiero (%s, usuario_id, creado_en)
             SELECT %s, e.usuario_id, COALESCE(e.creado_en::timestamp without time zone, CURRENT_TIMESTAMP)
             FROM equipos.equipo e
             WHERE NOT EXISTS (
                 SELECT 1 FROM equipos.equipo_financiero ef WHERE ef.equipo_id = e.id
             )
             AND (%s)',
            insert_columns,
            select_expressions,
            not_null_conditions
        );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_financiero_equipo'
    ) THEN
        ALTER TABLE equipos.equipo_financiero
            ADD CONSTRAINT fk_equipo_financiero_equipo
            FOREIGN KEY (equipo_id) REFERENCES equipos.equipo(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_financiero_proveedor'
    ) THEN
        ALTER TABLE equipos.equipo_financiero
            ADD CONSTRAINT fk_equipo_financiero_proveedor
            FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_financiero_moneda'
    ) THEN
        ALTER TABLE equipos.equipo_financiero
            ADD CONSTRAINT fk_equipo_financiero_moneda
            FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_financiero_usuario'
    ) THEN
        ALTER TABLE equipos.equipo_financiero
            ADD CONSTRAINT fk_equipo_financiero_usuario
            FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
    END IF;
END $$;

ALTER TABLE equipos.equipo DROP CONSTRAINT IF EXISTS fk_equipo_proveedor;
ALTER TABLE equipos.equipo DROP CONSTRAINT IF EXISTS fk_equipo_moneda;

ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS costo;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS valor_tasacion;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS valor_tasacion_pyg;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS valor_tasacion_brl;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS situacion_pago;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS proveedor_id;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS moneda_id;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS monto_total;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS monto_ya_pagado;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS cantidad_cuotas;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS cantidad_cuotas_pagadas;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS dia_vencimiento;
