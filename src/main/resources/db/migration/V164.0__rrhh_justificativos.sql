-- =====================================================================
-- RRHH — Justificativos (ex "novedades de jornada")
-- =====================================================================
-- 1) Renombra rrhh.jornada_novedad -> rrhh.justificativo. El nombre viejo no
--    reflejaba la funcion: lo que se carga son JUSTIFICACIONES de por que un
--    dia no fue una jornada normal. El rename es seguro porque el modulo RRHH
--    todavia no se desplegó en ninguna instancia.
-- 2) Crea el catalogo rrhh.tipo_justificativo: los tipos dejan de ser un enum
--    hardcodeado y pasan a definir su propio comportamiento (si evita la
--    penalizacion automatica, cuanto descuenta del salario, si requiere
--    documento respaldatorio). Asi se pueden agregar tipos sin desplegar.
-- 3) Migra los tipos existentes (columna varchar) al catalogo.
-- Aditivo e idempotente.
-- =====================================================================

-- 1) rename de la tabla ------------------------------------------------
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'rrhh' AND table_name = 'jornada_novedad')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'rrhh' AND table_name = 'justificativo') THEN
        ALTER TABLE rrhh.jornada_novedad RENAME TO justificativo;
    END IF;
END $$;

-- 2) catalogo de tipos -------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.tipo_justificativo (
    id                    BIGSERIAL PRIMARY KEY,
    nombre                VARCHAR(60) NOT NULL,
    descripcion           TEXT,
    -- si TRUE, un justificativo de este tipo evita la penalizacion automatica
    evita_penalizacion    BOOLEAN NOT NULL DEFAULT TRUE,
    -- NO | MEDIO_DIA | DIA_COMPLETO — cuanto descuenta en la liquidacion
    descuenta_salario     VARCHAR(20) NOT NULL DEFAULT 'NO',
    requiere_documento    BOOLEAN NOT NULL DEFAULT FALSE,
    -- los genera el sistema (vacaciones/feriados): no se cargan a mano
    generado_por_sistema  BOOLEAN NOT NULL DEFAULT FALSE,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id            BIGINT REFERENCES personas.usuario(id),
    creado_en             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tipo_justificativo_nombre
    ON rrhh.tipo_justificativo (upper(nombre));

INSERT INTO rrhh.tipo_justificativo
    (nombre, descripcion, evita_penalizacion, descuenta_salario, requiere_documento, generado_por_sistema, creado_en)
SELECT v.nombre, v.descripcion, v.evita, v.desc_sal, v.req_doc, v.sistema, now()
FROM (VALUES
    ('VACACION',               'Dia de vacaciones. Lo genera el modulo de vacaciones al marcar un periodo como GOZADO.', TRUE,  'NO',           FALSE, TRUE),
    ('FERIADO',                'Dia feriado.',                                                                          TRUE,  'NO',           FALSE, TRUE),
    ('JUSTIFICADO',            'Permiso justificado por el superior.',                                                   TRUE,  'NO',           FALSE, FALSE),
    ('REPOSO MEDICO',          'Reposo indicado por profesional de salud.',                                              TRUE,  'NO',           TRUE,  FALSE),
    ('AUSENCIA JUSTIFICADA',   'Ausencia con justificacion aceptada.',                                                   TRUE,  'NO',           FALSE, FALSE),
    ('DUELO',                  'Licencia por fallecimiento de familiar.',                                                TRUE,  'NO',           TRUE,  FALSE),
    ('MEDIA FALTA',            'Ausencia de medio dia. Descuenta medio dia de salario.',                                 FALSE, 'MEDIO_DIA',    FALSE, FALSE),
    ('AUSENCIA INJUSTIFICADA', 'Ausencia sin justificacion. Descuenta el dia completo.',                                 FALSE, 'DIA_COMPLETO', FALSE, FALSE)
) AS v(nombre, descripcion, evita, desc_sal, req_doc, sistema)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.tipo_justificativo t WHERE upper(t.nombre) = upper(v.nombre)
);

-- 3) FK al catalogo + backfill desde la columna varchar vieja -----------
ALTER TABLE rrhh.justificativo
    ADD COLUMN IF NOT EXISTS tipo_justificativo_id BIGINT REFERENCES rrhh.tipo_justificativo(id);

UPDATE rrhh.justificativo j
SET tipo_justificativo_id = t.id
FROM rrhh.tipo_justificativo t
WHERE j.tipo_justificativo_id IS NULL
  AND j.tipo IS NOT NULL
  AND upper(replace(j.tipo, '_', ' ')) = upper(t.nombre);

CREATE INDEX IF NOT EXISTS idx_justificativo_tipo
    ON rrhh.justificativo (tipo_justificativo_id);
