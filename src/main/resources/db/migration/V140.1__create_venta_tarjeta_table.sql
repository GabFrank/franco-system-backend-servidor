CREATE TABLE financiero.venta_tarjeta (
    id                  BIGSERIAL,
    sucursal_id         BIGINT NOT NULL,
    venta_id            BIGINT NOT NULL,
    terminal_pos_id     BIGINT,
    caja_id             BIGINT NOT NULL,
    codigo_autorizacion VARCHAR(100),
    numero_boleta       VARCHAR(100),
    monto               NUMERIC(18,2) NOT NULL,
    imagen_url          VARCHAR(500),
    usuario_id          BIGINT,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id),
    CONSTRAINT fk_vt_terminal_pos FOREIGN KEY (terminal_pos_id) REFERENCES financiero.terminal_pos(id),
    CONSTRAINT fk_vt_usuario      FOREIGN KEY (usuario_id)      REFERENCES personas.usuario(id)
);

CREATE INDEX idx_venta_tarjeta_venta_id ON financiero.venta_tarjeta(venta_id, sucursal_id);
CREATE INDEX idx_venta_tarjeta_caja_id  ON financiero.venta_tarjeta(caja_id, sucursal_id);
CREATE INDEX idx_venta_tarjeta_estado   ON financiero.venta_tarjeta(caja_id, sucursal_id, estado);
