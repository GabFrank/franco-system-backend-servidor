-- Recepciones pendientes de lote (#429, fase 2, entrega 3).
--
-- Una recepcion cargada con «Cargar lote despues» deja una fila SIN LOTE (lote_id nulo) de tipo
-- COMPRA en operaciones.movimiento_stock_lote. Esta tabla anota como se resolvio cada una:
-- ATRIBUIDO (se paso a un lote con un ajuste ATRIBUIR de la fase 1) o DESCARTADO (se cierra sin
-- mover stock). Puede haber varias resoluciones por fila hasta cubrir su cantidad.
--
-- SOLO central y sin registrar en configuraciones.replication_table, igual que lote_archivado:
-- la filial no la necesita y una tabla nueva en la publicacion cortaria la replicacion de las
-- filiales viejas. Aditiva: el codigo viejo no la lee. Vuelta atras = dejarla.
CREATE TABLE IF NOT EXISTS operaciones.lote_pendiente_resuelto (
    id                    BIGSERIAL PRIMARY KEY,
    msl_id                BIGINT        NOT NULL,
    sucursal_id           BIGINT        NOT NULL,
    lote_id               BIGINT REFERENCES operaciones.lote (id),
    cantidad_atribuida    NUMERIC       NOT NULL DEFAULT 0,
    cantidad_no_atribuida NUMERIC       NOT NULL DEFAULT 0,
    accion                VARCHAR(20)   NOT NULL CHECK (accion IN ('ATRIBUIDO', 'DESCARTADO')),
    motivo                TEXT,
    movimiento_stock_id   BIGINT,
    usuario_id            BIGINT,
    fecha                 TIMESTAMP     NOT NULL DEFAULT now(),
    anulada               BOOLEAN       NOT NULL DEFAULT false,
    anulada_por           BIGINT,
    anulada_motivo        TEXT,
    anulada_en            TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lote_pendiente_resuelto_msl
    ON operaciones.lote_pendiente_resuelto (msl_id, sucursal_id) WHERE anulada = false;

COMMENT ON TABLE operaciones.lote_pendiente_resuelto IS
    'Resoluciones de recepciones cargadas sin lote (atribuidas o descartadas). Solo central, no replica.';
