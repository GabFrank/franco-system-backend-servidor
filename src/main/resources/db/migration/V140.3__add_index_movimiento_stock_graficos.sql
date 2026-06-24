-- Flyway:executeInTransaction=false
-- Acelera enriquecimiento de rotación en productos más vendidos (solo top N productos)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_movimiento_stock_fecha_tipo_producto
    ON operaciones.movimiento_stock(creado_en, tipo_movimiento, producto_id)
    WHERE estado = true;
