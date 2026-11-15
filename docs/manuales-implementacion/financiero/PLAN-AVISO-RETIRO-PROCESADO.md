# Plan — Aviso de caja ya procesada en «Ingresar retiro de PDV»

Rama `feature/financiero-aviso-retiro-procesado` en central y desktop, desde `develop`.

## Problema

El diálogo «Ingresar retiro de PDV» (caja mayor → Ingresar retiro) lista solo los retiros
**flotantes** (`retirosFlotantes`: `caja_virtual_id` y `movimiento_caja_virtual_id` nulos). Si el
operador busca por **N° Caja** una caja de PDV cuyos retiros ya entraron a una caja mayor, la tabla
queda vacía con «No hay retiros flotantes para ingresar», sin decir que ya se procesaron, dónde ni
quién.

## Qué se quiere

Con el filtro **N° Caja** cargado, por cada retiro de esa caja ya procesado, un aviso arriba de la
tabla:

> Caja #N (<sucursal>): retiro #X procesado en **<caja mayor>** por **<usuario>** el dd/MM/yy HH:mm

Variantes:
- Movimiento sin usuario (lo ingresó el poller): «… por el sistema …».
- Marker `-1` con caja mayor (verificado o procesado sin montos positivos): «retiro #X procesado en
  <caja mayor> sin montos».
- Marker `-1` sin caja mayor (limpieza histórica de pendientes): «retiro #X dado por procesado sin
  ingreso a caja mayor».

Se muestra aunque la caja tenga además retiros pendientes. Sin filtro de caja no se muestra nada.
Tope visual: 5 líneas y «… y N más».

**Fuera de alcance** (no se pidió): retiros `EN_PROCESO` o `CANCELADO` de la caja, que tampoco son
flotantes y siguen sin explicación.

## Fase 1 — central

- `RetiroIngresadoDto` (repository/financiero, junto a `RetiroSituacion`): `retiroId`,
  `sucursalId`, `sucursalNombre`, `cajaSalidaId`, `cajaVirtualId`, `cajaVirtualNombre`,
  `movimientoId` (null si el marker es `-1`), `usuario` (nombre de la persona o nickname; null =
  sistema), `ingresadoEn` (creado_en del movimiento).
- `RetiroRepository.findIngresadosDeCaja(cajaId, sucId, pageable)` y
  `findIngresadosDeCajaEnSucursales(cajaId, sucIds, pageable)`, return `List` (sin count):
  JPQL `select new` desde `Retiro r` con `r.movimientoCajaVirtualId is not null`,
  **left join** `MovimientoCajaVirtual m on m.id = r.movimientoCajaVirtualId` (el `-1` no matchea y
  sigue saliendo), **left join** `CajaVirtual cv on cv.id = coalesce(r.cajaVirtualId, m.cajaVirtual.id)`,
  left join usuario/persona del movimiento, **left join** `Sucursal s on s.id = r.sucursalId`.
  Orden: `r.creadoEn desc`. Una consulta, sin N+1. Se une por el marker y no por `m.origenId`: un
  retiro con varias monedas tiene N movimientos y daría filas repetidas.
- `RetiroService.findIngresadosDeCaja(cajaId, sucId, cajaVirtualDestinoId)`: **el mismo recorte que
  `findFlotantes`** para el rol acotado (`sucursalesOperablesIds` ∩ `sucursalesDeCajaIds(destino)`,
  vacío = lista vacía sin consultar, `sucId` fuera = vacío). Tope 50 (`PageRequest.of(0, 50)`).
- `RetiroGraphQL.retirosIngresadosDeCaja(cajaId: ID!, sucId: ID, cajaVirtualDestinoId: ID): [RetiroIngresado]!`
  con `seg.requireVerCaja()` como primera línea — el mismo permiso que `retirosFlotantes`.
- `retiro.graphqls`: `type RetiroIngresado` + la query.
- Tests:
  - `RetiroServiceIngresadosTest` (Mockito): acotado sin sucursales → vacío sin consultar; acotado
    con `sucId` fuera → vacío; acotado con destino → intersección; rol completo → `sucId` tal cual,
    tope 50.
  - Resolver: sin rol de tesorería → rechazo.
  - `RetiroIngresadosQueriesIT` (patrón `*QueriesIT`, contra DB real con la property del repo):
    marker normal, dos monedas = una fila, `-1` con y sin caja, movimiento sin usuario, misma caja en
    dos sucursales. Es lo único que valida la JPQL antes del arranque.

## Fase 2 — desktop

- `retiro/graphql/graphql-query.ts` + `RetirosIngresadosDeCajaGQL` +
  `RetiroService.onGetIngresadosDeCaja(cajaId, sucId, destinoId)`: servidor = true,
  **`LECTURA_ESTRICTA`** (el error del servidor y el de red llegan al `error:` y el genérico no
  muestra snackbar), `silentLoad = true` (sin segundo «Buscando…») y
  `{ silenciarAvisoTimeout: true, sinAviso: true }`.
