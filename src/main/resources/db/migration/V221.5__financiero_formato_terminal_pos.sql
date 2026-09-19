-- =====================================================================
-- formato_terminal_pos: el formato es del MODELO DE APARATO, no del proveedor
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- financiero.formato_qr_pos resuelve el formato POR PROVEEDOR, con un unico indice parcial
-- (uq_formato_qr_pos_proveedor). Eso no distingue una maquinita Bancard de un portal web Bancard,
-- y tampoco dos firmwares distintos de la misma marca. Y son cosas que se leen de formas
-- distintas: la maquinita imprime un ticket que hay que fotografiar, el portal web imprime un QR
-- que el lector del PDV escanea.
--
-- El modelo nuevo: un proveedor tiene tantos formatos como modelos de aparato tenga, y cada
-- terminal elige el suyo.
--
-- ⚠️ ORDEN DE DESPLIEGUE: la migracion espejo V95.5 del repo filial va ANTES que esta, y no
-- alcanza con que este MERGEADA — tiene que estar DESPLEGADA en toda la flota del canal.
-- financiero.formato_terminal_pos se replica MAIN_TO_ALL: en cuanto entra a la publicacion, toda
-- filial que no tenga la tabla corta su replicacion con el slot reteniendo WAL. Mismo patron que
-- V217.5 respecto de V91.5, y mismo mecanismo del corte del 2026-08-20.
--
-- POR QUE NO ES UN RENAME
--
-- formato_qr_pos esta replicada MAIN_TO_ALL. Un ALTER TABLE ... RENAME no la saca de la
-- publicacion --Postgres trackea la membresia por OID, no por nombre-- pero SI rompe el otro lado:
-- el protocolo de replicacion logica identifica la relacion EN EL SUBSCRIBER por schema.nombre. Si
-- central renombra y las 24 filiales todavia no, cada apply worker busca formato_qr_pos, no la
-- encuentra y se detiene. Esa ventana no se puede cerrar: cada filial actualiza por cron cada 15
-- minutos, por su cuenta.
--
-- Por eso: tabla nueva, se copia la fila, y el DROP de la vieja va en una entrega posterior,
-- cuando toda la flota corra el codigo nuevo. formato_qr_pos queda VIVA Y SIN USO.
-- =====================================================================

-- ── 1) financiero.formato_terminal_pos ──────────────────────────────────────────────────────
--
-- Central es el PUBLISHER de esta tabla, asi que aca SI van las restricciones: es el unico lado
-- que la escribe, y el ABM las necesita. En el espejo del filial (V95.5) solo la PK lleva NOT
-- NULL, deliberadamente — alla una restriccion que este lado no comparta seria un corte de
-- replicacion, no un error de validacion.
CREATE TABLE IF NOT EXISTS financiero.formato_terminal_pos (
    id                     BIGSERIAL    NOT NULL,
    nombre                 VARCHAR(100) NOT NULL,
    proveedor_servicio_id  BIGINT       NULL,
    -- MAQUINA | WEB | API. Es el router del flujo: decide que camino se le ofrece al cajero y,
    -- sobre todo, cual se le CIERRA.
    --
    -- VARCHAR con CHECK y no un enum de PostgreSQL: financiero.venta_tarjeta.estado ya es
    -- VARCHAR(20) mapeado a String en Java y el modulo entero sigue esa convencion. Un enum
    -- exigiria un ALTER TYPE coordinado en las 24 filiales cada vez que aparezca un tipo nuevo, y
    -- ALTER TYPE ... ADD VALUE no se puede usar en la misma transaccion que lo agrega.
    tipo                   VARCHAR(20)  NOT NULL DEFAULT 'MAQUINA',
    -- Regex con grupos nombrados, anclado con ^ y $.
    --
    -- Nullable en la BASE pero obligatorio para MAQUINA y WEB: lo valida
    -- FormatoTerminalPosService, que puede leer el tipo. Un CHECK que cruce dos columnas seria mas
    -- estricto pero dejaria al usuario con un error de Postgres en vez de una frase.
    -- Solo API puede no tener patron: ahi los campos llegan estructurados del proveedor.
    patron                 TEXT         NULL,
    mapeo                  TEXT         NOT NULL,
    ejemplo                TEXT         NULL,
    activo                 BOOLEAN      NOT NULL DEFAULT true,
    usuario_id             BIGINT       NULL,
    creado_en              TIMESTAMP    NULL DEFAULT NOW(),
    CONSTRAINT formato_terminal_pos_pkey PRIMARY KEY (id),
    CONSTRAINT formato_terminal_pos_tipo_check
        CHECK (tipo IN ('MAQUINA', 'WEB', 'API')),
    CONSTRAINT formato_terminal_pos_proveedor_fk
        FOREIGN KEY (proveedor_servicio_id) REFERENCES personas.proveedor_servicio (id),
    CONSTRAINT formato_terminal_pos_usuario_fk
        FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id)
);

