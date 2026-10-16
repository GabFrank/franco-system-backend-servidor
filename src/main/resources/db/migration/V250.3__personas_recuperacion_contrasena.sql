-- =====================================================================
-- Recuperacion de contrasena ("¿Olvido su contrasena?" del login)
-- =====================================================================
-- recuperacion_contrasena: una fila por pedido de recuperacion.
--   CORREO  el usuario tiene correo: se le manda un codigo de 6 digitos.
--   RRHH    no tiene correo: la solicitud la aprueba RRHH y el usuario la
--           termina con el codigo secreto que vio en pantalla al crearla.
--   Los codigos se guardan con BCrypt, nunca en claro.
--
-- recuperacion_contrasena_intento: cada verificacion de identidad
--   (usuario + cedula + fecha de nacimiento), para bloquear la fuerza bruta
--   por usuario y por IP.
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- La contrasena nueva baja a las filiales por la replicacion de personas.usuario.
-- =====================================================================

CREATE TABLE IF NOT EXISTS personas.recuperacion_contrasena (
    id              bigserial PRIMARY KEY,
    usuario_id      bigint NOT NULL REFERENCES personas.usuario (id),
    tipo            varchar(10) NOT NULL,
    estado          varchar(15) NOT NULL,
    email           varchar(255),
    telefono        varchar(50),
    observacion     text,
    codigo_hash     varchar(100) NOT NULL,
    intentos        integer NOT NULL DEFAULT 0,
    ip              varchar(64),
    vence_en        timestamp NOT NULL,
    resuelto_por_id bigint REFERENCES personas.usuario (id),
    resuelto_en     timestamp,
    motivo_rechazo  text,
    completado_en   timestamp,
    creado_en       timestamp NOT NULL DEFAULT now(),
    CONSTRAINT ck_recuperacion_contrasena_tipo CHECK (tipo IN ('CORREO', 'RRHH')),
    CONSTRAINT ck_recuperacion_contrasena_estado
        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'COMPLETADA', 'ANULADA'))
);

CREATE INDEX IF NOT EXISTS idx_recuperacion_contrasena_usuario
    ON personas.recuperacion_contrasena (usuario_id, creado_en);

CREATE INDEX IF NOT EXISTS idx_recuperacion_contrasena_estado
    ON personas.recuperacion_contrasena (tipo, estado, creado_en);

CREATE TABLE IF NOT EXISTS personas.recuperacion_contrasena_intento (
    id        bigserial PRIMARY KEY,
    nickname  varchar(100) NOT NULL,
    ip        varchar(64),
    exito     boolean NOT NULL,
    creado_en timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recuperacion_intento_nickname
    ON personas.recuperacion_contrasena_intento (nickname, creado_en);

CREATE INDEX IF NOT EXISTS idx_recuperacion_intento_ip
    ON personas.recuperacion_contrasena_intento (ip, creado_en);
