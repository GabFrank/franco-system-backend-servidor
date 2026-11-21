-- Descripcion de los movimientos de operaciones financieras con el comprobante y la otra punta (desktop #721).
--
-- Antes: banco «Deposito bancario (op #116)» y caja el enum crudo «DEPOSITO_BANCARIO (op #116)». Ahora, la misma
-- regla que OperacionFinancieraDescripcion.java:
--   «Deposito · Boleta 2443309 · COFRE CENTRAL» (banco) / «Deposito · Boleta 2443309 · BNF 019001922421» (caja).
--   Sin comprobante: «Deposito (op #116) · COFRE CENTRAL». La otra punta es la caja o la cuenta del otro lado.
--   El lado sale del tipo de movimiento: egreso/salida = origen (un cambio en la misma caja la tiene en los dos).
--
-- Solo la columna descripcion de los movimientos con origen_tipo = 'OPERACION_FINANCIERA', y no el AJUSTE de la
-- diferencia (lleva su propio rotulo). Las reversas son origen_tipo = 'ANULACION' y no se tocan. Nunca montos ni
-- saldos.
--
-- Aditiva, idempotente (solo donde la nueva difiere) y reversible desde descripcion_original:
--   UPDATE financiero.movimiento_bancario     SET descripcion = descripcion_original WHERE descripcion_original IS NOT NULL;
--   UPDATE financiero.movimiento_caja_virtual SET descripcion = descripcion_original WHERE descripcion_original IS NOT NULL;
-- Central-only: ninguna de las dos tablas esta en una publicacion.

ALTER TABLE financiero.movimiento_bancario ADD COLUMN IF NOT EXISTS descripcion_original varchar(300);
ALTER TABLE financiero.movimiento_caja_virtual ADD COLUMN IF NOT EXISTS descripcion_original text;

COMMENT ON COLUMN financiero.movimiento_bancario.descripcion_original IS
    'Descripcion anterior a V277.5 (desktop #721), solo si cambio. Solo auditoria y reversa por SQL.';
COMMENT ON COLUMN financiero.movimiento_caja_virtual.descripcion_original IS
    'Descripcion anterior a V277.5 (desktop #721), solo si cambio. Solo auditoria y reversa por SQL.';

DO $$
DECLARE
    esperadas_banco integer;
    esperadas_caja  integer;
    hechas_banco    integer;
    hechas_caja     integer;
BEGIN
    -- Nombre de cada punta de cada operacion: la caja, o «BANCO NUMERO» de la cuenta (si falta el banco, el numero;
    -- si falta el numero, el nombre de la cuenta).
    DROP TABLE IF EXISTS v2775_op;
    CREATE TEMP TABLE v2775_op ON COMMIT DROP AS
    SELECT o.id,
           CASE o.tipo_operacion
               WHEN 'DEPOSITO_BANCARIO'         THEN 'Depósito'
               WHEN 'RETIRO_BANCARIO'           THEN 'Retiro bancario'
               WHEN 'TRANSFERENCIA_BANCARIA'    THEN 'Transf. bancaria'
               WHEN 'TRANSFERENCIA_ENTRE_CAJAS' THEN 'Transf. entre cajas'
               WHEN 'CAMBIO_DIVISA'             THEN 'Cambio de divisa'
               ELSE 'Operación financiera'
           END
           || CASE WHEN nullif(trim(o.numero_comprobante), '') IS NULL THEN ' (op #' || o.id || ')'
                   ELSE CASE WHEN o.tipo_operacion = 'DEPOSITO_BANCARIO' THEN ' · Boleta ' ELSE ' · Comp. ' END
                        || trim(o.numero_comprobante)
              END AS cabeza,
           coalesce(co.nombre,
                    CASE WHEN bo.nombre IS NOT NULL AND cbo.numero IS NOT NULL THEN bo.nombre || ' ' || cbo.numero END,
                    cbo.numero, cbo.nombre) AS nombre_origen,
           coalesce(cd.nombre,
                    CASE WHEN bd.nombre IS NOT NULL AND cbd.numero IS NOT NULL THEN bd.nombre || ' ' || cbd.numero END,
                    cbd.numero, cbd.nombre) AS nombre_destino
      FROM financiero.operacion_financiera o
      LEFT JOIN financiero.caja_virtual    co  ON co.id  = o.caja_mayor_origen_id
      LEFT JOIN financiero.caja_virtual    cd  ON cd.id  = o.caja_mayor_destino_id
      LEFT JOIN financiero.cuenta_bancaria cbo ON cbo.id = o.cuenta_bancaria_origen_id
      LEFT JOIN financiero.cuenta_bancaria cbd ON cbd.id = o.cuenta_bancaria_destino_id
      LEFT JOIN financiero.banco           bo  ON bo.id  = cbo.banco_id
      LEFT JOIN financiero.banco           bd  ON bd.id  = cbd.banco_id;

    DROP TABLE IF EXISTS v2775_banco;
    CREATE TEMP TABLE v2775_banco ON COMMIT DROP AS
    SELECT m.id,
           op.cabeza || coalesce(' · ' || nullif(
               CASE WHEN m.tipo_movimiento = 'SALIDA_MANUAL' THEN op.nombre_destino ELSE op.nombre_origen END, ''), '')
               AS nueva
      FROM financiero.movimiento_bancario m
      JOIN v2775_op op ON op.id = m.origen_id
     WHERE m.origen_tipo = 'OPERACION_FINANCIERA';
    DELETE FROM v2775_banco b USING financiero.movimiento_bancario m
     WHERE m.id = b.id AND m.descripcion IS NOT DISTINCT FROM b.nueva;

    DROP TABLE IF EXISTS v2775_caja;
    CREATE TEMP TABLE v2775_caja ON COMMIT DROP AS
    SELECT m.id,
           op.cabeza || coalesce(' · ' || nullif(
               CASE WHEN m.tipo_movimiento IN ('EGRESO', 'TRANSFERENCIA_SALIDA') THEN op.nombre_destino
                    ELSE op.nombre_origen END, ''), '')
               AS nueva
      FROM financiero.movimiento_caja_virtual m
      JOIN v2775_op op ON op.id = m.origen_id
     WHERE m.origen_tipo = 'OPERACION_FINANCIERA'
       AND m.tipo_movimiento <> 'AJUSTE';
    DELETE FROM v2775_caja c USING financiero.movimiento_caja_virtual m
     WHERE m.id = c.id AND m.descripcion IS NOT DISTINCT FROM c.nueva;

    SELECT count(*) INTO esperadas_banco FROM v2775_banco;
    SELECT count(*) INTO esperadas_caja  FROM v2775_caja;

    UPDATE financiero.movimiento_bancario m
       SET descripcion_original = coalesce(m.descripcion_original, m.descripcion),
           descripcion          = left(b.nueva, 300)
      FROM v2775_banco b
     WHERE m.id = b.id;
    GET DIAGNOSTICS hechas_banco = ROW_COUNT;

    UPDATE financiero.movimiento_caja_virtual m
       SET descripcion_original = coalesce(m.descripcion_original, m.descripcion),
           descripcion          = c.nueva
      FROM v2775_caja c
     WHERE m.id = c.id;
    GET DIAGNOSTICS hechas_caja = ROW_COUNT;

    IF hechas_banco <> esperadas_banco OR hechas_caja <> esperadas_caja THEN
        RAISE EXCEPTION 'V277.5: banco % de %, caja % de %', hechas_banco, esperadas_banco, hechas_caja, esperadas_caja;
    END IF;

    RAISE NOTICE 'V277.5: descripciones reescritas: % de banco, % de caja', hechas_banco, hechas_caja;
END $$;
