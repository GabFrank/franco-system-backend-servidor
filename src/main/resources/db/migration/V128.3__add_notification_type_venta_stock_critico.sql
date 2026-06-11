INSERT INTO configuraciones.notificacion_tipo_role (role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
VALUES
    (1, 'VENTA_STOCK_CRITICO', 'Alerta de venta con stock resultante cero o negativo', false, NOW()),
    (5, 'VENTA_STOCK_CRITICO', 'Alerta de venta con stock resultante cero o negativo', false, NOW())
ON CONFLICT (role_id, tipo_notificacion) DO NOTHING;
