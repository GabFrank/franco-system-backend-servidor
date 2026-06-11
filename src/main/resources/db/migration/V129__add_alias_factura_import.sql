-- Tablas de aprendizaje del proceso de importacion automatica de facturas.
-- Cada vez que el usuario confirma un import vinculando producto/proveedor manualmente,
-- guardamos un alias para que la proxima factura de ese proveedor con ese texto/codigo
-- sea matcheada automaticamente con HIGH confidence.

CREATE TABLE IF NOT EXISTS operaciones.alias_proveedor_import (
    id           BIGSERIAL PRIMARY KEY,
    texto_ocr    VARCHAR(500) NOT NULL,
    ruc          VARCHAR(20),
    proveedor_id BIGINT NOT NULL REFERENCES personas.proveedor(id) ON DELETE CASCADE,
    creado_en    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT alias_proveedor_import_texto_unique UNIQUE (texto_ocr)
);

CREATE INDEX IF NOT EXISTS idx_alias_proveedor_import_ruc
    ON operaciones.alias_proveedor_import(ruc);

COMMENT ON TABLE operaciones.alias_proveedor_import IS
    'Mapeo aprendido del nombre/RUC en factura/XML del proveedor a Proveedor interno. Confianza HIGH cuando hay match.';


CREATE TABLE IF NOT EXISTS operaciones.alias_producto_import (
    id           BIGSERIAL PRIMARY KEY,
    texto_ocr    VARCHAR(500) NOT NULL,
    codigo_ocr   VARCHAR(100),
    producto_id  BIGINT NOT NULL REFERENCES productos.producto(id) ON DELETE CASCADE,
    proveedor_id BIGINT NOT NULL REFERENCES personas.proveedor(id) ON DELETE CASCADE,
    creado_en    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT alias_producto_import_texto_proveedor_unique UNIQUE (texto_ocr, proveedor_id)
);

CREATE INDEX IF NOT EXISTS idx_alias_producto_import_codigo
    ON operaciones.alias_producto_import(codigo_ocr);

CREATE INDEX IF NOT EXISTS idx_alias_producto_import_proveedor
    ON operaciones.alias_producto_import(proveedor_id);

COMMENT ON TABLE operaciones.alias_producto_import IS
    'Mapeo aprendido del nombre/codigo de item en factura del proveedor X a Producto interno. Confianza HIGH cuando hay match exacto, MEDIUM via fuzzy.';
