-- =====================================================================
-- Tokens de servicio para bots (issue #579, fase 1)
-- =====================================================================
-- Credencial opaca por bot ("frcsvc_..."), con vencimiento obligatorio y
-- revocable una por una. Se guarda solo el SHA-256 del token; el token en
-- claro se muestra una vez, al emitirlo. Cada token esta ligado a un usuario:
-- el request queda autenticado con ese usuario y aplican sus roles.
--
--   escribe  TokenServicioService (crearTokenServicio / revocarTokenServicio)
--   lee      TokenServicioValidator (cada request con "Token frcsvc_...")
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- Los hashes no tienen que salir del central.
--
-- Rollback: la version anterior del backend ignora la tabla. Para apagar todos
-- los tokens sin desplegar:
--   UPDATE personas.token_servicio SET revocado_en = now() WHERE revocado_en IS NULL;
-- =====================================================================

CREATE TABLE IF NOT EXISTS personas.token_servicio (
    id             bigserial PRIMARY KEY,
    usuario_id     bigint NOT NULL REFERENCES personas.usuario (id) ON DELETE CASCADE,
    nombre         varchar(80) NOT NULL,
    prefijo        varchar(12) NOT NULL,
    hash           char(64) NOT NULL,
    creado_por     bigint REFERENCES personas.usuario (id) ON DELETE SET NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    expira_en      timestamptz NOT NULL,
    revocado_en    timestamptz,
    revocado_por   bigint REFERENCES personas.usuario (id) ON DELETE SET NULL,
    ultimo_uso_en  timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_token_servicio_hash
    ON personas.token_servicio (hash);
CREATE INDEX IF NOT EXISTS idx_token_servicio_usuario
    ON personas.token_servicio (usuario_id);
