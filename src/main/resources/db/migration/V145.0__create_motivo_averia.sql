-- Modulo Devoluciones: catalogo de motivos de averia/devolucion (reason codes).

CREATE TABLE IF NOT EXISTS operaciones.motivo_averia (
    id               BIGSERIAL PRIMARY KEY,
    descripcion      VARCHAR(120) NOT NULL,
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    genera_gasto     BOOLEAN NOT NULL DEFAULT FALSE,
    aplica_proveedor BOOLEAN NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE operaciones.motivo_averia IS 'Catalogo de motivos de averia/devolucion de producto';

-- FK diferido desde devolucion_item.motivo_averia_id (creado en V144).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = 'operaciones' AND table_name = 'devolucion_item'
                   AND constraint_name = 'fk_devolucion_item_motivo_averia') THEN
        ALTER TABLE operaciones.devolucion_item
            ADD CONSTRAINT fk_devolucion_item_motivo_averia
            FOREIGN KEY (motivo_averia_id) REFERENCES operaciones.motivo_averia(id);
    END IF;
END $$;

-- Seeds de referencia (idempotente por descripcion).
INSERT INTO operaciones.motivo_averia (descripcion, activo, genera_gasto, aplica_proveedor)
SELECT v.descripcion, TRUE, v.genera_gasto, v.aplica_proveedor
FROM (VALUES
        ('VENCIDO',          TRUE,  TRUE),
        ('AVERIADO',         TRUE,  TRUE),
        ('ROTO',             TRUE,  TRUE),
        ('MAL ESTADO',       TRUE,  TRUE),
        ('DEFECTO DE FABRICA', FALSE, TRUE),
        ('RECALL',           FALSE, TRUE),
        ('ERROR DE PEDIDO',  FALSE, TRUE)
     ) AS v(descripcion, genera_gasto, aplica_proveedor)
WHERE NOT EXISTS (
    SELECT 1 FROM operaciones.motivo_averia m WHERE UPPER(m.descripcion) = v.descripcion
);
