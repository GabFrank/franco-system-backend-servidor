-- Plantilla de bono recurrente. El bono real sigue viviendo en rrhh.bono;
-- esta tabla solo define que se genera, para quien y cada cuanto.
CREATE TABLE IF NOT EXISTS rrhh.bono_recurrente (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT NOT NULL REFERENCES personas.funcionario(id),
    tipo              VARCHAR(30),
    monto             NUMERIC(18,2) NOT NULL DEFAULT 0,
    frecuencia        VARCHAR(20) NOT NULL DEFAULT 'MENSUAL',
    motivo            TEXT,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id        BIGINT REFERENCES personas.usuario(id),
    autorizado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bono_recurrente_funcionario
    ON rrhh.bono_recurrente(funcionario_id);

-- Trazabilidad del bono generado hacia su plantilla, y clave de idempotencia.
ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS bono_recurrente_id BIGINT
    REFERENCES rrhh.bono_recurrente(id);
ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS periodo VARCHAR(7);

-- El indice parcial es la idempotencia real: si el cron y un saveBonoRecurrente
-- corren a la vez, la base impide el duplicado. Los bonos manuales
-- (bono_recurrente_id NULL) quedan fuera de la restriccion.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bono_recurrente_periodo
    ON rrhh.bono(bono_recurrente_id, periodo)
    WHERE bono_recurrente_id IS NOT NULL;
