-- =====================================================================
-- Compras móvil: factura en PAPEL leída por IA (OpenAI).
--
-- * empresarial.configuracion_sistema: clave/valor (la clave de OpenAI va cifrada).
-- * documento_recibido: ya no todo documento tiene CDC; origen SIFEN|FOTO y
--   los datos de la lectura de IA. Índice único por número de factura.
-- * documento_recibido_foto: las hojas fotografiadas.
-- * proveedor_codigo_producto: puede aprenderse por descripción cuando la
--   factura de papel no trae código.
--
-- Aditivo e idempotente. Central-only: no entra a configuraciones.replication_table.
-- =====================================================================

CREATE TABLE IF NOT EXISTS empresarial.configuracion_sistema (
    clave           VARCHAR(100) PRIMARY KEY,
    valor           TEXT,
    descripcion     TEXT,
    encriptado      BOOLEAN NOT NULL DEFAULT false,
    modificado_en   TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('openai.api_key', NULL, 'Clave de la API de OpenAI (cifrada)', true)
ON CONFLICT (clave) DO NOTHING;

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('openai.modelo', 'gpt-4o', 'Modelo de OpenAI que lee las facturas', false)
ON CONFLICT (clave) DO NOTHING;

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('openai.prompt_adicional', '', 'Instrucciones extra que se suman al prompt de lectura de facturas', false)
ON CONFLICT (clave) DO NOTHING;

-- documento_recibido
ALTER TABLE operaciones.documento_recibido ALTER COLUMN cdc DROP NOT NULL;

ALTER TABLE operaciones.documento_recibido
    ADD COLUMN IF NOT EXISTS origen VARCHAR(10) NOT NULL DEFAULT 'SIFEN',
    ADD COLUMN IF NOT EXISTS cabecera_confirmada BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS ia_modelo VARCHAR(60),
    ADD COLUMN IF NOT EXISTS ia_tokens_entrada INTEGER,
    ADD COLUMN IF NOT EXISTS ia_tokens_salida INTEGER,
    ADD COLUMN IF NOT EXISTS ia_respuesta TEXT,
    ADD COLUMN IF NOT EXISTS ia_dudosos TEXT;

-- Una misma factura (emisor + tipo + timbrado + número) se carga una sola vez.
CREATE UNIQUE INDEX IF NOT EXISTS uk_documento_recibido_numero
    ON operaciones.documento_recibido (ruc_emisor, COALESCE(tipo_documento, 1), timbrado, establecimiento, punto_expedicion, numero)
    WHERE timbrado IS NOT NULL AND numero IS NOT NULL AND ruc_emisor IS NOT NULL;

ALTER TABLE operaciones.documento_recibido_item
    ADD COLUMN IF NOT EXISTS ia_dudosos TEXT;

CREATE TABLE IF NOT EXISTS operaciones.documento_recibido_foto (
    id                      BIGSERIAL PRIMARY KEY,
    documento_recibido_id   BIGINT NOT NULL REFERENCES operaciones.documento_recibido (id) ON DELETE CASCADE,
    orden                   INTEGER NOT NULL,
    mime                    VARCHAR(50) NOT NULL,
    imagen                  BYTEA NOT NULL,
    creado_en               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_documento_recibido_foto_documento
    ON operaciones.documento_recibido_foto (documento_recibido_id, orden);

-- proveedor_codigo_producto
ALTER TABLE productos.proveedor_codigo_producto ALTER COLUMN codigo_proveedor DROP NOT NULL;

ALTER TABLE productos.proveedor_codigo_producto
    ADD COLUMN IF NOT EXISTS descripcion_normalizada VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_proveedor_codigo_producto_clave') THEN
        ALTER TABLE productos.proveedor_codigo_producto
            ADD CONSTRAINT ck_proveedor_codigo_producto_clave
            CHECK (codigo_proveedor IS NOT NULL OR descripcion_normalizada IS NOT NULL);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_proveedor_codigo_producto_descripcion
    ON productos.proveedor_codigo_producto (proveedor_id, descripcion_normalizada)
    WHERE codigo_proveedor IS NULL;
