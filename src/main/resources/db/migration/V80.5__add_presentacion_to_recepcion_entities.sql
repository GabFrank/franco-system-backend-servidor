-- V80: Agregar campos de presentación a las entidades de recepción
-- Esto permite trazabilidad completa de la presentación a través del flujo de compras

-- V80.5: Add presentacion fields to recepcion entities
-- This migration comes from the compras branch

DO $$
BEGIN
    -- Agregar presentacion_en_nota a nota_recepcion_item
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item' AND column_name = 'presentacion_en_nota_id') THEN
            ALTER TABLE operaciones.nota_recepcion_item ADD COLUMN presentacion_en_nota_id BIGINT;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item' AND column_name = 'presentacion_en_nota_id') THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item' AND constraint_name = 'fk_nota_recepcion_item_presentacion_en_nota') THEN
                ALTER TABLE operaciones.nota_recepcion_item 
                ADD CONSTRAINT fk_nota_recepcion_item_presentacion_en_nota 
                FOREIGN KEY (presentacion_en_nota_id) REFERENCES productos.presentacion(id);
            END IF;
        END IF;
    END IF;
    
    -- Agregar presentacion_recibida a recepcion_mercaderia_item
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'presentacion_recibida_id') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item ADD COLUMN presentacion_recibida_id BIGINT;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'presentacion_recibida_id') THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND constraint_name = 'fk_recepcion_mercaderia_item_presentacion_recibida') THEN
                ALTER TABLE operaciones.recepcion_mercaderia_item 
                ADD CONSTRAINT fk_recepcion_mercaderia_item_presentacion_recibida 
                FOREIGN KEY (presentacion_recibida_id) REFERENCES productos.presentacion(id);
            END IF;
        END IF;
    END IF;
END $$;

-- Agregar comentarios para documentar el propósito de estos campos
COMMENT ON COLUMN operaciones.nota_recepcion_item.presentacion_en_nota_id IS 'Presentación que el proveedor especifica en su factura/nota de recepción';
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.presentacion_recibida_id IS 'Presentación que se recibe físicamente en la sucursal';

-- Crear índices para mejorar el rendimiento de las consultas
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_item_presentacion_en_nota ON operaciones.nota_recepcion_item(presentacion_en_nota_id);
CREATE INDEX IF NOT EXISTS idx_recepcion_mercaderia_item_presentacion_recibida ON operaciones.recepcion_mercaderia_item(presentacion_recibida_id); 