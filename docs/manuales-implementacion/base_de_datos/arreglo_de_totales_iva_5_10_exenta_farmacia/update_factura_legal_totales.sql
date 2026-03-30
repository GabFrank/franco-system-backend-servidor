-- =====================================================
-- Script: Actualizar totales e IVAs de Factura Legal
-- Fecha: 2026-02-05
-- Lógica: Descuento se aplica proporcionalmente, IVA sobre monto con descuento
-- Parámetros: :fechaInicio y :fechaFin (formato: 'YYYY-MM-DD HH:MM:SS')
-- =====================================================

-- =====================================================
-- PASO 1: Estadísticas del Rango de Fechas
-- =====================================================
SELECT 
    COUNT(*) as total_facturas,
    COUNT(CASE WHEN fl.descuento > 0 THEN 1 END) as facturas_con_descuento,
    COUNT(CASE WHEN fl.descuento = 0 OR fl.descuento IS NULL THEN 1 END) as facturas_sin_descuento,
    MIN(fl.creado_en) as fecha_minima,
    MAX(fl.creado_en) as fecha_maxima
FROM financiero.factura_legal fl
WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin;

-- =====================================================
-- PASO 2: Cálculo de Nuevos Valores (Preview)
-- =====================================================
WITH items_por_iva AS (
    SELECT 
        fli.factura_legal_id,
        fli.sucursal_id,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 0 THEN fli.total ELSE 0 END) as suma_items_iva_0,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 5 THEN fli.total ELSE 0 END) as suma_items_iva_5,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 10 THEN fli.total ELSE 0 END) as suma_items_iva_10,
        SUM(fli.total) as suma_total_items
    FROM financiero.factura_legal_item fli
    INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
    GROUP BY fli.factura_legal_id, fli.sucursal_id
),
calculos AS (
    SELECT 
        fl.id,
        fl.sucursal_id,
        fl.numero_factura,
        fl.descuento,
        COALESCE(i.suma_items_iva_0, 0) as suma_items_iva_0,
        COALESCE(i.suma_items_iva_5, 0) as suma_items_iva_5,
        COALESCE(i.suma_items_iva_10, 0) as suma_items_iva_10,
        COALESCE(i.suma_total_items, 0) as suma_total_items,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN fl.descuento / i.suma_total_items 
            ELSE 0 
        END as porcentaje_descuento,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_0, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_0, 0)
        END as nuevo_total_parcial_0,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_5, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_5, 0)
        END as nuevo_total_parcial_5,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_10, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_10, 0)
        END as nuevo_total_parcial_10
    FROM financiero.factura_legal fl
    LEFT JOIN items_por_iva i ON i.factura_legal_id = fl.id AND i.sucursal_id = fl.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
)
SELECT 
    id,
    sucursal_id,
    numero_factura,
    descuento,
    suma_items_iva_0,
    suma_items_iva_5,
    suma_items_iva_10,
    suma_total_items,
    ROUND(porcentaje_descuento * 100, 4) as porcentaje_descuento_pct,
    nuevo_total_parcial_0,
    nuevo_total_parcial_5,
    nuevo_total_parcial_10,
    ROUND(nuevo_total_parcial_5 / 21.0, 2) as nuevo_iva_parcial_5,
    ROUND(nuevo_total_parcial_10 / 11.0, 2) as nuevo_iva_parcial_10,
    ROUND(nuevo_total_parcial_0 + nuevo_total_parcial_5 + nuevo_total_parcial_10, 2) as nuevo_total_final
FROM calculos
ORDER BY id, sucursal_id
LIMIT 100;

