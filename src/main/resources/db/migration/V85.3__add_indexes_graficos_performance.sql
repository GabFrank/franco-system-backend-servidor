-- Flyway:executeInTransaction=false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_cobro_sucursal 
    ON operaciones.cobro_detalle(cobro_id, sucursal_id);

-- CRÍTICO: Optimiza consultas de ventas por fecha
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_creado_en 
    ON operaciones.venta(creado_en);

-- CRÍTICO: Optimiza consultas de ventas concluidas
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_concluida 
    ON operaciones.venta(creado_en, sucursal_id) 
    WHERE estado = 'CONCLUIDA';

-- CRÍTICO: Optimiza formas de pago por fecha
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_fecha_pago 
    ON operaciones.cobro_detalle(creado_en, forma_pago_id) 
    WHERE pago = true;

-- CRÍTICO: Optimiza productos más vendidos
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_item_fecha_producto 
    ON operaciones.venta_item(creado_en, producto_id, sucursal_id) 
    WHERE activo = true;

-- Índices de soporte
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_sucursal_estado 
    ON operaciones.venta(sucursal_id, estado);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_forma_pago 
    ON operaciones.cobro_detalle(forma_pago_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_item_producto_sucursal 
    ON operaciones.venta_item(producto_id, sucursal_id);
