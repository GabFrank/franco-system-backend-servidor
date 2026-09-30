-- =====================================================================
-- RRHH — Ítem de liquidación programado para otro periodo
-- =====================================================================
-- Un ítem cargado desde una liquidación para que se aplique en la de un
-- periodo posterior (que todavía no existe). Al generar la liquidación de ese
-- periodo, cada programado PENDIENTE entra como ítem automático con
-- referencia_tipo = 'ITEM_PROGRAMADO'. Al pagarla queda APLICADO.
-- Todo aditivo. Central-only (schema rrhh, no se replica a filiales).
-- =====================================================================

CREATE TABLE IF NOT EXISTS rrhh.liquidacion_item_programado (
    id                      BIGSERIAL PRIMARY KEY,
    funcionario_id          BIGINT NOT NULL REFERENCES personas.funcionario(id),
    periodo                 VARCHAR(7) NOT NULL,
    liquidacion_concepto_id BIGINT REFERENCES rrhh.liquidacion_concepto(id),
    codigo                  VARCHAR(50) NOT NULL,
    descripcion             VARCHAR(255),
    monto                   NUMERIC(18,2) NOT NULL DEFAULT 0,
    tipo                    VARCHAR(20) NOT NULL,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    liquidacion_id          BIGINT,
    liquidacion_final_id    BIGINT,
    origen_liquidacion_id   BIGINT,
    usuario_id              BIGINT REFERENCES personas.usuario(id),
    creado_en               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_liq_item_programado_func_periodo
    ON rrhh.liquidacion_item_programado(funcionario_id, periodo, estado);
