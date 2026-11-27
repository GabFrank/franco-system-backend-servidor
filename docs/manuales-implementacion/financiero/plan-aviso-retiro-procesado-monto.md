# Plan: monto en el aviso de retiro procesado

Pedido de Franco (2026-11-26): en el diálogo «Ingresar retiro de PDV» (caja mayor), el aviso de un retiro
ya procesado muestra dónde, quién y cuándo, pero no cuánto. Hoy dice:

> Caja #3841 (SUC. KM5): retiro #3448 procesado en CAJA MAYOR TESORERIA por GUILLERMO FRANCO AREVALOS el 26/11/26 10:37

Va a decir:

> Caja #3841 (SUC. KM5): retiro #3448 (1.500.000 Gs · 200 R$) procesado en CAJA MAYOR TESORERIA por … el …

Sigue a central #650 y desktop #759 (los dos mergeados el 15/11).

Rama `feature/financiero-aviso-retiro-monto` en central y desktop, desde `origin/develop`.

## De dónde sale el monto

El monto que muestra el aviso es **el que entró a caja mayor**, y eso depende del camino de ingreso:

- **Poller o ingreso manual** (`RetiroIngresoService` → `RetiroTesoreriaProcesador`): postean `retiro_detalle`,
  lo declarado por el PDV.
- **Verificación** (`RetiroVerificacionService.acreditar`): postea **lo contado**
  (`retiro_verificacion_detalle.contado`), que puede ser otro monto y hasta otra moneda. Ejemplos en la base
  local: el 5453/1 declaró 800 R$ y entraron 790 R$; el 1002/24 declaró 800.000 Gs y entraron 850.000 Gs + 50 R$.
  Cuando se anula la verificación, el movimiento vuelve a null y el retiro sale del aviso.

Regla: **si el retiro tiene una verificación vigente (`anulada = false`), se muestra la suma de `contado`.
Si no, la de `retiro_detalle`.** No se suman los movimientos de caja mayor: el poller no graba
`origenSucursalId`, así que el id del retiro no alcanza para separar sucursales.

Costo: `retiro_detalle` no tiene índice por `retiro_id` (solo la PK `(id, sucursal_id)`, 79 mil filas en local).
Reusar `RetiroResolver.retiroGs/Rs/Ds`, que hace una consulta por retiro y moneda, serían hasta 50 × 3 lecturas
completas por búsqueda. Va **una consulta agrupada por fuente** para toda la lista: un Seq Scan de
`retiro_detalle` (EXPLAIN local, cost ~2010) más una lectura chica de la verificación. No se agrega índice:
`retiro_detalle` es una tabla replicada y eso pide migración aparte.

Monedas fijas 1/2/3 (Gs, R$, US$), igual que `RetiroResolver`. Un detalle de otra moneda o sin moneda no se muestra.

## Datos nuevos

| Campo | Lo escribe | Lo lee |
|---|---|---|
| `RetiroIngresado.retiroGs/retiroRs/retiroDs` (GraphQL, central) | `RetiroService.findIngresadosDeCaja`: contado de la verificación vigente (`RetiroVerificacionRepository.sumarContadoVigente`) o, si no hay, `retiro_detalle` (`RetiroDetalleRepository.sumarPorMoneda`) | desktop `retirosIngresadosDeCajaQuery` → `textoRetiroIngresado` (aviso del diálogo) |

Sin migración, sin tablas nuevas, sin variables de entorno. La query vive solo en el central: el filial no la
tiene. Grep de `retirosIngresadosDeCaja|RetiroIngresado` en mobile-pwa, frc-mobile y filial: 0 usos.

## Fase 1: central (commit `feat(financiero): monto del retiro en retirosIngresadosDeCaja`)

- `RetiroDetalleRepository.sumarPorMoneda(retiroIds)`: JPQL `select d.retiro.id, d.sucursalId, d.moneda.id,
  sum(d.cantidad) … where d.retiro.id in :ids group by …`. Mismo patrón fórmula + columna que
  `VentaTarjetaRepository` (`vt.venta.id`).
- `RetiroVerificacionRepository.sumarContadoVigente(retiroIds)`: `select v.retiroId, v.sucursalId, d.moneda.id,
  sum(d.contado) from RetiroVerificacionDetalle d join d.verificacion v where v.anulada = false and
  v.retiroId in :ids group by …`.
- `RetiroIngresadoDto`: pasa a `@RequiredArgsConstructor` (los 9 finales del constructor JPQL, en el mismo
  orden) y suma `retiroGs/Rs/Ds` como campos **no finales** con `@Setter`.
- `RetiroService`: se inyecta `RetiroDetalleRepository` directo (un repositorio no tiene el ciclo que obliga a
  usar el `ObjectProvider` del servicio). `verificacionRepository` ya está inyectado. `RetiroServiceTest:45`
  suma el argumento al constructor.
