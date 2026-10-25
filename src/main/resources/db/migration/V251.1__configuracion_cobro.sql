-- =====================================================================
-- configuracion_cobro: tope del Aumento (F8) del cobro (desktop #546)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El tope del aumento era un 20% del total clavado en el desktop, y solo lo respetaba el boton:
-- la tecla F8 y el delivery no tenian tope. Esta tabla lo vuelve configurable: con tope
-- (porcentaje) o sin tope. La edita TESORERIA GESTIONAR desde el desktop; el cobro la lee del
-- filial, para que funcione sin internet.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V106.1) VA ANTES
--
-- Es MAIN_TO_ALL. Cuando "Sincronizar publicaciones" la agregue a central_pub y refresque las
-- suscripciones, el REFRESH de una filial sin la tabla falla entero. Antes de desplegar este JAR,
-- cada filial del canal tiene que tener 106.1 en flyway_schema_history.
--
-- Despues, NINGUNA escritura sobre esta tabla hasta verificar pg_subscription_rel.srsubstate='r'
-- en cada suscripcion. En farmacia/bodega el alta a la publicacion es manual (los schedulers de
-- replicacion estan apagados ahi).
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA
--
-- - Seed: la tabla entra a la publicacion con copy_data=false, asi que una fila sembrada aca nunca
--   llegaria a las filiales. Sin filas = con tope del 20% en el central y en cada filial.
-- - ALTER PUBLICATION: lo hace syncPublicationsWithReplicationTable a partir del registro en
--   replication_table (mismo camino que V150.1 y V231.1), cuando la flota ya tiene el espejo.
--
-- ESTE ES EL LADO PUBLISHER: las restricciones viven aca y no en el filial.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_cobro (
    id                      BIGSERIAL PRIMARY KEY,
    aumento_con_tope        BOOLEAN       NOT NULL DEFAULT TRUE,
    aumento_tope_porcentaje NUMERIC(6, 2) NOT NULL DEFAULT 20,
    usuario_id              BIGINT,
    creado_en               TIMESTAMP     DEFAULT NOW(),
    modificado_en           TIMESTAMP     DEFAULT NOW(),
    CONSTRAINT fk_configuracion_cobro_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id),
    CONSTRAINT ck_configuracion_cobro_tope CHECK (aumento_tope_porcentaje > 0 AND aumento_tope_porcentaje <= 100)
);

-- Una sola fila: dos guardados simultaneos sobre la tabla vacia no crean dos.
CREATE UNIQUE INDEX IF NOT EXISTS uq_configuracion_cobro_unica
    ON financiero.configuracion_cobro ((true));

COMMENT ON TABLE financiero.configuracion_cobro IS
    'Configuracion del cobro del PDV, una sola fila. MAIN_TO_ALL. Sin filas = aumento con tope del 20%.';

COMMENT ON COLUMN financiero.configuracion_cobro.aumento_con_tope IS
    'true = el aumento del cobro no puede pasar aumento_tope_porcentaje del total; false = sin tope.';

COMMENT ON COLUMN financiero.configuracion_cobro.aumento_tope_porcentaje IS
    'Tope del aumento, en porcentaje del total del cobro. Se conserva aunque aumento_con_tope sea false.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.configuracion_cobro', 'MAIN_TO_ALL', 'Configuracion Cobro', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
