-- =====================================================================
-- RRHH — Número fijo de operación en el catálogo de conceptos
-- =====================================================================
-- Atajo al cargar un ítem de liquidación: se tipea el número y se elige la
-- operación (ej. 1 = AJUSTE (HABER)). Es fijo: no se corre al agregar o
-- desactivar operaciones. Único solo entre activos, así desactivar una
-- operación libera su número (el ABM no borra, solo desactiva).
-- Aditivo. rrhh.liquidacion_concepto es central-only (no se replica).
-- =====================================================================

ALTER TABLE rrhh.liquidacion_concepto ADD COLUMN IF NOT EXISTS numero INTEGER;

-- Numeración inicial: los activos no automáticos, en el orden en que hoy los muestra
-- el select (es_haber desc, descripcion), con codigo (UNIQUE) como desempate. La base
-- MAX(numero) hace que un re-run no choque con números ya puestos.
UPDATE rrhh.liquidacion_concepto c
   SET numero = n.rn + COALESCE((SELECT MAX(numero) FROM rrhh.liquidacion_concepto), 0)
  FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY es_haber DESC, descripcion, codigo) AS rn
          FROM rrhh.liquidacion_concepto
         WHERE activo AND NOT es_calculado_auto AND numero IS NULL) n
 WHERE c.id = n.id;

-- Después del UPDATE: el índice ya encuentra los números únicos.
CREATE UNIQUE INDEX IF NOT EXISTS uq_liquidacion_concepto_numero_activo
    ON rrhh.liquidacion_concepto(numero) WHERE numero IS NOT NULL AND activo;

COMMENT ON COLUMN rrhh.liquidacion_concepto.numero IS
    'Numero fijo de la operacion para cargar items de liquidacion (atajo). Unico entre activos.';
