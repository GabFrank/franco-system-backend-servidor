-- Migración para corregir vinculaciones incorrectas causadas por V86.3
-- Desvinculamos transferencias de hojas de ruta donde el chofer no coincide con el usuario de transporte
-- o donde la diferencia de tiempo es excesiva (más de 24 horas de la creación de la hoja de ruta)

UPDATE operaciones.transferencia t
SET hoja_ruta_id = NULL
FROM operaciones.hoja_ruta hr
WHERE t.hoja_ruta_id = hr.id
AND (
    -- El chofer de la hoja de ruta no corresponde al usuario que transportó (vía persona_id)
    NOT EXISTS (
        SELECT 1 FROM personas.usuario u 
        WHERE u.id = t.usuario_transporte_id 
        AND u.persona_id = hr.chofer_id
    )
    OR
    -- O la transferencia se creó mucho antes o después de la hoja de ruta (margen de 24h para ser conservadores)
    t.creado_en < hr.creado_en - INTERVAL '24 hours'
    OR
    t.creado_en > hr.creado_en + INTERVAL '48 hours'
);
