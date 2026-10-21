# Plan — des-verificar un ítem mientras se avanza la misma transferencia (issue #471)

Rama: `fix/transferencias-desconfirmar-concurrente-avance` (desde `develop` 4ee6d571, que ya trae
#470). Pieza: central.

## Qué pasa hoy

`desconfirmarTransferenciaItem` no corre en transacción ni toma lock:

1. `findById` del ítem, sin lock.
2. Chequeo de #470: rechaza si la etapa de la transferencia ya pasó la pedida. Lee la etapa sin lock,
   así que un avance que commitea justo después no se ve.
3. Vacía las columnas de la etapa.
4. `desactivarMovimiento`: `MovimientoStockService.save` (`@Transactional`) **commitea solo**.
5. `TransferenciaItemService.save` (`SERIALIZABLE`) corre solo: si un avance de la misma
   transferencia tocó el ítem, `40001` crudo.

Si falla el paso 5, el movimiento del paso 4 ya quedó desactivado y el ítem sigue verificado: stock
y verificación desalineados.

## Qué se hace

Mismo esquema que `saveTransferenciaItem` en #470, reusando lo que #470 dejó:

1. `desconfirmarTransferenciaItem` corre entero en un `TransactionTemplate`.
2. Orden de locks del avance, transferencia → ítem:
   - `service.transferenciaIdDe(id)` (escalar, no carga el ítem) y
     `transferenciaService.lockCompartidoById` de esa transferencia: espera a un avance o a un
     `saveTransferencia` en curso, no a otros saves de ítems;
   - `service.lockById(id)` del ítem: lectura fresca y serializa con un save del mismo ítem.
3. El chequeo de etapa cerrada de #470 usa la `Transferencia` que devolvió `lockCompartidoById`
   (no `ti.getTransferencia()`), igual que `guardarItem`. Ningún `findById` del ítem ni de la
   transferencia antes de los locks: `lockById` no refresca una instancia ya cargada.
4. `desactivarMovimiento` y el save del ítem quedan en la misma transacción: si falla el save, el
   movimiento no queda desactivado.
5. `desactivarMovimiento` llama además a `MovimientoStockLoteService.sincronizarEstado(ms)`: hoy
   desactiva el movimiento agregado pero deja activas sus filas de `movimiento_stock_lote`, y el
   saldo por lote sigue contando una salida que el total ya no cuenta (hasta re-verificar, que pasa
   por `desglosarTransferenciaPorLote` → `sincronizarEstado`). Es la misma llamada que ya usa el
   desglose; escribe solo filas del movimiento propio y no lee saldo, así que no necesita
   `bloquearSaldo`.

Dentro de la transacción de afuera (READ COMMITTED) el `SERIALIZABLE` de
`TransferenciaItemService.save` se une y no aplica; eso saca el `40001`. `MovimientoStockService.save`
también se une (sin `REQUIRES_NEW` en el camino: la auditoría de `ModificacionService` es solo para
movimientos AJUSTE).

**No se toca**: schema GraphQL, clientes, el `SERIALIZABLE` del servicio (otros llamadores).

## Fases

| Fase | Commit | Tests |
|---|---|---|
| 0 | `docs(transferencias): plan para des-verificar un item mientras se avanza la transferencia` | — |
| 1 | `fix(transferencias): des-verificar un item con la transferencia bloqueada` | ver abajo |

Tests (`TransferenciaItemGraphQLDesconfirmarTest`, que pasa a necesitar
`PlatformTransactionManager` mock y los stubs de `transferenciaIdDe`/`lockCompartidoById`/`lockById`):

- carga la transferencia con `lockCompartidoById` y el ítem con `lockById` (`never()` sobre
  `findById`), en ese orden, dentro de una transacción; desactivar el movimiento y guardar el ítem
  pasan antes del commit;
- el chequeo de etapa usa la transferencia bloqueada: `lockCompartidoById` devuelve una etapa
  cerrada distinta de la de `ti.getTransferencia()` → rechazo;
- desactivar el movimiento sincroniza sus filas por lote (`sincronizarEstado`);
- si el save del ítem lanza, no hay commit (la transacción hace rollback) — revertir y ver que falla;
- los tests existentes (limpia solo la etapa pedida, desactiva el movimiento, etapa cerrada) siguen.

Batería: `./mvnw clean verify -B -DskipFlyway=true` en copia fuera de `dev-frc`.

## Datos, persistencia, clientes, filial

- **Migraciones / datos nuevos**: ninguno.
- **Contrato GraphQL**: igual. Clientes: desktop (`onDesconfirm`) y PWA (`desconfirmarItem` antes de
  `guardarItem`); frc-mobile no usa la mutation.
- **Filial**: `N/A para filial`: los clientes llaman al central (igual que en #470).
- **Réplica**: sin cambios.

## Riesgos

- **Esperas**: un des-verificar espera a un avance en curso de la misma transferencia (5-10 s con
  muchos ítems) en vez de fallar.
- **Atomicidad nueva**: si falla el save del ítem, ahora tampoco queda el movimiento desactivado.
- **Clientes cortan por timeout**: no. El desktop corta mutations a los 60 s
  (`TIMEOUT_POR_DEFECTO_MS`, `timeout-link.ts:5`); la PWA no les pone tope. Si un avance tardara más,
  el cliente cortaría y la transacción del servidor seguiría y podría commitear.
- **El par desconfirmar + guardar de la PWA** sigue siendo dos requests: si el guardar falla después
  de un desconfirmar commiteado, el ítem queda des-verificado (consistente; preexistente y la PWA
  recarga).

## Prueba de runtime (central local, perfil dev)

1. Transferencia clonada en preparación, con ítems verificados.
2. **Solapados (bloqueante)**: transferencia tomada `FOR UPDATE` desde psql 8 s + `desconfirmar` en
   paralelo → espera (no `40001`) y des-verifica al soltar; con el avance a
   `PREPARACION_MERCADERIA_CONCLUIDA` en el medio → rechazo con texto y nada cambia.
3. **Atomicidad (bloqueante)**: CHECK temporal que hace fallar el `UPDATE` del ítem → error, y el
   movimiento sigue activo (hoy quedaría desactivado).
4. **Lotes (bloqueante)**: producto con lote verificado en preparación; des-verificar → movimiento y
   sus filas de `movimiento_stock_lote` inactivos; re-verificar → los dos activos de nuevo.
5. Desktop: «Desconfirmar» en preparación, transporte y recepción; PWA: confirmar un ítem con motivo
   (des-verifica y guarda). Igual que antes.
6. Borrar los datos de prueba.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Decisión |
|---|---|---|
| A | Contrato igual; llamadores: desktop `onDesconfirm`, PWA botón y `guardarVerificacion` (secuencial, sin deadlock); frc-mobile no usa la mutation | Confirma el plan |
| A | El cambio visible es la espera en vez del `40001`; ¿cortan los clientes antes? | Verificado: desktop 60 s, PWA sin tope. Anotado en riesgos |
| A | El chequeo de etapa debe usar la transferencia bloqueada, no `ti.getTransferencia()` | Punto 3 + test |
| B | «Desactivar libera unidades del lote» era falso: no toca `movimiento_stock_lote` y el saldo por lote queda desalineado | Verificado. Se agrega `sincronizarEstado` (punto 5) + prueba de runtime 4 |
| B | Sin ciclo de locks contra avance, `saveTransferencia` y `saveTransferenciaItem` del mismo ítem (ese toma el saldo con el ítem tomado, pero el ítem lo serializa); sin `REQUIRES_NEW` en la cadena | Sin cambios |
| B | Un `findById` antes de los locks anularía el fix en silencio | Test con `never()` |
| B | El test con PTM mock prueba el cableado, no la atomicidad | La prueba de runtime 3 es bloqueante |

## Sin verificar todavía

- Todo lo de runtime.
