-- Subdividir vehiculos.vehiculo en tablas tematicas sin cambiar el contrato del CRUD.

CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_finanzas (
    vehiculo_id BIGINT PRIMARY KEY REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE,
    situacion_pago VARCHAR(50),
    proveedor_id BIGINT REFERENCES personas.proveedor(id),
    moneda_id BIGINT REFERENCES financiero.moneda(id),
    monto_total NUMERIC(15,2),
    monto_ya_pagado NUMERIC(15,2),
    cantidad_cuotas INTEGER,
    cantidad_cuotas_pagadas INTEGER,
    dia_vencimiento INTEGER
);

CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_adjuntos (
    vehiculo_id BIGINT PRIMARY KEY REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE,
    imagenes_vehiculo TEXT,
    imagenes_documentos TEXT
);

CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_especificaciones (
    vehiculo_id BIGINT PRIMARY KEY REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE,
    primer_kilometraje NUMERIC(15,2),
    capacidad_kg NUMERIC(15,2),
    capacidad_pasajeros INTEGER,
    tipo_combustible_id BIGINT REFERENCES vehiculos.tipo_combustible(id),
    chasis VARCHAR(255),
    aire_acondicionado BOOLEAN,
    mantenimiento_motor_intervalo INTEGER,
    mantenimiento_caja_intervalo INTEGER
);

-- Backfill historico
INSERT INTO vehiculos.vehiculo_finanzas (
    vehiculo_id, situacion_pago, proveedor_id, moneda_id, monto_total, monto_ya_pagado,
    cantidad_cuotas, cantidad_cuotas_pagadas, dia_vencimiento
)
SELECT
    v.id, v.situacion_pago, v.proveedor_id, v.moneda_id, v.monto_total, v.monto_ya_pagado,
    v.cantidad_cuotas, v.cantidad_cuotas_pagadas, v.dia_vencimiento
FROM vehiculos.vehiculo v
ON CONFLICT (vehiculo_id) DO NOTHING;

INSERT INTO vehiculos.vehiculo_adjuntos (
    vehiculo_id, imagenes_vehiculo, imagenes_documentos
)
SELECT
    v.id, v.imagenes_vehiculo, v.imagenes_documentos
FROM vehiculos.vehiculo v
ON CONFLICT (vehiculo_id) DO NOTHING;

INSERT INTO vehiculos.vehiculo_especificaciones (
    vehiculo_id, primer_kilometraje, capacidad_kg, capacidad_pasajeros, tipo_combustible_id,
    chasis, aire_acondicionado, mantenimiento_motor_intervalo, mantenimiento_caja_intervalo
)
SELECT
    v.id, v.primer_kilometraje, v.capacidad_kg, v.capacidad_pasajeros, v.tipo_combustible_id,
    v.chasis, v.aire_acondicionado, v.mantenimiento_motor_intervalo, v.mantenimiento_caja_intervalo
FROM vehiculos.vehiculo v
ON CONFLICT (vehiculo_id) DO NOTHING;

-- Limpiar columnas legacy movidas a subtablas
ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_tipo_combustible_id_fkey;
ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_proveedor_id_fkey;
ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_moneda_id_fkey;

ALTER TABLE vehiculos.vehiculo
    DROP COLUMN IF EXISTS primer_kilometraje,
    DROP COLUMN IF EXISTS capacidad_kg,
    DROP COLUMN IF EXISTS capacidad_pasajeros,
    DROP COLUMN IF EXISTS imagenes_vehiculo,
    DROP COLUMN IF EXISTS imagenes_documentos,
    DROP COLUMN IF EXISTS tipo_combustible_id,
    DROP COLUMN IF EXISTS chasis,
    DROP COLUMN IF EXISTS aire_acondicionado,
    DROP COLUMN IF EXISTS mantenimiento_motor_intervalo,
    DROP COLUMN IF EXISTS mantenimiento_caja_intervalo,
    DROP COLUMN IF EXISTS situacion_pago,
    DROP COLUMN IF EXISTS proveedor_id,
    DROP COLUMN IF EXISTS moneda_id,
    DROP COLUMN IF EXISTS monto_total,
    DROP COLUMN IF EXISTS monto_ya_pagado,
    DROP COLUMN IF EXISTS cantidad_cuotas,
    DROP COLUMN IF EXISTS cantidad_cuotas_pagadas,
    DROP COLUMN IF EXISTS dia_vencimiento;
