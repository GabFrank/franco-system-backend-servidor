-- Cancelar gasto se ejecuta en el central, pero el balance de caja tambien se
-- calcula en la filial (PdvCajaService.generarBalance). Para que la filial vea la
-- cancelacion, financiero.gasto tiene que bajar del central a la filial dueña.
--
-- La fila ya existe en replication_table desde V112 como BRANCH_TO_MAIN. Activando
-- replicate_central_to_branch_with_filter, LogicalReplicationService agrega la tabla
-- a central_<db>_filialN_pub con WHERE (sucursal_id = N) y refresca las
-- suscripciones. Mismo esquema que financiero.retiro (ver V155.1) y operaciones.venta.
--
-- gasto_detalle no se toca: la cancelacion solo modifica la cabecera.
--
-- REQUISITO DE ORDEN: la filial ya tiene que tener aplicada su migracion de la
-- columna 'cancelado' (V86.1 en franco-system-backend-filial) antes de que esto
-- corra en produccion. Si no, el apply worker de la suscripcion falla y la
-- replicacion queda trabada acumulando WAL.
UPDATE configuraciones.replication_table
SET replicate_central_to_branch_with_filter = true
WHERE table_name = 'financiero.gasto';
