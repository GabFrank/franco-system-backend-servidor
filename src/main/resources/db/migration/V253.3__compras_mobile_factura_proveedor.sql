-- =====================================================================
-- Compras en el celular: la factura electrónica del proveedor (KuDE)
-- =====================================================================
-- El comprador escanea el QR del KuDE, el central consulta la factura entera
-- en SIFEN (siConsDE, código 0422) y la cruza con el pedido para crear la
-- nota de recepción.
--
-- * documento_recibido: la factura tal como la dio SIFEN (XML incluido). Hace
--   de caché: un CDC se consulta una sola vez. Cuando se confirma, apunta a la
--   nota que generó. La nota no se toca: el vínculo vive de este lado.
-- * documento_recibido_item: un renglón de la factura y el resultado del cruce
--   (producto, presentación, factor, ítem del pedido).
-- * proveedor_codigo_producto: lo aprendido. El comprador vincula a mano un
--   código del proveedor con una presentación nuestra una vez y las facturas
--   siguientes de ese proveedor lo reconocen solas.
--
-- Aditivo e idempotente. Central-only: compras vive en el central, no entra a
-- configuraciones.replication_table ni a ninguna publicación.
-- =====================================================================

CREATE TABLE IF NOT EXISTS operaciones.documento_recibido (
    id                      BIGSERIAL PRIMARY KEY,
    cdc                     VARCHAR(44)   NOT NULL,
    tipo_documento          INTEGER,
    ruc_emisor              VARCHAR(20),
    dv_emisor               VARCHAR(2),
    razon_social_emisor     VARCHAR(255),
    ruc_receptor            VARCHAR(20),
    dv_receptor             VARCHAR(2),
    razon_social_receptor   VARCHAR(255),
    timbrado                INTEGER,
    establecimiento         VARCHAR(3),
    punto_expedicion        VARCHAR(3),
    numero                  VARCHAR(7),
    fecha_emision           TIMESTAMP,
    condicion_operacion     INTEGER,
    moneda                  VARCHAR(3),
    cotizacion              NUMERIC(18,4),
    subtotal_exenta         NUMERIC(18,4),
    subtotal_iva5           NUMERIC(18,4),
    subtotal_iva10          NUMERIC(18,4),
    total_descuento         NUMERIC(18,4),
    total_general           NUMERIC(18,4),
    total_iva5              NUMERIC(18,4),
    total_iva10             NUMERIC(18,4),
    total_iva               NUMERIC(18,4),
    codigo_respuesta        VARCHAR(10),
    mensaje_respuesta       VARCHAR(255),
    xml                     TEXT,
    fecha_consulta          TIMESTAMP,
    proveedor_id            BIGINT REFERENCES personas.proveedor (id),
    pedido_id               BIGINT REFERENCES operaciones.pedido (id),
    nota_recepcion_id       BIGINT REFERENCES operaciones.nota_recepcion (id) ON DELETE SET NULL,
    usuario_id              BIGINT REFERENCES personas.usuario (id),
    creado_en               TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_documento_recibido_cdc UNIQUE (cdc)
);

-- Una nota sale de una sola factura.
CREATE UNIQUE INDEX IF NOT EXISTS uk_documento_recibido_nota
    ON operaciones.documento_recibido (nota_recepcion_id)
    WHERE nota_recepcion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_documento_recibido_pedido
    ON operaciones.documento_recibido (pedido_id);

CREATE TABLE IF NOT EXISTS operaciones.documento_recibido_item (
    id                      BIGSERIAL PRIMARY KEY,
    documento_recibido_id   BIGINT NOT NULL REFERENCES operaciones.documento_recibido (id) ON DELETE CASCADE,
    orden                   INTEGER NOT NULL,
    codigo_proveedor        VARCHAR(60),
    gtin                    VARCHAR(20),
    gtin_paquete            VARCHAR(20),
    descripcion             VARCHAR(500),
    unidad_medida           VARCHAR(20),
    cantidad                NUMERIC(18,4),
    precio_unitario         NUMERIC(18,4),
    descuento_unitario      NUMERIC(18,4),
    porcentaje_descuento    NUMERIC(8,4),
    total                   NUMERIC(18,4),
    afectacion_iva          INTEGER,
    tasa_iva                INTEGER,
    lote                    VARCHAR(80),
    vencimiento             DATE,
    -- resultado del cruce con el pedido
    producto_id             BIGINT REFERENCES productos.producto (id),
    presentacion_id         BIGINT REFERENCES productos.presentacion (id),
    factor                  NUMERIC(18,4),
    tipo_match              VARCHAR(20),
    pedido_item_id          BIGINT REFERENCES operaciones.pedido_item (id) ON DELETE SET NULL,
    es_bonificacion         BOOLEAN NOT NULL DEFAULT false,
    observacion             TEXT,
    nota_recepcion_item_id  BIGINT REFERENCES operaciones.nota_recepcion_item (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS ix_documento_recibido_item_documento
    ON operaciones.documento_recibido_item (documento_recibido_id);

CREATE TABLE IF NOT EXISTS productos.proveedor_codigo_producto (
    id                      BIGSERIAL PRIMARY KEY,
    proveedor_id            BIGINT NOT NULL REFERENCES personas.proveedor (id),
    codigo_proveedor        VARCHAR(60) NOT NULL,
    descripcion_proveedor   VARCHAR(500),
    presentacion_id         BIGINT NOT NULL REFERENCES productos.presentacion (id) ON DELETE CASCADE,
    -- unidades base por cada unidad que factura el proveedor
    factor                  NUMERIC(18,4) NOT NULL,
    usos                    INTEGER NOT NULL DEFAULT 1,
    ultimo_uso              TIMESTAMP,
    usuario_id              BIGINT REFERENCES personas.usuario (id),
    creado_en               TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_proveedor_codigo_producto UNIQUE (proveedor_id, codigo_proveedor)
);