-- =====================================================
-- PASO 3: Comparación Actuales vs Nuevos (Preview)
-- =====================================================
WITH items_por_iva AS (
    SELECT 
        fli.factura_legal_id,
        fli.sucursal_id,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 0 THEN fli.total ELSE 0 END) as suma_items_iva_0,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 5 THEN fli.total ELSE 0 END) as suma_items_iva_5,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 10 THEN fli.total ELSE 0 END) as suma_items_iva_10,
        SUM(fli.total) as suma_total_items
    FROM financiero.factura_legal_item fli
    INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
    GROUP BY fli.factura_legal_id, fli.sucursal_id
),
calculos AS (
    SELECT 
        fl.id,
        fl.sucursal_id,
        fl.numero_factura,
        fl.total_parcial_0 as actual_total_parcial_0,
        fl.total_parcial_5 as actual_total_parcial_5,
        fl.total_parcial_10 as actual_total_parcial_10,
        fl.iva_parcial_5 as actual_iva_parcial_5,
        fl.iva_parcial_10 as actual_iva_parcial_10,
        fl.total_final as actual_total_final,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_0, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_0, 0)
        END as nuevo_total_parcial_0,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_5, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_5, 0)
        END as nuevo_total_parcial_5,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_10, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_10, 0)
        END as nuevo_total_parcial_10
    FROM financiero.factura_legal fl
    LEFT JOIN items_por_iva i ON i.factura_legal_id = fl.id AND i.sucursal_id = fl.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
)
SELECT 
    id,
    sucursal_id,
    numero_factura,
    actual_total_parcial_0,
    nuevo_total_parcial_0,
    ROUND(nuevo_total_parcial_0 - actual_total_parcial_0, 2) as dif_parcial_0,
    actual_total_parcial_5,
    nuevo_total_parcial_5,
    ROUND(nuevo_total_parcial_5 - actual_total_parcial_5, 2) as dif_parcial_5,
    actual_total_parcial_10,
    nuevo_total_parcial_10,
    ROUND(nuevo_total_parcial_10 - actual_total_parcial_10, 2) as dif_parcial_10,
    actual_iva_parcial_5,
    ROUND(nuevo_total_parcial_5 / 21.0, 2) as nuevo_iva_parcial_5,
    ROUND((nuevo_total_parcial_5 / 21.0) - actual_iva_parcial_5, 2) as dif_iva_5,
    actual_iva_parcial_10,
    ROUND(nuevo_total_parcial_10 / 11.0, 2) as nuevo_iva_parcial_10,
    ROUND((nuevo_total_parcial_10 / 11.0) - actual_iva_parcial_10, 2) as dif_iva_10,
    actual_total_final,
    ROUND(nuevo_total_parcial_0 + nuevo_total_parcial_5 + nuevo_total_parcial_10, 2) as nuevo_total_final,
    ROUND((nuevo_total_parcial_0 + nuevo_total_parcial_5 + nuevo_total_parcial_10) - actual_total_final, 2) as dif_total_final
FROM calculos
WHERE ABS(nuevo_total_parcial_0 - actual_total_parcial_0) > 1 
   OR ABS(nuevo_total_parcial_5 - actual_total_parcial_5) > 1 
   OR ABS(nuevo_total_parcial_10 - actual_total_parcial_10) > 1
ORDER BY ABS((nuevo_total_parcial_0 + nuevo_total_parcial_5 + nuevo_total_parcial_10) - actual_total_final) DESC
LIMIT 100;

