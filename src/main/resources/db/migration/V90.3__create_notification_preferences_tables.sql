
-- Tabla para definir qué tipos de notificaciones están habilitadas para cada rol
CREATE TABLE configuraciones.notificacion_tipo_role (
    id bigserial NOT NULL,
    role_id int8 NOT NULL,
    tipo_notificacion varchar(100) NOT NULL,
    descripcion varchar(255),
    es_obligatorio boolean DEFAULT false NOT NULL, -- Si es true, el usuario no puede desactivarla
    creado_en timestamp DEFAULT now(),
    
    CONSTRAINT notificacion_tipo_role_pk PRIMARY KEY (id),
    CONSTRAINT fk_notificacion_tipo_role_role FOREIGN KEY (role_id) REFERENCES personas.role(id),
    CONSTRAINT uk_notificacion_tipo_role UNIQUE (role_id, tipo_notificacion)
);

-- Tabla para guardar las preferencias de cada usuario
CREATE TABLE configuraciones.notificacion_preferencia_usuario (
    id bigserial NOT NULL,
    usuario_id int8 NOT NULL,
    tipo_notificacion varchar(100) NOT NULL,
    habilitado boolean DEFAULT true NOT NULL,
    creado_en timestamp DEFAULT now(),
    actualizado_en timestamp DEFAULT now(),
    
    CONSTRAINT notificacion_preferencia_usuario_pk PRIMARY KEY (id),
    CONSTRAINT fk_notif_pref_usu_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id),
    CONSTRAINT uk_notif_pref_usu_usuario_tipo UNIQUE (usuario_id, tipo_notificacion)
);

-- Índices para optimizar las consultas frecuentes
CREATE INDEX idx_notif_tipo_role_role_id ON configuraciones.notificacion_tipo_role(role_id);
CREATE INDEX idx_notif_pref_usu_usuario_id ON configuraciones.notificacion_preferencia_usuario(usuario_id);
