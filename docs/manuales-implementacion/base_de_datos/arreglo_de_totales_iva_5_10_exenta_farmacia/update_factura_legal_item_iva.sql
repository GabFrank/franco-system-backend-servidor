-- Script SQL para actualizar el IVA de todos los factura_legal_item
-- Sigue la lógica de prioridades del código Java:
-- 1. Si fli.iva ya tiene valor, se mantiene (no se actualiza)
-- 2. Si tiene producto_id directo, usa producto.iva
-- 3. Si tiene presentacion_id, usa presentacion.producto.iva
-- 4. Si tiene venta_item_id, usa venta_item.producto.iva
-- 5. Si no tiene ninguna relación, deja NULL (para detectar problemas)

-- IMPORTANTE: Ejecutar primero en modo SELECT para verificar los cambios antes de actualizar

-- ============================================
-- CONFIGURACIÓN DE FECHAS (MODIFICAR SEGÚN NECESIDAD)
-- ============================================
-- Ajustar estas fechas según el rango que se desee procesar
-- Formato: 'YYYY-MM-DD' o 'YYYY-MM-DD HH:MI:SS'
DO $$
DECLARE
    fecha_desde TIMESTAMP := '2024-01-01 00:00:00';
    fecha_hasta TIMESTAMP := '2025-12-31 23:59:59';
BEGIN
    -- Las variables se usarán en las queries siguientes
    -- Para usar un rango específico, descomentar y ajustar:
    -- fecha_desde := '2024-01-01 00:00:00';
    -- fecha_hasta := '2024-12-31 23:59:59';
END $$;

-- Variables para usar en las queries (ajustar según necesidad):
-- Usar fecha de factura_legal (fl.fecha) o fecha de creación del item (fli.creado_en)

-- ============================================
-- PASO 0: ESTADÍSTICAS GENERALES
-- ============================================
-- Ajustar las fechas en el WHERE según necesidad
SELECT 
    'Estadísticas generales' as tipo,
    COUNT(*) as total_items,
    COUNT(CASE WHEN fli.iva IS NULL THEN 1 END) as items_sin_iva,
    COUNT(CASE WHEN fli.iva = 0 THEN 1 END) as items_iva_0,
    COUNT(CASE WHEN fli.iva = 5 THEN 1 END) as items_iva_5,
    COUNT(CASE WHEN fli.iva = 10 THEN 1 END) as items_iva_10,
    COUNT(CASE WHEN fli.iva IS NULL AND fli.producto_id IS NULL AND fli.presentacion_id IS NULL AND fli.venta_item_id IS NULL THEN 1 END) as items_sin_relacion
FROM financiero.factura_legal_item fli
INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp;
  -- Alternativa: usar fecha de creación del item
  -- WHERE fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  --   AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp;

-- ============================================
-- PASO 1: VERIFICAR QUÉ SE ACTUALIZARÁ
-- ============================================
SELECT 
    fli.id,
    fli.sucursal_id,
    fli.descripcion,
    fli.iva as iva_actual,
    COALESCE(
        -- Prioridad 2: producto directo
        (SELECT p.iva::integer FROM productos.producto p WHERE p.id = fli.producto_id),
        -- Prioridad 3: producto a través de presentación
        (SELECT pr.iva::integer 
         FROM productos.presentacion pres 
         INNER JOIN productos.producto pr ON pr.id = pres.producto_id 
         WHERE pres.id = fli.presentacion_id),
        -- Prioridad 4: producto a través de venta_item
        (SELECT pr.iva::integer 
         FROM operaciones.venta_item vi 
         INNER JOIN productos.producto pr ON pr.id = vi.producto_id 
         WHERE vi.id = fli.venta_item_id AND vi.sucursal_id = fli.sucursal_id),
        NULL
    ) as iva_nuevo,
    CASE 
        WHEN fli.iva IS NOT NULL THEN 'No se actualiza (ya tiene IVA)'
        WHEN fli.producto_id IS NOT NULL THEN 'Actualizar desde producto directo'
        WHEN fli.presentacion_id IS NOT NULL THEN 'Actualizar desde presentación'
        WHEN fli.venta_item_id IS NOT NULL THEN 'Actualizar desde venta_item'
        ELSE 'Sin relación - dejar NULL'
    END as motivo
FROM financiero.factura_legal_item fli
INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fli.iva IS NULL  -- Solo actualizar los que tienen NULL
  AND fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp
  -- Alternativa: usar fecha de creación del item
  -- AND fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  -- AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp
ORDER BY fli.id, fli.sucursal_id;

