-- =====================================================================
-- RRHH — Fase 0: Fundaciones
-- =====================================================================
-- Crea el schema `rrhh`, extiende `personas.funcionario` de forma
-- ADITIVA (columnas nullable), crea las tablas base de configuracion y
-- conceptos de liquidacion con sus seeds, y siembra los roles RRHH.
--
-- Todo es aditivo y retrocompatible: no altera tipos existentes, no
-- elimina ni renombra columnas, y usa IF NOT EXISTS / ON CONFLICT /
-- WHERE NOT EXISTS para ser idempotente y no romper datos existentes.
-- Ninguna tabla RRHH se agrega a publicaciones de replicacion
-- (gestion central-only, ver plan §18.2).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS rrhh;

-- ---------------------------------------------------------------------
-- 1. Extension aditiva de personas.funcionario (todas nullable)
-- ---------------------------------------------------------------------
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS fecha_egreso    timestamp;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS motivo_egreso   varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS codigo_interno  varchar(50);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS ips_activo      boolean DEFAULT false;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS numero_ips      varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS valor_jornal    numeric(18,2);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS moneda_id       bigint;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS cuenta_bancaria varchar(100);

-- FK a la moneda del sueldo (nullable; no afecta filas existentes)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_funcionario_moneda'
          AND table_schema = 'personas'
    ) THEN
        ALTER TABLE personas.funcionario
            ADD CONSTRAINT fk_funcionario_moneda
            FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2. rrhh.configuracion_rrhh — parametros key/value tipados
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.configuracion_rrhh (
    id          BIGSERIAL PRIMARY KEY,
    clave       VARCHAR(100) NOT NULL UNIQUE,
    valor       VARCHAR(255),
    tipo        VARCHAR(20) NOT NULL DEFAULT 'STRING',
    descripcion VARCHAR(255),
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id  BIGINT REFERENCES personas.usuario(id),
    creado_en   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_configuracion_rrhh_clave
    ON rrhh.configuracion_rrhh(clave);

-- ---------------------------------------------------------------------
-- 3. rrhh.liquidacion_concepto — catalogo de conceptos de liquidacion
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.liquidacion_concepto (
    id                BIGSERIAL PRIMARY KEY,
    codigo            VARCHAR(50) NOT NULL UNIQUE,
    descripcion       VARCHAR(255),
    es_haber          BOOLEAN NOT NULL DEFAULT TRUE,
    es_calculado_auto BOOLEAN NOT NULL DEFAULT FALSE,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id        BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_liquidacion_concepto_codigo
    ON rrhh.liquidacion_concepto(codigo);

-- ---------------------------------------------------------------------
-- 4. Seeds — conceptos de liquidacion (idempotente)
-- ---------------------------------------------------------------------
INSERT INTO rrhh.liquidacion_concepto (codigo, descripcion, es_haber, es_calculado_auto) VALUES
    ('SALARIO_BASE',       'SALARIO BASE',         TRUE,  TRUE),
    ('HORA_EXTRA',         'HORA EXTRA',           TRUE,  TRUE),
    ('BONO_MANUAL',        'BONO MANUAL',          TRUE,  FALSE),
    ('AGUINALDO',          'AGUINALDO',            TRUE,  TRUE),
    ('COMISION',           'COMISION',             TRUE,  TRUE),
    ('VACACION_VENTA',     'VENTA DE VACACIONES',  TRUE,  FALSE),
    ('IPS_DESCUENTO',      'DESCUENTO IPS',        FALSE, TRUE),
    ('ADELANTO_DESCUENTO', 'DESCUENTO ADELANTO',   FALSE, TRUE),
    ('VALE_DESCUENTO',     'DESCUENTO VALE',       FALSE, TRUE),
    ('PENALIZACION',       'PENALIZACION',         FALSE, TRUE),
    ('PRESTAMO_CUOTA',     'CUOTA PRESTAMO',       FALSE, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5. Seeds — configuracion RRHH (parametros, idempotente)
-- ---------------------------------------------------------------------
INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion) VALUES
    ('IPS_PORCENTAJE_FUNCIONARIO',            '9',    'NUMBER',  'Aporte IPS del funcionario (descuento)'),
    ('IPS_PORCENTAJE_PATRONAL',               '16.5', 'NUMBER',  'Aporte patronal IPS (reporte)'),
    ('SALARIO_MINIMO_LEGAL_PYG',              '2798309', 'NUMBER', 'Salario minimo legal PYG'),
    ('DIAS_VACACIONES_HASTA_5A',              '12',   'NUMBER',  'Dias de vacaciones hasta 5 anios de antiguedad'),
    ('DIAS_VACACIONES_5_10A',                 '18',   'NUMBER',  'Dias de vacaciones de 5 a 10 anios'),
    ('DIAS_VACACIONES_MAS_10A',               '30',   'NUMBER',  'Dias de vacaciones mas de 10 anios'),
    ('INDEMNIZACION_DIAS_POR_ANIO',           '15',   'NUMBER',  'Dias de indemnizacion por anio (despido injustificado)'),
    ('INDEMNIZACION_ANTIGUEDAD_MIN_DIAS',     '90',   'NUMBER',  'Antiguedad minima en dias para indemnizacion'),
    ('RECARGO_HE_DIURNA',                     '50',   'NUMBER',  'Porcentaje de recargo hora extra diurna'),
    ('RECARGO_HE_NOCTURNA',                   '100',  'NUMBER',  'Porcentaje de recargo hora extra nocturna'),
    ('RECARGO_HE_FERIADO',                    '100',  'NUMBER',  'Porcentaje de recargo hora extra en feriado'),
    ('TOLERANCIA_TARDANZA_MIN',               '5',    'NUMBER',  'Tolerancia de tardanza en minutos'),
    ('PRESCRIPCION_VACACIONES_MESES',         '24',   'NUMBER',  'Meses para prescripcion de vacaciones'),
    ('DIA_CIERRE_MES',                        '30',   'NUMBER',  'Dia de cierre mensual de liquidacion'),
    ('MES_AGUINALDO',                         '12',   'NUMBER',  'Mes de pago del aguinaldo'),
    ('PENALIZACION_AUTO_TARDANZA',            'true', 'BOOLEAN', 'Generar penalizacion automatica por tardanza'),
    ('PENALIZACION_MONTO_TARDANZA',           '0',    'NUMBER',  'Monto fijo de penalizacion por tardanza'),
    ('PENALIZACION_MONTO_POR_MINUTO_TARDANZA','0',    'NUMBER',  'Monto de penalizacion por minuto de tardanza'),
    ('HORAS_JORNADA_LABORAL',                 '8',    'NUMBER',  'Horas de la jornada laboral (base de valorizacion de HE)')
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- 6. Seeds — roles RRHH (idempotente por nombre)
-- ---------------------------------------------------------------------
-- Nombres con espacios para respetar la convencion existente de
-- personas.role (ej: 'VER FUNCIONARIOS'), asi el desktop los matchea.
INSERT INTO personas.role (nombre, creado_en)
SELECT r.nombre, now()
FROM (VALUES
    ('RRHH VER'),
    ('RRHH GESTIONAR'),
    ('RRHH LIQUIDAR'),
    ('RRHH APROBAR'),
    ('RRHH PAGAR'),
    ('RRHH CONFIG'),
    ('COMISION GESTIONAR'),
    ('COMISION APROBAR')
) AS r(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre
);
