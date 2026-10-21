# Plan — finalizar una toma en una sola transacción (#465)

Rama: `fix/inventario-finalizacion-transaccional` (desde `origin/develop` 35a975bf). Pieza: **central** solamente.

## Problema

`InventarioGraphQL.finalizarInventario` → `finalizarInventarioEnSucursal` no tiene transacción: cada
`save` commitea solo.

1. **Finalización a medias**: si falla en el producto N, los N−1 anteriores quedan ajustados, la toma
   sigue `ABIERTO` (el `save(inventario)` es lo último) y re-finalizarla vuelve a pasar por ellos.
2. **Carrera con el saldo por lote**: entre `saldosPorLote` y `escribirDesglose` una transferencia,
   devolución o ajuste por lote de la misma sucursal puede mover el lote. El lock de #463
   (`LoteFefoService.bloquearSaldo`, `pg_advisory_xact_lock`) no aplica porque no hay transacción.

## Medición previa (pedida en la issue)

Base local `bodega` (copia de prod), 6843 tomas `CONCLUIDO`: máximo **235 productos** (toma 2006),
p95 **44**. **Ninguna** tiene productos con `lote = true` todavía. El costo del lock por sucursal
solo lo pagan las tomas con algún producto con lote; el resto no lo toma. Tiempo real de finalizar
una toma de ~235 productos: se mide en el paso 9 (en local, con el log de la mutation).

## Cambio (una fase)

`graphql/operaciones/InventarioGraphQL.java`:

- `finalizarInventario(id)`: el cuerpo entero (lectura, finalización y `service.save(inventario)`)
  dentro de `new TransactionTemplate(transactionManager).execute(...)`, igual que
  `TransferenciaGraphQL.avanzarEtapaTransferencia`. Inyectar `PlatformTransactionManager`.
  Cualquier excepción hace rollback de todo: ni movimientos, ni desglose, ni cambio de estado.
