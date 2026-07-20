-- Registrar empresarial.impresora en configuraciones.replication_table para que el scheduler de
-- replicacion la gestione automaticamente. Sin esto, la tabla se creo (V143.3) y se agrego a mano
-- a central_pub, pero al NO estar registrada en replication_table el scheduler
-- (ReplicationPublicationSyncScheduler -> LogicalReplicationService.syncPublicationsWithReplicationTable)
-- no la considera y por lo tanto no refresca las suscripciones de las filiales via JDBC.
-- Resultado: las impresoras creadas en el central no bajan a las sucursales (ej. km2).
--
-- Direccion MAIN_TO_ALL (unidireccional central -> todas las filiales): el registro de impresoras
-- se administra y escribe SOLO en el servidor central (la filial no tiene mutation saveImpresora ni
-- generador de id; el desktop guarda siempre con clientName="servidor") y se replica completo a cada
-- filial, que lo necesita para el routing de impresion (la impresora "sabe" en que sucursal esta).
-- Patron identico a V142.1__registrar_replicacion_terminal_pos.sql.
--
-- Nota ops: REFRESH PUBLICATION usa copy_data = false, asi que las filas de impresora YA existentes
-- en el central no se copian retroactivamente; solo se replican los cambios (INSERT/UPDATE/DELETE)
-- posteriores al refresh. Para bajar impresoras preexistentes a una filial, re-guardar el registro
-- en el central (dispara un UPDATE que se replica) o hacer una copia puntual.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('empresarial.impresora', 'MAIN_TO_ALL', 'Impresoras', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
