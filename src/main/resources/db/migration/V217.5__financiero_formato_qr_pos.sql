-- Formato del QR que imprime el POS, configurable desde el sistema.
--
-- QUE PROBLEMA RESUELVE
--
-- Hoy, para registrar una venta con tarjeta, el cajero deja el PDV, saca una foto del cupon
-- termico con el celular y espera que el OCR acierte. Falla seguido. ValidaPix acepto imprimir
-- un QR en el cupon con los datos ya estructurados (formato FRCP1, en produccion desde el
-- 2026-08-27), asi que ese cupon se puede leer con el lector que el PDV ya tiene.
--
-- El resto de los proveedores todavia no contesto, y varios NO van a poder cambiar su formato:
-- habra que adaptarse al que ya imprimen. Por eso el formato NO se escribe en el codigo. Cada
-- proveedor tiene su fila con un regex de grupos nombrados y un mapeo a nuestros campos, y se
-- carga desde la pantalla de administracion, sin release.
--
-- QUE NO RESUELVE
--
-- El lector es keyboard-wedge con teclado es-LA. Podemos adaptarnos a cualquier FORMATO, no a
-- cualquier TRANSPORTE: si un proveedor imprime multilinea, o con llaves/comillas/pipe que el
-- wedge no tipea, ningun regex lo arregla — eso se resuelve configurando el scanner. El charset
-- es la restriccion no negociable que hay que pasarle a cada proveedor.
--
-- ⚠️ ORDEN DE DESPLIEGUE: la migracion espejo V91.5 del repo filial va ANTES que esta, en toda
-- la flota. financiero.formato_qr_pos se replica MAIN_TO_ALL y venta_tarjeta.qr_crudo es una
-- columna nueva en una tabla ya replicada: si el filial no las tiene, el apply worker
-- central→filial entra en crash-loop con el slot reteniendo WAL (corte del 2026-08-20).

-- ── 1) financiero.formato_qr_pos ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financiero.formato_qr_pos (
    id                     BIGSERIAL    NOT NULL,
    nombre                 VARCHAR(100) NOT NULL,
    proveedor_servicio_id  BIGINT       NULL,
    patron                 TEXT         NOT NULL,
    mapeo                  TEXT         NOT NULL,
    ejemplo                TEXT         NOT NULL,
    activo                 BOOLEAN      NOT NULL DEFAULT true,
    usuario_id             BIGINT       NULL,
    creado_en              TIMESTAMP    NULL DEFAULT NOW(),
    CONSTRAINT formato_qr_pos_pkey PRIMARY KEY (id),
    CONSTRAINT formato_qr_pos_proveedor_fk
        FOREIGN KEY (proveedor_servicio_id) REFERENCES personas.proveedor_servicio (id),
    CONSTRAINT formato_qr_pos_usuario_fk
        FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id)
);

-- Un solo formato por proveedor. Parcial, porque proveedor_servicio_id NULL es un caso valido:
-- es el formato "comodin", el que se prueba cuando la terminal escaneada no tiene proveedor
-- asignado o el proveedor no tiene formato propio. Puede haber varios comodines.
CREATE UNIQUE INDEX IF NOT EXISTS uq_formato_qr_pos_proveedor
    ON financiero.formato_qr_pos (proveedor_servicio_id)
    WHERE proveedor_servicio_id IS NOT NULL;

COMMENT ON COLUMN financiero.formato_qr_pos.patron  IS
    'Regex con grupos nombrados (?<nombre>...). Debe estar anclado con ^ y $.';
COMMENT ON COLUMN financiero.formato_qr_pos.mapeo   IS
    'JSON: campo destino -> {de: grupo, y opcionalmente mapa / escala / escalaSegunMoneda / formato+zona / mayusculas}.';
COMMENT ON COLUMN financiero.formato_qr_pos.ejemplo IS
    'Cadena real de ejemplo. El ABM no deja guardar si el patron no la matchea.';

-- ── 2) financiero.venta_tarjeta: la cadena cruda escaneada ──────────────────────────────────
--
-- Sin esto no hay forma de diagnosticar un cupon que parseo mal: el cajero ya se fue, el ticket
-- termico se borro y los campos quedaron a medias. Se guarda tal cual entro, sin normalizar.
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS qr_crudo VARCHAR(512) NULL;

-- ── 3) Replicacion ──────────────────────────────────────────────────────────────────────────
--
-- MAIN_TO_ALL como financiero.terminal_pos (V142.1): la config se administra en central y baja a
-- todas las filiales. El filial nunca la escribe, asi que no lleva
-- replicate_central_to_branch_with_filter.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.formato_qr_pos', 'MAIN_TO_ALL', 'Formato QR POS', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;

-- ── 4) Sin semilla ──────────────────────────────────────────────────────────────────────────
--
-- Esta migracion sembraba el formato «ValidaPix FRCP1». Se quito el 2026-09-22, antes de
-- promoverla a produccion: farmacia y bodega son empresas distintas y no comparten configuracion,
-- y un formato es la configuracion de un proveedor concreto. Sembrarlo en las dos era regalarle
-- a bodega un proveedor que no usa. Cada empresa crea sus formatos desde el ABM; un INSERT hecho
-- despues de que la tabla este publicada replica solo a sus filiales.
--
-- En alpha la semilla ya se habia aplicado: se borro a mano y el checksum se reparo por SQL
-- (equivalente a `flyway repair`). En produccion esta migracion nunca corrio con la semilla.
