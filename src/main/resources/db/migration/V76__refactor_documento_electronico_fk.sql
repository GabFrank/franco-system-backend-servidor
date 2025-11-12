-- V76__refactor_documento_electronico_fk.sql
-- Objetivo: Eliminar la columna redundante factura_legal_sucursal_id y usar la columna sucursal_id existente
-- para la clave foránea a factura_legal.

-- =================================================================================================
-- PASO 1: Eliminar las restricciones existentes que usan factura_legal_sucursal_id
-- =================================================================================================
-- Eliminar la clave foránea
ALTER TABLE financiero.documento_electronico
    DROP CONSTRAINT IF EXISTS fk_documento_electronico_factura_legal;

-- Eliminar la restricción de unicidad
ALTER TABLE financiero.documento_electronico
    DROP CONSTRAINT IF EXISTS uk_documento_electronico_factura_legal;

-- =================================================================================================
-- PASO 2: (Opcional pero recomendado) Verificar la consistencia de los datos antes de eliminar la columna
-- Esta consulta no debería devolver ninguna fila. Si lo hace, hay una inconsistencia.
-- SELECT id, sucursal_id, factura_legal_sucursal_id
-- FROM financiero.documento_electronico
-- WHERE sucursal_id <> factura_legal_sucursal_id;
-- =================================================================================================


-- =================================================================================================
-- PASO 3: Eliminar la columna redundante
-- =================================================================================================
ALTER TABLE financiero.documento_electronico
    DROP COLUMN IF EXISTS factura_legal_sucursal_id;

-- =================================================================================================
-- PASO 4: Crear la nueva clave foránea usando la columna sucursal_id existente
-- =================================================================================================
ALTER TABLE financiero.documento_electronico
    ADD CONSTRAINT fk_documento_electronico_factura_legal
    FOREIGN KEY (factura_legal_id, sucursal_id)
    REFERENCES financiero.factura_legal(id, sucursal_id)
    ON DELETE CASCADE;

-- =================================================================================================
-- PASO 5: Recrear la restricción de unicidad con las columnas correctas
-- =================================================================================================
ALTER TABLE financiero.documento_electronico
    ADD CONSTRAINT uk_documento_electronico_factura_legal
    UNIQUE (factura_legal_id, sucursal_id);

