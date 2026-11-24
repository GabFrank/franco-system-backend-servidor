-- Ordenes configurables por caja mayor (issues #540, #603 y desktop #653).
--
-- JSON arrays, mismo formato que cuentas_bancarias_orden (V186.5). NULL = orden por defecto, asi que
-- las cajas existentes no cambian de comportamiento.
--   monedas_orden                  ids de moneda: cards de saldo de la caja
--   sucursales_orden               ids de sucursal: selects de sucursal en los dialogos de la caja
--   operaciones_financieras_orden  TipoOperacionFinanciera: orden del dialogo de operaciones
--   operaciones_financieras_ocultas TipoOperacionFinanciera que el dialogo no muestra (no habilita nada)
--
-- Aditiva. Central-only: caja_virtual_configuracion no esta en ninguna publicacion.

ALTER TABLE financiero.caja_virtual_configuracion ADD COLUMN IF NOT EXISTS monedas_orden text;
ALTER TABLE financiero.caja_virtual_configuracion ADD COLUMN IF NOT EXISTS sucursales_orden text;
ALTER TABLE financiero.caja_virtual_configuracion ADD COLUMN IF NOT EXISTS operaciones_financieras_orden text;
ALTER TABLE financiero.caja_virtual_configuracion ADD COLUMN IF NOT EXISTS operaciones_financieras_ocultas text;
