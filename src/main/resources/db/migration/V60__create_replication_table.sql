-- Create configuraciones schema if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'configuraciones') THEN
        CREATE SCHEMA configuraciones;
    END IF;
END$$;

-- Create ReplicationDirection enum type
CREATE TYPE configuraciones.replication_direction AS ENUM ('MAIN_TO_ALL', 'MAIN_TO_SPECIFIC', 'BRANCH_TO_MAIN');

-- Create replication_table table
CREATE TABLE configuraciones.replication_table (
    id SERIAL PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL UNIQUE,
    direction configuraciones.replication_direction NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    creado_en TIMESTAMP WITHOUT TIME ZONE,
    usuario_id BIGINT
);

-- Add foreign key constraint
ALTER TABLE configuraciones.replication_table
    ADD CONSTRAINT fk_replication_table_usuario
    FOREIGN KEY (usuario_id)
    REFERENCES personas.usuario (id);

-- Add comment to table
COMMENT ON TABLE configuraciones.replication_table IS 'Stores configuration for tables that should be replicated between central and branch servers';

-- Add comments to columns
COMMENT ON COLUMN configuraciones.replication_table.table_name IS 'Full table name with schema (e.g., "personas.usuario")';
COMMENT ON COLUMN configuraciones.replication_table.direction IS 'Direction of replication (MAIN_TO_ALL, MAIN_TO_SPECIFIC, BRANCH_TO_MAIN)';
COMMENT ON COLUMN configuraciones.replication_table.enabled IS 'Whether this table should be replicated';
COMMENT ON COLUMN configuraciones.replication_table.description IS 'Additional notes or description';

-- Insert default tables for replication
INSERT INTO configuraciones.replication_table (table_name, direction, description, creado_en) VALUES
-- Main to All Branches (from central server tables)
('configuraciones.actualizacion', 'MAIN_TO_ALL', 'System update information', NOW()),
('configuraciones.local', 'MAIN_TO_ALL', 'Local configuration', NOW()),
('empresarial.cargo', 'MAIN_TO_ALL', 'Position/role information', NOW()),
('empresarial.configuracion_general', 'MAIN_TO_ALL', 'General configuration', NOW()),
('empresarial.punto_de_venta', 'MAIN_TO_ALL', 'Point of sale information', NOW()),
('empresarial.sector', 'MAIN_TO_ALL', 'Sector information', NOW()),
('empresarial.sucursal', 'MAIN_TO_ALL', 'Branch information', NOW()),
('productos.familia', 'MAIN_TO_ALL', 'Product family', NOW()),
('productos.subfamilia', 'MAIN_TO_ALL', 'Product subfamily', NOW()),
('productos.producto', 'MAIN_TO_ALL', 'Product information', NOW()),

-- Branch to Main (from branch to central server)
('financiero.factura_legal', 'BRANCH_TO_MAIN', 'Legal invoice data', NOW()),
('financiero.factura_legal_item', 'BRANCH_TO_MAIN', 'Legal invoice items', NOW()),
('operaciones.venta', 'BRANCH_TO_MAIN', 'Sales data', NOW()),
('operaciones.venta_item', 'BRANCH_TO_MAIN', 'Sales items', NOW()),
('operaciones.stock_por_producto_sucursal', 'BRANCH_TO_MAIN', 'Branch product stock information', NOW()); 
