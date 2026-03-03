-- V84.5: Corregir campo presentacion_en_nota_id en nota_recepcion_item
-- Fecha: 2024-01-XX
-- Descripción: Actualizar registros existentes donde presentacion_en_nota_id es NULL
-- copiando la presentación del pedido_item correspondiente
-- Esta migración viene de la rama compras

-- Verificar que la columna existe antes de actualizar
-- Si la columna no existe, esta migración no hace nada (la columna se crea en V80.5)
DO $$
BEGIN
    -- Verificar si la columna existe
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'operaciones' 
        AND table_name = 'nota_recepcion_item' 
        AND column_name = 'presentacion_en_nota_id'
    ) THEN
        -- Actualizar nota_recepcion_item donde presentacion_en_nota_id es NULL
        -- copiando la presentación del pedido_item relacionado
        UPDATE operaciones.nota_recepcion_item 
        SET presentacion_en_nota_id = (
            SELECT pi.presentacion_creacion_id 
            FROM operaciones.pedido_item pi 
            WHERE pi.id = nota_recepcion_item.pedido_item_id
        )
        WHERE presentacion_en_nota_id IS NULL 
        AND pedido_item_id IS NOT NULL;
        
        -- Comentario para documentar la corrección
        COMMENT ON COLUMN operaciones.nota_recepcion_item.presentacion_en_nota_id IS 'Presentación que el proveedor especifica en su factura/nota de recepción (corregido en V84.5)';
    ELSE
        RAISE NOTICE 'Columna presentacion_en_nota_id no existe en nota_recepcion_item. Esta migración requiere que V80.5 se ejecute primero.';
    END IF;
END $$;