-- Habilita opt-out por usuario para la notificación DIFERENCIA_MALETIN,
-- igual que VENTA_STOCK_CRITICO (ver V128.3 / V137.3).
INSERT INTO configuraciones.notificacion_tipo_role (role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
SELECT r.id,
       'DIFERENCIA_MALETIN',
       'Alerta de diferencia detectada en maletín',
       false,
       NOW()
FROM personas.role r
WHERE r.nombre IN ('ANALISIS DE CAJA', 'ANALISIS CONTABLE', 'ANALISIS DE VENTA')
ON CONFLICT (role_id, tipo_notificacion) DO NOTHING;
