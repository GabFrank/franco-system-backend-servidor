# Plan — producto inactivo inactiva sus presentaciones y precios

Rama: `fix/productos-inactivar-presentaciones-y-precios` (central, desde `develop`).

## Qué

Cuando un producto **pasa de activo a inactivo** al guardarse, toda presentación del producto y
todo precio por sucursal de esas presentaciones que no esté ya inactivo pasa a `activo = false`.

Decisiones de Franco (2026-10-22):

- **Reactivar el producto no reactiva nada.** Presentaciones y precios se reactivan a mano.
- **Sin migración.** No se corrigen los productos que hoy ya están inactivos con hijos activos.

## Por qué solo en la transición (hallazgo de la auditoría, eje A)

La PWA da de alta productos **inactivos** a propósito (`frc-mobile-pwa`
`src/app/pages/producto/editar/producto-nuevo.page.ts`, «Nace inactivo: se activa desde el hub
cuando ya se puede vender»), les carga presentaciones, códigos y precios, y guarda la cabecera
completa por `saveProducto` varias veces antes de activarlo. Si la cascada corriera en cada
guardado de un producto inactivo, apagaría los precios recién cargados y `faltaParaActivar`
(`producto-editar.reglas.ts`) no dejaría activarlo nunca. Con la transición:

- alta inactiva (sin estado anterior) → no cascadea;
- guardar la cabecera de un producto ya inactivo → no cascadea;
- activo (o `null`) → `false` → cascadea.

## Dónde

Única puerta: `ProductoService.save(ProductoInput)`, que llama la mutation `saveProducto`
(desktop y PWA). Ningún otro código pone `producto.activo = false` (grep de `setActivo(false)`).

- Antes de `repository.save`, guardar `activoAnterior` como valor (no la entidad: con
  open-session-in-view `findById` devuelve la instancia gestionada y el merge la pisa).
- Después del save, si `activoAnterior` no es `false` y el nuevo es `false`: recorrer
  `presentacionService.findByProductoId` y `precioPorSucursalService.findByPresentacionId`, y
  guardar con `activo=false` cada uno cuyo `activo` no sea `false` (`null` cuenta como activo).
- Se guarda por `PresentacionService.save` / `PrecioPorSucursalService.save` (no por el
  repositorio) para que quede el registro de `ModificacionService`. No pasa por los resolvers, así
  que no dispara la notificación FCM de precio actualizado.
- Va antes de `sincronizarProducto`; el índice Lucene no indexa `activo` de presentación/precio.

## Fases

1. **Única fase**: el cambio en `ProductoService.save` + `ProductoServiceInactivarTest` (Mockito,
   sin contexto):
   - activo → inactivo: presentaciones activas y `null` se guardan inactivas, sus precios activos
     también; las ya inactivas no se re-guardan;
   - inactivo → inactivo (caso PWA): no se toca nada;
   - producto nuevo inactivo: no se toca nada;
   - activo → activo y inactivo → activo: no se toca nada;
   - revertir el fix y ver que el primer test falla.

## Datos nuevos

Ninguno: no hay columnas, claves ni campos GraphQL nuevos. Se escribe `activo` de
`productos.presentacion` y `productos.precio_por_sucursal`, que ya existe y ya leen PDV, búsqueda,
ABM del desktop y `faltaParaActivar` de la PWA.

## Replicación / multi-repo

- `presentacion` y `precio_por_sucursal` son MAIN_TO_ALL: el `UPDATE` llega solo a las filiales.
  Sin DDL, sin espejo en filial.
- Filial: N/A — los productos se editan en el central; el filial no tiene lógica de `activo` en
  `ProductoService`.
- Desktop: N/A sin cambios — guarda el producto solo; las presentaciones se releen por el resolver.
- PWA: N/A sin cambios — con la regla de transición su alta inactiva sigue andando (se prueba).
- Sin contrato GraphQL nuevo: no hay orden de despliegue.

## Riesgo / reversibilidad

- Sin transacción envolvente (como el resto de `save`): si un guardado de hijo falla, el usuario ve
  el error y quedan hijos activos. Como ya no es idempotente, se completa activando y desactivando
  de nuevo el producto.
- Un producto grande (3 presentaciones × 18 sucursales × tipos de precio) son ~100 guardados con su
  registro de modificación en un solo request. Aceptable para una acción manual y rara; se mide en
  la prueba.
- Un diálogo del desktop abierto de antes podría re-guardar una presentación con `activo=true`.
  Mismo caso que cualquier edición concurrente hoy.
- Rollback: revertir el JAR. Lo ya inactivado queda; se recupera desde el registro de
  modificaciones:

  ```sql
  -- hijos que inactivó la cascada de un producto, en una ventana de tiempo
  SELECT r.tabla_nombre, r.entidad_id, r.creado_en
  FROM configuraciones.modificacion_registro r
  JOIN configuraciones.modificacion_detalle d ON d.modificacion_registro_id = r.id
  WHERE r.tabla_nombre IN ('presentacion', 'precio_por_sucursal')
    AND d.campo_nombre = 'activo' AND d.valor_anterior = 'true' AND d.valor_nuevo = 'false'
    AND r.creado_en BETWEEN :desde AND :hasta;
  ```
  (nombres de columnas a confirmar en la prueba de runtime).

## Pruebas de runtime

Central local propio + desktop web (usuario MR) + PWA:
1. Desktop: producto activo con 2+ presentaciones y precios → desactivar y guardar → SQL:
   presentaciones y precios en `false`; registros en `configuraciones.modificacion_registro`.
   Medir el tiempo del guardado.
2. Reactivarlo → presentaciones y precios siguen en `false`.
3. Guardar un producto activo sin cambios → nada se toca.
4. PWA: alta de producto (nace inactivo) → presentación, código y precio → editar datos generales →
   precio sigue activo → activar desde el hub funciona.
5. Correr la consulta de rollback sobre el caso 1 y ver que lista exactamente lo que se apagó.

## Sin verificar

- Propagación real a una filial (por lectura: tablas ya publicadas).
