-- Los resolvers de campo MovimientoCajaVirtual.esPagoConsolidado y MovimientoBancario.pagoId buscan
-- el detalle por el movimiento que posteo, una vez por fila de la pagina de movimientos. Sin indice
-- cada busqueda recorre la tabla entera. Parciales: cada linea lleva a lo sumo una de las dos columnas.
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_mov_caja
    ON financiero.pago_solicitud_detalle (movimiento_caja_virtual_id)
    WHERE movimiento_caja_virtual_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_mov_banco
    ON financiero.pago_solicitud_detalle (movimiento_bancario_id)
    WHERE movimiento_bancario_id IS NOT NULL;
