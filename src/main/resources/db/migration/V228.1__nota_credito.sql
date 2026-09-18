-- =====================================================================
-- Nota de Credito Electronica (NCE, iTiDE 5)
-- =====================================================================
-- Anula total o parcialmente una factura electronica **despues** de las 48 h en que SIFEN acepta
-- el evento de cancelacion, o documenta devoluciones, descuentos y bonificaciones sobre una
-- factura aprobada.
--
-- En esta entrega solo NC TOTAL: la nota copia 1:1 los items y los totales de la factura (sin
-- recalcular, para no introducir diferencias de redondeo). La NC parcial queda para despues.
--
-- Central-only, igual que nota_remision: no entra a replication_table ni a ninguna publicacion.
-- PK compuesta (id, sucursal_id); el id lo asigna el service desde la secuencia (con @IdClass,
-- @GeneratedValue no puede insertar — ver §13.11 del plan).
--
-- La factura asociada es OBLIGATORIA: no hay NC standalone en este alcance.
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.nota_credito (
    id                      BIGSERIAL,
    sucursal_id             BIGINT       NOT NULL,

    timbrado_detalle_id     BIGINT       NOT NULL,
    numero_nota_credito     INTEGER      NOT NULL,
    fecha                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- la factura que se acredita
    factura_legal_id        BIGINT       NOT NULL,

    -- motivo del catalogo TiMotEmi + descripcion libre (esta ultima solo para el KuDE)
    motivo_emision          VARCHAR(50)  NOT NULL,
    descripcion_motivo      VARCHAR(255) NULL,

    -- receptor: snapshot de la factura
    cliente_id              BIGINT       NULL,
    nombre                  VARCHAR(255) NULL,
    ruc                     VARCHAR(30)  NULL,
    direccion               VARCHAR(255) NULL,

    -- moneda heredada de la factura: una NC NO elige moneda propia
    moneda_extranjera       VARCHAR(10)  NULL,
    tipo_cambio             NUMERIC(18,6) NULL,

    -- totales copiados de la factura (NC total)
    iva_parcial_0           NUMERIC(18,4) NULL,
    iva_parcial_5           NUMERIC(18,4) NULL,
    iva_parcial_10          NUMERIC(18,4) NULL,
    total_parcial_0         NUMERIC(18,4) NULL,
    total_parcial_5         NUMERIC(18,4) NULL,
    total_parcial_10        NUMERIC(18,4) NULL,
    descuento               NUMERIC(18,4) NULL,
    total_final             NUMERIC(18,4) NULL,

    activo                  BOOLEAN      NOT NULL DEFAULT true,
    usuario_id              BIGINT       NULL,
    creado_en               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    actualizado_en          TIMESTAMP WITH TIME ZONE NULL,

    CONSTRAINT pk_nota_credito PRIMARY KEY (id, sucursal_id),
    CONSTRAINT uk_nota_credito_numero UNIQUE (timbrado_detalle_id, numero_nota_credito),
    CONSTRAINT fk_nota_credito_timbrado_detalle FOREIGN KEY (timbrado_detalle_id, sucursal_id)
        REFERENCES financiero.timbrado_detalle (id, sucursal_id),
    CONSTRAINT fk_nota_credito_factura_legal FOREIGN KEY (factura_legal_id, sucursal_id)
        REFERENCES financiero.factura_legal (id, sucursal_id),
    CONSTRAINT fk_nota_credito_cliente FOREIGN KEY (cliente_id)
        REFERENCES personas.cliente (id)
);

CREATE INDEX IF NOT EXISTS idx_nota_credito_sucursal_fecha
    ON financiero.nota_credito (sucursal_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_nota_credito_factura_legal
    ON financiero.nota_credito (factura_legal_id, sucursal_id);

CREATE TABLE IF NOT EXISTS financiero.nota_credito_item (
    id                      BIGSERIAL,
    sucursal_id             BIGINT        NOT NULL,
    nota_credito_id         BIGINT        NOT NULL,

    factura_legal_item_id   BIGINT        NULL,
    producto_id             BIGINT        NULL,
    presentacion_id         BIGINT        NULL,
    descripcion             VARCHAR(255)  NOT NULL,
    cantidad                NUMERIC(14,4) NOT NULL,
    unidad_medida           VARCHAR(20)   NULL,
    precio_unitario         NUMERIC(18,4) NOT NULL,
    total                   NUMERIC(18,4) NOT NULL,
    iva                     INTEGER       NULL,

    creado_en               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_nota_credito_item PRIMARY KEY (id, sucursal_id),
    CONSTRAINT fk_nota_credito_item_nota FOREIGN KEY (nota_credito_id, sucursal_id)
        REFERENCES financiero.nota_credito (id, sucursal_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_nota_credito_item_nota
    ON financiero.nota_credito_item (nota_credito_id, sucursal_id);

-- El DE de una nota de credito apunta a su nota (columna creada en V225.1).
ALTER TABLE financiero.documento_electronico
    ADD CONSTRAINT fk_documento_electronico_nota_credito
    FOREIGN KEY (nota_credito_id, sucursal_id)
    REFERENCES financiero.nota_credito (id, sucursal_id);

-- Un DE nace de exactamente UN documento: factura, nota de credito o nota de remision.
-- NOT VALID + VALIDATE para no bloquear la tabla mientras se verifica lo existente; toda fila
-- actual tiene solo factura_legal_id, asi que valida sin tocar nada.
ALTER TABLE financiero.documento_electronico
    ADD CONSTRAINT ck_documento_electronico_un_origen CHECK (
        (CASE WHEN factura_legal_id  IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN nota_credito_id   IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN nota_remision_id  IS NOT NULL THEN 1 ELSE 0 END) = 1
    ) NOT VALID;

ALTER TABLE financiero.documento_electronico
    VALIDATE CONSTRAINT ck_documento_electronico_un_origen;
