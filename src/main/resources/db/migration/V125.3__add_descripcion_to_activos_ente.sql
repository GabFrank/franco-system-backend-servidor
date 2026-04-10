ALTER TABLE activos.ente ADD COLUMN descripcion VARCHAR(255);

-- Poblar descripciones existentes para Vehículos
UPDATE activos.ente e
SET descripcion = COALESCE('Chapa: ' || v.chapa, 'Vehículo #' || v.id)
FROM vehiculos.vehiculo v
WHERE e.referencia_id = v.id AND e.tipo_ente = 'VEHICULO';

-- Poblar descripciones existentes para Muebles
UPDATE activos.ente e
SET descripcion = COALESCE(m.descripcion, m.identificador, 'Mueble #' || m.id)
FROM activos.mueble m
WHERE e.referencia_id = m.id AND e.tipo_ente = 'MUEBLE';

-- Poblar descripciones existentes para Inmuebles
UPDATE activos.ente e
SET descripcion = COALESCE(i.nombre_asignado, i.direccion, 'Inmueble #' || i.id)
FROM activos.inmueble i
WHERE e.referencia_id = i.id AND e.tipo_ente = 'INMUEBLE';
