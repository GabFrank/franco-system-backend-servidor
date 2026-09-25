-- Gastos pagados desde la caja mayor: materializarlos en financiero.gasto.
--
-- Un gasto de tesoreria nace como operaciones.solicitud_pago (tipo GASTO) y se salda contra una
-- caja virtual. Vivia solo ahi, asi que no aparecia ni en el grafico "Gastos por Categoria" ni en
-- la lista de gastos: las dos leen financiero.gasto. Se materializa en la sucursal 0 (SERVIDOR),
-- sin caja ni responsable, vinculado a su solicitud por solicitud_pago_id.
--
-- De aca en adelante lo mantiene GastoTesoreriaService (al pagar y al anular). Esta migracion
-- agrega la columna y trae los gastos ya pagados.
--
-- Central-only: operaciones.solicitud_pago no existe en las filiales.

ALTER TABLE financiero.gasto
    ADD COLUMN IF NOT EXISTS solicitud_pago_id bigint;

COMMENT ON COLUMN financiero.gasto.solicitud_pago_id IS
    'Solicitud de pago (CPP) que origino el gasto cuando se pago desde la caja mayor. NULL en los gastos de caja fisica.';

-- A lo sumo un gasto por solicitud: es lo que hace idempotente la sincronizacion.
CREATE UNIQUE INDEX IF NOT EXISTS uk_gasto_solicitud_pago
    ON financiero.gasto (solicitud_pago_id)
    WHERE solicitud_pago_id IS NOT NULL;

-- Backfill de los gastos ya pagados desde la caja mayor.
-- El id es correlativo dentro de la sucursal 0, como el resto de las claves compuestas.
INSERT INTO financiero.gasto (
    id, sucursal_id, solicitud_pago_id, tipo_gasto_id, usuario_id, observacion,
    creado_en, activo, finalizado, cancelado,
    retiro_gs, retiro_rs, retiro_ds, vuelto_gs, vuelto_rs, vuelto_ds
)
SELECT
    (SELECT COALESCE(MAX(g.id), 0) FROM financiero.gasto g WHERE g.sucursal_id = 0)
        + ROW_NUMBER() OVER (ORDER BY sp.id),
    0,
    sp.id,
    sp.tipo_gasto_id,
    sp.usuario_id,
    '#' || sp.id || ' - ' || COALESCE(NULLIF(per.nombre, ''), '—') || ' - ' || COALESCE(sp.observaciones, ''),
    COALESCE(p.creado_en, sp.fecha_solicitud),
    true,
    true,
    false,
    CASE WHEN sp.moneda_id = 1 THEN sp.monto_pagado ELSE 0 END,
    CASE WHEN sp.moneda_id = 2 THEN sp.monto_pagado ELSE 0 END,
    CASE WHEN sp.moneda_id = 3 THEN sp.monto_pagado ELSE 0 END,
    0, 0, 0
FROM operaciones.solicitud_pago sp
LEFT JOIN operaciones.pago p       ON p.id  = sp.pago_id
LEFT JOIN personas.proveedor pr    ON pr.id = sp.proveedor_id
LEFT JOIN personas.persona per     ON per.id = pr.persona_id
WHERE sp.tipo = 'GASTO'
  AND COALESCE(sp.monto_pagado, 0) > 0
  AND NOT EXISTS (
      SELECT 1 FROM financiero.gasto g WHERE g.solicitud_pago_id = sp.id
  );
