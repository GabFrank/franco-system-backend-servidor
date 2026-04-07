-- Registrar administrativo.jornada en replication_table para replicación central → filiales (MAIN_TO_ALL).
-- No se define REPLICA IDENTITY FULL aquí: el sync (scheduler o botón "Sincronizar publicaciones")
-- lo aplicará antes de añadir la tabla a central_pub si no lo tiene.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('administrativo.jornada', 'MAIN_TO_ALL', 'Jornada (RRHH)', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
