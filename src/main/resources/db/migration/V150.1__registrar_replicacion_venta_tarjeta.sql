-- Venta con tarjeta offline-first:
-- - configuracion_venta_tarjeta: central -> todas las filiales (fuente de verdad: central).
-- - venta_tarjeta: la filial crea (PENDIENTE) y sube al central; el estado que
--   setea la app movil en el central (COMPLETADO) vuelve a la filial duena
--   via central_filialX_pub con WHERE sucursal_id = X (mismo esquema que operaciones.venta).
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.configuracion_venta_tarjeta', 'MAIN_TO_ALL', 'Configuracion Venta Tarjeta', true, false, NOW()),
    ('financiero.venta_tarjeta', 'BRANCH_TO_MAIN', 'Venta Tarjeta', true, true, NOW())
ON CONFLICT (table_name) DO NOTHING;
