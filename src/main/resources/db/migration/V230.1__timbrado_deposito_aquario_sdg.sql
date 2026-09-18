-- El DEPOSITO AQUARIO SDG (sucursal 13) no podía emitir notas de remisión: sus dos
-- timbrado_detalle apuntan a timbrados inactivos y no electrónicos, así que
-- `prellenarNotaRemision` fallaba y el diálogo se abría vacío.
--
-- Se le da el timbrado de la central REUSANDO SU MISMO id (105). La PK de timbrado_detalle es
-- compuesta (id, sucursal_id), así que (105, 1) y (105, 13) son dos filas válidas, y el sistema
-- ya tiene ese patrón: los ids 89 y 93 están compartidos entre sucursales.
--
-- Por qué reusar el id y no crear uno nuevo: la serie de las notas se controla con
--   nota_remision.uk_nota_remision_numero UNIQUE (timbrado_detalle_id, numero_nota_remision)
-- y con findMaxNumeroByTimbradoDetalleId(id), las dos SIN sucursal. Compartiendo el id, las dos
-- sucursales comparten la serie, que es lo pedido. Con un id nuevo tendrían series separadas
-- declarando el mismo establecimiento y punto ante la SET: numeración duplicada.
--
-- ⚠️ punto_de_venta_id va en NULL A PROPOSITO. La FACTURA resuelve su timbrado con
-- findFirstByPuntoDeVentaId(pdvId) y numera con `numero_actual + 1`, un contador POR FILA. Si
-- esta fila fuera alcanzable desde el camino de facturación, la sucursal 13 numeraría facturas
-- con su propio contador declarando el establecimiento 001 de la central: números duplicados
-- ante la SET. Con el campo en NULL esa query nunca la encuentra. No es precaución teórica: la
-- sucursal 13 tiene un PDV.
--
-- ⚠️ La dirección es la de la central porque la sucursal 13 no tiene ninguna cargada (decisión
-- de Franco, 2026-09-18). Va al XML como dDirLocSal, o sea declara que el traslado sale de la
-- central. CORREGIR cuando se cargue la dirección real del depósito.

INSERT INTO financiero.timbrado_detalle (
    id, timbrado_id, punto_de_venta_id, punto_expedicion,
    cantidad, rango_desde, rango_hasta, numero_actual,
    activo, creado_en, usuario_id, sucursal_id,
    departamento, ciudad, codigo_ciudad, localidad, barrio, direccion, telefono)
SELECT
    td.id, td.timbrado_id, NULL, td.punto_expedicion,
    td.cantidad, td.rango_desde, td.rango_hasta, td.numero_actual,
    true, now(), td.usuario_id, 13,
    td.departamento, td.ciudad, td.codigo_ciudad, td.localidad, td.barrio, td.direccion, td.telefono
FROM financiero.timbrado_detalle td
WHERE td.id = 105
  AND td.sucursal_id = 1
  AND NOT EXISTS (
      SELECT 1 FROM financiero.timbrado_detalle x
      WHERE x.id = 105 AND x.sucursal_id = 13
  );
