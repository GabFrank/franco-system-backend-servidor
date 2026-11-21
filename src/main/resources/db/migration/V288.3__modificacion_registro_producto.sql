-- Historial de cambios para administrativos: buscar «quién cambió el precio de tal producto».
-- modificacion_registro es central-only (no está en replication_table ni en publicaciones).

ALTER TABLE configuraciones.modificacion_registro ADD COLUMN IF NOT EXISTS producto_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_modificacion_producto_fecha
    ON configuraciones.modificacion_registro (producto_id, modificado_en);

-- Lo ya registrado: el producto sale de la fila que se modificó, si todavía existe.
UPDATE configuraciones.modificacion_registro r SET producto_id = r.entidad_id
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'PRODUCTO';

UPDATE configuraciones.modificacion_registro r SET producto_id = p.producto_id
FROM productos.presentacion p
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'PRESENTACION' AND p.id = r.entidad_id;

UPDATE configuraciones.modificacion_registro r SET producto_id = p.producto_id
FROM productos.precio_por_sucursal pps
JOIN productos.presentacion p ON p.id = pps.presentacion_id
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'PRECIO_POR_SUCURSAL' AND pps.id = r.entidad_id;

UPDATE configuraciones.modificacion_registro r SET producto_id = p.producto_id
FROM productos.codigo c
JOIN productos.presentacion p ON p.id = c.presentacion_id
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'CODIGO' AND c.id = r.entidad_id;

UPDATE configuraciones.modificacion_registro r SET producto_id = c.producto_id
FROM productos.costo_por_producto c
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'COSTO_POR_PRODUCTO' AND c.id = r.entidad_id;

UPDATE configuraciones.modificacion_registro r SET producto_id = p.producto_id
FROM operaciones.inventario_producto_item i
JOIN productos.presentacion p ON p.id = i.presentacion_id
WHERE r.producto_id IS NULL AND r.tipo_entidad = 'INVENTARIO_ITEM' AND i.id = r.entidad_id;

UPDATE configuraciones.modificacion_registro r SET producto_id = (
    SELECT m.producto_id FROM operaciones.movimiento_stock m WHERE m.id = r.entidad_id LIMIT 1)
WHERE r.producto_id IS NULL AND r.tipo_entidad IN ('AJUSTE_STOCK', 'ANULACION_STOCK_COMPRA', 'AJUSTE_STOCK_LOTE');

-- Filas que ya no existen (por ejemplo, borradas): el producto o la presentación que quedó en el detalle.
UPDATE configuraciones.modificacion_registro r SET producto_id = d.valor_id
FROM (
    SELECT modificacion_registro_id, MAX(COALESCE(valor_nuevo_id, valor_anterior_id)) AS valor_id
    FROM configuraciones.modificacion_detalle
    WHERE campo_nombre = 'producto'
    GROUP BY modificacion_registro_id
) d
WHERE r.producto_id IS NULL AND d.modificacion_registro_id = r.id AND d.valor_id IS NOT NULL;

UPDATE configuraciones.modificacion_registro r SET producto_id = p.producto_id
FROM (
    SELECT modificacion_registro_id, MAX(COALESCE(valor_nuevo_id, valor_anterior_id)) AS valor_id
    FROM configuraciones.modificacion_detalle
    WHERE campo_nombre = 'presentacion'
    GROUP BY modificacion_registro_id
) d
JOIN productos.presentacion p ON p.id = d.valor_id
WHERE r.producto_id IS NULL AND d.modificacion_registro_id = r.id;
