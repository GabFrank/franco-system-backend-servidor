-- =====================================================================
-- Nota de Remision Electronica (NRE, iTiDE 7)
-- =====================================================================
-- Ampara el traslado de mercaderia: entre sucursales (transferencia), hacia un cliente
-- (con factura) o a un tercero (consignacion, reparacion, devolucion a proveedor).
-- No lleva valores ni IVA: su carga fiscal esta en la logistica (salida, entrega, vehiculo,
-- chofer, transportista).
--
-- Central-only, igual que transferencia* y devolucion*: NO se registra en
-- configuraciones.replication_table ni en ninguna publicacion. Las emite solo el central
-- (decision D1 del plan).
--
-- PK compuesta (id, sucursal_id) por coherencia con factura_legal y documento_electronico,
-- cuyas FK compuestas la exigen. sucursal_id de una NR = la sucursal de origen del traslado.
--
-- Numeracion: numero_nota_remision es la serie del timbrado para este tipo de documento,
-- independiente de la de facturas. La UNIQUE (timbrado_detalle_id, numero_nota_remision) es
-- la red de seguridad del lock pesimista que toma el servicio.
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.nota_remision (
    id                      BIGSERIAL,
    sucursal_id             BIGINT       NOT NULL,

    timbrado_detalle_id     BIGINT       NOT NULL,
    numero_nota_remision    INTEGER      NOT NULL,
    fecha                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- de donde nace: TRANSFERENCIA | FACTURA | MANUAL
    origen                  VARCHAR(20)  NOT NULL,
    transferencia_id        BIGINT       NULL,
    factura_legal_id        BIGINT       NULL,

    -- traslado (gCamNRE)
    motivo_emision          VARCHAR(50)  NOT NULL,
    responsable_emision     VARCHAR(50)  NOT NULL,
    km_estimado             INTEGER      NULL,
    fecha_inicio_traslado   DATE         NULL,
    fecha_fin_traslado      DATE         NULL,
    fecha_estimada_factura  DATE         NULL,

    -- receptor (gDatRec): SIFEN no admite innominado en una NRE
    cliente_id              BIGINT       NULL,
    receptor_nombre         VARCHAR(255) NOT NULL,
    receptor_ruc            VARCHAR(30)  NULL,
    receptor_direccion      VARCHAR(255) NULL,
    receptor_departamento   VARCHAR(100) NULL,
    receptor_codigo_ciudad  INTEGER      NULL,
    receptor_ciudad         VARCHAR(100) NULL,

    -- salida (gCamSal) y entrega (gCamEnt)
    salida_direccion        VARCHAR(255) NULL,
    salida_departamento     VARCHAR(100) NULL,
    salida_codigo_ciudad    INTEGER      NULL,
    salida_ciudad           VARCHAR(100) NULL,
    entrega_direccion       VARCHAR(255) NULL,
    entrega_departamento    VARCHAR(100) NULL,
    entrega_codigo_ciudad   INTEGER      NULL,
    entrega_ciudad          VARCHAR(100) NULL,

    -- transporte (gTransp / gCamTrans)
    tipo_transporte         VARCHAR(20)  NULL,
    modalidad_transporte    VARCHAR(20)  NULL,
    transportista_nombre    VARCHAR(255) NULL,
    transportista_ruc       VARCHAR(30)  NULL,
    transportista_direccion VARCHAR(255) NULL,

    -- vehiculo y chofer: FK al catalogo + snapshot, porque el catalogo puede cambiar despues
    vehiculo_id             BIGINT       NULL,
    vehiculo_marca          VARCHAR(100) NULL,
    vehiculo_matricula      VARCHAR(30)  NULL,
    chofer_persona_id       BIGINT       NULL,
    chofer_nombre           VARCHAR(255) NULL,
    chofer_documento        VARCHAR(30)  NULL,
    chofer_direccion        VARCHAR(255) NULL,

    activo                  BOOLEAN      NOT NULL DEFAULT true,
    usuario_id              BIGINT       NULL,
    creado_en               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    actualizado_en          TIMESTAMP WITH TIME ZONE NULL,

    CONSTRAINT pk_nota_remision PRIMARY KEY (id, sucursal_id),
    CONSTRAINT uk_nota_remision_numero UNIQUE (timbrado_detalle_id, numero_nota_remision),
    -- timbrado_detalle tiene PK compuesta (id, sucursal_id), igual que factura_legal
    CONSTRAINT fk_nota_remision_timbrado_detalle FOREIGN KEY (timbrado_detalle_id, sucursal_id)
        REFERENCES financiero.timbrado_detalle (id, sucursal_id),
    CONSTRAINT fk_nota_remision_factura_legal FOREIGN KEY (factura_legal_id, sucursal_id)
        REFERENCES financiero.factura_legal (id, sucursal_id),
    CONSTRAINT fk_nota_remision_transferencia FOREIGN KEY (transferencia_id)
        REFERENCES operaciones.transferencia (id),
    CONSTRAINT fk_nota_remision_vehiculo FOREIGN KEY (vehiculo_id)
        REFERENCES vehiculos.vehiculo (id),
    CONSTRAINT fk_nota_remision_chofer FOREIGN KEY (chofer_persona_id)
        REFERENCES personas.persona (id),
    CONSTRAINT fk_nota_remision_cliente FOREIGN KEY (cliente_id)
        REFERENCES personas.cliente (id)
);

CREATE INDEX IF NOT EXISTS idx_nota_remision_sucursal_fecha
    ON financiero.nota_remision (sucursal_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_nota_remision_transferencia
    ON financiero.nota_remision (transferencia_id) WHERE transferencia_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_nota_remision_factura_legal
    ON financiero.nota_remision (factura_legal_id, sucursal_id) WHERE factura_legal_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS financiero.nota_remision_item (
    id                  BIGSERIAL,
    sucursal_id         BIGINT        NOT NULL,
    nota_remision_id    BIGINT        NOT NULL,

    producto_id         BIGINT        NULL,
    presentacion_id     BIGINT        NULL,
    codigo              VARCHAR(50)   NULL,
    descripcion         VARCHAR(255)  NOT NULL,
    cantidad            NUMERIC(14,4) NOT NULL,
    -- codigo de unidad de medida de SIFEN (cUniMed). VARCHAR como en factura_legal_item,
    -- de donde se precarga; se convierte al construir el DE.
    unidad_medida       VARCHAR(20)   NULL,

    creado_en           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_nota_remision_item PRIMARY KEY (id, sucursal_id),
    CONSTRAINT fk_nota_remision_item_nota FOREIGN KEY (nota_remision_id, sucursal_id)
        REFERENCES financiero.nota_remision (id, sucursal_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_nota_remision_item_nota
    ON financiero.nota_remision_item (nota_remision_id, sucursal_id);

-- El DE de una nota de remision apunta a su nota (columna creada en V225.1).
ALTER TABLE financiero.documento_electronico
    ADD CONSTRAINT fk_documento_electronico_nota_remision
    FOREIGN KEY (nota_remision_id, sucursal_id)
    REFERENCES financiero.nota_remision (id, sucursal_id);

-- Los ids de estas tablas los genera solo el central (no se replican), pero el trigger de
-- V226.1 no aplica aca: no hay filial escribiendo en ellas.
