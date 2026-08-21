-- Marcar los retiros historicos como YA INGRESADOS, para que dejen de figurar en la
-- lista de "retiros flotantes" del hub de caja mayor.
--
-- CORRER A MANO, NO ES UNA MIGRACION FLYWAY. Toca decenas de miles de filas de dos bases
-- productivas y en farmacia se replica a las filiales; tiene que correrse mirando, por lote,
-- no al azar de un deploy.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- EL PROBLEMA
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- El mecanismo de ingresar un retiro del PDV a una caja mayor es nuevo. Todos los retiros
-- anteriores quedaron "flotando" y aparecen en la lista, que los trae todos porque ninguno
-- esta marcado. Con ese ruido la pantalla es inusable y no se pueden ingresar los retiros
-- reales del dia.
--
-- La lista sale de RetiroRepository.findFlotantes:
--
--   where caja_virtual_id is null
--     and movimiento_caja_virtual_id is null
--     and (estado is null or estado <> 'EN_PROCESO')
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- LA ESTRATEGIA, Y LA TRAMPA QUE EVITA
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Se escribe el centinela -1 en `movimiento_caja_virtual_id` y se DEJA `caja_virtual_id` EN
-- NULL.
--
-- ⚠️ NO setear `caja_virtual_id`. El guard del poller (RetiroTesoreriaProcesador.procesar) es
--    `cajaVirtualId != null && movimientoCajaVirtualId == null`. Un UPDATE que asigne caja y
--    deje el movimiento en null convierte a las ~65.000 filas en trabajo pendiente del poller:
--    si `tesoreria.retiro-poller.enabled` esta en true, postea decenas de miles de INGRESOS
--    falsos en la caja mayor. Con el centinela en el movimiento, el retiro nunca califica.
--
-- Por que -1 y no un id real: la columna NO tiene FK (verificado en bodega y farmacia el
-- 2026-08-21) y el schema la expone como escalar plano (`movimientoCajaVirtualId: Int`), sin
-- resolver que cargue el movimiento; el desktop ni la lee. Un valor imposible ademas hace la
-- operacion perfectamente identificable y reversible (PASO 3).
--
-- Efecto colateral deseado: RetiroIngresoService.ingresarACajaMayor rechaza con
-- "El retiro #N ya fue ingresado a una caja mayor" si alguien intenta ingresarlo. Correcto:
-- esa plata ya se manejo fuera del sistema.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- REPLICACION — LAS DOS BASES NO SE COMPORTAN IGUAL
-- ─────────────────────────────────────────────────────────────────────────────────────────
--  · bodega (5552): `financiero.retiro` NO esta en ninguna publicacion. El UPDATE no viaja.
--  · farmacia (5551): SI esta, en `central_filial{1,3,4}_pub` y
--    `central_filial_farmacia_{5,6}_pub`, con filtro `sucursal_id = N`. El UPDATE se replica,
--    cada filial recibe solo sus filas.
--    Verificado el 2026-08-21: las filiales 1, 3, 4 y 6 TIENEN la columna
--    `movimiento_caja_virtual_id`, asi que no hay drift de schema. La filial 5 esta apagada:
--    su slot va a acumular este WAL hasta que se encienda, sumando a lo que ya retiene.
--
-- Por eso el UPDATE va POR LOTES con commit intermedio: un unico UPDATE de 10.000+ filas
-- genera un pico de WAL que las filiales tienen que digerir de golpe.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 0 — fotografiar antes de tocar (correr en cada base)
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- select count(*) as flotantes_total,
--        count(*) filter (where creado_en < date_trunc('day', now())) as anteriores_a_hoy,
--        count(*) filter (where creado_en is null)                    as sin_fecha,
--        count(*) filter (where creado_en >= date_trunc('day', now())) as de_hoy_se_conservan,
--        min(creado_en)::date as mas_viejo
--   from financiero.retiro
--  where caja_virtual_id is null
--    and movimiento_caja_virtual_id is null
--    and (estado is null or estado <> 'EN_PROCESO');
--
-- Medido el 2026-08-21:  bodega 54.274 (54.256 viejos, 18 de hoy)
--                        farmacia 10.589 (10.572 viejos + 14 sin fecha, 3 de hoy)


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 1 — marcar por lotes
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Corte: todo lo anterior a HOY 00:00 (hora del servidor). Los retiros de hoy se conservan
-- flotantes a proposito — son los que se quieren poder ingresar.
-- `creado_en is null` se incluye: son historicos sin fecha (14 en farmacia), y si no se
-- marcan quedan para siempre en la lista porque nunca matchean un corte por fecha.
--
-- Ejecutar SIN envolver en una transaccion explicita (el bloque commitea por lote).

