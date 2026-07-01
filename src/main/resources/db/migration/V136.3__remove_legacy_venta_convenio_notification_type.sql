DELETE FROM configuraciones.notificacion_preferencia_usuario
WHERE tipo_notificacion = 'VENTA_CONVENIO';

DELETE FROM configuraciones.notificacion_tipo_role
WHERE tipo_notificacion = 'VENTA_CONVENIO';
