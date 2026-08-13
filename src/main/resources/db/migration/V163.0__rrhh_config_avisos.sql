-- =====================================================================
-- RRHH — Config: dias de aviso para notificaciones/KPIs
-- =====================================================================
-- Parametros de anticipacion para las alertas del scheduler y el dashboard.
-- Aditivo, idempotente.
-- =====================================================================

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT v.clave, v.valor, v.tipo, v.descripcion, now()
FROM (VALUES
    ('DOCUMENTO_AVISO_VENCIMIENTO_DIAS', '30', 'NUMBER', 'Dias de anticipacion para avisar que un documento del legajo esta por vencer'),
    ('VACACION_AVISO_PRESCRIPCION_DIAS', '60', 'NUMBER', 'Dias de anticipacion para avisar que unas vacaciones estan por prescribir')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave
);
