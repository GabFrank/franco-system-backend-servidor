-- =====================================================================
-- Eventos por webhook en el central (srv #587, fase C1)
-- =====================================================================
-- Tablas locales del nodo (eventos.*): las mismas de la filial (V111.3 y
-- V111.4), para que el paquete com.franco.dev.eventos sea uno solo.
--
-- configuraciones.webhook_suscripcion: el central la escribe y la reparte
-- MAIN_TO_ALL a las filiales, que tienen un espejo con las MISMAS columnas
-- (V111.3 + V111.4). Aca suma FK, CHECK, defaults y el trigger de
-- config_version. El secreto no esta en la tabla: secreto_ref es un nombre
-- y el valor esta en el overlay de cada nodo.
--
-- Las dos filas de replication_table van con enabled=false: si se publica
-- webhook_suscripcion antes de que las filiales tengan el espejo, se corta
-- su replica. Se prenden en la operacion 1c (cicd #39), con checklist.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS eventos;
CREATE SCHEMA IF NOT EXISTS configuraciones;

-- Consumidores: el sistema que recibe (Loom, el backend de un cliente...).
-- No se replica.
CREATE TABLE IF NOT EXISTS configuraciones.eventos_consumidor (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR     NOT NULL,
    contacto            VARCHAR,
    permite_personales  BOOLEAN     NOT NULL DEFAULT false,
    permite_sensibles   BOOLEAN     NOT NULL DEFAULT false,
    max_suscripciones   INTEGER     NOT NULL DEFAULT 10,
    activo              BOOLEAN     NOT NULL DEFAULT true,
    creado_por          BIGINT,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS configuraciones.webhook_suscripcion (
    id                BIGSERIAL PRIMARY KEY,
    consumidor_id     BIGINT REFERENCES configuraciones.eventos_consumidor (id),
    nombre            VARCHAR,
    url               VARCHAR,
    eventos           TEXT[],
    eventos_pausados  TEXT[],
    sucursal_ids      BIGINT[],
    umbrales          JSONB,
    contenido         VARCHAR,
    entrega           VARCHAR,
    version_evento    INTEGER     DEFAULT 1,
    datos_personales  BOOLEAN     DEFAULT false,
    cabecera_nombre   VARCHAR,
    secreto_ref       VARCHAR,
    latido            BOOLEAN     DEFAULT false,
    verificada        BOOLEAN     DEFAULT false,
    activo            BOOLEAN     DEFAULT false,
    config_version    BIGINT      DEFAULT 1,
    creado_por        BIGINT,
    creado_en         TIMESTAMPTZ DEFAULT now(),
    actualizado_en    TIMESTAMPTZ,
    filtros           JSONB,
    campos            JSONB,
    datos_sensibles   BOOLEAN     DEFAULT false,
    CONSTRAINT webhook_suscripcion_url_https CHECK (url IS NULL OR url LIKE 'https://%'),
    CONSTRAINT webhook_suscripcion_activa_verificada CHECK (activo IS NOT TRUE OR verificada IS TRUE)
);

-- Cada cambio sube config_version: las filiales y las entregas saben con que version se repartio
CREATE OR REPLACE FUNCTION configuraciones.webhook_suscripcion_version() RETURNS trigger AS $$
BEGIN
    NEW.config_version := COALESCE(OLD.config_version, 0) + 1;
    NEW.actualizado_en := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_webhook_suscripcion_version ON configuraciones.webhook_suscripcion;
CREATE TRIGGER trg_webhook_suscripcion_version
    BEFORE UPDATE ON configuraciones.webhook_suscripcion
    FOR EACH ROW EXECUTE FUNCTION configuraciones.webhook_suscripcion_version();

CREATE TABLE IF NOT EXISTS eventos.outbox_evento (
    id              BIGSERIAL PRIMARY KEY,
    evento_id       VARCHAR     NOT NULL UNIQUE,
    tipo            VARCHAR     NOT NULL,
    version_evento  INTEGER     NOT NULL DEFAULT 1,
    gravedad        VARCHAR,
    ocurrido_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sucursal_id     BIGINT,
    usuario_id      BIGINT,
    sujeto          VARCHAR,
    datos           JSONB,
    link            VARCHAR,
    repartido_en    TIMESTAMPTZ,
    atributos       JSONB
);

CREATE INDEX IF NOT EXISTS idx_outbox_evento_sin_despachar
    ON eventos.outbox_evento (id) WHERE repartido_en IS NULL;

CREATE TABLE IF NOT EXISTS eventos.webhook_entrega (
    id                  BIGSERIAL PRIMARY KEY,
    outbox_id           BIGINT      NOT NULL REFERENCES eventos.outbox_evento (id) ON DELETE CASCADE,
    suscripcion_id      BIGINT      NOT NULL,
    secuencia           BIGINT      NOT NULL,
    config_version      BIGINT,
    umbral_aplicado     JSONB,
    estado              VARCHAR     NOT NULL DEFAULT 'PENDIENTE',
    intentos            INTEGER     NOT NULL DEFAULT 0,
    primer_intento_en   TIMESTAMPTZ,
    proximo_intento_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_hasta         TIMESTAMPTZ,
    ultimo_status_http  INTEGER,
    ultimo_error        VARCHAR(300),
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en          TIMESTAMPTZ,
    UNIQUE (suscripcion_id, secuencia),
    UNIQUE (outbox_id, suscripcion_id)
) WITH (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.02);

CREATE INDEX IF NOT EXISTS idx_webhook_entrega_vivas
    ON eventos.webhook_entrega (proximo_intento_en, suscripcion_id)
    WHERE estado IN ('PENDIENTE', 'REINTENTO', 'EN_CURSO');

CREATE TABLE IF NOT EXISTS eventos.destino_estado (
    suscripcion_id          BIGINT PRIMARY KEY,
    ultima_secuencia        BIGINT      NOT NULL DEFAULT 0,
    fallos_seguidos         INTEGER     NOT NULL DEFAULT 0,
    pausado_hasta           TIMESTAMPTZ,
    pausado_por_410         BOOLEAN     NOT NULL DEFAULT false,
    ultimo_error            VARCHAR(300),
    actualizado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    pausado_config_version  BIGINT
);

-- Una fila por nodo (PK = sucursal_id; el central es 0). Llega de las filiales BRANCH_TO_MAIN.
CREATE TABLE IF NOT EXISTS eventos.nodo_estado (
    sucursal_id            BIGINT PRIMARY KEY,
    emisor_encendido       BOOLEAN,
    firma_configurada      BOOLEAN,
    config_version         BIGINT,
    pendientes             INTEGER,
    pendiente_mas_viejo    TIMESTAMPTZ,
    descartadas_24h        INTEGER,
    fallos_emisor          BIGINT,
    hora_nodo              TIMESTAMPTZ,
    actualizado_en         TIMESTAMPTZ
);
ALTER TABLE eventos.nodo_estado REPLICA IDENTITY DEFAULT;

COMMENT ON TABLE configuraciones.eventos_consumidor IS 'Sistemas que reciben eventos por webhook. Solo central, no se replica.';
COMMENT ON TABLE configuraciones.webhook_suscripcion IS 'Suscripciones a eventos. Se escriben en el central y bajan MAIN_TO_ALL al espejo de cada filial.';
COMMENT ON TABLE eventos.outbox_evento IS 'Cola local de eventos del nodo. No se replica.';
COMMENT ON TABLE eventos.webhook_entrega IS 'Entregas por suscripcion, con lease y reintentos. No se replica.';
COMMENT ON TABLE eventos.nodo_estado IS 'Estado de cada nodo para la pantalla del central. BRANCH_TO_MAIN.';

-- Replica APAGADA hasta la operacion 1c (ver encabezado)
INSERT INTO configuraciones.replication_table (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES ('configuraciones.webhook_suscripcion', 'MAIN_TO_ALL', 'Suscripciones a eventos por webhook (srv #587). Prender en 1c.', false, false, NOW())
ON CONFLICT (table_name) DO NOTHING;

INSERT INTO configuraciones.replication_table (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES ('eventos.nodo_estado', 'BRANCH_TO_MAIN', 'Estado de eventos por nodo (srv #587). Prender en 1c.', false, false, NOW())
ON CONFLICT (table_name) DO NOTHING;

-- Rol para administrar consumidores y suscripciones (no se otorga a nadie)
INSERT INTO personas.role (nombre, creado_en)
SELECT 'WEBHOOKS GESTIONAR', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'WEBHOOKS GESTIONAR'
);
