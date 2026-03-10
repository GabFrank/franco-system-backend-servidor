-- Habilitar la extensión para búsquedas de texto tipo trigrama si no existe
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. Índice para búsqueda ultra rápida por descripción (optimiza LIKE %texto%)
-- Esto acelera significativamente los filtros de texto en el reporte y la lista de productos
CREATE INDEX IF NOT EXISTS idx_producto_descripcion_trgm ON productos.producto USING gin (descripcion gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_producto_descripcion_factura_trgm ON productos.producto USING gin (descripcion_factura gin_trgm_ops);

-- 2. Índice para acelerar el cálculo de "Costo Cero" 
-- Optimiza la búsqueda del último registro de costo por fecha para cada producto
CREATE INDEX IF NOT EXISTS idx_costo_por_producto_lookup ON productos.costo_por_producto (producto_id, creado_en DESC);
