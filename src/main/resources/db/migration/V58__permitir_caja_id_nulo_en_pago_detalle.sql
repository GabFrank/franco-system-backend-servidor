-- Allow NULL value for caja_id in pago_detalle table
ALTER TABLE operaciones.pago_detalle 
    DROP CONSTRAINT IF EXISTS pago_detalle_caja_fk;

-- First make the column nullable
ALTER TABLE operaciones.pago_detalle 
    ALTER COLUMN caja_id DROP NOT NULL;

-- Then recreate the foreign key constraint allowing NULL values
ALTER TABLE operaciones.pago_detalle 
    ADD CONSTRAINT pago_detalle_caja_fk 
    FOREIGN KEY (caja_id, sucursal_id) 
    REFERENCES financiero.pdv_caja(id, sucursal_id) 
    ON DELETE RESTRICT; 
