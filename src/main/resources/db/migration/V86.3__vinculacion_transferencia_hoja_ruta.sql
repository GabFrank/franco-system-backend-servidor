
ALTER TABLE operaciones.transferencia 
ADD COLUMN IF NOT EXISTS hoja_ruta_id BIGINT;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_transferencia_hoja_ruta') THEN 
        ALTER TABLE operaciones.transferencia 
        ADD CONSTRAINT fk_transferencia_hoja_ruta 
        FOREIGN KEY (hoja_ruta_id) REFERENCES operaciones.hoja_ruta(id); 
    END IF; 
END $$;

UPDATE operaciones.transferencia t
SET hoja_ruta_id = hr_sub.id
FROM (
    SELECT DISTINCT ON (u.id) 
        u.id as usuario_id, 
        hr.id
    FROM personas.usuario u
    JOIN personas.persona p ON u.persona_id = p.id
    JOIN operaciones.hoja_ruta hr ON hr.chofer_id = p.id
    ORDER BY u.id, hr.creado_en DESC
) as hr_sub
WHERE t.usuario_transporte_id = hr_sub.usuario_id
AND t.hoja_ruta_id IS NULL
AND t.usuario_transporte_id IS NOT NULL;
