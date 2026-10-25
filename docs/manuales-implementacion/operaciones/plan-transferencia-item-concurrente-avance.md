# Plan — guardar un ítem mientras se avanza la misma transferencia (issue #468)

Rama: `fix/transferencias-item-concurrente-avance` (desde `develop` 35a975bf). Pieza: central.

## Qué pasa hoy

`saveTransferenciaItem` no corre en transacción. `TransferenciaItemService.save` es
`@Transactional(isolation = SERIALIZABLE)` y, solo, abre una SERIALIZABLE propia. Si un
`avanzarEtapaTransferencia` de la misma transferencia ya modificó el ítem (copia de etapa), el
`UPDATE` espera y, cuando el avance commitea, PostgreSQL lo rechaza con `40001`. El cliente ve
`LockAcquisitionException ... no se pudo serializar el acceso`.

Dos ventanas, no una:

1. **Solapados** (la de la issue): el save arranca antes de que el avance commitee → `40001` crudo.
   El error evita pisar, pero no dice qué hacer.
2. **Pantalla vieja**: el save llega **después** del commit del avance → no hay `40001` y el save
   pisa en silencio lo que el avance acaba de copiar (por ejemplo, cambia `cantidadPreparacion`
   cuando la transferencia ya está en `PREPARACION_MERCADERIA_CONCLUIDA` y la cantidad de
   transporte ya se copió de la vieja).

Solo con el lock de la propuesta de la issue, la ventana 1 pasa a ser la 2: el save espera, relee y
**pisa**. Por eso el lock va con una validación.

## Qué hacen los clientes (relevado en desktop, PWA y frc-mobile)

- Los ítems nuevos (`id` null) nacen solo en `PRE_TRANSFERENCIA_CREACION`.
- Un ítem existente solo cambia el grupo de campos de la etapa en curso: pre-transferencia en
  creación, preparación en `PREPARACION_MERCADERIA`, transporte en `TRANSPORTE_VERIFICACION`,
  recepción en `RECEPCION_EN_VERIFICACION`. «Elegir lotes» del desktop (creación, origen,
  preparación) reenvía el ítem sin cambios.
- Desktop y frc-mobile reenvían el ítem **entero** (`toInput()`) con lo guardado; la PWA manda solo
  el grupo de la etapa.
- El desktop manda los vencimientos como `yyyy-MM-dd HH:mm`: sin segundos.
- frc-mobile crea ítems con `cantidadPreparacion/Transporte/Recepcion = 0`.

## Qué se hace

1. `TransferenciaRepository.lockCompartidoById(id)`: `@Lock(PESSIMISTIC_READ)` → `FOR SHARE`.
   Choca con el `FOR UPDATE` del avance y del `saveTransferencia`, pero **no** entre dos saves de
   ítems: dos operadores verificando ítems de la misma transferencia siguen en paralelo.
   `TransferenciaService.lockCompartidoById` lo expone.
2. `TransferenciaItemRepository`: `transferenciaIdDe(itemId)` (JPQL escalar, no carga la entidad)
   y `lockById(itemId)` con `@Lock(PESSIMISTIC_WRITE)`.
