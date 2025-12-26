-- V65.5: Agregar nota_recepcion_id a pedido_item (si no existe)
-- Esta migración viene de la rama compras
-- La columna puede ya existir si fue creada en una migración anterior

DO $$
BEGIN
    -- Verificar si la columna existe antes de agregarla
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'operaciones' 
        AND table_name = 'pedido_item' 
        AND column_name = 'nota_recepcion_id'
    ) THEN
        ALTER TABLE operaciones.pedido_item ADD nota_recepcion_id int8 NULL;
        RAISE NOTICE 'Columna nota_recepcion_id agregada a pedido_item';
    ELSE
        RAISE NOTICE 'Columna nota_recepcion_id ya existe en pedido_item, omitiendo creación';
    END IF;
    
    -- Verificar si la constraint existe antes de agregarla
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_schema = 'operaciones' 
        AND table_name = 'pedido_item' 
        AND constraint_name = 'pedido_item_nota_recepcion_fk'
    ) THEN
        ALTER TABLE operaciones.pedido_item 
        ADD CONSTRAINT pedido_item_nota_recepcion_fk 
        FOREIGN KEY (nota_recepcion_id) 
        REFERENCES operaciones.nota_recepcion(id) 
        ON DELETE SET NULL 
        ON UPDATE CASCADE;
        RAISE NOTICE 'Constraint pedido_item_nota_recepcion_fk agregada';
    ELSE
        RAISE NOTICE 'Constraint pedido_item_nota_recepcion_fk ya existe, omitiendo creación';
    END IF;
END $$;
