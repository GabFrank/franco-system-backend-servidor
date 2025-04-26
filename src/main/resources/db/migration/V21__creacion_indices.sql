DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_class c 
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'venta_caja_id_idx' -- Replace with your index name
        AND n.nspname = 'operaciones'  -- Replace with the schema name, like 'public'
    ) THEN
        CREATE INDEX venta_caja_id_idx ON operaciones.venta USING btree (caja_id, sucursal_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'cobro_detalle_cobro_id_idx' -- Replace with your index name
        AND n.nspname = 'operaciones'  -- Replace with the schema name, like 'public'
    ) THEN
        CREATE INDEX cobro_detalle_cobro_id_idx ON operaciones.cobro_detalle USING btree (cobro_id, sucursal_id);
    END IF;
END $$;

CREATE INDEX conteo_moneda_conteo_id_idx ON financiero.conteo_moneda (conteo_id,sucursal_id);
CREATE INDEX movimiento_stock_producto_id_idx ON operaciones.movimiento_stock (producto_id,sucursal_id);
CREATE INDEX transferencia_item_transferencia_id_idx ON operaciones.transferencia_item (transferencia_id);
CREATE INDEX costo_por_producto_producto_id_idx ON productos.costo_por_producto (producto_id);
CREATE INDEX presentacion_producto_id_idx ON productos.presentacion (producto_id);
CREATE INDEX precio_por_sucursal_presentacion_id_idx ON productos.precio_por_sucursal (presentacion_id);
