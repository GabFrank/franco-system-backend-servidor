-- Migración V85: Agregar campo sucursal_influencia_id a nota_recepcion_item_distribucion
-- Fecha: 2024-12-19
-- Descripción: Agregar soporte para sucursal de influencia en distribuciones de nota de recepción
-- para mantener trazabilidad completa desde el pedido original

-- V85.5: Add sucursal_influencia_id to nota_recepcion_item_distribucion
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND column_name = 'sucursal_influencia_id') THEN
            ALTER TABLE operaciones.nota_recepcion_item_distribucion ADD COLUMN sucursal_influencia_id BIGINT;
        END IF;
    END IF;
END $$;

-- Agregar comentario a la columna
COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.sucursal_influencia_id IS 
'ID de la sucursal de influencia (quién necesita el producto) para mantener trazabilidad con el pedido original';

-- Agregar foreign key constraint
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND column_name = 'sucursal_influencia_id') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_sucursal_influencia') THEN
            ALTER TABLE operaciones.nota_recepcion_item_distribucion
            ADD CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_influencia
            FOREIGN KEY (sucursal_influencia_id)
            REFERENCES empresarial.sucursal(id);
        END IF;
    END IF;
END $$;

-- Agregar comentario a la constraint
COMMENT ON CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_influencia 
ON operaciones.nota_recepcion_item_distribucion IS 
'Foreign key que relaciona la distribución con la sucursal de influencia del pedido original';

-- Crear índice para optimizar consultas por sucursal de influencia
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_distribucion_sucursal_influencia 
ON operaciones.nota_recepcion_item_distribucion(sucursal_influencia_id);

-- Crear índice compuesto para optimizar consultas por sucursal de influencia y entrega
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_distribucion_sucursal_influencia_entrega 
ON operaciones.nota_recepcion_item_distribucion(sucursal_influencia_id, sucursal_entrega_id);

-- Agregar comentario al índice compuesto
COMMENT ON INDEX operaciones.idx_nota_recepcion_item_distribucion_sucursal_influencia_entrega IS 
'Índice compuesto para optimizar consultas de distribuciones por sucursal de influencia y entrega'; 