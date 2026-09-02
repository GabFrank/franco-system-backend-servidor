-- Backfill de origen_tipo en movimientos historicos de caja — CORRER A MANO, NO ES FLYWAY.
--
-- Contexto. Hasta el fix de F4/F6, PagoProveedorService grababa siempre origen_tipo =
-- 'PAGO_CPP' sin mirar el concepto real, asi que en el dashboard de la caja todo pago
-- figura como "Compra": los gastos, los vales y las liquidaciones tambien. El codigo ya
-- clasifica bien de aca en adelante; esto corrige lo que quedo atras.
--
-- Por que no es una migracion Flyway. Hay precedente de las dos formas: V180.5 hizo un
-- backfill de esta misma columna dentro de la migracion. Pero ese caso era deterministico
-- (FK 1:1, nada que decidir). Aca hay tres clases de fila que NO se pueden derivar:
--
--   · eventos MIXTO — una linea de caja que paga solicitudes de tipos distintos. Para esos
--     'PAGO_CPP' es la clasificacion correcta, no un bug.
--   · RRHH huerfano — solicitud tipo RRHH sin fila en ninguna tabla puente. El codigo en
--     vivo asume VALE por default, pero eso es una heuristica para el flujo en tiempo real,
--     no evidencia para reescribir historia.
--   · pagos anteriores a V182.5, sin fila en pago_solicitud_detalle.
--
-- Una migracion corre ciega al bootear: no hay punto donde alguien vea los conteos antes de
-- aplicar. Y son tres instancias (alpha / farmacia / bodega) con historiales distintos. Si
-- la clasificacion tuviera un error, aca se reedita y se re-corre; en una migracion ya
-- aplicada habria que escribir otra migracion correctiva por algo puramente cosmetico.
--
-- Filial no se toca. financiero.movimiento_caja_virtual no existe en su schema ni esta en
-- ninguna publicacion de replicacion — la caja mayor es central-only.
--
-- Es idempotente: el WHERE filtra por origen_tipo = 'PAGO_CPP', asi que una fila ya
-- corregida deja de matchear. Se puede correr de nuevo sin efecto.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 0 — mirar el terreno antes de tocar nada
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Reparto actual. Sirve de "antes" para comparar al final:
--
--   select origen_tipo, count(*)
--     from financiero.movimiento_caja_virtual
--    group by origen_tipo
--    order by 2 desc;
--
-- Que pasaria si se aplicara: correr la CTE del PASO 1 cambiando el UPDATE por
--
--   select coalesce(nuevo_origen, '(sin cambio)') as destino, count(*)
--     from clasificado group by 1 order by 2 desc;
--
-- Si el numero de '(sin cambio)' es alto, no es un error: son las compras reales, que ya
-- estaban bien etiquetadas, mas las tres clases ambiguas de arriba.

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 1 — reclasificar solo lo que se puede derivar sin ambiguedad
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- El movimiento no tiene FK al pago; el vinculo va por la tabla puente. Para RRHH, el tipo
-- de la solicitud no alcanza (los cuatro conceptos comparten tipo RRHH), asi que se
-- desambigua por cual tabla la reclama — cada una tiene indice unico sobre solicitud_pago_id.
--
-- Todas las condiciones exigen count(distinct sp.tipo) = 1: si el evento pago solicitudes de
-- mas de un tipo, es MIXTO y se deja como esta.

WITH clasificado AS (
  SELECT m.id,
    CASE
      WHEN count(DISTINCT sp.tipo) = 1 AND max(sp.tipo::text) = 'GASTO'
           THEN 'GASTO'
      WHEN count(DISTINCT sp.tipo) = 1 AND max(sp.tipo::text) = 'RRHH'
           AND count(DISTINCT sp.id) = count(DISTINCT v.solicitud_pago_id)
           THEN 'RRHH_VALE'
      WHEN count(DISTINCT sp.tipo) = 1 AND max(sp.tipo::text) = 'RRHH'
           AND count(DISTINCT sp.id) = count(DISTINCT ls.solicitud_pago_id)
           THEN 'RRHH_LIQUIDACION_SUELDO'
      WHEN count(DISTINCT sp.tipo) = 1 AND max(sp.tipo::text) = 'RRHH'
           AND count(DISTINCT sp.id) = count(DISTINCT lf.solicitud_pago_id)
           THEN 'RRHH_LIQUIDACION_FINAL'
      WHEN count(DISTINCT sp.tipo) = 1 AND max(sp.tipo::text) = 'RRHH'
           AND count(DISTINCT sp.id) = count(DISTINCT ag.solicitud_pago_id)
           THEN 'RRHH_AGUINALDO'
      ELSE NULL   -- COMPRA (ya correcto), MIXTO, RRHH huerfano, o sin puente: no tocar
    END AS nuevo_origen
    FROM financiero.movimiento_caja_virtual m
    JOIN financiero.pago_solicitud_detalle psd ON psd.movimiento_caja_virtual_id = m.id
    JOIN operaciones.solicitud_pago sp         ON sp.id = psd.solicitud_pago_id
    LEFT JOIN rrhh.vale               v  ON v.solicitud_pago_id  = sp.id
    LEFT JOIN rrhh.liquidacion_sueldo ls ON ls.solicitud_pago_id = sp.id
    LEFT JOIN rrhh.liquidacion_final  lf ON lf.solicitud_pago_id = sp.id
    LEFT JOIN rrhh.aguinaldo          ag ON ag.solicitud_pago_id = sp.id
   WHERE m.origen_tipo = 'PAGO_CPP'
   GROUP BY m.id
)
UPDATE financiero.movimiento_caja_virtual m
   SET origen_tipo = c.nuevo_origen
  FROM clasificado c
 WHERE m.id = c.id
   AND c.nuevo_origen IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 2 — verificar ANTES de confirmar
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Correr esto todavia dentro de la transaccion y comparar contra el "antes" del PASO 0:
--
--   select origen_tipo, count(*)
--     from financiero.movimiento_caja_virtual
--    group by origen_tipo
--    order by 2 desc;
--
-- Que esperar: PAGO_CPP baja; GASTO y los RRHH_* suben en la misma cantidad. El total
-- general no cambia — esto no crea ni borra movimientos.
--
-- Control de que no se toco nada que no correspondia: los movimientos reclasificados a
-- RRHH_* tienen que tener su documento del lado de RRHH.
--
--   select m.origen_tipo, count(*)
--     from financiero.movimiento_caja_virtual m
--    where m.origen_tipo like 'RRHH_%'
--    group by m.origen_tipo;
--
-- Si algo no cierra: ROLLBACK; y revisar. Si cierra:

COMMIT;
-- ROLLBACK;   -- <- descomentar esta y comentar el COMMIT para un ensayo en seco
