INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.terminal_pos', 'MAIN_TO_ALL', 'Terminal Pos', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
