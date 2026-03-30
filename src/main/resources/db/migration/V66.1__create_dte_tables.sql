CREATE TABLE financiero.lote_dte (
    id BIGSERIAL NOT NULL,
    id_protocolo_sifen VARCHAR(50),
    fecha_envio TIMESTAMPTZ,
    estado_sifen VARCHAR(30),
    respuesta_sifen TEXT,
    usuario_id INT8 NULL,
    creado_en TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT lote_dte_pk PRIMARY KEY (id),
    CONSTRAINT lote_dte_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE financiero.documento_electronico (
    id BIGSERIAL NOT NULL,
    lote_id INT8 NULL,
    factura_legal_id INT8 NULL,
    sucursal_id INT8 NULL,
    cdc VARCHAR(44) UNIQUE,
    estado_sifen VARCHAR(20) DEFAULT 'PENDIENTE' NOT NULL,
    mensaje_sifen TEXT,
    xml_firmado TEXT,
    url_qr VARCHAR(500),
    usuario_id INT8 NULL,
    creado_en TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT documento_electronico_pk PRIMARY KEY (id),
    CONSTRAINT documento_electronico_lote_fk FOREIGN KEY (lote_id) REFERENCES financiero.lote_dte(id) ON DELETE SET NULL,
    CONSTRAINT documento_electronico_factura_legal_fk FOREIGN KEY (factura_legal_id, sucursal_id) REFERENCES financiero.factura_legal(id, sucursal_id) ON DELETE SET NULL,
    CONSTRAINT documento_electronico_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE financiero.evento_dte (
    id BIGSERIAL NOT NULL,
    documento_electronico_id INT8 NOT NULL,
    tipo_evento INT NOT NULL,
    fecha_evento TIMESTAMPTZ DEFAULT NOW(),
    cdc_evento VARCHAR(44) UNIQUE,
    mensaje_respuesta_sifen TEXT,
    motivo TEXT,
    observacion TEXT,
    usuario_id INT8 NULL,
    creado_en TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT evento_dte_pk PRIMARY KEY (id),
    CONSTRAINT evento_dte_documento_electronico_fk FOREIGN KEY (documento_electronico_id) REFERENCES financiero.documento_electronico(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT evento_dte_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

-- Índices para mejorar filtros por estado y rango de fechas
CREATE INDEX IF NOT EXISTS idx_documento_electronico_estado ON financiero.documento_electronico (estado_sifen);
CREATE INDEX IF NOT EXISTS idx_documento_electronico_creado_en ON financiero.documento_electronico (creado_en);
CREATE INDEX IF NOT EXISTS idx_documento_electronico_lote_id ON financiero.documento_electronico (lote_id);

CREATE INDEX IF NOT EXISTS idx_lote_dte_estado ON financiero.lote_dte (estado_sifen);
CREATE INDEX IF NOT EXISTS idx_lote_dte_creado_en ON financiero.lote_dte (creado_en);



