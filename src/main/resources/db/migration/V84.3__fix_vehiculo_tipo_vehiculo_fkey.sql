ALTER TABLE IF EXISTS vehiculos.vehiculo 
    DROP CONSTRAINT IF EXISTS vehiculo_tipo_vehiculo_fkey;
ALTER TABLE vehiculos.vehiculo 
    ADD CONSTRAINT vehiculo_tipo_vehiculo_fkey 
    FOREIGN KEY (tipo_vehiculo) 
    REFERENCES vehiculos.tipo_vehiculo(id);

