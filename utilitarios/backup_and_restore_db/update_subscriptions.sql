-- ======================================================================
-- Script para actualizar suscripciones después de restaurar la BD
-- Base de datos: general_fact_test_2
-- Fecha: 2025-10-21
-- Objetivo: Actualizar suscripciones para apuntar a bodega_fact_test_2
-- ======================================================================

-- IMPORTANTE: Ejecutar estos comandos en psql conectado a general_fact_test_2:
-- psql -h localhost -p 5552 -U franco -d general_fact_test_2

-- ----------------------------------------------------------------------
-- Paso 1: Deshabilitar y actualizar central_filial24_sub
-- ----------------------------------------------------------------------

-- Deshabilitar temporalmente
ALTER SUBSCRIPTION central_filial24_sub DISABLE;

-- Actualizar connection string para apuntar a bodega_fact_test_2
ALTER SUBSCRIPTION central_filial24_sub 
CONNECTION 'dbname=bodega_fact_test_2 host=localhost user=franco password=franco port=5551';

-- Re-habilitar
ALTER SUBSCRIPTION central_filial24_sub ENABLE;

-- Refrescar para sincronizar con la publicación
ALTER SUBSCRIPTION central_filial24_sub REFRESH PUBLICATION;

-- ----------------------------------------------------------------------
-- Paso 2: Deshabilitar y actualizar filial24_central_sub
-- ----------------------------------------------------------------------

-- Deshabilitar temporalmente
ALTER SUBSCRIPTION filial24_central_sub DISABLE;

-- Actualizar connection string para apuntar a bodega_fact_test_2
ALTER SUBSCRIPTION filial24_central_sub 
CONNECTION 'dbname=bodega_fact_test_2 host=localhost user=franco password=franco port=5551';

-- Re-habilitar
ALTER SUBSCRIPTION filial24_central_sub ENABLE;

-- Refrescar para sincronizar con la publicación
ALTER SUBSCRIPTION filial24_central_sub REFRESH PUBLICATION;

-- ----------------------------------------------------------------------
-- Paso 3: Verificar el estado final
-- ----------------------------------------------------------------------

-- Ver configuración de suscripciones
SELECT 
    subname AS "Subscription Name",
    subenabled AS "Enabled",
    subconninfo AS "Connection Info",
    subslotname AS "Slot Name"
FROM pg_subscription
ORDER BY subname;

-- Ver estadísticas de replicación (verificar que estén activas)
SELECT 
    s.subname AS "Subscription",
    s.subenabled AS "Enabled",
    sr.pid AS "Worker PID",
    sr.received_lsn AS "Received LSN",
    sr.last_msg_send_time AS "Last Send",
    sr.last_msg_receipt_time AS "Last Receipt",
    CASE 
        WHEN sr.pid IS NOT NULL THEN 'ACTIVE'
        WHEN s.subenabled THEN 'ENABLED (connecting...)'
        ELSE 'DISABLED'
    END AS "Status"
FROM pg_subscription s
LEFT JOIN pg_stat_subscription sr ON s.oid = sr.subid
ORDER BY s.subname;

-- ----------------------------------------------------------------------
-- NOTAS:
-- ----------------------------------------------------------------------
-- 1. Si ves suscripciones duplicadas en las consultas anteriores,
--    significa que el dump trajo suscripciones extras del servidor remoto.
-- 2. Para eliminar duplicados deshabilitados, identifica el OID y usa:
--    SELECT oid, subname, subenabled FROM pg_subscription;
--    Luego elimina usando el nombre específico
-- 3. Las suscripciones deben mostrar PID activo después de unos segundos
-- 4. Si hay errores de conexión, verifica que bodega_fact_test_2 exista
--    en el servidor localhost:5551
