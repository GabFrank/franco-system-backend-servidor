-- V83__create_evento_inutilizacion_de_documento_electronico.sql
-- Script de migración para crear tabla intermedia que vincula
-- eventos de inutilización con documentos electrónicos afectados

-- =====================================================
-- PASO 1: Crear tabla evento_inutilizacion_de_documento_electronico
-- =====================================================
CREATE TABLE financiero.evento_inutilizacion_de_documento_electronico (
    id BIGSERIAL PRIMARY KEY,
    evento_inutilizacion_de_id BIGINT NOT NULL,
    evento_inutilizacion_de_sucursal_id BIGINT NOT NULL,
    documento_electronico_id BIGINT NOT NULL,
    documento_electronico_sucursal_id BIGINT NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_evento_inutilizacion_de_doc_evento 
        FOREIGN KEY (evento_inutilizacion_de_id, evento_inutilizacion_de_sucursal_id) 
        REFERENCES financiero.evento_inutilizacion_de(id, sucursal_id) 
        ON DELETE CASCADE,
    
    -- Validación: ambas sucursales deben coincidir
    CONSTRAINT chk_evento_inutilizacion_de_doc_sucursal 
        CHECK (evento_inutilizacion_de_sucursal_id = documento_electronico_sucursal_id),
    
    CONSTRAINT fk_evento_inutilizacion_de_doc_documento 
        FOREIGN KEY (documento_electronico_id, documento_electronico_sucursal_id) 
        REFERENCES financiero.documento_electronico(id, sucursal_id) 
        ON DELETE CASCADE,
    
    CONSTRAINT uk_evento_inutilizacion_de_documento 
        UNIQUE (evento_inutilizacion_de_id, evento_inutilizacion_de_sucursal_id, 
                documento_electronico_id, documento_electronico_sucursal_id)
);

-- =====================================================
-- PASO 2: Crear índices para búsquedas frecuentes
-- =====================================================
CREATE INDEX idx_evento_inutilizacion_de_doc_evento 
    ON financiero.evento_inutilizacion_de_documento_electronico(
        evento_inutilizacion_de_id, 
        evento_inutilizacion_de_sucursal_id
    );

CREATE INDEX idx_evento_inutilizacion_de_doc_documento 
    ON financiero.evento_inutilizacion_de_documento_electronico(
        documento_electronico_id, 
        documento_electronico_sucursal_id
    );

-- =====================================================
-- PASO 3: Agregar a publicaciones de replicación
-- =====================================================
DO $$
DECLARE
    v_sucursal_id INTEGER;
    v_pub TEXT;
BEGIN
    FOR v_sucursal_id IN SELECT id FROM empresarial.sucursal WHERE id != 0 ORDER BY id
    LOOP
        v_pub := 'central_filial' || v_sucursal_id || '_pub';
        
        IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = v_pub) THEN
            BEGIN
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_inutilizacion_de_documento_electronico WHERE (evento_inutilizacion_de_sucursal_id = %L OR documento_electronico_sucursal_id = %L)', 
                    v_pub, v_sucursal_id, v_sucursal_id);
            EXCEPTION WHEN duplicate_object THEN
                -- Ya existe en la publicación, continuar
                NULL;
            WHEN OTHERS THEN
                NULL;
            END;
        END IF;
    END LOOP;
END $$;