-- ============================================
-- PASO 2: ACTUALIZAR (ejecutar solo después de verificar)
-- ============================================
UPDATE financiero.factura_legal_item fli
SET iva = COALESCE(
    -- Prioridad 2: producto directo
    (SELECT p.iva::integer FROM productos.producto p WHERE p.id = fli.producto_id),
    -- Prioridad 3: producto a través de presentación
    (SELECT pr.iva::integer 
     FROM productos.presentacion pres 
     INNER JOIN productos.producto pr ON pr.id = pres.producto_id 
     WHERE pres.id = fli.presentacion_id),
    -- Prioridad 4: producto a través de venta_item
    (SELECT pr.iva::integer 
     FROM operaciones.venta_item vi 
     INNER JOIN productos.producto pr ON pr.id = vi.producto_id 
     WHERE vi.id = fli.venta_item_id AND vi.sucursal_id = fli.sucursal_id),
    NULL  -- Si no tiene ninguna relación, dejar NULL
)
FROM financiero.factura_legal fl
WHERE fli.iva IS NULL  -- Solo actualizar los que tienen NULL
  AND fl.id = fli.factura_legal_id 
  AND fl.sucursal_id = fli.sucursal_id
  AND fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp;
  -- Alternativa: usar fecha de creación del item
  -- AND fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  -- AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp;

-- ============================================
-- PASO 3: VERIFICAR RESULTADOS
-- ============================================
SELECT 
    COUNT(*) as total_items_actualizados,
    COUNT(CASE WHEN fli.iva IS NULL THEN 1 END) as items_sin_iva,
    COUNT(CASE WHEN fli.iva = 0 THEN 1 END) as items_iva_0,
    COUNT(CASE WHEN fli.iva = 5 THEN 1 END) as items_iva_5,
    COUNT(CASE WHEN fli.iva = 10 THEN 1 END) as items_iva_10
FROM financiero.factura_legal_item fli
INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp;
  -- Alternativa: usar fecha de creación del item
  -- WHERE fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  --   AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp;
  -- Para una factura específica, agregar:
  -- AND fli.factura_legal_id = 20363

-- ============================================
-- PASO 4: IDENTIFICAR ITEMS SIN RELACIÓN (requieren atención manual)
-- ============================================
SELECT 
    fli.id,
    fli.sucursal_id,
    fli.factura_legal_id,
    fli.descripcion,
    fli.iva,
    fli.producto_id,
    fli.presentacion_id,
    fli.venta_item_id,
    'Item sin relación - requiere atención manual' as observacion
FROM financiero.factura_legal_item fli
INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fli.iva IS NULL
  AND fli.producto_id IS NULL
  AND fli.presentacion_id IS NULL
  AND fli.venta_item_id IS NULL
  AND fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp
  -- Alternativa: usar fecha de creación del item
  -- AND fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  -- AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp
ORDER BY fli.factura_legal_id, fli.id;

-- ============================================
-- OPCIONAL: Actualizar solo una factura específica
-- ============================================
-- Para actualizar solo la factura 20363, agregar:
-- AND fli.factura_legal_id = 20363
-- en la cláusula WHERE del UPDATE

-- ============================================
-- ACTUALIZAR TODAS LAS FACTURAS (versión completa con filtro de fecha)
-- ============================================
-- Si quieres actualizar TODAS las facturas legales en un rango de fechas, usa esta versión:
/*
UPDATE financiero.factura_legal_item fli
SET iva = COALESCE(
    -- Prioridad 2: producto directo
    (SELECT p.iva::integer FROM productos.producto p WHERE p.id = fli.producto_id),
    -- Prioridad 3: producto a través de presentación
    (SELECT pr.iva::integer 
     FROM productos.presentacion pres 
     INNER JOIN productos.producto pr ON pr.id = pres.producto_id 
     WHERE pres.id = fli.presentacion_id),
    -- Prioridad 4: producto a través de venta_item
    (SELECT pr.iva::integer 
     FROM operaciones.venta_item vi 
     INNER JOIN productos.producto pr ON pr.id = vi.producto_id 
     WHERE vi.id = fli.venta_item_id AND vi.sucursal_id = fli.sucursal_id),
    NULL  -- Si no tiene ninguna relación, dejar NULL
)
FROM financiero.factura_legal fl
WHERE fli.iva IS NULL  -- Solo actualizar los que tienen NULL
  AND fl.id = fli.factura_legal_id 
  AND fl.sucursal_id = fli.sucursal_id
  AND fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp;
  -- Alternativa: usar fecha de creación del item
  -- AND fli.creado_en >= '2024-01-01 00:00:00'::timestamp
  -- AND fli.creado_en <= '2025-12-31 23:59:59'::timestamp;
*/
postgresql://frc_efact_user:jSDRWKbwn3mRJkHbJ0lbWv5jO0xXvCpM@dpg-d61m4dkhg0os73fpbf90-a.oregon-postgres.render.com/frc_efact_db_ocvv