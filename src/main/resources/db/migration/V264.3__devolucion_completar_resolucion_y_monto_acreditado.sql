-- Issue #208: completa los dos campos que quedaban NULL en las devoluciones ya cerradas.
-- Solo toca filas en NULL, asi que se puede correr de nuevo sin efecto.

-- 1) Descartadas: resolucion = DESCARTE.
UPDATE operaciones.devolucion
SET resolucion = 'DESCARTE'
WHERE estado = 'DESCARTADO'
  AND resolucion IS NULL;

-- 2) Acreditadas con nota de credito consolidada: monto_acreditado = su parte de la nota.
-- Mismo reparto que NotaCreditoDevolucionService.repartirMonto: cada devolucion pesa lo
-- que valen sus items al costo por unidad base que la nota le dio a cada producto
-- (si ninguno esta en la nota, por cantidad base; si tampoco, en partes iguales), y la
-- ultima se lleva el resto para que la suma de exacto el total de la nota.
-- Solo notas cuyas devoluciones acreditadas tienen todas el monto en NULL.
WITH costo AS (
    SELECT nci.nota_credito_id, nci.producto_id,
           SUM(nci.total) / NULLIF(SUM(nci.cantidad_base), 0) AS costo_base
    FROM operaciones.nota_credito_devolucion_item nci
    GROUP BY nci.nota_credito_id, nci.producto_id
),
peso AS (
    SELECT n.id AS nota_id, n.monto_total, d.id AS devolucion_id,
           COALESCE(SUM(di.cantidad * COALESCE(p.cantidad, 1) * c.costo_base), 0) AS valor,
           COALESCE(SUM(di.cantidad * COALESCE(p.cantidad, 1)), 0) AS cantidad
    FROM operaciones.nota_credito_devolucion n
    JOIN operaciones.devolucion d ON d.retiro_id = n.retiro_id AND d.estado = 'ACREDITADO'
    LEFT JOIN operaciones.devolucion_item di ON di.devolucion_id = d.id
    LEFT JOIN productos.presentacion p ON p.id = di.presentacion_id
    LEFT JOIN costo c ON c.nota_credito_id = n.id AND c.producto_id = di.producto_id
    WHERE NOT EXISTS (SELECT 1 FROM operaciones.devolucion d2
                      WHERE d2.retiro_id = n.retiro_id AND d2.estado = 'ACREDITADO'
                        AND d2.monto_acreditado IS NOT NULL)
    GROUP BY n.id, n.monto_total, d.id
),
elegido AS (
    SELECT nota_id, monto_total, devolucion_id,
           CASE WHEN SUM(valor) OVER (PARTITION BY nota_id) > 0 THEN valor ELSE cantidad END AS w,
           CASE WHEN SUM(valor) OVER (PARTITION BY nota_id) > 0
                THEN SUM(valor) OVER (PARTITION BY nota_id)
                ELSE SUM(cantidad) OVER (PARTITION BY nota_id) END AS w_total,
           COUNT(*) OVER (PARTITION BY nota_id) AS n,
           ROW_NUMBER() OVER (PARTITION BY nota_id ORDER BY devolucion_id DESC) AS desde_el_final
    FROM peso
),
parte AS (
    SELECT nota_id, monto_total, devolucion_id, desde_el_final,
           CASE WHEN w_total > 0 THEN monto_total * w / w_total ELSE monto_total / n END AS monto
    FROM elegido
),
final AS (
    SELECT devolucion_id,
           CASE WHEN desde_el_final = 1
                THEN monto_total - COALESCE(SUM(monto) FILTER (WHERE desde_el_final > 1)
                                            OVER (PARTITION BY nota_id), 0)
                ELSE monto END AS monto
    FROM parte
)
UPDATE operaciones.devolucion d
SET monto_acreditado = f.monto
FROM final f
WHERE d.id = f.devolucion_id
  AND d.monto_acreditado IS NULL;