3. `saveTransferenciaItem` corre entero en un `TransactionTemplate` (mismo patrón que
   `saveTransferencia` de #464), en este orden — el mismo del avance:
   1. la transferencia con `lockCompartidoById`: la **real del ítem** si el ítem existe
      (`transferenciaIdDe`), si no la del input. Es la primera carga de entidad del request, así que
      viene fresca;
   2. el ítem existente con `lockById` del ítem: lectura fresca y dos saves del **mismo** ítem se
      serializan (hoy el `SERIALIZABLE` los convertía en `40001`; sin esto el segundo pisaría al
      primero en silencio). Ítems distintos siguen en paralelo;
   3. validación (punto 4), PATCH, save, lotes, movimiento → saldo.

   Dentro de la transacción de afuera (READ COMMITTED) el `SERIALIZABLE` de
   `TransferenciaItemService.save` no aplica: se une, igual que en el avance. Eso saca el `40001`.
   El costo de COMPRAS (`registrarCostoCompraManual`, que es `@Transactional`) va **después** del
   commit: si lanzara adentro, marcaría la transacción rollback-only y el catch que hoy lo traga no
   evitaría perder el ítem.
4. Validación nueva, solo para ítems existentes y transferencias con etapa (null no valida):
   **un save no cambia un grupo de una etapa que la transferencia ya cerró.** Se compara por
   `EtapaTransferencia.getOrden()`. Último momento en que cada grupo se puede cambiar:

   | Grupo | Hasta la etapa (inclusive) | Por qué ahí |
   |---|---|---|
   | pre-transferencia | `PRE_TRANSFERENCIA_ORIGEN` | al entrar a preparación se copia a preparación |
   | preparación | `PREPARACION_MERCADERIA` | al concluir se copia a transporte |
   | transporte | `TRANSPORTE_VERIFICACION` | al salir se cierra la verificación del transporte |
   | recepción | `RECEPCION_EN_VERIFICACION` | al concluir se cierran los movimientos |

   Campos que se comparan por grupo: los que el avance copia (cantidad con `Objects.equals`,
   presentación por id, vencimiento, motivo de modificación, motivo de rechazo). «Cambia» = el
   input trae el campo (no null) y difiere de lo guardado. El vencimiento se compara **por día**: es
   una fecha, la PWA y frc-mobile mandan solo `yyyy-MM-dd`, y el desktop puede reenviar una fecha
   pura corrida una hora (00:00 del día de cambio de horario). Un reenvío con lo guardado pasa.
   Mensaje: «La transferencia N ya esta en etapa X: el item M no se puede cambiar en <grupo> porque
   esa etapa ya se cerro (la avanzo otro usuario mientras guardabas). Volve a abrirla para ver como
   quedo.»
5. **Control de stock negativo con savepoint SQL.** Dentro de una transacción `JdbcTemplate` usa la
   misma conexión, así que un error en su `INSERT` (que hoy se traga y «el ítem se guarda igual»)
   abortaría la transacción y perdería el ítem. `TransactionStatus.createSavepoint()` no sirve
   (con Hibernate, Spring 5.3 no da `SavepointManager`: verificado con `javap`), así que
   `registrarTransferencia` hace `SAVEPOINT` / `ROLLBACK TO SAVEPOINT` / `RELEASE` por SQL cuando
   hay transacción activa, alrededor de su bloque (no toca el lock de saldo). Sigue antes del
   movimiento: el stock previo se lee sin la salida.
6. Comentarios que dejan de ser ciertos: el invariante de `LoteFefoService.bloquearSaldo` («el
   ítem se commitea antes de pedir el lock») y el de `ControlStockNegativoService` («JdbcTemplate
   usa su propia conexión»). Orden de locks: **transferencia → ítem → saldo**, igual que el avance.

**No se toca**: el `SERIALIZABLE` del service (otros llamadores), el schema GraphQL, los clientes.

## Fases

| Fase | Commit | Tests |
|---|---|---|
| 0 | `docs(transferencias): plan para guardar un item mientras se avanza la transferencia` | — |
| 1 | `fix(transferencias): guardar un item con la transferencia bloqueada y sin pisar etapas cerradas` | ver abajo |
| 2 | `fix(transferencias): no des-verificar una etapa que la transferencia ya cerro` | `TransferenciaItemGraphQLDesconfirmarTest` |

**Fase 2 (agregada en el paso 8).** La PWA des-verifica y después guarda (`guardarVerificacion`).
Con la pantalla vieja, antes el save volvía a llenar el grupo que `desconfirmar` había vaciado; con
la fase 1 el save se rechaza y el grupo cerrado quedaba vaciado y con su movimiento desactivado. La
fase 1 empeoraba ese camino, así que `desconfirmarTransferenciaItem` rechaza una etapa que la
transferencia ya pasó (mismo criterio: `getOrden()` de la etapa actual mayor que el de la pedida).
No toma lock: la carrera de `desconfirmar` contra un avance sigue como residual.

Tests (`TransferenciaItemGraphQLEtapaCerradaTest` nuevo + ajuste de los tres existentes, que
necesitan `PlatformTransactionManager` mock y `lockCompartidoById`):

- carga la transferencia con `lockCompartidoById` (nunca `findById`) dentro de una transacción, y
  para un ítem existente la del ítem aunque el input traiga otra; el ítem con `lockById`;
- en `PREPARACION_MERCADERIA_CONCLUIDA`, cambiar `cantidadPreparacion` → rechazo, sin save ni
  movimiento (revertir la validación y ver que falla);
- en `PREPARACION_MERCADERIA`, cambiar preparación pasa; cambiar pre-transferencia se rechaza;
- reenvío del ítem entero con lo guardado pasa en cualquier etapa, también con el vencimiento
  corrido una hora el mismo día;
- etapas intermedias (`PREPARACION_MERCADERIA_CONCLUIDA`, `TRANSPORTE_EN_CAMINO`,
  `TRANSPORTE_EN_DESTINO`) y etapa null;
- el costo de COMPRAS se registra después del save;
- ítem nuevo con ceros en etapas futuras (frc-mobile) pasa;
- `ControlStockNegativoService`: un fallo del `INSERT` vuelve al savepoint (SQL esperado).
- El savepoint y la conexión compartida no se pueden probar con mocks: van en la prueba de runtime.

Batería: `./mvnw clean verify -B -DskipFlyway=true` (en copia fuera de `dev-frc`: jdtls pisa
`target/` de los worktrees).

## Datos, persistencia, clientes, filial

- **Migraciones**: ninguna. **Datos nuevos**: ninguno.
- **Contrato GraphQL**: sin cambios de schema. Lo nuevo es un rechazo con texto. El desktop lo
  muestra por `onCustomMutation`/snackbar, la PWA por `datos.mutar`, frc-mobile con «algo salió mal».
- **Filial**: `N/A para filial`. El filial tiene su propio `saveTransferenciaItem` con la misma
  carrera, pero ningún cliente lo invoca: el desktop usa `servidor = true` (generic-crud.service.ts
  :813/828) y PWA y frc-mobile solo hablan con el central. Queda como residual.
- **Réplica**: `transferencia`/`transferencia_item` no cambian de forma ni de dirección.

## Riesgos

- **Falso rechazo de un reenvío entero** si lo que vuelve no es idéntico a lo guardado (formato de
  vencimiento, presentación). Se mitiga comparando el vencimiento por día y la presentación por id;
  la prueba de runtime del desktop (que reenvía todo) pasó.
- **Esperas más largas**: un save de ítem espera a un avance o a un `saveTransferencia` de la misma
  transferencia, y el lock de saldo se mantiene hasta el commit del save. Espera, no falla.
- **Atomicidad nueva**: si falla el movimiento de stock, la asignación de lotes o vence la espera
  del lock de saldo (15 s, «Reintentá»), hoy el ítem queda guardado sin movimiento; ahora se
  revierte todo. Es mejor, pero cambia lo que ve el usuario (no queda guardado a medias).
- **Locks hasta el commit**: el save sostiene transferencia (share), ítem y lote mientras espera el
  saldo, y un avance de muchos ítems (5-10 s con 118 en #463) hace esperar al save. Sin
  `lock_timeout` propio, igual que `lockById` de #464.

## Prueba de runtime (central local, perfil dev)

1. Transferencia de prueba clonada en la base local, con ítems, en `PREPARACION_MERCADERIA`.
2. **Solapados (bloqueante)**: fila de la transferencia tomada `FOR UPDATE` desde psql (simula el
   avance), `saveTransferenciaItem` en paralelo → queda en espera de lock (no `40001`); al soltar,
   guarda. Con el avance real a `PREPARACION_MERCADERIA_CONCLUIDA` en el medio → rechazo con texto.
3. **Pantalla vieja** en el desktop (`ng serve -c web`): dos pestañas, avanzar en una y modificar un
   ítem en la otra → aviso, lo copiado no cambia.
4. **Reenvíos enteros que tienen que pasar**: desktop modificar/confirmar/rechazar en preparación,
   transporte y recepción; «Elegir lotes»; alta y edición en creación; PWA verificar; avance de
   punta a punta igual que antes.
5. Alta de ítem con el control de stock negativo forzado a fallar → el ítem queda guardado; y
   dentro del save `pg_backend_pid()` de JPA y de `JdbcTemplate` coinciden.
6. Dos saves simultáneos del mismo ítem: el segundo espera y no pisa con lectura vieja.
7. Restaurar/borrar los datos de prueba.

## Auditoría del plan (paso 5)

Dos auditores en paralelo, sin verse. Qué se hizo con cada hallazgo:

| Eje | Hallazgo | Decisión |
|---|---|---|
| B | `status.createSavepoint()` lanza con `JpaTransactionManager` + Hibernate | Verificado (`javap`: `SessionTransactionData` no es `SavepointManager`). Savepoint por SQL en el servicio |
| B | Sin el `SERIALIZABLE`, dos saves del mismo ítem: el segundo pisa al primero en silencio | Lock del ítem (`PESSIMISTIC_WRITE`) después del de la transferencia |
| B | `registrarCostoCompraManual` es `@Transactional`: si lanza adentro, se pierde el save entero | Costo de COMPRAS después del commit |
| A + B | Lock y validación sobre el `transferenciaId` del input, no el del ítem | Para un ítem existente, la transferencia real del ítem (consulta escalar sin lock) |
| A | Desktop puede reenviar un vencimiento de fecha pura corrido una hora (DST): comparar a minutos lo rechazaría y trabaría el ítem | Vencimiento por día |
| A | Etapa null / orden de comparación sin definir | Etapa null no valida; se compara por `getOrden()`; tests de etapas intermedias y null |
| A | Ningún flujo legítimo de desktop (develop y master), PWA ni frc-mobile cambia un grupo cerrado; ítems nuevos (con ceros de frc-mobile) no se validan | Confirma la regla |
| A | Filial tiene el mismo resolver | Ningún cliente lo usa: residual |
| B | Sin ciclo de locks: avance, `saveTransferencia` y verificación toman la transferencia antes que ítems; ajustes y FEFO solo saldo; `desconfirmar`/`verificarProducto` no piden saldo | Sin cambios; queda escrito acá |
| B | Los tests unitarios no prueban lock, savepoint ni conexión compartida | La prueba de runtime 2 y un alta con el control forzado a fallar son bloqueantes |

## Auditoría del diff (paso 8)

3 fijos, sin condicionales (el diff no toca release, migraciones ni replicación).

| Eje | Hallazgo | Decisión |
|---|---|---|
| Fijo 1 | Sin resolver nuevo ni dato expuesto; falta de rol en las mutations de transferencia es preexistente (#177). El mensaje dice la etapa de un id ajeno, lo mismo que ya devuelve `transferencia(id)`; los locks duran lo que la transacción | Sin cambios |
| Fijo 2 | JPQL válido; `PESSIMISTIC_READ` → `for share of <alias>`, solo la tabla raíz (las EAGER van por select aparte); `JdbcTemplate` comparte la conexión del `JpaTransactionManager` de Boot | Confirmado en runtime (SQL capturado abajo) |
| Fijo 3 | Ningún falso rechazo en desktop, PWA ni frc-mobile. **La PWA des-verifica antes de guardar: con pantalla vieja quedaba un grupo cerrado vaciado** | Fase 2 |
| Fijo 3 | frc-mobile muestra «algo salió mal» sin el texto; el botón «Editar» del desktop en preparación queda habilitado para quien no es responsable (ahora se rechaza) | Residual de clientes |

## Resultado (2026-10-21)

**Tests.** `./mvnw clean verify -B -DskipFlyway=true`: 2209 tests, 0 fallas. Con
`validarEtapasCerradas` comentado fallan 4 tests de `TransferenciaItemGraphQLEtapaCerradaTest`; con
la validación de `desconfirmar` anulada falla `noDesverificaUnaEtapaCerrada`.

**Runtime** (central de la rama en 8084 desde copia, perfil dev, `bodega` local; clones de la 51251
→ 58227 a 58229; desktop `web-production` de `develop` en 4201; PWA de `develop` en 4302):

| Prueba | Resultado |
|---|---|
| Avance simulado por psql (`FOR UPDATE` 8 s) sin cambios + save del ítem | El save queda en `wait_event_type = Lock` sobre `... from operaciones.transferencia transferen0_ where transferen0_.id=$1 for share of transferen0_` y guarda al soltar (~7 s). Sin `40001` |
| Avance simulado que pasa a `PREPARACION_MERCADERIA_CONCLUIDA` y copia a transporte + save que cambia preparación | Espera y se rechaza con el texto; el ítem queda como lo dejó el avance |
| Alta con el `INSERT` de control forzado a fallar (CHECK temporal) | El ítem se guarda, el control no, warning en el log. Sin el CHECK el control se registra: el `SAVEPOINT` corrió dentro de la transacción, en la misma conexión |
| Dos saves del mismo ítem (psql con el ítem `FOR UPDATE` que cambia la observación) | El save espera, relee y conserva la observación del otro |
| `desconfirmar` de una etapa cerrada / de la etapa en curso (API) | Rechazo sin tocar nada / vacía el grupo como antes |
| Desktop, pantalla vieja: preparación concluida por otro, «Modificar cantidad» | Snackbar con el motivo; la fila no cambia; «Actualizar» muestra la etapa real |
| Desktop, flujo completo de la 58229: preparar, modificar cantidad, verificar para transporte sin chofer, rechazar y desconfirmar/confirmar en transporte, transporte, recepción, rechazar en recepción, finalizar | Todo pasa. El ítem con vencimiento `2024-10-06 00:00` volvió del desktop como `01:00` (el corrimiento del cambio de horario): por día pasa; a minutos se habría trabado |
| Desktop en creación: editar cantidad, alta, «Elegir lotes» (creación y preparación, con lotes de prueba) | Pasa; en preparación el movimiento se re-desglosa al lote elegido (lock de saldo dentro de la transacción) |
| PWA: rechazar en preparación; pantalla vieja con preparación concluida → «Confirmar como viene» | Pasa / el `desconfirmar` se rechaza con el texto y el grupo cerrado queda intacto |

## Residuales (fuera de este PR)

- `desconfirmarTransferenciaItem` no toma lock: la carrera contra un avance en curso sigue (el
  rechazo de la fase 2 cubre la pantalla vieja, no los solapados).
- frc-mobile no muestra el texto del rechazo; el desktop deja abrir «Editar» en preparación a quien
  no es responsable.
- El `saveTransferenciaItem` del filial (sin llamadores).
- Un input con `transferenciaId` distinto al del ítem lo sigue moviendo de transferencia, como hoy.
