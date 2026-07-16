-- Modulo Devoluciones: TipoGasto semilla para la merma (devolucion SIN_PROVEEDOR).
-- El gasto generado por una salida por averiado/vencido sin devolucion a proveedor
-- se clasifica con este tipo. Idempotente por descripcion. Id via max+1 (patron del repo).

INSERT INTO financiero.tipo_gasto
    (id, descripcion, activo, is_clasificacion, autorizacion,
     afecta_finanzas_activo, es_pago_cuota_activo, activo_en_sucursales, creado_en)
SELECT COALESCE((SELECT MAX(id) FROM financiero.tipo_gasto), 0) + 1,
       'MERMA/AVERIA DE PRODUCTO', TRUE, FALSE, FALSE,
       FALSE, FALSE, TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM financiero.tipo_gasto WHERE UPPER(descripcion) = 'MERMA/AVERIA DE PRODUCTO'
);
