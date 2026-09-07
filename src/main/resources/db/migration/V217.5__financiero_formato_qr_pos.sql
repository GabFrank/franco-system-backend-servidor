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

-- ── 4) Semilla: ValidaPix / FRCP1 ───────────────────────────────────────────────────────────
--
-- FRCP1*AUTH*BOL*CUR*AMT*REF*TS — 7 campos posicionales separados por '*'.
-- Se carga como comodin (proveedor_servicio_id NULL) porque la persona juridica de ValidaPix
-- todavia no esta dada de alta como proveedor de servicio; al crearla, se le asigna desde el ABM.
--
-- Tres trampas que el parser debe respetar y que este formato ejercita:
--   1. BOL viene VACIO en Pix (hay '**'). El split debe ser pelado: colapsar los vacios corre
--      las posiciones y la moneda se leeria como numero de boleta.
--   2. REF es el EndToEndId del BCB; sus ultimos 11 caracteres son case-sensitive y el proveedor
--      los imprime en mayuscula. Comparar sin distinguir caja.
--   3. TS es hora LOCAL; la fecha embebida en el EndToEndId es UTC (3 h de diferencia siempre).
--
-- monto usa escalaSegunMoneda: AMT viene en la menor unidad y cuanto vale depende de la moneda
-- (financiero.moneda.decimales — GUARANI 0, REAL y DOLAR 2). Una escala fija estaria mal en una
-- de las dos.
INSERT INTO financiero.formato_qr_pos (nombre, proveedor_servicio_id, patron, mapeo, ejemplo, activo)
SELECT
    'ValidaPix FRCP1',
    NULL,
    '^FRCP1\*(?<auth>[A-Z0-9]{0,20})\*(?<bol>[A-Z0-9]{0,20})\*(?<cur>PYG|BRL|USD)\*(?<amt>[0-9]{1,15})\*(?<ref>[A-Z0-9]{0,40})\*(?<ts>[0-9]{12})$',
    '{"codigoAutorizacion":{"de":"auth"},"numeroBoleta":{"de":"bol"},"moneda":{"de":"cur","mapa":{"PYG":1,"BRL":2,"USD":3}},"monto":{"de":"amt","escalaSegunMoneda":true},"identificadorTransaccion":{"de":"ref"},"fecha":{"de":"ts","formato":"yyyyMMddHHmm","zona":"America/Asuncion"}}',
    'FRCP1*CXF1**BRL*9455*E60701190202608271700DY5BCKNPMBQ*202608271401',
    true
WHERE NOT EXISTS (
    SELECT 1 FROM financiero.formato_qr_pos WHERE nombre = 'ValidaPix FRCP1'
);
