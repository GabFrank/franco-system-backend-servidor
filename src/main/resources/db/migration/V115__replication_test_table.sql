-- Tabla de test para verificar replicación bidireccional entre central y filial.
CREATE TABLE IF NOT EXISTS configuraciones.replication_test (
    id BIGSERIAL PRIMARY KEY,
    test_uuid VARCHAR(64) NOT NULL,
    source_db VARCHAR(20) NOT NULL,
    sucursal_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

ALTER TABLE configuraciones.replication_test REPLICA IDENTITY FULL;

COMMENT ON TABLE configuraciones.replication_test IS 'Tabla usada para test E2E de replicación lógica (insert/select/delete en ambas direcciones).';

-- Incluir en replication_table para que se replique en ambas direcciones (BRANCH_TO_MAIN + flag para central_filialX_pub).
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('configuraciones.replication_test', 'BRANCH_TO_MAIN', 'Tabla de test de replicación', true, true, NOW())
ON CONFLICT (table_name) DO NOTHING;
