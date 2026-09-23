-- Índices de las dos FK que V227.1 y V228.1 agregaron a documento_electronico.
--
-- Postgres NO crea índice automáticamente del lado que referencia. Sin ellos:
--   * findByNotaRemisionId / findByNotaCreditoId (el camino que usa el desktop para mostrar,
--     reenviar y anular el DE de cada nota) hacen seq scan sobre documento_electronico,
--   * y cada UPDATE/DELETE en nota_remision / nota_credito obliga a barrer la tabla entera
--     para chequear la FK.
-- No se nota hoy porque hay pocas notas; se nota cuando documento_electronico crece, que es
-- todos los días.
--
-- Parciales: la enorme mayoría de las filas son facturas y tienen las dos columnas en NULL,
-- así que el índice queda chico y no paga el costo de indexar lo que nunca se consulta.

CREATE INDEX IF NOT EXISTS idx_documento_electronico_nota_remision
    ON financiero.documento_electronico (nota_remision_id, sucursal_id)
    WHERE nota_remision_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documento_electronico_nota_credito
    ON financiero.documento_electronico (nota_credito_id, sucursal_id)
    WHERE nota_credito_id IS NOT NULL;