DO $$
DECLARE
    lote     int := 2000;
    afectadas int;
    total     int := 0;
BEGIN
    LOOP
        -- ⚠️ La PK de las transaccionales es COMPUESTA (id, sucursal_id): en farmacia hay
        --    decenas de miles de `id` repetidos entre sucursales. Filtrar por `id IN (...)`
        --    marca TAMBIEN las filas homonimas de otras sucursales — incluidas las ya
        --    ingresadas, cuyo `movimiento_caja_virtual_id` real quedaria pisado por -1.
        --    El join va SIEMPRE por las dos columnas.
        UPDATE financiero.retiro r
           SET movimiento_caja_virtual_id = -1
          FROM (
                 SELECT id, sucursal_id
                   FROM financiero.retiro
                  WHERE caja_virtual_id IS NULL
                    AND movimiento_caja_virtual_id IS NULL
                    AND (estado IS NULL OR estado <> 'EN_PROCESO')
                    AND (creado_en IS NULL OR creado_en < date_trunc('day', now()))
                  LIMIT lote
               ) x
         WHERE r.id = x.id
           AND r.sucursal_id = x.sucursal_id;

        GET DIAGNOSTICS afectadas = ROW_COUNT;
        EXIT WHEN afectadas = 0;

        total := total + afectadas;
        COMMIT;
        RAISE NOTICE 'marcados % (acumulado %)', afectadas, total;

        -- respiro para que los apply workers de las filiales no se atrasen (solo importa en farmacia)
        PERFORM pg_sleep(0.2);
    END LOOP;

    RAISE NOTICE 'LISTO: % retiros historicos marcados', total;
END $$;


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 2 — verificar
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Lo que sigue en la lista (tiene que ser SOLO lo de hoy):
--
-- select count(*), min(creado_en)::date, max(creado_en)::date
--   from financiero.retiro
--  where caja_virtual_id is null
--    and movimiento_caja_virtual_id is null
--    and (estado is null or estado <> 'EN_PROCESO');
--
-- Lo marcado por este script:
--
-- select count(*) from financiero.retiro where movimiento_caja_virtual_id = -1;
--
-- Los tres controles de que no se marco de mas (los tres tienen que dar 0):
--
-- select count(*) from financiero.retiro
--  where movimiento_caja_virtual_id = -1 and caja_virtual_id is not null;   -- ya ingresados, pisados
-- select count(*) from financiero.retiro
--  where movimiento_caja_virtual_id = -1 and creado_en >= date_trunc('day', now());  -- de hoy
-- select count(*) from financiero.retiro
--  where movimiento_caja_virtual_id = -1 and estado = 'EN_PROCESO';         -- abiertos en el PDV
--
-- Estos tres son el detector del error de PK compuesta: si el join se hizo solo por `id`,
-- alguno da distinto de 0.
--
-- En farmacia, ademas, confirmar que la replicacion sobrevivio:
--
-- select s.subname, (st.pid is not null) as worker, st.last_msg_receipt_time
--   from pg_subscription s left join pg_stat_subscription st using (subname)
--  where s.subname not like 'bodega%';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 3 — deshacer (el centinela hace esto trivial)
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- update financiero.retiro
--    set movimiento_caja_virtual_id = null
--  where movimiento_caja_virtual_id = -1;
--
-- Ningun retiro legitimamente ingresado puede tener -1 (los reales llevan el id del
-- MovimientoCajaVirtual que genero el posteo), asi que el revert es exacto y no puede
-- desmarcar de mas.
