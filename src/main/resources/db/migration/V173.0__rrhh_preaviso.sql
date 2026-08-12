-- Preaviso en el finiquito: días por tramo de antigüedad (configurables, fijos)
-- + columnas en liquidacion_final para persistir el preaviso calculado y el flag
-- "otorgado". Aditivo e idempotente.

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion)
SELECT * FROM (VALUES
    ('PREAVISO_DIAS_HASTA_1A', '30', 'NUMBER', 'Dias de preaviso hasta 1 anio de antiguedad'),
    ('PREAVISO_DIAS_1_5A',     '45', 'NUMBER', 'Dias de preaviso de 1 a 5 anios de antiguedad'),
    ('PREAVISO_DIAS_5_10A',    '60', 'NUMBER', 'Dias de preaviso de 5 a 10 anios de antiguedad'),
    ('PREAVISO_DIAS_MAS_10A',  '90', 'NUMBER', 'Dias de preaviso mas de 10 anios de antiguedad')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave);

ALTER TABLE rrhh.liquidacion_final ADD COLUMN IF NOT EXISTS preaviso_otorgado BOOLEAN DEFAULT false;
ALTER TABLE rrhh.liquidacion_final ADD COLUMN IF NOT EXISTS preaviso_dias INTEGER;
ALTER TABLE rrhh.liquidacion_final ADD COLUMN IF NOT EXISTS preaviso_monto NUMERIC(18,2);
ALTER TABLE rrhh.liquidacion_final ADD COLUMN IF NOT EXISTS preaviso_es_descuento BOOLEAN;
