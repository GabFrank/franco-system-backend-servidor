-- Fecha de retiro cargada a mano en la verificacion de la recepcion fisica.
--
-- Por que vive en el item y no solo en operaciones.lote: la verificacion del item y la
-- finalizacion de la recepcion son dos pasos separados en el tiempo. El lote maestro recien se
-- crea al finalizar (RecepcionMercaderiaService.generarMovimientoStock ->
-- MovimientoStockLoteService.registrarEntradaCompra), leyendo del item ya guardado. Sin esta
-- columna el dato que carga el operador se pierde entre un paso y el otro.
--
-- Es opcional: cuando queda NULL, LoteService sigue derivando la fecha de retiro de
-- (vencimiento - producto.dias_vencimiento), que es el comportamiento actual. Solo cuando el
-- operador escribe una fecha esa pisa al calculo.
--
-- operaciones.recepcion_mercaderia_item NO esta registrada en configuraciones.replication_table,
-- asi que agregar la columna no toca ninguna publicacion ni suscripcion.
ALTER TABLE operaciones.recepcion_mercaderia_item
    ADD COLUMN IF NOT EXISTS fecha_retiro DATE;

COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.fecha_retiro IS
    'Fecha de retiro cargada manualmente en la verificacion. NULL = derivar de dias_vencimiento.';