-- =====================================================
-- PASO 4: Resumen de Facturas con Errores
-- =====================================================
WITH items_por_iva AS (
    SELECT 
        fli.factura_legal_id,
        fli.sucursal_id,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 0 THEN fli.total ELSE 0 END) as suma_items_iva_0,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 5 THEN fli.total ELSE 0 END) as suma_items_iva_5,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 10 THEN fli.total ELSE 0 END) as suma_items_iva_10,
        SUM(fli.total) as suma_total_items
    FROM financiero.factura_legal_item fli
    INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
    GROUP BY fli.factura_legal_id, fli.sucursal_id
),
calculos AS (
    SELECT 
        fl.id,
        fl.sucursal_id,
        fl.total_parcial_0,
        fl.total_parcial_5,
        fl.total_parcial_10,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_0, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_0, 0)
        END as correcto_parcial_0,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_5, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_5, 0)
        END as correcto_parcial_5,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_10, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_10, 0)
        END as correcto_parcial_10
    FROM financiero.factura_legal fl
    LEFT JOIN items_por_iva i ON i.factura_legal_id = fl.id AND i.sucursal_id = fl.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
)
SELECT 
    COUNT(*) as total_facturas,
    COUNT(CASE WHEN ABS(total_parcial_0 - correcto_parcial_0) > 1 
                OR ABS(total_parcial_5 - correcto_parcial_5) > 1 
                OR ABS(total_parcial_10 - correcto_parcial_10) > 1 
           THEN 1 END) as facturas_con_errores,
    COUNT(CASE WHEN ABS(total_parcial_0 - correcto_parcial_0) > 1 THEN 1 END) as errores_parcial_0,
    COUNT(CASE WHEN ABS(total_parcial_5 - correcto_parcial_5) > 1 THEN 1 END) as errores_parcial_5,
    COUNT(CASE WHEN ABS(total_parcial_10 - correcto_parcial_10) > 1 THEN 1 END) as errores_parcial_10
FROM calculos;

-- =====================================================
-- PASO 5: UPDATE (COMENTADO - Descomentar para ejecutar)
-- =====================================================

/*
WITH items_por_iva AS (
    SELECT 
        fli.factura_legal_id,
        fli.sucursal_id,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 0 THEN fli.total ELSE 0 END) as suma_items_iva_0,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 5 THEN fli.total ELSE 0 END) as suma_items_iva_5,
        SUM(CASE WHEN COALESCE(fli.iva, 0) = 10 THEN fli.total ELSE 0 END) as suma_items_iva_10,
        SUM(fli.total) as suma_total_items
    FROM financiero.factura_legal_item fli
    INNER JOIN financiero.factura_legal fl ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
    GROUP BY fli.factura_legal_id, fli.sucursal_id
),
calculos AS (
    SELECT 
        fl.id,
        fl.sucursal_id,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_0, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_0, 0)
        END as nuevo_total_parcial_0,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_5, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_5, 0)
        END as nuevo_total_parcial_5,
        CASE 
            WHEN COALESCE(i.suma_total_items, 0) > 0 AND COALESCE(fl.descuento, 0) > 0
            THEN ROUND(COALESCE(i.suma_items_iva_10, 0) * (1 - (fl.descuento / i.suma_total_items)), 2)
            ELSE COALESCE(i.suma_items_iva_10, 0)
        END as nuevo_total_parcial_10
    FROM financiero.factura_legal fl
    LEFT JOIN items_por_iva i ON i.factura_legal_id = fl.id AND i.sucursal_id = fl.sucursal_id
    WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin
)
UPDATE financiero.factura_legal fl
SET 
    total_parcial_0 = c.nuevo_total_parcial_0,
    total_parcial_5 = c.nuevo_total_parcial_5,
    total_parcial_10 = c.nuevo_total_parcial_10,
    iva_parcial_5 = ROUND(c.nuevo_total_parcial_5 / 21.0, 2),
    iva_parcial_10 = ROUND(c.nuevo_total_parcial_10 / 11.0, 2),
    total_final = ROUND(c.nuevo_total_parcial_0 + c.nuevo_total_parcial_5 + c.nuevo_total_parcial_10, 2)
FROM calculos c
WHERE fl.id = c.id AND fl.sucursal_id = c.sucursal_id;

-- Verificación post-actualización
SELECT 
    COUNT(*) as facturas_actualizadas,
    COUNT(CASE WHEN ABS(fl.total_final - (fl.total_parcial_0 + fl.total_parcial_5 + fl.total_parcial_10)) > 1 THEN 1 END) as facturas_con_diferencias
FROM financiero.factura_legal fl
WHERE fl.creado_en BETWEEN :fechaInicio AND :fechaFin;
*/
