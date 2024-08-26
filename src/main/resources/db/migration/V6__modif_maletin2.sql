ALTER TABLE financiero.maletin DROP CONSTRAINT maletin_sucursal_fk;
ALTER TABLE financiero.maletin ADD CONSTRAINT maletin_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL ON UPDATE CASCADE;
