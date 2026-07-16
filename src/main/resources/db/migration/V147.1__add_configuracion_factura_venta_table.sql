CREATE TABLE IF NOT EXISTS financiero.configuracion_factura_venta (
    id BIGSERIAL PRIMARY KEY,
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT NOW(),
    modificado_en TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_configuracion_factura_venta_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

INSERT INTO financiero.configuracion_factura_venta (habilitado, creado_en, modificado_en)
SELECT FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM financiero.configuracion_factura_venta);
