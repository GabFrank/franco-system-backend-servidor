-- Control de lotes - habilita el canal central -> filial del ledger por lote.
--
-- operaciones.movimiento_stock_lote esta registrada en configuraciones.replication_table (id 95)
-- como BRANCH_TO_MAIN + replicate_central_to_branch_with_filter = true, exactamente igual que
-- operaciones.movimiento_stock (85), operaciones.venta (13) y operaciones.venta_item (14). Pero a
-- diferencia de esas tres, NUNCA se agrego a las publicaciones de bajada: la configuracion estaba
-- bien y lo que faltaba era aplicarla.
--
-- Verificado en produccion farmacia el 2026-09-08: el central tenia el lote 03552 con 373 unidades
-- para SUC. III y la filial no tenia ni una sola fila con lote_id. Las entradas por compra las
-- registra el central (MovimientoStockLoteService.registrarEntradaCompra corre alli), asi que sin
-- este canal la filial solo ve las salidas que ella misma escribe: el ledger le queda en negativo,
-- LoteFefoService descarta esas lineas por venir sin lote_id y el POS reporta que no hay stock por
-- lotes aunque el central muestre cientos de unidades.
--
-- Las migraciones V82.3 y V87.3 de la filial documentan como premisa que el ledger "baja por
-- central_%_filial%_pub filtrada por sucursal". Esta migracion es lo que hace verdadera esa
-- premisa; hasta ahora nunca se habia cumplido.
--
-- ============================================================================
-- Por que se descubren las publicaciones en vez de nombrarlas
--
-- Los nombres no siguen un patron unico: conviven central_filial1_pub, central_filial4_pub y
-- central_filial_farmacia_6_pub. Peor todavia, el numero del nombre NO es el sucursal_id -
-- central_filial_farmacia_6_pub filtra por sucursal_id = 7 (SUC. II). Cualquier lista hardcodeada
-- o cualquier patron sobre el nombre queda mal en cuanto se da de alta una boca nueva.
--
-- El criterio que si es estable: la publicacion de bajada de una sucursal es la que ya publica la
-- tabla PADRE operaciones.movimiento_stock con un row filter. central_pub (MAIN_TO_ALL) no tiene
-- row filter y queda descartada sola, que es lo correcto: el ledger no debe ir entero a todas las
-- bocas.
--
-- El row filter se copia TAL CUAL del padre en vez de construirse. Asi la hija hereda siempre el
-- mismo recorte que la madre, y si manana el criterio cambia (por ejemplo agregar deposito), no
-- hay dos lugares que mantener sincronizados a mano.
--
-- Nota de operaciones: despues de esto cada filial necesita REFRESH PUBLICATION para tomar la
-- tabla. Lo hace solo ReplicationRefreshScheduler. Como va con copy_data = false, engancha el
-- stream de ahi en adelante y no copia lo previo; el backfill de lo viejo lo hace la filial en su
-- V93.3, por dblink y con ON CONFLICT DO NOTHING.
-- ============================================================================
DO $$
DECLARE
    r            RECORD;
    v_agregadas  INT := 0;
    v_ya_estaban INT := 0;
BEGIN
    FOR r IN
        SELECT pt.pubname, pt.rowfilter
        FROM pg_publication_tables pt
        WHERE pt.schemaname = 'operaciones'
          AND pt.tablename  = 'movimiento_stock'
          AND pt.rowfilter IS NOT NULL
        ORDER BY pt.pubname
    LOOP
        -- Idempotencia: ALTER PUBLICATION ADD TABLE sobre una tabla ya publicada es un error, no
        -- un no-op, asi que se pregunta antes. Permite re-correr el bloque a mano sin romper nada.
        IF EXISTS (
            SELECT 1 FROM pg_publication_tables x
            WHERE x.pubname    = r.pubname
              AND x.schemaname = 'operaciones'
              AND x.tablename  = 'movimiento_stock_lote'
        ) THEN
            v_ya_estaban := v_ya_estaban + 1;
            CONTINUE;
        END IF;

        -- El row filter va sin comillas por ser una expresion, no un identificador: pg_publication_tables
        -- lo devuelve ya parentizado, del estilo (sucursal_id = 4).
        EXECUTE format(
            'ALTER PUBLICATION %I ADD TABLE operaciones.movimiento_stock_lote WHERE %s',
            r.pubname, r.rowfilter);

        RAISE NOTICE 'Agregada operaciones.movimiento_stock_lote a % con filtro %',
                     r.pubname, r.rowfilter;
        v_agregadas := v_agregadas + 1;
    END LOOP;

    IF v_agregadas = 0 AND v_ya_estaban = 0 THEN
        RAISE NOTICE 'No se encontraron publicaciones de bajada por sucursal - se omite '
                     '(replicacion todavia no configurada en esta base)';
    ELSE
        RAISE NOTICE 'Publicaciones actualizadas: % agregadas, % ya la tenian',
                     v_agregadas, v_ya_estaban;
    END IF;
END $$;
