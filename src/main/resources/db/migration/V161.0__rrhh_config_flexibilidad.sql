-- =====================================================================
-- RRHH — Config: flexibilidad (nuevas claves parametrizables)
-- =====================================================================
-- Expone como configuracion valores que antes estaban hardcodeados.
-- Aditivo, idempotente (WHERE NOT EXISTS). Central-only.
-- =====================================================================

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT v.clave, v.valor, v.tipo, v.descripcion, now()
FROM (VALUES
    ('MESES_PROMEDIO_LIQUIDACION_FINAL', '6',   'NUMBER', 'Cantidad de ultimas liquidaciones para promediar el salario en el finiquito'),
    ('DIAS_MES_PROMEDIO',                '30',  'NUMBER', 'Divisor dias/mes para calcular el salario diario (hora extra, finiquito)'),
    ('DIAS_ANIO_ANTIGUEDAD',            '365', 'NUMBER', 'Divisor dias/anio para calcular la antiguedad en el finiquito')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave
);
