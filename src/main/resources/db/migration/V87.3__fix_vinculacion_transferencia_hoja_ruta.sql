UPDATE operaciones.transferencia t
SET hoja_ruta_id = NULL
FROM operaciones.hoja_ruta hr
WHERE t.hoja_ruta_id = hr.id
AND (
    NOT EXISTS (
        SELECT 1 FROM personas.usuario u 
        WHERE u.id = t.usuario_transporte_id 
        AND u.persona_id = hr.chofer_id
    )
    OR
    t.creado_en < hr.creado_en - INTERVAL '24 hours'
    OR
    t.creado_en > hr.creado_en + INTERVAL '48 hours'
);
