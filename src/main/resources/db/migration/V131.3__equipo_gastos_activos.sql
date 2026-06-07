-- Extiende equipos.equipo con los campos financieros y de activo alineados a activos.mueble / activos.inmueble
-- para integración con el módulo de gastos vía activos.ente y activos.ente_sucursal.

ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS propietario_id bigint;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS identificador character varying(100);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS valor_tasacion numeric(19, 2);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS valor_tasacion_pyg numeric(19, 2);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS valor_tasacion_brl numeric(19, 2);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS situacion_pago character varying(255);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS proveedor_id bigint;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS moneda_id bigint;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS monto_total numeric(19, 2);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS monto_ya_pagado numeric(19, 2);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS cantidad_cuotas integer;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS cantidad_cuotas_pagadas integer;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS dia_vencimiento integer;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS consume_energia boolean;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS consumo_valor character varying(50);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_propietario'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_propietario FOREIGN KEY (propietario_id) REFERENCES personas.persona(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_proveedor'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_moneda'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_tipo_equipo'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_tipo_equipo FOREIGN KEY (tipo_equipo_id) REFERENCES equipos.tipo_equipo(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_equipo_usuario'
    ) THEN
        ALTER TABLE equipos.equipo
            ADD CONSTRAINT fk_equipo_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
    END IF;
END $$;
