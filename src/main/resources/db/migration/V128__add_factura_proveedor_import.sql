-- Tabla de registro del proceso de importacion automatica de facturas de proveedores
-- (IA imagen/PDF en hito 2, XML SIFEN en hito 3).
-- Cada fila representa un archivo subido + estado del proceso + payload extraido.

CREATE TABLE IF NOT EXISTS operaciones.factura_proveedor_import (
    id               BIGSERIAL PRIMARY KEY,
    origen           VARCHAR(20) NOT NULL,  -- XML_SIFEN | IA_IMAGEN | IA_PDF
    estado           VARCHAR(30) NOT NULL,  -- PROCESANDO | REVISION_PENDIENTE | CONFIRMADO | ERROR
    nombre_archivo   VARCHAR(255),
    imagen_master_id BIGINT REFERENCES media.imagen_master(id) ON DELETE SET NULL,
    json_crudo       JSONB,                 -- respuesta raw de OpenAI (audit trail)
    json_validado    JSONB,                 -- payload validado/normalizado pasado al matcher
    tokens_prompt    INT,
    tokens_respuesta INT,
    modelo_ia        VARCHAR(50),
    error_mensaje    TEXT,
    pedido_id        BIGINT,                -- FK soft (no constraint para evitar dependencia circular en orden de creacion)
    usuario_id       BIGINT,
    creado_en        TIMESTAMP NOT NULL DEFAULT now(),
    modificado_en    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_factura_proveedor_import_estado
    ON operaciones.factura_proveedor_import(estado);

CREATE INDEX IF NOT EXISTS idx_factura_proveedor_import_usuario
    ON operaciones.factura_proveedor_import(usuario_id);

CREATE INDEX IF NOT EXISTS idx_factura_proveedor_import_creado_desc
    ON operaciones.factura_proveedor_import(creado_en DESC);

COMMENT ON TABLE operaciones.factura_proveedor_import IS
    'Registro del proceso de importacion automatica de facturas de proveedor (IA o XML SIFEN). Auditoria del payload extraido + uso de tokens IA.';
