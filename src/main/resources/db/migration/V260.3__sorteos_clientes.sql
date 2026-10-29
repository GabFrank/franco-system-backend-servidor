-- =====================================================================
-- sorteos para clientes: sorteo / sorteo_sucursal / sorteo_premio / sorteo_participacion
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Hoy el sorteo es manual: el cliente tira su ticket de venta en una urna. Esto lo lleva al
-- sistema: el central configura sorteos (monto minimo, vigencia, sucursales, premios), la
-- configuracion baja a las filiales (MAIN_TO_ALL) y, cuando una venta llega al minimo, la filial
-- imprime el ticket de participacion y registra la participacion, que sube al central
-- (BRANCH_TO_MAIN). Ver docs/superpowers/specs/2026-10-28-sorteos-clientes-design.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V109.3) VA ANTES, EN CADA FILIAL
--
-- sorteo, sorteo_sucursal y sorteo_premio son MAIN_TO_ALL. Apenas entran a central_pub, una
-- filial SIN las tablas corta TODA su replicacion entrante (apply worker en bucle: "logical
-- replication target relation does not exist"). Orden: filiales, despues central, despues desktop.
-- Igual que V232.1: el alta a la publicacion y el checklist de filiales van antes del deploy.
--
-- copy_data=false: un sorteo cargado antes de que una filial refresque su suscripcion NO le
-- llega nunca. Ningun sorteo real se carga hasta verificar srsubstate='r' en todas.
--
-- CHECKLIST DE VERIFICACION ANTES DE CARGAR EL PRIMER SORTEO REAL
--
-- (a) MAIN_TO_ALL (sorteo, sorteo_sucursal, sorteo_premio): en CADA filial verificar
--     srsubstate='r' para las tres tablas en pg_subscription_rel (suscripcion a central_pub).
--     No crear sorteos antes de eso.
-- (b) BRANCH_TO_MAIN (operaciones.sorteo_participacion): la tabla entra a la publicacion
--     *_filialX_pub de cada filial solo via ReplicationPublicationSyncScheduler (replication.sync
--     .enabled, a los 2 min del arranque y luego cada hora, y solo si la filial es alcanzable).
--     En CADA filial verificar:
--       select * from pg_publication_tables where tablename='sorteo_participacion';
--     y en el central verificar que la filialX_sub correspondiente la tenga en srsubstate='r':
--       select * from pg_subscription_rel where srrelid='operaciones.sorteo_participacion'::regclass;
--     (si no esta, hacer REFRESH PUBLICATION en esa suscripcion).
--     Consecuencia de copy_data=false: toda participacion insertada en una filial ANTES de que
--     la tabla este en la publicacion y la suscripcion del central refrescada NUNCA llega al
--     central (queda solo en la filial). El listado y "Validar codigo" no encontrarian ese ticket.
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA: ver V231.1 / V232.1 (mismo criterio).
--
-- ESTE ES EL LADO PUBLISHER: las restricciones (FK, CHECK) viven aca; el espejo de la filial no
-- las tiene. Los nombres de columna coinciden exactamente con V109.3 de la filial (la replicacion
-- logica mapea por nombre).
--
-- sorteo_participacion: la escribe SOLO la filial, una vez, y nunca se actualiza ni se borra
-- (INSERT-ONLY). Sin FK porque llega por replicacion y el orden de llegada no esta garantizado.
-- PK (id, sucursal_id) porque el id sale de la secuencia de cada filial. No tiene estado: el
-- central deriva ACTIVA/ANULADA mirando si la venta (venta_id, sucursal_id) esta cancelada.
-- =====================================================================
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS operaciones.sorteo (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR   NOT NULL,
    descripcion         VARCHAR,
    monto_minimo        NUMERIC   NOT NULL,
    modo_participacion  VARCHAR   NOT NULL,
    cantidad_fija       INTEGER,
    tope_por_venta      INTEGER,
    incluye_tarjeta     BOOLEAN   NOT NULL DEFAULT TRUE,
    fecha_desde         DATE,
    fecha_hasta         DATE,
    fecha_sorteo        DATE,
    todas_sucursales    BOOLEAN   NOT NULL DEFAULT FALSE,
    activo              BOOLEAN   NOT NULL DEFAULT TRUE,
    usuario_id          BIGINT,
    creado_en           TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_sorteo_usuario FOREIGN KEY (usuario_id)
        REFERENCES personas.usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_sorteo_monto_minimo CHECK (monto_minimo > 0),
    CONSTRAINT ck_sorteo_rango CHECK (
        fecha_desde IS NULL OR fecha_hasta IS NULL OR fecha_hasta >= fecha_desde),
    CONSTRAINT ck_sorteo_modo CHECK (
        modo_participacion IN ('UNA_POR_VENTA', 'POR_MULTIPLO', 'CANTIDAD_FIJA')),
    CONSTRAINT ck_sorteo_cantidad_fija CHECK (
        modo_participacion <> 'CANTIDAD_FIJA' OR (cantidad_fija IS NOT NULL AND cantidad_fija >= 1)),
    CONSTRAINT ck_sorteo_tope CHECK (tope_por_venta IS NULL OR tope_por_venta >= 1)
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    sorteo_id    BIGINT NOT NULL,
    sucursal_id  BIGINT NOT NULL,
    CONSTRAINT fk_sorteo_sucursal_sorteo FOREIGN KEY (sorteo_id)
        REFERENCES operaciones.sorteo (id) ON DELETE CASCADE,
    CONSTRAINT fk_sorteo_sucursal_sucursal FOREIGN KEY (sucursal_id)
        REFERENCES empresarial.sucursal (id),
    CONSTRAINT uq_sorteo_sucursal UNIQUE (sorteo_id, sucursal_id),
    CONSTRAINT ck_sorteo_sucursal_no_central CHECK (sucursal_id <> 0)
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_premio (
    id           BIGSERIAL PRIMARY KEY,
    sorteo_id    BIGINT  NOT NULL,
    orden        INTEGER NOT NULL DEFAULT 0,
    descripcion  VARCHAR,
    producto_id  BIGINT,
    cantidad     INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_sorteo_premio_sorteo FOREIGN KEY (sorteo_id)
        REFERENCES operaciones.sorteo (id) ON DELETE CASCADE,
    CONSTRAINT fk_sorteo_premio_producto FOREIGN KEY (producto_id)
        REFERENCES productos.producto (id),
    CONSTRAINT ck_sorteo_premio_cantidad CHECK (cantidad >= 1),
    CONSTRAINT ck_sorteo_premio_descripcion_o_producto CHECK (
        producto_id IS NOT NULL OR (descripcion IS NOT NULL AND btrim(descripcion) <> ''))
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_participacion (
    id           BIGINT    NOT NULL,
    sorteo_id    BIGINT    NOT NULL,
    venta_id     BIGINT    NOT NULL,
    sucursal_id  BIGINT    NOT NULL,
    cantidad     INTEGER   NOT NULL,
    cliente_id   BIGINT,
    documento    VARCHAR,
    nombre       VARCHAR,
    codigo       VARCHAR   NOT NULL,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id),
    CONSTRAINT uq_sorteo_participacion_sorteo_venta UNIQUE (sorteo_id, sucursal_id, venta_id)
);

CREATE INDEX IF NOT EXISTS idx_sorteo_sucursal_sorteo ON operaciones.sorteo_sucursal (sorteo_id);
CREATE INDEX IF NOT EXISTS idx_sorteo_premio_sorteo ON operaciones.sorteo_premio (sorteo_id);
-- codigo NO es UNIQUE: es tabla subscriber, un duplicado frenaria la replicacion entrante de la filial.
CREATE INDEX IF NOT EXISTS idx_sorteo_participacion_codigo ON operaciones.sorteo_participacion (codigo);
CREATE INDEX IF NOT EXISTS idx_sorteo_participacion_venta
    ON operaciones.sorteo_participacion (sucursal_id, venta_id);

COMMENT ON TABLE operaciones.sorteo IS
    'Sorteo para clientes. MAIN_TO_ALL: cada filial lo evalua al cobrar. Corte de emergencia: UPDATE ... SET activo=false.';
COMMENT ON TABLE operaciones.sorteo_sucursal IS
    'Sucursales donde rige un sorteo con todas_sucursales=false (nunca la 0). MAIN_TO_ALL.';
COMMENT ON TABLE operaciones.sorteo_premio IS
    'Premios de un sorteo: descripcion libre y/o producto, con cantidad y orden. MAIN_TO_ALL.';
COMMENT ON TABLE operaciones.sorteo_participacion IS
    'Participacion de un cliente en un sorteo, una fila por venta y sorteo. BRANCH_TO_MAIN, INSERT-ONLY: la escribe la filial una vez y no se actualiza ni se borra.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('operaciones.sorteo', 'MAIN_TO_ALL', 'Sorteos para clientes', true, false, NOW()),
    ('operaciones.sorteo_sucursal', 'MAIN_TO_ALL', 'Sucursales de un sorteo', true, false, NOW()),
    ('operaciones.sorteo_premio', 'MAIN_TO_ALL', 'Premios de un sorteo', true, false, NOW()),
    ('operaciones.sorteo_participacion', 'BRANCH_TO_MAIN', 'Participaciones en sorteos (insert-only)', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
