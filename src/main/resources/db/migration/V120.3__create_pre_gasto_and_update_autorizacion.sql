-- Crear enum estado_pre_gasto
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type t 
        JOIN pg_namespace n ON t.typnamespace = n.oid 
        WHERE t.typname = 'estado_pre_gasto' AND n.nspname = 'financiero'
    ) THEN
        CREATE TYPE financiero.estado_pre_gasto AS ENUM ('PENDIENTE', 'TRAMITE', 'AUTORIZADO', 'RECHAZADO', 'COMPLETADO');
    END IF;
END$$;

-- Agregar valor PRE_GASTO al enum tipo_autorizacion si no existe
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        JOIN pg_namespace n ON t.typnamespace = n.oid
        WHERE n.nspname = 'administrativo' AND t.typname = 'tipo_autorizacion' AND e.enumlabel = 'PRE_GASTO'
    ) THEN
        ALTER TYPE administrativo.tipo_autorizacion ADD VALUE 'PRE_GASTO';
    END IF;
END$$;

-- Crear tabla pre_gasto
CREATE TABLE IF NOT EXISTS financiero.pre_gasto (
    id BIGINT NOT NULL,
    sucursal_id BIGINT NOT NULL,
    funcionario_id BIGINT REFERENCES personas.persona(id),
    ente_id BIGINT,
    tipo_gasto_id BIGINT REFERENCES financiero.tipo_gasto(id),
    descripcion VARCHAR(500),
    moneda_id BIGINT REFERENCES financiero.moneda(id),
    monto_solicitado NUMERIC(15,2),
    sucursal_caja_id BIGINT REFERENCES empresarial.sucursal(id),
    estado financiero.estado_pre_gasto DEFAULT 'PENDIENTE',
    qr_token VARCHAR(255),
    autorizado_por_id BIGINT REFERENCES personas.persona(id),
    delegado_a_id BIGINT REFERENCES personas.persona(id),
    motivo_rechazo VARCHAR(500),
    monto_retirado NUMERIC(15,2),
    monto_gastado NUMERIC(15,2),
    saldo_devolver NUMERIC(15,2),
    usuario_id BIGINT,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id)
);

-- Crear tabla gasolinera si no existe
CREATE TABLE IF NOT EXISTS financiero.gasolinera (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    activo BOOLEAN DEFAULT true,
    usuario_id BIGINT,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Crear tabla gasto_rendicion
CREATE TABLE IF NOT EXISTS financiero.gasto_rendicion (
    id BIGSERIAL PRIMARY KEY,
    pre_gasto_id BIGINT,
    sucursal_id BIGINT,
    tipo_gasto_id BIGINT REFERENCES financiero.tipo_gasto(id),
    monto_total NUMERIC(15,2),
    foto_factura_url VARCHAR(500),
    foto_producto_url VARCHAR(500),
    ente_id BIGINT,
    gasolinera_id BIGINT REFERENCES financiero.gasolinera(id),
    km_actual NUMERIC(15,2),
    litros NUMERIC(10,2),
    precio_por_litro NUMERIC(10,2),
    ubicacion_provisoria VARCHAR(500),
    establecimiento_alimentacion VARCHAR(255),
    usuario_id BIGINT,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (pre_gasto_id, sucursal_id) REFERENCES financiero.pre_gasto(id, sucursal_id)
);

-- Crear tabla intermedia para comensales
CREATE TABLE IF NOT EXISTS financiero.gasto_rendicion_funcionario (
    gasto_rendicion_id BIGINT REFERENCES financiero.gasto_rendicion(id) ON DELETE CASCADE,
    funcionario_id BIGINT REFERENCES personas.persona(id),
    PRIMARY KEY (gasto_rendicion_id, funcionario_id)
);

-- Índices para rendimiento
CREATE INDEX IF NOT EXISTS idx_pre_gasto_estado ON financiero.pre_gasto(estado);
CREATE INDEX IF NOT EXISTS idx_pre_gasto_funcionario ON financiero.pre_gasto(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_pre_gasto_sucursal ON financiero.pre_gasto(sucursal_caja_id);
CREATE INDEX IF NOT EXISTS idx_gasto_rendicion_pre_gasto ON financiero.gasto_rendicion(pre_gasto_id);
