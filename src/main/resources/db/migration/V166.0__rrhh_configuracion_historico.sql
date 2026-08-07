-- =====================================================================
-- RRHH — Auditoria de cambios en los parametros de configuracion.
-- =====================================================================
-- Los parametros de rrhh.configuracion_rrhh impactan la nomina (IPS, recargos
-- de hora extra, dias de vacaciones, salario minimo legal). Hoy la tabla tiene
-- usuario_id/creado_en del ultimo cambio, pero no historial: no hay forma de
-- saber que valor regia cuando se liquido un periodo, ni quien lo cambio.
--
-- Aditiva: tabla nueva, no toca configuracion_rrhh. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS rrhh.configuracion_rrhh_historico (
    id                      BIGINT PRIMARY KEY,
    configuracion_rrhh_id   BIGINT NOT NULL,
    clave                   VARCHAR(120) NOT NULL,
    valor_anterior          VARCHAR(255),
    valor_nuevo             VARCHAR(255),
    usuario_id              BIGINT,
    creado_en               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE SEQUENCE IF NOT EXISTS rrhh.configuracion_rrhh_historico_id_seq;

-- El id se toma de la secuencia por DEFAULT, como el resto de las tablas del schema
-- (ej. rrhh.funcionario_documento). Sin esto el insert llega con id NULL y viola el
-- NOT NULL: el generador del proyecto espera la convencion <tabla>_id_seq + DEFAULT.
ALTER TABLE rrhh.configuracion_rrhh_historico
    ALTER COLUMN id SET DEFAULT nextval('rrhh.configuracion_rrhh_historico_id_seq');

ALTER SEQUENCE rrhh.configuracion_rrhh_historico_id_seq
    OWNED BY rrhh.configuracion_rrhh_historico.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_config_historico_configuracion') THEN
        ALTER TABLE rrhh.configuracion_rrhh_historico
            ADD CONSTRAINT fk_config_historico_configuracion
            FOREIGN KEY (configuracion_rrhh_id) REFERENCES rrhh.configuracion_rrhh (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_config_historico_clave
    ON rrhh.configuracion_rrhh_historico (clave, creado_en DESC);
