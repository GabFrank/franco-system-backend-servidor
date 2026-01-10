-- Flyway:executeInTransaction=false

-- =====================================================
-- Migración V86.3: Índices adicionales para venta-mes
-- =====================================================
-- Índices específicos para optimizar la consulta ventaPorPeriodo
-- que se usa en el gráfico de ventas por mes

-- =====================================================
-- ÍNDICES ADICIONALES PARA VENTA
-- =====================================================

-- Índice compuesto para la consulta principal de ventas
-- Optimiza: WHERE creado_en BETWEEN ? AND ? AND estado NOT IN ('CANCELADA', 'ABIERTA')
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_fecha_estado_sucursal 
    ON operaciones.venta(creado_en, estado, sucursal_id);

-- Índice para JOIN con cobro (clave foránea)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_cobro_fk 
    ON operaciones.venta(cobro_id);

-- Índice parcial para ventas válidas (excluye canceladas y abiertas)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_validas 
    ON operaciones.venta(creado_en, sucursal_id, cobro_id) 
    WHERE estado NOT IN ('CANCELADA', 'ABIERTA');

-- =====================================================
-- ÍNDICES ADICIONALES PARA COBRO
-- =====================================================

-- Índice compuesto para relación venta-cobro
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_id_sucursal 
    ON operaciones.cobro(id, sucursal_id);

-- =====================================================
-- ÍNDICES ADICIONALES PARA COBRO_DETALLE
-- =====================================================

-- Índice compuesto para la consulta de batch
-- Optimiza: WHERE cobro_id IN (...) AND sucursal_id = ?
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_cobro_id 
    ON operaciones.cobro_detalle(cobro_id, sucursal_id, forma_pago_id);

-- Índice para JOIN con moneda
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_moneda_fk 
    ON operaciones.cobro_detalle(moneda_id);

-- Índice compuesto para filtros de pago
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cobro_detalle_pago_valor 
    ON operaciones.cobro_detalle(pago, vuelto, descuento, aumento);

-- =====================================================
-- ÍNDICES PARA TABLAS RELACIONADAS
-- =====================================================

-- Índice para forma_pago (usado en JOINs)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_forma_pago_id 
    ON financiero.forma_pago(id);

-- Índice para moneda (usado en JOINs)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_moneda_id 
    ON financiero.moneda(id);

-- =====================================================
-- ÍNDICES COVERING (incluyen columnas adicionales)
-- =====================================================

-- Índice covering para venta (incluye todas las columnas necesarias)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_covering 

-- =====================================================
-- ÍNDICES ADICIONALES PARA GASTO
-- =====================================================

-- Índice para optimizar filtros de gastos por fecha y sucursal
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_gasto_fecha_sucursal_activo 
    ON financiero.gasto(creado_en, sucursal_id, activo);
