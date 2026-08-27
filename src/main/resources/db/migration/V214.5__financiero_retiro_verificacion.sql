-- Verificacion de retiro en tesoreria: el que recibe cuenta la plata contra lo que declaro
-- el PDV, y a la caja mayor entra LO CONTADO, no lo declarado.
--
-- Por que tablas nuevas y no columnas en financiero.retiro:
--
--   * financiero.retiro es BRANCH_TO_MAIN y ademas tiene replicate_central_to_branch_with_filter
--     desde V155.1, o sea que replica en las dos direcciones. Meter un segundo escritor en esa
--     fila es pedir problemas.
--   * financiero.retiro_detalle (donde viven los montos) es BRANCH_TO_MAIN a secas: un cambio
--     hecho en central NUNCA baja a la filial. Corregir ahi dejaria al cajero viendo su monto
--     original, con PdvCajaService.generarBalance calculando sobre el — divergencia silenciosa.
--   * retiro.observacion ya tiene dueño: la escribe el cajero en filial al armar el retiro.
--
-- El retiro del PDV queda intacto: es la declaracion del origen y es la evidencia. La
-- correccion es un documento de tesoreria con su propia historia.
--
-- ⚠️ NINGUNA de estas tablas se inserta en configuraciones.replication_table.
-- ReplicationPublicationSyncScheduler corre cada hora en produccion (replication.sync.enabled)
-- y agrega a la publicacion cualquier tabla que aparezca ahi. El patron habitual del repo es
-- que la migracion inserte esa fila (ver V150.1, V142.1, V144.3); acá NO va, a proposito.
-- Son central-only, como el schema rrhh.
--
-- No se agrega ningun valor a financiero.estado_retiro: los cuatro estados de verificacion
-- (NECESITA_VERIFICACION, EN_VERIFICACION, VERIFICADO_CONCLUIDO_SIN_PROBLEMA y _CON_PROBLEMA)
-- existen desde V0 en central y en toda filial. Agregar etiquetas a un enum replicado es lo
-- que corto la replica el 2026-08-20 con tipo_dispositivo, y lo que V154.1 ya documentaba.

-- ── Trazabilidad del ledger: el origen necesita la sucursal ──────────────────────────────
--
-- Retiro tiene PK compuesta (id, sucursal_id) porque el id NO es global: cada filial numera
-- desde 1. En la base real el id 1 lo comparten 23 sucursales. movimiento_caja_virtual guarda
-- solo origen_id (RetiroTesoreriaProcesador descarta la sucursal), asi que buscar los
-- movimientos de un retiro por (origen_tipo, origen_id) devuelve tambien los de otras
-- sucursales. Sin esto, revertir una verificacion puede tocar la fila equivocada.
ALTER TABLE financiero.movimiento_caja_virtual
    ADD COLUMN IF NOT EXISTS origen_sucursal_id bigint;

COMMENT ON COLUMN financiero.movimiento_caja_virtual.origen_sucursal_id IS
    'Segunda mitad de la PK del documento de origen, cuando es compuesta (ej. Retiro). '
    'Null para origenes de PK simple.';

CREATE INDEX IF NOT EXISTS ix_mov_caja_virtual_origen
    ON financiero.movimiento_caja_virtual (origen_tipo, origen_id, origen_sucursal_id)
    WHERE origen_id IS NOT NULL;

-- ── Cabecera de la verificacion ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financiero.retiro_verificacion (
    id              bigserial PRIMARY KEY,
    retiro_id       bigint      NOT NULL,
    sucursal_id     bigint      NOT NULL,
    usuario_id      bigint,
    creado_en       timestamp   NOT NULL DEFAULT now(),
    -- SIN_DIFERENCIA | CON_DIFERENCIA
    resultado       varchar(20) NOT NULL,
    -- true = se confirmo lo declarado sin contar por denominacion. Si despues aparece una
    -- diferencia hay que poder saber que ese retiro nunca se conto billete por billete.
    rapida          boolean     NOT NULL DEFAULT false,
    observacion     text,
    anulada         boolean     NOT NULL DEFAULT false,
    CONSTRAINT retiro_verificacion_retiro_fk
        FOREIGN KEY (retiro_id, sucursal_id)
        REFERENCES financiero.retiro (id, sucursal_id)
);

-- Una verificacion vigente por retiro. Es la defensa de fondo contra el doble clic: dos
-- tesoreros verificando el mismo retiro a la vez acreditarian dos veces. El servicio ademas
-- toma lock pesimista sobre el retiro, mismo patron que asegurarSolicitud en RRHH.
CREATE UNIQUE INDEX IF NOT EXISTS ux_retiro_verificacion_vigente
    ON financiero.retiro_verificacion (retiro_id, sucursal_id)
    WHERE anulada = false;

-- ── Detalle por moneda ───────────────────────────────────────────────────────────────────
--
-- La comparacion es por moneda y nunca por total convertido: un retiro puede cerrar en el
-- total y tener 100 R$ de menos con su equivalente de mas en guaranies. Eso no es un retiro
-- correcto, es un cambio informal hecho en el camino.
--
-- La categoria vive aca y no en la cabecera porque un mismo retiro puede ser FALTANTE en una
-- moneda y SOBRANTE en otra a la vez.
CREATE TABLE IF NOT EXISTS financiero.retiro_verificacion_detalle (
    id              bigserial PRIMARY KEY,
    verificacion_id bigint         NOT NULL REFERENCES financiero.retiro_verificacion (id) ON DELETE CASCADE,
    moneda_id       bigint         NOT NULL,
    declarado       numeric(19, 4) NOT NULL,
    contado         numeric(19, 4) NOT NULL,
    diferencia      numeric(19, 4) NOT NULL,
    -- DIFERENCIA_CONTEO | FALTANTE | SOBRANTE | BILLETE_NO_RECIBIBLE | NO_RECIBIDO | OTRO
    categoria       varchar(30),
    CONSTRAINT ux_retiro_verif_detalle_moneda UNIQUE (verificacion_id, moneda_id)
);

-- ── Caso a investigar ────────────────────────────────────────────────────────────────────
--
-- Separado a proposito. Si la diferencia queda solo como una observacion del retiro, nadie la
-- mira. Tesoreria abre el caso y sigue trabajando; otro lo cierra. El que recibe no investiga.
--
-- Sin categoria propia: la lleva el detalle, por moneda. El caso agrupa.
CREATE TABLE IF NOT EXISTS financiero.retiro_caso (
    id              bigserial PRIMARY KEY,
    retiro_id       bigint      NOT NULL,
    sucursal_id     bigint      NOT NULL,
    verificacion_id bigint      REFERENCES financiero.retiro_verificacion (id),
    -- ABIERTO | EN_INVESTIGACION | RESUELTO
    estado          varchar(20) NOT NULL DEFAULT 'ABIERTO',
    abierto_por     bigint,
    asignado_a      bigint,
    resuelto_por    bigint,
    creado_en       timestamp   NOT NULL DEFAULT now(),
    resuelto_en     timestamp,
    resolucion      text,
    CONSTRAINT retiro_caso_retiro_fk
        FOREIGN KEY (retiro_id, sucursal_id)
        REFERENCES financiero.retiro (id, sucursal_id)
);

CREATE INDEX IF NOT EXISTS ix_retiro_caso_abiertos
    ON financiero.retiro_caso (estado, creado_en DESC)
    WHERE estado <> 'RESUELTO';
