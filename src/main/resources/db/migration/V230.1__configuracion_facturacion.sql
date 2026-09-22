-- =====================================================================
-- configuracion_facturacion: politica de facturacion por sucursal (issue filial #127)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- En el filial, la decision "esta venta se factura" la tomaba el contador facturaCountDown, una
-- property que se edita a mano en cada servidor, y el boton "Venta + Ticket" se la salteaba.
-- Esta tabla es la politica administrable: una fila global (sucursal_id NULL) y a lo sumo un
-- override por sucursal. El filial la lee en cada venta; sin filas, usa su property de siempre.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V103.1) VA ANTES
--
-- Es MAIN_TO_ALL. Cuando "Sincronizar publicaciones" la agregue a central_pub y refresque las
-- suscripciones, el REFRESH de una filial sin la tabla falla entero. Antes de desplegar este
-- JAR, cada filial del canal tiene que tener 103.1 en flyway_schema_history.
--
-- Despues, NINGUNA escritura sobre esta tabla hasta verificar pg_subscription_rel.srsubstate='r'
-- en cada suscripcion. En farmacia/bodega el alta a la publicacion es manual (los schedulers de
-- replicacion estan apagados ahi): ver el plan del filial, seccion "Orden de PRs y despliegue".
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA
--
-- - Seed: la tabla entra a la publicacion con copy_data=false, asi que una fila sembrada aca
--   nunca llegaria a las filiales. Y una global sembrada pisaria el facturaCountDown que cada
--   filial tiene ajustado a mano. Sin filas = comportamiento de hoy en todas.
-- - ALTER PUBLICATION: lo hace syncPublicationsWithReplicationTable a partir del registro en
--   replication_table (mismo camino que V150.1), cuando la flota ya tiene el espejo.
--
-- ESTE ES EL LADO PUBLISHER: las restricciones viven aca y no en el filial.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_facturacion (
    id                            BIGSERIAL PRIMARY KEY,
    sucursal_id                   BIGINT,
    modo                          VARCHAR(20) NOT NULL DEFAULT 'INTERVALO',
    ventas_sin_factura            INTEGER     NOT NULL DEFAULT 0,
    venta_ticket_respeta_politica BOOLEAN     NOT NULL DEFAULT FALSE,
    usuario_id                    BIGINT,
    creado_en                     TIMESTAMP   DEFAULT NOW(),
    modificado_en                 TIMESTAMP   DEFAULT NOW(),
    CONSTRAINT fk_configuracion_facturacion_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal (id),
    CONSTRAINT fk_configuracion_facturacion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id),
    CONSTRAINT ck_configuracion_facturacion_modo CHECK (modo IN ('TODAS', 'INTERVALO', 'A_PEDIDO')),
    CONSTRAINT ck_configuracion_facturacion_ventas_sin_factura CHECK (ventas_sin_factura >= 0)
);

-- Una sola por sucursal y una sola global. Dos indices parciales y no COALESCE(sucursal_id, 0):
-- la sucursal 0 existe (es la del servidor central) y chocaria con la global.
CREATE UNIQUE INDEX IF NOT EXISTS uq_configuracion_facturacion_sucursal
    ON financiero.configuracion_facturacion (sucursal_id)
    WHERE sucursal_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_configuracion_facturacion_global
    ON financiero.configuracion_facturacion ((sucursal_id IS NULL))
    WHERE sucursal_id IS NULL;

COMMENT ON TABLE financiero.configuracion_facturacion IS
    'Politica de facturacion automatica del filial. MAIN_TO_ALL. Sin filas = cada filial usa su property facturaCountDown. Kill switch: DELETE de toda la tabla.';

COMMENT ON COLUMN financiero.configuracion_facturacion.sucursal_id IS
    'NULL = politica global; valor = override de esa sucursal.';

COMMENT ON COLUMN financiero.configuracion_facturacion.ventas_sin_factura IS
    'Solo INTERVALO: ventas sin factura entre dos facturadas (misma semantica que facturaCountDown).';

COMMENT ON COLUMN financiero.configuracion_facturacion.venta_ticket_respeta_politica IS
    'false = Venta + Ticket y delivery facturan siempre (comportamiento historico); true = decide la politica.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.configuracion_facturacion', 'MAIN_TO_ALL', 'Configuracion Facturacion', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
