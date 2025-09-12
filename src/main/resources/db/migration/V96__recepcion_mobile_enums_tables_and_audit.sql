-- ===============================================
-- MIGRACIÓN V96: Enums, Tablas y Auditoría Recepción Móvil
-- ===============================================

-- 1) Enums nuevos (schema operaciones)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace 
                   WHERE t.typname = 'metodo_verificacion' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.metodo_verificacion AS ENUM ('ESCANER','MANUAL');
        COMMENT ON TYPE operaciones.metodo_verificacion IS 'Método utilizado para verificar el ítem en recepción física';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace 
                   WHERE t.typname = 'motivo_verificacion_manual' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.motivo_verificacion_manual AS ENUM ('CODIGO_ILEGIBLE','PRODUCTO_SIN_CODIGO');
        COMMENT ON TYPE operaciones.motivo_verificacion_manual IS 'Motivos de uso de verificación manual (sin escáner)';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace 
                   WHERE t.typname = 'tipo_origen_vencimiento' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.tipo_origen_vencimiento AS ENUM ('RECEPCION_MERCADERIA','AJUSTE_STOCK','VENTA','TRANSFERENCIA');
        COMMENT ON TYPE operaciones.tipo_origen_vencimiento IS 'Origen del registro de vencimiento de producto';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace 
                   WHERE t.typname = 'estado_constancia' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.estado_constancia AS ENUM ('EMITIDA','ANULADA');
        COMMENT ON TYPE operaciones.estado_constancia IS 'Estados de la constancia de recepción';
    END IF;
END $$;

-- 2) ALTER TABLE recepcion_mercaderia_item: agregar columnas de auditoría
ALTER TABLE operaciones.recepcion_mercaderia_item 
    ADD COLUMN IF NOT EXISTS metodo_verificacion operaciones.metodo_verificacion,
    ADD COLUMN IF NOT EXISTS motivo_verificacion_manual operaciones.motivo_verificacion_manual;

COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.metodo_verificacion IS 'Método de verificación (ESCANER/MANUAL)';
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.motivo_verificacion_manual IS 'Motivo cuando la verificación fue MANUAL';

-- 3) Tabla producto_vencimiento
CREATE TABLE IF NOT EXISTS operaciones.producto_vencimiento (
    id BIGSERIAL PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    presentacion_id BIGINT,
    sucursal_id BIGINT NOT NULL,
    fecha_vencimiento DATE,
    cantidad DOUBLE PRECISION,
    tipo_origen operaciones.tipo_origen_vencimiento,
    origen_id BIGINT,
    usuario_id BIGINT NOT NULL,
    fecha_creacion TIMESTAMP,

    CONSTRAINT fk_prod_venc_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_prod_venc_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id),
    CONSTRAINT fk_prod_venc_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT fk_prod_venc_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_prod_venc_producto ON operaciones.producto_vencimiento(producto_id);
CREATE INDEX IF NOT EXISTS idx_prod_venc_sucursal ON operaciones.producto_vencimiento(sucursal_id);
CREATE INDEX IF NOT EXISTS idx_prod_venc_fecha ON operaciones.producto_vencimiento(fecha_vencimiento);

COMMENT ON TABLE operaciones.producto_vencimiento IS 'Registro de vencimientos por producto, presentación y sucursal con origen de operación';

-- 4) Tabla constancia_de_recepcion
CREATE TABLE IF NOT EXISTS operaciones.constancia_de_recepcion (
    id BIGSERIAL PRIMARY KEY,
    recepcion_mercaderia_id BIGINT NOT NULL,
    proveedor_id BIGINT NOT NULL,
    sucursal_id BIGINT NOT NULL,
    fecha_emision TIMESTAMP NOT NULL,
    usuario_id BIGINT NOT NULL,
    codigo_verificacion VARCHAR(255) NOT NULL UNIQUE,
    estado operaciones.estado_constancia NOT NULL,

    CONSTRAINT fk_const_recep FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id),
    CONSTRAINT fk_const_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id),
    CONSTRAINT fk_const_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT fk_const_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_const_recep ON operaciones.constancia_de_recepcion(recepcion_mercaderia_id);

COMMENT ON TABLE operaciones.constancia_de_recepcion IS 'Documento formal e inmutable emitido al finalizar recepción física';

-- 5) Tabla constancia_de_recepcion_item
CREATE TABLE IF NOT EXISTS operaciones.constancia_de_recepcion_item (
    id BIGSERIAL PRIMARY KEY,
    constancia_de_recepcion_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    presentacion_id BIGINT,
    cantidad_recibida DOUBLE PRECISION,
    cantidad_rechazada_fisico DOUBLE PRECISION,

    CONSTRAINT fk_const_item_const FOREIGN KEY (constancia_de_recepcion_id) REFERENCES operaciones.constancia_de_recepcion(id),
    CONSTRAINT fk_const_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_const_item_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id)
);

CREATE INDEX IF NOT EXISTS idx_const_item_const ON operaciones.constancia_de_recepcion_item(constancia_de_recepcion_id);
CREATE INDEX IF NOT EXISTS idx_const_item_producto ON operaciones.constancia_de_recepcion_item(producto_id);

COMMENT ON TABLE operaciones.constancia_de_recepcion_item IS 'Detalle de productos incluidos en la constancia de recepción';

