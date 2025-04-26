ALTER TABLE operaciones.inventario_producto DROP CONSTRAINT inventario_producto_zona_fk;
ALTER TABLE operaciones.inventario_producto ADD CONSTRAINT inventario_producto_zona_fk FOREIGN KEY (zona_id) REFERENCES empresarial.zona(id) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE empresarial.sector ADD CONSTRAINT sector_unique UNIQUE (sucursal_id,descripcion);
ALTER TABLE empresarial.zona ADD CONSTRAINT zona_unique UNIQUE (sector_id,descripcion);


