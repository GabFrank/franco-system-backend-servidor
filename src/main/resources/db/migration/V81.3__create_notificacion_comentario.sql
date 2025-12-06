CREATE TABLE IF NOT EXISTS configuraciones.notificacion_comentario (
    id BIGSERIAL PRIMARY KEY,
    notificacion_id BIGINT NOT NULL REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES personas.usuario(id) ON DELETE CASCADE,
    comentario TEXT NOT NULL,
    comentario_padre_id BIGINT REFERENCES configuraciones.notificacion_comentario(id) ON DELETE CASCADE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_comentario_notificacion 
    ON configuraciones.notificacion_comentario(notificacion_id);

CREATE INDEX IF NOT EXISTS idx_notif_comentario_usuario 
    ON configuraciones.notificacion_comentario(usuario_id);

CREATE INDEX IF NOT EXISTS idx_notif_comentario_padre 
    ON configuraciones.notificacion_comentario(comentario_padre_id);

CREATE INDEX IF NOT EXISTS idx_notif_comentario_creado 
    ON configuraciones.notificacion_comentario(creado_en);

CREATE INDEX IF NOT EXISTS idx_notif_comentario_notif_creado 
    ON configuraciones.notificacion_comentario(notificacion_id, creado_en);

COMMENT ON TABLE configuraciones.notificacion_comentario IS 'Comentarios asociados a notificaciones del sistema';
COMMENT ON COLUMN configuraciones.notificacion_comentario.notificacion_id IS 'Referencia a la notificación';
COMMENT ON COLUMN configuraciones.notificacion_comentario.usuario_id IS 'Usuario que creó el comentario';
COMMENT ON COLUMN configuraciones.notificacion_comentario.comentario IS 'Contenido del comentario';
COMMENT ON COLUMN configuraciones.notificacion_comentario.comentario_padre_id IS 'Referencia al comentario padre si es una respuesta';