- Primera lectura dentro de la transacción: `service.lockById(id)` (`InventarioRepository.lockById`
  con `@Lock(PESSIMISTIC_WRITE)`, igual que `TransferenciaRepository.lockById` de #450) y recién
  ahí el chequeo `estado != CONCLUIDO`. Un reintento del cliente o un doble clic espera al primero,
  lee `CONCLUIDO` y devuelve `null` como hoy, en vez de crear AJUSTES duplicados que
  `findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId` después borra sin `ORDER BY`
  (podría borrar el real). Orden de locks: fila de `inventario` → advisory; ningún dueño del
  advisory escribe `inventario`.
- `finalizarInventarioEnSucursal(id)`: al recorrer los ítems, anotar si algún producto contado tiene
  `lote = true`. Si hay alguno, `loteFefoService.bloquearSaldo(sucursalId)` **antes del bucle que
  lee `stockSistema` / `saldosPorLote`** y escribe. Inyectar `LoteFefoService`.
  - Antes del lock la transacción solo leyó (y quizá flusheó el `UPDATE` de la fila de `inventario`,
    que ningún dueño del lock escribe): respeta el invariante de deadlock de `bloquearSaldo`.
  - Toma sin productos con lote: sin lock, mismo camino que hoy salvo la atomicidad.
`service/operaciones/InventarioService.java`:

- `save`: el `entidadAnterior` para la auditoría se lee en una transacción `REQUIRES_NEW` de solo
  lectura (ve lo commiteado). Dentro de la transacción nueva, `findById` devolvía la misma instancia
  ya pasada a `CONCLUIDO` y el registro de modificación perdía el cambio de estado y `fechaFin`.
  Fuera de una transacción el resultado es el mismo que hoy. Un `SELECT` simple no espera el lock
  de fila, así que no hay auto-espera.
- `lockById(id)`, delegando en el repositorio.

- No cambia el schema GraphQL ni hay migración. `finalizarInventarioEnSucursal` no está en ningún
  `.graphqls` (solo lo llama `finalizarInventario`).

Commit: `fix(inventario): finalizar la toma en una sola transaccion y bajo el lock de saldo por lote`.

### Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave.

## Tests (paso 9)

`InventarioGraphQLFinalizarTest` (unitario, mocks):

1. Toma con un producto con lote → `bloquearSaldo(SUCURSAL)` se llama **antes** de
   `saldosPorLote` y del primer `movimientoStockService.save` (`InOrder`).
2. Toma sin productos con lote → `bloquearSaldo` nunca se llama.
3. `finalizarInventario` abre la transacción (`transactionManager.getTransaction`) y, si
   `movimientoStockService.save` falla en el 2.º producto, llama `rollback` y no `commit`, y no
   guarda la toma.

4. `bloquearSaldo` lanza (espera vencida) → rollback, sin commit.
5. Toma ya `CONCLUIDO` al tomar el lock → no escribe nada y devuelve `null`.
6. `InventarioService.save` de una toma existente compara contra la copia leída aparte, no contra
   la misma instancia.

Los mocks de producto con lote marcan las dos fuentes (`presentacion.producto` y
`productoService.findById`), que en producción son la misma entidad.

Revertir el fix y comprobar que 1, 3, 5 y 6 fallan por la lógica (no por un campo inexistente). Batería: `./mvnw clean verify -B -DskipFlyway=true`.

## Prueba de runtime (local, central 8081 perfil dev)

- Finalizar desde el desktop una toma de prueba con un producto con lote (crear con SQL/UI en la
  sucursal 24) y otra sin lote; comparar movimientos y desglose contra lo contado.
- Forzar una falla a mitad (p. ej. un producto contado borrado/inválido por SQL) y comprobar que no
  queda ningún `movimiento_stock` AJUSTE de esa toma y que sigue `ABIERTO`.
- Concurrencia: con la finalización frenada (breakpoint o `pg_sleep` en otra sesión que tenga el
  lock), lanzar una transferencia con lote de la misma sucursal y ver que espera / se rechaza con
  «Reintentá».
- Medir el tiempo de finalizar una toma copiada de la 2006 (235 productos).

## Riesgos y qué queda fuera

- Con lock: una toma grande con lotes frena hasta 15 s las operaciones por lote de esa sucursal;
  pasado eso se rechazan con «Reintentá». Mitiga: solo tomas con lote, y la medición.
- `ModificacionService` corre en `REQUIRES_NEW`: si la finalización hace rollback en el producto N,
  quedan N−1 registros de auditoría de ajustes que no existen (y se repiten en cada reintento).
  Mismo patrón que `AjusteStockLoteService`; no se toca. Además cada `save` usa una segunda
  conexión mientras la principal sigue tomada (secuencial: a lo sumo 2 por finalización).
- Error visible: el «Reintentá» de `bloquearSaldo` llega al desktop (timeout 300 s en
  `inventario.service.ts`) como error GraphQL; en la app vieja sale el «Algo salió mal» genérico.
  No se tocan los clientes.
- **Fuera**: la carrera del stock agregado de productos **sin** lote (nadie más toma el lock para
  ellos y la venta llega por replicación); `cancelarInventario` / `reabrirInventario` tampoco son transaccionales.
- Rollback: revertir el commit. Sin migración.

## Auditoría del plan (paso 5)

- **Eje A** (contrato): sin cambio de GraphQL, enum, migración ni variable. Hallazgo: la auditoría
  de la toma perdía el diff dentro de la transacción → **incorporado** (`InventarioService.save`).
  Mensaje de error en clientes y pool → **anotados en Riesgos**.
- **Eje B** (estado): sin deadlock ni auto-espera; lecturas sin cambio de resultado; lock
  condicionado a lote no deja una carrera extra. Hallazgos: doble finalización por reintento →
  **incorporado** (`lockById` + re-chequeo); auditoría (mismo que A) → incorporado; registros
  huérfanos y pool → **anotados**; tests 4–6 y mocks de las dos fuentes de `lote` → **incorporados**.

## Auditoría del diff (paso 8)

3 ejes fijos; ningún condicional (sin migración, workflows ni réplica en el diff).

- **Autorización**: sin acceso nuevo; `finalizarInventario` ya no tenía control de rol (preexistente,
  #177). Nuevo y acotado: un usuario logueado que finaliza una toma con lotes frena las operaciones
  por lote de esa sucursal mientras dura (tope de espera 15 s).
- **Esquema**: sin migración. `lockById` genera `for update of inventario0_` (la `sucursal` EAGER va
  en un select aparte): verificado en el log.
- **Clientes**: sin cambio de contrato. Desktop y PWA muestran el mensaje del backend; la PWA toma
  el `null` de un doble clic como éxito; la app vieja muestra «Algo salió mal» genérico (como antes).

## Resultado de las pruebas (paso 9, 2026-10-21)

Batería `clean verify`: 2216 tests, 0 fallas. Los 6 tests nuevos fallan con el fix revertido.

Central local 8081 (perfil dev) + desktop web servido desde `develop`, sucursal 24, usuario MR. Datos
creados por SQL/API y borrados al terminar (base de vuelta a la línea base).

| Caso | Por | Resultado |
|---|---|---|
| T1: toma con lote (133: A 55, B 38) + sin lote (145: 25) | pantalla | 133 −14 = A −5, B −2, SIN LOTE −7; 145 −4; CONCLUIDO. `FOR UPDATE` + advisory en el log |
| Auditoría de la toma | base | registra estado ABIERTO→CONCLUIDO, abierto y fechaFin (antes «Entidad actualizada» por OSIV). Ruido: `usuario` 410→410 por proxy |
| T2: falla forzada en el 2.º producto (trigger) | pantalla | «Ups! Algo salió mal…»; 0 ajustes, 0 desglose, toma ABIERTO. Quedan 2 registros de auditoría huérfanos (esperado) |
| T3: lock de lotes tomado por otro, se suelta a los 14 s | pantalla | espera 13,8 s y finaliza |
| T5: lock tomado más de 15 s | pantalla | a los 15,0 s «Hay otra operación con lotes en curso en la sucursal 24. Reintentá en unos segundos.»; nada escrito; reintento posterior OK |
| T4: dos finalizaciones simultáneas | API | la 1.ª CONCLUIDO, la 2.ª `null`; un solo ajuste por producto |
| T6: toma sin lote con el lock de lotes tomado | API | 0,07 s, no espera |
| T7: 236 productos (copia de la toma 2006) + 1 con lote | API | 10,3 s; lock de lotes tomado ~10,2 s (log SQL en DEBUG) |
