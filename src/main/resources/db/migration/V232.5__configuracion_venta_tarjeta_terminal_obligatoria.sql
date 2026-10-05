-- =====================================================================
-- configuracion_venta_tarjeta.terminal_obligatoria
-- =====================================================================
-- Si el PDV deja cerrar una venta con tarjeta sin haber elegido la terminal. true (default) = no:
-- el cobro exige la terminal antes de cerrar. false = el comportamiento anterior.
--
-- Por que el default es true y no "el de hoy": en farmacia filial 1, del 2026-09-25 al 28,
-- 116 de 631 venta_tarjeta quedaron sin terminal y 109 terminaron NO_COMPLETADO sin conciliar. El
-- lector escribia en el cobro de atras y su Enter finalizaba la venta. Decidido con Gabriel el
-- 2026-09-28: configurable, arrancando en el lado seguro.
--
-- ⚠️ ORDEN DE DESPLIEGUE: la tabla es MAIN_TO_ALL. El espejo V104.5 del filial va ANTES y tiene que
-- estar DESPLEGADO en la flota. ADD COLUMN con DEFAULT constante no reescribe filas ni genera cambios
-- replicados; el primer UPDATE de la fila (guardar desde el ABM) si, y una filial sin la columna
-- detiene su apply worker. No tocar el ABM hasta verificar las filiales.
-- =====================================================================
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS terminal_obligatoria BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN financiero.configuracion_venta_tarjeta.terminal_obligatoria IS
    'Si el PDV exige elegir la terminal antes de cerrar una venta con tarjeta. true = la exige.';
