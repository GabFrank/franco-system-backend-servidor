-- V71__sifen_implementacion_completa.sql
-- Script de migración para implementación completa de facturación electrónica SIFEN

-- =====================================================
-- PASO 1: Crear enum estado_evento_enum
-- =====================================================
CREATE TYPE financiero.estado_evento_enum AS ENUM (
    'PENDIENTE',
    'APROBADO',
    'RECHAZADO',
    'ERROR_ENVIO'
);

-- =====================================================
-- PASO 2: Modificar tabla documento_electronico
-- =====================================================

-- Hacer NOT NULL las columnas sucursal_id y factura_legal_id
-- (la tabla está vacía según confirmación del usuario)
ALTER TABLE financiero.documento_electronico 
    ALTER COLUMN sucursal_id SET NOT NULL;

ALTER TABLE financiero.documento_electronico 
    ALTER COLUMN factura_legal_id SET NOT NULL;

-- Agregar constraint UNIQUE en factura_legal_id
ALTER TABLE financiero.documento_electronico 
    ADD CONSTRAINT uk_documento_electronico_factura_legal UNIQUE (factura_legal_id);

-- =====================================================
-- PASO 3: Modificar tabla lote_de
-- =====================================================

-- Agregar columna aprobado
ALTER TABLE financiero.lote_de 
    ADD COLUMN aprobado BOOLEAN;

-- =====================================================
-- PASO 4: Crear tabla evento_cancelacion_de
-- =====================================================
CREATE TABLE financiero.evento_cancelacion_de (
    id BIGSERIAL PRIMARY KEY,
    documento_electronico_id BIGINT NOT NULL,
    evento_id VARCHAR(255) NOT NULL,
    fecha_firma TIMESTAMP WITH TIME ZONE NOT NULL,
    cdc_documento VARCHAR(44) NOT NULL,
    motivo_cancelacion TEXT,
    xml_evento TEXT,
    estado financiero.estado_evento_enum NOT NULL DEFAULT 'PENDIENTE',
    fecha_procesamiento TIMESTAMP WITH TIME ZONE,
    protocolo_autorizacion VARCHAR(100),
    codigo_respuesta VARCHAR(10),
    mensaje_respuesta TEXT,
    respuesta_bruta TEXT,
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    CONSTRAINT fk_evento_cancelacion_documento 
        FOREIGN KEY (documento_electronico_id) 
        REFERENCES financiero.documento_electronico(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_cancelacion_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id) 
        ON DELETE SET NULL
);

-- Índices para evento_cancelacion_de
CREATE INDEX idx_evento_cancelacion_documento 
    ON financiero.evento_cancelacion_de(documento_electronico_id);

CREATE INDEX idx_evento_cancelacion_estado 
    ON financiero.evento_cancelacion_de(estado);

CREATE INDEX idx_evento_cancelacion_cdc 
    ON financiero.evento_cancelacion_de(cdc_documento);

-- =====================================================
-- PASO 5: Crear tabla evento_nominacion_de
-- =====================================================
CREATE TABLE financiero.evento_nominacion_de (
    id BIGSERIAL PRIMARY KEY,
    documento_electronico_id BIGINT NOT NULL,
    evento_id VARCHAR(50),
    fecha_firma TIMESTAMP WITH TIME ZONE,
    cdc_documento VARCHAR(50),
    cliente_id BIGINT,
    nombre_receptor VARCHAR(255),
    documento_receptor VARCHAR(50),
    tipo_receptor VARCHAR(50),
    total_factura NUMERIC,
    fecha_emision TIMESTAMP WITH TIME ZONE,
    fecha_recepcion TIMESTAMP WITH TIME ZONE,
    xml_evento TEXT,
    estado financiero.estado_evento_enum,
    fecha_procesamiento TIMESTAMP WITH TIME ZONE,
    protocolo_autorizacion VARCHAR(50),
    codigo_respuesta VARCHAR(10),
    mensaje_respuesta TEXT,
    respuesta_bruta TEXT,
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    CONSTRAINT fk_evento_nominacion_documento 
        FOREIGN KEY (documento_electronico_id) 
        REFERENCES financiero.documento_electronico(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_nominacion_cliente 
        FOREIGN KEY (cliente_id) 
        REFERENCES personas.cliente(id) 
        ON DELETE SET NULL,
    
    CONSTRAINT fk_evento_nominacion_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id) 
        ON DELETE SET NULL
);

-- Índices para evento_nominacion_de
CREATE INDEX idx_evento_nominacion_documento 
    ON financiero.evento_nominacion_de(documento_electronico_id);

CREATE INDEX idx_evento_nominacion_estado 
    ON financiero.evento_nominacion_de(estado);

CREATE INDEX idx_evento_nominacion_cdc 
    ON financiero.evento_nominacion_de(cdc_documento);

CREATE INDEX idx_evento_nominacion_cliente 
    ON financiero.evento_nominacion_de(cliente_id);

