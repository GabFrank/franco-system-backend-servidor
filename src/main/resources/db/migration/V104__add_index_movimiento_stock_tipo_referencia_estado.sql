-- Índice para optimizar búsqueda de movimientos por tipo, referencia y estado.
-- Usado por deshacerVerificacion y resetearVerificacion (query findByTipoMovimientoAndReferenciaAndEstadoTrue).
-- La tabla movimiento_stock tiene millones de registros; sin este índice la consulta hace full table scan (~7+ segundos).
CREATE INDEX IF NOT EXISTS movimiento_stock_tipo_ref_estado_idx 
ON operaciones.movimiento_stock (tipo_movimiento, referencia) 
WHERE estado = true;
