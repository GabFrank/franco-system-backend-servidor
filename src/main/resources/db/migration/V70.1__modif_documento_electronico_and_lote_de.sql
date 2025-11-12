-- 1. Crear tipos ENUM para los nuevos estados
CREATE TYPE financiero.estado_de_enum AS ENUM (
    'PENDIENTE',
    'EN_LOTE', 
    'APROBADO',
    'RECHAZADO',
    'CANCELADO'
);

CREATE TYPE financiero.estado_lote_de_enum AS ENUM (
    'PENDIENTE_ENVIO',
    'EN_PROCESO',
    'PROCESADO',
    'PROCESADO_CON_ERRORES',
    'ERROR_ENVIO',
    'ERROR_RED',
    'ERROR_PERMANENTE',
    'RECHAZADO'
);

-- 2. Renombrar tabla lote_dte a lote_de
ALTER TABLE financiero.lote_dte RENAME TO lote_de;

-- 3. Modificar tabla lote_de según nueva estructura
-- Agregar nuevas columnas
ALTER TABLE financiero.lote_de ADD COLUMN fecha_ultimo_intento TIMESTAMPTZ;
ALTER TABLE financiero.lote_de ADD COLUMN intentos INTEGER DEFAULT 0;
ALTER TABLE financiero.lote_de ADD COLUMN actualizado_en TIMESTAMPTZ DEFAULT NOW();

-- Renombrar columnas que mantienen el mismo tipo
ALTER TABLE financiero.lote_de RENAME COLUMN id_protocolo_sifen TO protocolo;
ALTER TABLE financiero.lote_de RENAME COLUMN fecha_envio TO fecha_procesado;

-- Para el estado necesitamos usar nombre temporal porque cambia de VARCHAR a ENUM
ALTER TABLE financiero.lote_de RENAME COLUMN estado_sifen TO estado_temp;
ALTER TABLE financiero.lote_de ADD COLUMN estado financiero.estado_lote_de_enum;

-- Migrar datos del estado temporal al nuevo enum
UPDATE financiero.lote_de SET 
    estado = CASE 
        WHEN estado_temp = 'PENDIENTE' THEN 'PENDIENTE_ENVIO'::financiero.estado_lote_de_enum
        WHEN estado_temp = 'ENVIADO' THEN 'EN_PROCESO'::financiero.estado_lote_de_enum
        WHEN estado_temp = 'PROCESADO' THEN 'PROCESADO'::financiero.estado_lote_de_enum
        WHEN estado_temp = 'ERROR' THEN 'ERROR_ENVIO'::financiero.estado_lote_de_enum
        ELSE 'PENDIENTE_ENVIO'::financiero.estado_lote_de_enum
    END;

-- Eliminar columna temporal
ALTER TABLE financiero.lote_de DROP COLUMN estado_temp;


-- 4. Modificar tabla documento_electronico según nueva estructura
-- Agregar nuevas columnas
ALTER TABLE financiero.documento_electronico ADD COLUMN codigo_respuesta_sifen VARCHAR(10);
ALTER TABLE financiero.documento_electronico ADD COLUMN xml_original TEXT;
ALTER TABLE financiero.documento_electronico ADD COLUMN numero_documento VARCHAR(50);
ALTER TABLE financiero.documento_electronico ADD COLUMN tipo_documento VARCHAR(20);
ALTER TABLE financiero.documento_electronico ADD COLUMN fecha_emision TIMESTAMPTZ;
ALTER TABLE financiero.documento_electronico ADD COLUMN fecha_recepcion_sifen TIMESTAMPTZ;
ALTER TABLE financiero.documento_electronico ADD COLUMN activo BOOLEAN DEFAULT TRUE;
ALTER TABLE financiero.documento_electronico ADD COLUMN actualizado_en TIMESTAMPTZ DEFAULT NOW();

-- Renombrar columnas que mantienen el mismo tipo
ALTER TABLE financiero.documento_electronico RENAME COLUMN lote_id TO lote_de_id;
ALTER TABLE financiero.documento_electronico RENAME COLUMN mensaje_sifen TO mensaje_respuesta_sifen;

-- Para el estado necesitamos usar nombre temporal porque cambia de VARCHAR a ENUM
ALTER TABLE financiero.documento_electronico RENAME COLUMN estado_sifen TO estado_temp;
ALTER TABLE financiero.documento_electronico ADD COLUMN estado financiero.estado_de_enum DEFAULT 'PENDIENTE';

-- Migrar datos del estado temporal al nuevo enum
UPDATE financiero.documento_electronico SET 
    estado = CASE 
        WHEN estado_temp = 'PENDIENTE' THEN 'PENDIENTE'::financiero.estado_de_enum
        WHEN estado_temp = 'APROBADO' THEN 'APROBADO'::financiero.estado_de_enum
        WHEN estado_temp = 'RECHAZADO' THEN 'RECHAZADO'::financiero.estado_de_enum
        WHEN estado_temp = 'CANCELADO' THEN 'CANCELADO'::financiero.estado_de_enum
        ELSE 'PENDIENTE'::financiero.estado_de_enum
    END;

-- Eliminar columna temporal
ALTER TABLE financiero.documento_electronico DROP COLUMN estado_temp;

-- 5. Actualizar foreign keys
-- Eliminar foreign key existente
ALTER TABLE financiero.documento_electronico DROP CONSTRAINT IF EXISTS documento_electronico_lote_fk;

-- Agregar nueva foreign key
ALTER TABLE financiero.documento_electronico 
ADD CONSTRAINT documento_electronico_lote_de_fk 
FOREIGN KEY (lote_de_id) REFERENCES financiero.lote_de(id) ON DELETE SET NULL;

-- 6. Actualizar índices
-- Eliminar índices existentes
DROP INDEX IF EXISTS financiero.idx_documento_electronico_estado;
DROP INDEX IF EXISTS financiero.idx_documento_electronico_lote_id;
DROP INDEX IF EXISTS financiero.idx_lote_dte_estado;

-- Crear nuevos índices
CREATE INDEX IF NOT EXISTS idx_documento_electronico_estado ON financiero.documento_electronico (estado);
CREATE INDEX IF NOT EXISTS idx_documento_electronico_lote_de_id ON financiero.documento_electronico (lote_de_id);
CREATE INDEX IF NOT EXISTS idx_documento_electronico_fecha_emision ON financiero.documento_electronico (fecha_emision);
CREATE INDEX IF NOT EXISTS idx_documento_electronico_activo ON financiero.documento_electronico (activo);

CREATE INDEX IF NOT EXISTS idx_lote_de_estado ON financiero.lote_de (estado);
CREATE INDEX IF NOT EXISTS idx_lote_de_fecha_procesado ON financiero.lote_de (fecha_procesado);
CREATE INDEX IF NOT EXISTS idx_lote_de_intentos ON financiero.lote_de (intentos);

