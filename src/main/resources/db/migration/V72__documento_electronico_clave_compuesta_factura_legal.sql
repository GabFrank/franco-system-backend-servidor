-- V72__documento_electronico_clave_compuesta_factura_legal.sql
-- Corrección de la relación entre documento_electronico y factura_legal
-- para soportar la clave primaria compuesta de factura_legal (id + sucursal_id)

-- =====================================================
-- PASO 1: Eliminar constraint UNIQUE anterior (solo factura_legal_id)
-- =====================================================
ALTER TABLE financiero.documento_electronico 
    DROP CONSTRAINT IF EXISTS uk_documento_electronico_factura_legal;

-- =====================================================
-- PASO 2: Agregar columna factura_legal_sucursal_id
-- =====================================================
ALTER TABLE financiero.documento_electronico 
    ADD COLUMN IF NOT EXISTS factura_legal_sucursal_id BIGINT;

-- =====================================================
-- PASO 3: Actualizar datos existentes (si los hay)
-- =====================================================
-- Copiar el sucursal_id de cada documento a la nueva columna
UPDATE financiero.documento_electronico 
SET factura_legal_sucursal_id = sucursal_id 
WHERE factura_legal_sucursal_id IS NULL;

-- =====================================================
-- PASO 4: Hacer NOT NULL la nueva columna
-- =====================================================
ALTER TABLE financiero.documento_electronico 
    ALTER COLUMN factura_legal_sucursal_id SET NOT NULL;

-- =====================================================
-- PASO 5: Agregar constraint UNIQUE en la combinación
-- =====================================================
ALTER TABLE financiero.documento_electronico 
    ADD CONSTRAINT uk_documento_electronico_factura_legal 
    UNIQUE (factura_legal_id, factura_legal_sucursal_id);

-- =====================================================
-- PASO 6: Agregar foreign key constraint para FacturaLegal
-- =====================================================
ALTER TABLE financiero.documento_electronico 
    ADD CONSTRAINT fk_documento_electronico_factura_legal 
    FOREIGN KEY (factura_legal_id, factura_legal_sucursal_id) 
    REFERENCES financiero.factura_legal(id, sucursal_id) 
    ON DELETE CASCADE;

-- =====================================================
-- COMENTARIOS
-- =====================================================
-- Esta migración corrige el mapeo JPA entre DocumentoElectronico y FacturaLegal.
-- FacturaLegal tiene una clave primaria compuesta (id, sucursal_id), por lo que
-- la relación debe usar @JoinColumns con ambas columnas.

