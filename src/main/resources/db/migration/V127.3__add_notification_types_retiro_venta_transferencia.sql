INSERT INTO configuraciones.notificacion_tipo_role (role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
VALUES
    (1, 'RETIRO', 'Notificación de retiro realizado en sucursal', false, NOW()),
    (5, 'RETIRO', 'Notificación de retiro realizado en sucursal', false, NOW()),
    (1, 'VENTA_TRANSFERENCIA', 'Notificación de venta con pago por transferencia', false, NOW()),
    (5, 'VENTA_TRANSFERENCIA', 'Notificación de venta con pago por transferencia', false, NOW())
ON CONFLICT (role_id, tipo_notificacion) DO NOTHING;
