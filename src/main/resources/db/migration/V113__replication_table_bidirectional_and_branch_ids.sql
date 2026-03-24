-- Add columns for Option B: branch_ids (MAIN_TO_SPECIFIC) and replicate_central_to_branch_with_filter (bidirectional flag for BRANCH_TO_MAIN)

ALTER TABLE configuraciones.replication_table
    ADD COLUMN IF NOT EXISTS branch_ids BIGINT[],
    ADD COLUMN IF NOT EXISTS replicate_central_to_branch_with_filter BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN configuraciones.replication_table.branch_ids IS 'For MAIN_TO_SPECIFIC: destination branch IDs. Null for other directions.';
COMMENT ON COLUMN configuraciones.replication_table.replicate_central_to_branch_with_filter IS 'When direction=BRANCH_TO_MAIN and true: table is also in central_filialX_pub (central to branch with WHERE sucursal_id = X) for all branches.';

-- Set flag for existing BRANCH_TO_MAIN tables that are used bidirectionally (central → branch with filter)
UPDATE configuraciones.replication_table
SET replicate_central_to_branch_with_filter = true
WHERE direction = 'BRANCH_TO_MAIN'
  AND table_name IN (
    'financiero.factura_legal',
    'financiero.factura_legal_item',
    'operaciones.venta',
    'operaciones.venta_item',
    'operaciones.stock_por_producto_sucursal'
  );