-- ⚠️ UNICIDAD POR (proveedor, nombre) Y NO POR PROVEEDOR SOLO.
--
-- La V217.5 creo uq_formato_qr_pos_proveedor, unico por proveedor, y FormatoQrPosService.validar()
-- lo refuerza con "El proveedor ya tiene el formato X. Editalo en vez de crear otro". Eso prohibe
-- exactamente lo que esta tabla existe para permitir: Bancard v5.2 y Bancard v5.5 conviviendo.
-- Es un CAMBIO DE COMPORTAMIENTO respecto del ABM viejo y va dicho en la descripcion del PR.
CREATE UNIQUE INDEX IF NOT EXISTS uq_formato_terminal_pos_proveedor_nombre
    ON financiero.formato_terminal_pos (proveedor_servicio_id, nombre)
    WHERE proveedor_servicio_id IS NOT NULL;

-- Los comodines (proveedor NULL) tambien tienen que distinguirse entre si. El indice de arriba no
-- los cubre porque en Postgres dos NULL no son iguales, asi que (NULL,'X') dos veces pasaria.
CREATE UNIQUE INDEX IF NOT EXISTS uq_formato_terminal_pos_nombre_comodin
    ON financiero.formato_terminal_pos (nombre)
    WHERE proveedor_servicio_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_formato_terminal_pos_proveedor
    ON financiero.formato_terminal_pos (proveedor_servicio_id);

COMMENT ON TABLE financiero.formato_terminal_pos IS
    'Como se lee el ticket de un modelo de aparato. Reemplaza a formato_qr_pos, que queda viva y sin uso hasta que toda la flota corra el codigo nuevo.';
COMMENT ON COLUMN financiero.formato_terminal_pos.tipo IS
    'MAQUINA | WEB | API. Cierra el camino que no corresponde: una terminal WEB solo lee QR, una MAQUINA solo camara.';
COMMENT ON COLUMN financiero.formato_terminal_pos.mapeo IS
    'JSON: campo destino -> {de: grupo, obligatorio: bool, y opcionalmente mapa / escala / escalaSegunMoneda / formato+zona / mayusculas}. Los obligatorios deciden tres cosas: que debe encontrar el OCR, cuando el resultado es utilizable, y que campos pide la carga a mano.';

-- ── 2) Copiar lo que ya existe ──────────────────────────────────────────────────────────────
--
-- formato_qr_pos tiene UNA sola fila (ValidaPix FRCP1), asi que la copia es trivial. Se hace por
-- nombre y no por id: la tabla nueva tiene su propia secuencia y no hay ninguna FK apuntando
-- todavia a los ids viejos.
--
-- tipo = 'WEB' porque FRCP1 IMPRIME UN QR y se lee con el lector del PDV. El tipo describe COMO SE
-- LEE el ticket, no si el aparato es fisico: un cupon con QR va por el lector, uno sin QR va por
-- camara + OCR. Si esto resulta estar al reves para ValidaPix, se corrige con un UPDATE de una
-- fila; hoy no cambia nada porque ninguna terminal tiene formato asignado.
INSERT INTO financiero.formato_terminal_pos
    (nombre, proveedor_servicio_id, tipo, patron, mapeo, ejemplo, activo, usuario_id, creado_en)
SELECT f.nombre, f.proveedor_servicio_id, 'WEB', f.patron, f.mapeo, f.ejemplo, f.activo,
       f.usuario_id, f.creado_en
FROM financiero.formato_qr_pos f
WHERE NOT EXISTS (
    SELECT 1 FROM financiero.formato_terminal_pos n WHERE n.nombre = f.nombre
);

-- ── 3) financiero.terminal_pos: a que formato apunta ────────────────────────────────────────
--
-- Nullable a proposito y SIN backfill: el dia del corte lo estan TODAS las terminales de las 24
-- sucursales, y la asignacion se completa a mano por SQL. El desktop bloquea la venta con tarjeta
-- mientras la terminal no tenga formato, asi que ese SQL es PRERREQUISITO de liberar la version
-- del desktop, no una tarea posterior.
--
-- ⚠️ Este ADD COLUMN sobre terminal_pos, que es MAIN_TO_ALL, exige que la columna YA EXISTA en
-- todas las filiales (V95.5), o el apply worker se detiene con "missing replicated column". Es el
-- precedente literal de la cabecera de V153.1.
ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS formato_terminal_pos_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'terminal_pos_formato_fk'
    ) THEN
        -- La FK SI va en central: aca las dos tablas se escriben desde el mismo ABM, en la misma
        -- base, asi que no hay carrera posible. En el filial no va, porque las dos bajan por
        -- streams distintos sin orden garantizado entre ellos.
        ALTER TABLE financiero.terminal_pos
            ADD CONSTRAINT terminal_pos_formato_fk
            FOREIGN KEY (formato_terminal_pos_id)
            REFERENCES financiero.formato_terminal_pos (id);
    END IF;
END $$;

COMMENT ON COLUMN financiero.terminal_pos.formato_terminal_pos_id IS
    'Formato del modelo de aparato que es esta terminal. NULL = sin configurar: el desktop bloquea la venta con tarjeta y pide que un administrador lo asigne.';

-- ── 4) Replicacion ──────────────────────────────────────────────────────────────────────────
--
-- MAIN_TO_ALL como financiero.terminal_pos (V142.1) y formato_qr_pos (V217.5): la config se
-- administra en central y baja a todas las filiales. El filial nunca la escribe, asi que no lleva
-- replicate_central_to_branch_with_filter.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.formato_terminal_pos', 'MAIN_TO_ALL', 'Formato de terminal POS', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
