CREATE TABLE IF NOT EXISTS operaciones.configuracion_transferencia (
    id BIGSERIAL PRIMARY KEY,
    permitir_stock_negativo BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT NOW(),
    modificado_en TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_configuracion_transferencia_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

INSERT INTO operaciones.configuracion_transferencia (permitir_stock_negativo, creado_en, modificado_en)
SELECT FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM operaciones.configuracion_transferencia);
