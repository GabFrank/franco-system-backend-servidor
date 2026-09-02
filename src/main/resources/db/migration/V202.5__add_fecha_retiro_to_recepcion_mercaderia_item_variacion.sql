-- Fecha de retiro por variacion de un item de recepcion.
--
-- Una variacion es "una parte de lo recibido que tiene su propio lote": es como se registra que
-- del mismo producto bajaron del camion dos lotes distintos. El desglose por lote
-- (MovimientoStockLoteService.desglosarVariaciones) crea una fila de stock por variacion y llama
-- a LoteService.obtenerOCrear con las fechas de esa variacion.
--
-- Hasta ahora esa llamada pasaba fechaRetiro = null y el retiro se derivaba siempre de
-- (vencimiento - producto.dias_vencimiento). La verificacion en mobile carga la fecha de retiro a
-- mano, igual que la verificacion detallada del desktop (V158.3 agrego la columna equivalente en
-- recepcion_mercaderia_item), asi que sin esta columna ese dato se perdia entre la verificacion y
-- la finalizacion de la recepcion.
--
-- Es opcional: NULL mantiene el comportamiento historico (derivar de dias_vencimiento).
--
-- operaciones.recepcion_mercaderia_item_variacion NO esta registrada en
-- configuraciones.replication_table, asi que agregar la columna no toca ninguna publicacion.
ALTER TABLE operaciones.recepcion_mercaderia_item_variacion
    ADD COLUMN IF NOT EXISTS fecha_retiro DATE;

COMMENT ON COLUMN operaciones.recepcion_mercaderia_item_variacion.fecha_retiro IS
    'Fecha de retiro cargada manualmente en la verificacion. NULL = derivar de dias_vencimiento.';
