-- Registra operaciones.lote en configuraciones.replication_table para que el scheduler
-- (ReplicationPublicationSyncScheduler -> LogicalReplicationService.syncPublicationsWithReplicationTable)
-- la gestione automaticamente.
--
-- ============================================================================
-- ORDEN DE DESPLIEGUE - CRITICO
-- Esta migracion NO debe aplicarse hasta que TODAS las filiales tengan la tabla creada
-- (filial V82.3). Registrarla antes hace que el sync agregue la tabla a las publicaciones y
-- refresque las suscripciones; si operaciones.lote no existe en general6, el subscriber de esa
-- filial rompe.
-- ============================================================================
--
-- Direccion MAIN_TO_ALL: el maestro de lotes se administra EXCLUSIVAMENTE en el central.
-- Los lotes nacen en la recepcion de mercaderia, que solo ocurre en el central, y el cambio de
-- estado (recall) tambien se hace ahi. La filial solo lee: los necesita para FEFO y para saber
-- que lotes estan bloqueados. Mismo patron que productos.producto y empresarial.impresora.
--
-- Por eso operaciones.lote NO necesita el esquema par/impar de ids que si usa
-- operaciones.movimiento_stock_lote: como la filial nunca inserta lotes, no hay riesgo de
-- colision de PK y alcanza con un BIGSERIAL simple.
--
-- Nota ops: el REFRESH PUBLICATION usa copy_data = false, asi que los lotes YA existentes en el
-- central no bajan retroactivamente. Mientras falten, el LEFT JOIN de la vista v_stock_lote
-- degrada sin romper (se ve el numero de lote y el saldo, quedan nulos vencimiento/retiro/estado).
-- Para bajar lotes preexistentes a una filial, re-guardarlos en el central: eso dispara un UPDATE
-- que si se replica.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('operaciones.lote', 'MAIN_TO_ALL', 'Maestro de lotes de producto', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
