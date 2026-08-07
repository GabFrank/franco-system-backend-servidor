-- Registra operaciones.movimiento_stock_lote en configuraciones.replication_table para que el
-- scheduler (ReplicationPublicationSyncScheduler -> LogicalReplicationService
-- .syncPublicationsWithReplicationTable) la gestione automaticamente.
--
-- ============================================================================
-- ORDEN DE DESPLIEGUE - CRITICO
-- Esta migracion NO debe aplicarse hasta que TODAS las filiales tengan la tabla creada
-- (filial V81.3). Registrarla antes hace que el sync agregue la tabla a las publicaciones y
-- refresque las suscripciones de las filiales; si la tabla no existe en general6, el subscriber
-- de esa filial rompe.
-- ============================================================================
--
-- Direccion BRANCH_TO_MAIN + replicate_central_to_branch_with_filter = true: EXACTAMENTE la misma
-- configuracion que operaciones.movimiento_stock (registrada en V112, flag activado en V113).
-- El hijo tiene que viajar por el mismo canal que el padre, sino llega desincronizado.
--
--   - Flujo compras: el central inserta con sucursal_id = sucursal de entrega
--     (RecepcionMercaderiaService.generarMovimientoStock) y baja a esa filial por
--     central_filialX_pub con WHERE sucursal_id = X.
--   - Flujo ventas (Fase 2): la filial inserta local y sube al central por su publicacion.
--
-- Nota de diseno: esta tabla es un LEDGER, no un contador. Por eso la replicacion bidireccional
-- es segura: cada fila es un INSERT con PK propia, no hay lost updates. Un
-- cantidad_disponible replicado en ambos sentidos se desviaria en silencio, porque los
-- conflictos de replicacion se saltean.
--
-- Nota ops: el REFRESH PUBLICATION usa copy_data = false, asi que las filas preexistentes no se
-- copian retroactivamente. Para esta tabla es irrelevante: arranca vacia en ambos lados.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('operaciones.movimiento_stock_lote', 'BRANCH_TO_MAIN', 'Movimiento stock por lote', true, true, NOW())
ON CONFLICT (table_name) DO NOTHING;
