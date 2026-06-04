-- Flyway:executeInTransaction=false

-- Índices para optimizar el reporte lucro por funcionario.
-- El cuello de botella principal es el join venta -> venta_item sin índice en venta_id.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_item_venta_sucursal
    ON operaciones.venta_item (venta_id, sucursal_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_concluida_fecha_suc_usuario
    ON operaciones.venta (creado_en, sucursal_id, usuario_id)
    WHERE estado = 'CONCLUIDA';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_costo_por_producto_producto_id_desc
    ON productos.costo_por_producto (producto_id, id DESC)
    INCLUDE (ultimo_precio_compra);