- `findIngresadosDeCaja`: con la lista no vacía, las dos consultas y reparto por `(retiroId, sucursalId)`.
  Lo contado gana sobre lo declarado; si un retiro tiene verificación vigente, sus montos salen solo de ahí,
  aunque sea en otras monedas. Con varias sucursales, el `in` por id trae filas de otros retiros con el mismo
  id, y el reparto las descarta por la clave. Lista vacía: no consulta nada.
- `retiro.graphqls`: `retiroGs/retiroRs/retiroDs: Float` en `RetiroIngresado`.
- Tests:
  - `RetiroServiceTest`: mismo id en otra sucursal no se mezcla; R$ y US$ van a su campo; con verificación
    vigente gana lo contado, incluida una moneda que no estaba declarada; sin detalle quedan null; lista vacía
    no consulta.
  - `RetiroIngresadosQueriesIT`: las dos JPQL contra la base real, porque una JPQL mal armada recién falla en
    runtime. Va con `-Dit.retiroIngresados=true`, en una copia fuera de `dev-frc` (por jdtls), y hay que leer
    en el log que corrió y no que se salteó.

## Fase 2: desktop (commit `feat(financiero): monto en el aviso de retiro procesado`)

- `retirosIngresadosDeCajaQuery`: pide `retiroGs retiroRs retiroDs`.
- `RetiroIngresado` suma los tres campos opcionales.
- `formatMontosRetiro` recibe `Partial<Pick<Retiro, 'retiroGs' | 'retiroRs' | 'retiroDs'>>`. En `Retiro` los
  tres son obligatorios, y con `Pick` solo no compila pasarle un `RetiroIngresado` con campos opcionales (lo
  probó el auditor A con el tsc del repo). Los otros llamadores le pasan un `Retiro` y siguen compilando.
- `textoRetiroIngresado`: solo en el caso con movimiento y con algún monto, agrega `(montos)` después del
  número de retiro. Los casos «sin montos» y «dado por procesado» quedan como están: en la limpieza el
  retiro no entró a ninguna caja, y poner un monto ahí parecería que sí.
- Test: `retiro-ingresado.spec.ts`, con el texto con montos, una sola moneda y sin detalle (texto de hoy).

## Orden y compatibilidad

- **Merge central → desktop, y deploy en el mismo orden:** el central se despliega en alpha, beta, bodega y
  farmacia antes de promover el desktop a ese canal (el deploy del central es manual y por instancia). Un desktop nuevo contra un central viejo pide campos que no existen: la query
  falla por validación y el diálogo queda sin aviso (ese error ya se traga, «si falla, no hay aviso»). **No se pierde solo el monto: se pierde el aviso entero de
  #759, sin ningún error visible.** Un
  desktop viejo contra un central nuevo no cambia nada.
- Ninguna tabla replicada se toca.

## Pruebas

- Central: `RetiroServiceTest` y la IT contra `bodega@5551`. `mvn package` de la copia (memoria jdtls).
- Desktop: el worktree no tiene `node_modules`, así que primero `npm ci --legacy-peer-deps`. Después el spec de
  Karma y `npm run check`.
- Pantalla: central 8081 y desktop web. Buscar en «Ingresar retiro de PDV», con casos elegidos por SQL: una caja con un
  retiro procesado por el poller o por ingreso manual, una con un retiro de más de una moneda y una con una
  verificación con diferencia (por ejemplo el 1002/24). Si falta el caso multimoneda, crearlo por
  SQL y restaurarlo después.

## Sin verificar

- El tiempo de la consulta agrupada en bodega de producción, con `retiro_detalle` más grande. Es una sola
  lectura completa, y hoy la lista de flotantes ya hace 60 por página.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| MAYOR: la verificación postea lo contado, no `retiro_detalle` | B | Regla contado vigente > declarado, con test e IT |
| MAYOR: `Pick<Retiro>` con campos opcionales no compila | A | `Partial<Pick<…>>` |
| Menor: un desktop antes del central pierde el aviso entero | A, B | Orden de deploy explícito en «Orden y compatibilidad» |
| Menor: el constructor de `RetiroService` rompe el test | A | Repositorio directo y `RetiroServiceTest:45` |
| Menor: la IT no corre sin `-Dit.retiroIngresados=true` | A, B | Comando en «Pruebas» |
| Menor: el worktree del desktop sin `node_modules` | A | `npm ci --legacy-peer-deps` en «Pruebas» |
| Costo: un Seq Scan por búsqueda | B | Aceptado, sin índice (tabla replicada) |
| No verificado: si `RetiroCasoService` postea ajustes al cerrar un caso | B | Verificado: no registra movimientos de caja mayor (grep sin resultados) |
