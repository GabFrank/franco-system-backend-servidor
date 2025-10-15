-- First, identify and set NULL for any precio_id values in venta_item that don't exist in precio_por_sucursal
UPDATE operaciones.venta_item 
SET precio_id = NULL 
WHERE precio_id NOT IN (SELECT id FROM productos.precio_por_sucursal);

-- Now it's safe to add the foreign key constraint
ALTER TABLE operaciones.venta_item 
ADD CONSTRAINT venta_item_precio_por_sucursal_fk 
FOREIGN KEY (precio_id) 
REFERENCES productos.precio_por_sucursal(id) 
ON DELETE SET NULL 
ON UPDATE CASCADE;

