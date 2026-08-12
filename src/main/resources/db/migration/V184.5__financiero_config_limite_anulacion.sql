-- =====================================================================
-- Tesorería — Fase 8: parámetros de config (CN4 límite de anulación, CN10 audit)
-- =====================================================================
-- Aditivo e idempotente.
-- =====================================================================

-- CN4: días máximos hacia atrás para anular un movimiento de caja (null = sin límite)
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS dias_limite_anulacion integer;

-- CN10: auditoría de cambios de configuración financiera
CREATE TABLE IF NOT EXISTS financiero.auditoria_config (
    id          bigserial PRIMARY KEY,
    clave       varchar(80) NOT NULL,
    valor_anterior varchar(300),
    valor_nuevo    varchar(300),
    usuario_id  bigint,
    creado_en   timestamp DEFAULT now()
);