- `ingresar-retiro-caja-mayor-dialog`:
  - `avisosProcesados: string[]` precalculado en el `.ts` (sin funciones en el template).
  - En `cargar()`: si cambió la clave (caja, sucursal), se vacía el aviso **como primera acción** y,
    si hay caja con `Number.isFinite`, se piden los ingresados con un contador propio. Solo responde
    la última. Error o `null` → sin aviso. Al paginar la clave no cambia: no se vuelve a pedir ni
    parpadea.
  - Después de procesar un retiro (`cargar()` tras la verificación), se vuelve a pedir aunque la
    clave no cambie: el recién ingresado tiene que aparecer en el aviso.
  - Borrar el N° Caja a mano o `limpiarFiltros()` → la clave cambia → aviso vacío.
  - Modo escaneo: no hay aviso (no hay filtro de caja); al pasar a «Ver todos» rige lo de arriba.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `RetiroIngresado` (type GraphQL, solo lectura) | ya existente: `RetiroTesoreriaProcesador.procesar` (ingreso directo y poller) y `RetiroVerificacionService.acreditar` (verificación) escriben `movimiento_caja_virtual` (usuario, caja) y el marker `retiro.movimiento_caja_virtual_id` (`-1` sin montos); `RetiroVerificacionService.anular` lo vuelve a null | `ingresar-retiro-caja-mayor-dialog` vía `RetiroService.onGetIngresadosDeCaja` |

Sin migración, sin tablas nuevas, nada replicado. Solo central + desktop (tesorería es central-only).
La PWA no usa estas queries.

## Orden de PRs, compatibilidad y rollback

Central primero. Las cuatro combinaciones son inocuas:
- desktop viejo + central nuevo/viejo: no llama la query.
- desktop nuevo + central viejo: error de validación → `LECTURA_ESTRICTA` lo lleva al `error:` →
  sin aviso y sin snackbar; la tabla funciona como hoy.
- Revertir cualquiera de los dos no deja estado: no hay escritura.

## Seguridad

- Permiso: `requireVerCaja` (igual que los flotantes) y el mismo recorte del rol acotado.
- Devuelve el nombre de la caja mayor y del usuario que ingresó, que es lo que se pidió; no montos ni saldos.

## Pruebas de runtime (local)

Central propio (8081, sync/refresh de réplica apagados), desktop `ng serve -c web`, usuario MR:

1. Caja con un retiro ingresado por «Procesar» (verificación) → aviso con caja mayor, usuario y fecha.
2. Caja con uno ingresado y otro pendiente → aviso + fila pendiente; procesar el pendiente → aparece
   en el aviso.
3. Caja sin ingresados → sin aviso. Borrar el N° Caja a mano y con «Limpiar filtros» → el aviso se va.
4. Marker `-1` con y sin caja, y movimiento sin usuario (por SQL, restaurando después).
5. Central de `develop` (sin la query) → sin aviso, sin snackbar, tabla normal.
6. Rol acotado (TESORERIA CAJA) con caja de una sucursal que no cubre → sin aviso.
7. Caja «#N» repetida en dos sucursales sin sucursal elegida → dos avisos con su sucursal.
8. Paginar con caja cargada → el aviso no parpadea.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Copiar `PROPAGAR_ERROR_DE_RED` muestra snackbar contra un central viejo y el `catchError` nunca corre (`generic-crud.service.ts`, errorPolicy all + `terminar(null)`) | A y B, mayor | `LECTURA_ESTRICTA` + `silentLoad` + `null` = sin aviso |
| Marker `-1` (procesador:99, verificación:252, limpieza histórica de ~56k) desaparece con un join interno | A y B, mayor | left join al movimiento, caja por `coalesce(r.cajaVirtualId, m.cajaVirtual)`, textos propios |
| El aviso de otra caja puede quedar en pantalla | B, mayor | se vacía al cambiar la clave, antes de pedir |
| La verificación no pasa por el procesador | A, menor | tabla de datos corregida; prueba 1 por verificación |
| El acotado ignora la caja destino | A y B, menor | mismo recorte que `findFlotantes` |
| `Page` con `select new` deriva count frágil | A, menor | return `List` + `PageRequest` |
| Mockito no valida la JPQL | B | `RetiroIngresadosQueriesIT` |
| `NaN` en N° Caja, query por página, muchos avisos, modo escaneo | B, menor | `isFinite`, clave, tope 5 líneas, documentado |
| `EN_PROCESO`/`CANCELADO` sin explicación | A y B, menor | fuera de alcance, anotado |
| Movimiento revertido deja el marker | A | no aplica: `RETIRO_CAJA` no se anula desde la caja mayor (`TesoreriaService.java:250-254`); la única reversa es `anular` de la verificación, que limpia el marker |

## Auditoría del diff (paso 8)

Tres fijos (autorización, esquema y consulta, contrato con los clientes); ningún condicional aplica
(no toca release, migraciones ni replicación).

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| `-1` sobre un retiro CANCELADO o EN_PROCESO saldría como «dado por procesado» | esquema, menor | la consulta excluye esos estados, como los flotantes; IT con los dos casos |
| Si la consulta falla, la misma caja no la vuelve a pedir | contrato, bajo | el error libera la clave: Enter reintenta |
| El spec usaba fecha ISO y el central manda `yyyy-MM-dd HH:mm` | contrato, bajo | fixture con el formato real |
| Retiro con caja asignada y sin movimiento (espera al poller) no sale en ningún lado | esquema, menor | fuera de alcance: los dos caminos de ingreso asignan y postean en la misma transacción; 0 filas en la base local |
| Seq scan de `retiro` por cada búsqueda por caja (8,5 ms local, la misma que ya hace `retirosFlotantes`) | esquema, menor | aceptado; si crece, índice `(caja_salida_id, sucursal_id)` con migración aparte |
| Ids en `Int` | contrato, bajo | misma convención que `type Retiro`; sin cambio |

## Sin verificar

- Índice sobre `retiro(caja_salida_id)`: solo se vio uno sobre `caja_virtual_id` (V179.5). Con
  `caja_salida_id` + `sucursal_id` filtrando, se mide el plan de la consulta en la bodega local
  antes del PR.
