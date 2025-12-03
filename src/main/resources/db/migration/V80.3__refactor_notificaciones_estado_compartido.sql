ALTER TABLE configuraciones.notificacion
    ADD COLUMN IF NOT EXISTS estado_tablero VARCHAR(50) DEFAULT 'POR_VERIFICAR',
    ADD COLUMN IF NOT EXISTS verificado_por_usuario_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS fecha_verificacion TIMESTAMP,
    ADD COLUMN IF NOT EXISTS usuario_creador_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_notificacion_estado_tablero 
    ON configuraciones.notificacion(estado_tablero);

ALTER TABLE IF EXISTS configuraciones.notificacion_usuario 
    RENAME TO notificacion_usuario_old;

CREATE TABLE IF NOT EXISTS configuraciones.notificacion_destinatario (
    id BIGSERIAL PRIMARY KEY,
    notificacion_id BIGINT NOT NULL REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES personas.usuario(id) ON DELETE CASCADE,
    leida BOOLEAN DEFAULT FALSE,
    fecha_leida TIMESTAMP,
    fecha_entrega TIMESTAMP,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notificacion_destinatario UNIQUE(notificacion_id, usuario_id)
);

CREATE INDEX IF NOT EXISTS idx_notif_dest_usuario 
    ON configuraciones.notificacion_destinatario(usuario_id);
CREATE INDEX IF NOT EXISTS idx_notif_dest_notificacion 
    ON configuraciones.notificacion_destinatario(notificacion_id);
CREATE INDEX IF NOT EXISTS idx_notif_dest_leida 
    ON configuraciones.notificacion_destinatario(leida);
CREATE INDEX IF NOT EXISTS idx_notif_dest_usuario_leida 
    ON configuraciones.notificacion_destinatario(usuario_id, leida);

CREATE TABLE IF NOT EXISTS configuraciones.notificacion_role (
    id BIGSERIAL PRIMARY KEY,
    notificacion_id BIGINT NOT NULL REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES personas.role(id) ON DELETE CASCADE,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notificacion_role UNIQUE(notificacion_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_notif_role_notificacion 
    ON configuraciones.notificacion_role(notificacion_id);
CREATE INDEX IF NOT EXISTS idx_notif_role_role 
    ON configuraciones.notificacion_role(role_id);

CREATE TABLE IF NOT EXISTS configuraciones.notificacion_envio_log (
    id BIGSERIAL PRIMARY KEY,
    notificacion_id BIGINT NOT NULL REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES personas.usuario(id) ON DELETE SET NULL,
    token_fcm VARCHAR(500),
    estado_envio VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    mensaje_error TEXT,
    fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notif_log_notificacion 
    ON configuraciones.notificacion_envio_log(notificacion_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_usuario 
    ON configuraciones.notificacion_envio_log(usuario_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_estado 
    ON configuraciones.notificacion_envio_log(estado_envio);

UPDATE configuraciones.notificacion n
SET estado_tablero = COALESCE(
    (
        SELECT nu.estado_tablero
        FROM configuraciones.notificacion_usuario_old nu
        WHERE nu.notificacion_id = n.id
        AND nu.estado_tablero IS NOT NULL
        ORDER BY 
            CASE nu.estado_tablero
                WHEN 'VERIFICADO' THEN 3
                WHEN 'EN_PROCESO' THEN 2
                WHEN 'POR_VERIFICAR' THEN 1
                ELSE 0
            END DESC,
            nu.actualizado_en DESC
        LIMIT 1
    ),
    'POR_VERIFICAR'
);

INSERT INTO configuraciones.notificacion_destinatario 
    (notificacion_id, usuario_id, leida, fecha_leida, fecha_entrega, creado_en, actualizado_en)
SELECT DISTINCT ON (notificacion_id, usuario_id)
    notificacion_id,
    usuario_id,
    COALESCE(leida, FALSE) as leida,
    fecha_leida,
    fecha_entrega,
    creado_en,
    actualizado_en
FROM configuraciones.notificacion_usuario_old
WHERE usuario_id IS NOT NULL
ORDER BY notificacion_id, usuario_id, 
    leida DESC NULLS LAST,
    actualizado_en DESC
ON CONFLICT (notificacion_id, usuario_id) DO NOTHING;

INSERT INTO configuraciones.notificacion_envio_log 
    (notificacion_id, usuario_id, token_fcm, estado_envio, mensaje_error, fecha_envio, fecha_entrega)
SELECT 
    notificacion_id,
    usuario_id,
    token_fcm,
    estado_envio,
    mensaje_error,
    fecha_envio,
    fecha_entrega
FROM configuraciones.notificacion_usuario_old
WHERE token_fcm IS NOT NULL;
