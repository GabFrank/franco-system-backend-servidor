-- Issue #186: segunda versión del DROP del ledger de cuenta corriente de proveedor.
-- El código (MovimientoProveedor, ProveedorCuentaService, Proveedor.saldoActual) se
-- eliminó en 25eb1153; la deuda se deriva de SolicitudPago. Tabla y columna, creadas
-- en V182.5, quedaron inertes. Verificado el 2026-10-21: 0 filas en
-- movimiento_proveedor y 0 proveedores con saldo_actual <> 0 en alpha, farmacia y
-- bodega; ninguna de las dos está en publicaciones de replicación.

DROP TABLE IF EXISTS financiero.movimiento_proveedor;

ALTER TABLE personas.proveedor DROP COLUMN IF EXISTS saldo_actual;
