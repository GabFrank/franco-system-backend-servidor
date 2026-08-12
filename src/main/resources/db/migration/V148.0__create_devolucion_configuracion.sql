-- Modulo Devoluciones: configuracion a nivel empresa/instancia (fila unica id=1).
-- Aditiva. Los defaults reproducen el comportamiento hardcodeado actual, asi que
-- migrar no cambia ninguna conducta hasta que el usuario edite la configuracion.
-- Agregar una preferencia futura = ALTER TABLE ADD COLUMN (aditivo).

CREATE TABLE IF NOT EXISTS operaciones.devolucion_configuracion (
    id                        BIGINT PRIMARY KEY,
    -- Dashboard / umbrales
    dias_estancada            INT     NOT NULL DEFAULT 30,
    dias_estancada_urgente    INT     NOT NULL DEFAULT 60,
    ranking_top_n             INT     NOT NULL DEFAULT 5,
    dashboard_rango_default   VARCHAR(20)  NOT NULL DEFAULT 'MES_ACTUAL',
    -- Impresion
    ticket_ancho_mm           INT     NOT NULL DEFAULT 58,
    impresora_ticket          VARCHAR(80)  NOT NULL DEFAULT 'TICKET58',
    comprobante_titulo        VARCHAR(120) NOT NULL DEFAULT 'COMPROBANTE DE RETIRO',
    comprobante_texto_firma   VARCHAR(120) NOT NULL DEFAULT 'Firma del proveedor',
    -- Merma / gasto
    merma_respeta_motivo      BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_gasto_merma          VARCHAR(120) NOT NULL DEFAULT 'MERMA/AVERIA DE PRODUCTO',
    -- Alerta de compras
    alerta_incluye_retirado   BOOLEAN NOT NULL DEFAULT FALSE,
    alerta_bloqueante         BOOLEAN NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE operaciones.devolucion_configuracion IS 'Preferencias del modulo de devoluciones (fila unica id=1)';

-- Fila unica de configuracion. Idempotente.
INSERT INTO operaciones.devolucion_configuracion (id)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM operaciones.devolucion_configuracion WHERE id = 1);
