CREATE TABLE IF NOT EXISTS configuraciones.modificacion_registro (
    id BIGSERIAL PRIMARY KEY,
    
    tipo_entidad VARCHAR(100) NOT NULL,
    entidad_id BIGINT NOT NULL,
    entidad_sucursal_id BIGINT,
    schema_nombre VARCHAR(50) NOT NULL,
    tabla_nombre VARCHAR(100) NOT NULL,
    
    tipo_operacion VARCHAR(20) NOT NULL,
    
    usuario_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL,
    sucursal_id BIGINT REFERENCES empresarial.sucursal(id) ON DELETE SET NULL,
    
    modificado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    ip_address VARCHAR(45),
    user_agent TEXT,
    observacion TEXT,
    
    activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS configuraciones.modificacion_detalle (
    id BIGSERIAL PRIMARY KEY,
    
    modificacion_registro_id BIGINT NOT NULL REFERENCES configuraciones.modificacion_registro(id) ON DELETE CASCADE,
    
    campo_nombre VARCHAR(100) NOT NULL,
    campo_tipo VARCHAR(50),
    
    valor_anterior TEXT,
    valor_nuevo TEXT,
    
    valor_anterior_id BIGINT,
    valor_nuevo_id BIGINT,
    
    orden INTEGER NOT NULL DEFAULT 1,
    
    es_campo_sensible BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_modificacion_tipo_entidad 
    ON configuraciones.modificacion_registro(tipo_entidad);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_entidad_id 
    ON configuraciones.modificacion_registro(entidad_id, tipo_entidad);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_usuario 
    ON configuraciones.modificacion_registro(usuario_id);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_fecha 
    ON configuraciones.modificacion_registro(modificado_en);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_sucursal 
    ON configuraciones.modificacion_registro(sucursal_id);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_activo 
    ON configuraciones.modificacion_registro(activo) WHERE activo = TRUE;
    
CREATE INDEX IF NOT EXISTS idx_modificacion_tipo_operacion 
    ON configuraciones.modificacion_registro(tipo_operacion);
    
CREATE INDEX IF NOT EXISTS idx_modificacion_entidad_completa 
    ON configuraciones.modificacion_registro(tipo_entidad, entidad_id, entidad_sucursal_id);

CREATE INDEX IF NOT EXISTS idx_detalle_modificacion 
    ON configuraciones.modificacion_detalle(modificacion_registro_id);
    
CREATE INDEX IF NOT EXISTS idx_detalle_campo 
    ON configuraciones.modificacion_detalle(campo_nombre);
    
CREATE INDEX IF NOT EXISTS idx_detalle_sensible 
    ON configuraciones.modificacion_detalle(es_campo_sensible) WHERE es_campo_sensible = TRUE;