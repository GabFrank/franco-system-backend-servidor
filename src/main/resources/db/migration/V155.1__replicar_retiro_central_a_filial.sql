-- Cancelar retiro se ejecuta en el central, pero el balance de caja tambien se
-- calcula en la filial (PdvCajaService.generarBalance). Para que la filial vea la
-- cancelacion, financiero.retiro tiene que bajar del central a la filial dueña.
--
-- La fila ya existe en replication_table desde V112 como BRANCH_TO_MAIN. Activando
-- replicate_central_to_branch_with_filter, LogicalReplicationService agrega la tabla
-- a central_<db>_filialN_pub con WHERE (sucursal_id = N) y refresca las suscripciones.
-- Mismo esquema que operaciones.venta (de ahi que cancelarVenta ya funcione asi) y
-- que financiero.venta_tarjeta (ver V150.1).
--
-- retiro_detalle no se toca: la cancelacion solo modifica la cabecera.
UPDATE configuraciones.replication_table
SET replicate_central_to_branch_with_filter = true
WHERE table_name = 'financiero.retiro';
