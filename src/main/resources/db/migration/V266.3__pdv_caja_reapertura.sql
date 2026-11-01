-- =====================================================================
-- Reabrir una caja PDV cerrada por equivocacion (issue desktop #662)
-- =====================================================================
-- Un ADMIN reabre la caja desde el desktop (reabrirCajaDesdeServidor); el central reenvia a la
-- filial, que es la duena de la caja (reabrirCaja). Cada reapertura deja una fila en
-- financiero.pdv_caja_reapertura con quien, cuando, por que y el conteo de cierre descartado.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V110.3) VA ANTES, EN CADA FILIAL
--
-- BRANCH_TO_MAIN, INSERT-ONLY: la escribe solo la filial. La tabla entra a la *_filialX_pub de cada
-- filial via ReplicationPublicationSyncScheduler a partir del registro de abajo (mismo camino que
-- operaciones.sorteo_participacion, V260.3). Verificar en el central:
--   select * from pg_subscription_rel where srrelid='financiero.pdv_caja_reapertura'::regclass;
-- copy_data=false: una reapertura hecha antes de que la suscripcion del central tome la tabla
-- queda solo en la filial (la caja igual se reabre; solo falta el registro aca).
--
-- ESTE ES EL LADO SUBSCRIBER: sin FK (las filas llegan sin orden garantizado). PK (id, sucursal_id)
-- porque el id sale de la secuencia de cada filial. Columnas iguales a V110.3 de la filial (la
-- replicacion logica mapea por nombre).
-- =====================================================================
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS financiero.pdv_caja_reapertura (
    id                        BIGINT    NOT NULL,
    pdv_caja_id               BIGINT    NOT NULL,
    sucursal_id               BIGINT    NOT NULL,
    conteo_cierre_anterior_id BIGINT,
    fecha_cierre_anterior     TIMESTAMP,
    motivo                    VARCHAR   NOT NULL,
    usuario_id                BIGINT,
    creado_en                 TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id)
);

CREATE INDEX IF NOT EXISTS idx_pdv_caja_reapertura_caja
    ON financiero.pdv_caja_reapertura (sucursal_id, pdv_caja_id);

ALTER TABLE financiero.pdv_caja_reapertura REPLICA IDENTITY FULL;

COMMENT ON TABLE financiero.pdv_caja_reapertura IS
    'Reaperturas de cajas PDV hechas por un ADMIN, con motivo. BRANCH_TO_MAIN, INSERT-ONLY: la escribe la filial.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.pdv_caja_reapertura', 'BRANCH_TO_MAIN', 'Reaperturas de caja PDV (insert-only)', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
