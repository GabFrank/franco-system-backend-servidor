-- V82__create_evento_inutilizacion_de.sql
-- Script de migración para crear tabla evento_inutilizacion_de
-- Esta tabla almacena los eventos de inutilización de números enviados a SIFEN

-- =====================================================
-- PASO 1: Crear tabla evento_inutilizacion_de
-- =====================================================
CREATE TABLE financiero.evento_inutilizacion_de (
    id BIGSERIAL,
    sucursal_id BIGINT NOT NULL,
    timbrado_id BIGINT NOT NULL,
    timbrado_detalle_id BIGINT,
    evento_id VARCHAR(255) NOT NULL,
    fecha_firma TIMESTAMP WITH TIME ZONE NOT NULL,
    establecimiento VARCHAR(10) NOT NULL,
    punto_expedicion VARCHAR(10) NOT NULL,
    numero_inicio INTEGER NOT NULL,
    numero_fin INTEGER NOT NULL,
    tipo_de VARCHAR(50) NOT NULL,
    motivo_inutilizacion TEXT NOT NULL,
    estado financiero.estado_evento_enum NOT NULL DEFAULT 'PENDIENTE',
    fecha_procesamiento TIMESTAMP WITH TIME ZONE,
    protocolo_autorizacion VARCHAR(100),
    codigo_respuesta VARCHAR(10),
    mensaje_respuesta TEXT,
    respuesta_bruta TEXT,
    xml_evento TEXT,
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    CONSTRAINT evento_inutilizacion_de_pkey PRIMARY KEY (id, sucursal_id),
    
    CONSTRAINT fk_evento_inutilizacion_de_sucursal 
        FOREIGN KEY (sucursal_id) 
        REFERENCES empresarial.sucursal(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_inutilizacion_de_timbrado 
        FOREIGN KEY (timbrado_id) 
        REFERENCES financiero.timbrado(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_inutilizacion_de_timbrado_detalle 
        FOREIGN KEY (timbrado_detalle_id, sucursal_id) 
        REFERENCES financiero.timbrado_detalle(id, sucursal_id) 
        ON DELETE SET NULL,
    
    CONSTRAINT fk_evento_inutilizacion_de_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id) 
        ON DELETE SET NULL,
    
    CONSTRAINT chk_evento_inutilizacion_numero_rango 
        CHECK (numero_inicio <= numero_fin)
);

-- =====================================================
-- PASO 2: Crear índices para búsquedas frecuentes
-- =====================================================
CREATE INDEX idx_evento_inutilizacion_de_sucursal_id 
    ON financiero.evento_inutilizacion_de(sucursal_id);

CREATE INDEX idx_evento_inutilizacion_de_timbrado_id 
    ON financiero.evento_inutilizacion_de(timbrado_id);

CREATE INDEX idx_evento_inutilizacion_de_timbrado_detalle_id 
    ON financiero.evento_inutilizacion_de(timbrado_detalle_id);

CREATE INDEX idx_evento_inutilizacion_de_estado 
    ON financiero.evento_inutilizacion_de(estado);

CREATE INDEX idx_evento_inutilizacion_de_evento_id 
    ON financiero.evento_inutilizacion_de(evento_id);

CREATE INDEX idx_evento_inutilizacion_de_creado_en 
    ON financiero.evento_inutilizacion_de(creado_en DESC);

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
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_inutilizacion_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
            EXCEPTION WHEN duplicate_object THEN
                -- Ya existe en la publicación, continuar
                NULL;
            WHEN OTHERS THEN
                NULL;
            END;
        END IF;
    END LOOP;
END $$;

