# Plan — Eliminar un ítem programado desde el finiquito

Rama: `feature/rrhh-finiquito-eliminar-programado` (central y desktop, desde `develop`).
Pedido de Franco, 2026-11-02.

## Problema

Un ítem programado (`rrhh.liquidacion_item_programado`, estado PENDIENTE) entra solo al borrador del
finiquito como ítem `referencia_tipo = 'ITEM_PROGRAMADO'`. Hoy no hay forma de sacarlo por pantalla:

- `LiquidacionFinalService.eliminarItem` lo rechaza (`exigirNoProgramado`): «anulalo desde Items programados»;
- `LiquidacionItemProgramadoService.anular` lo rechaza mientras haya un finiquito no ANULADO que lo tenga
  (incluido el BORRADOR): «anulalo o volvé a generarlo antes»;
- regenerar lo vuelve a meter (sigue PENDIENTE), y el finiquito BORRADOR no se puede anular desde el desktop.

Caso real: finiquito #33 de VICTOR HUGO VERA PORTILLO, ítem «VALE CAPA DE LLUVIA 2/2 (PROGRAMADO 2026-11)».
Se sacó a mano por SQL el 2026-11-02; el programado #25 quedó PENDIENTE.

## Decisión

Eliminar el ítem programado desde el finiquito (solo en BORRADOR) **borra el ítem y anula ese programado**,
en la misma transacción. Solo ese programado: los demás del funcionario no se tocan (elegido por Franco).
Editar un ítem programado sigue bloqueado.

Rol: anular un programado es de `RRHH LIQUIDAR` (`anularItemProgramado`). Eliminar ítems del finiquito lo
puede también `RRHH SUCURSAL` (acotado). Para no abrirle al acotado una vía de anular programados, **el ítem
programado solo lo elimina quien tiene `RRHH LIQUIDAR` (o ADMIN)**; el acotado sigue recibiendo el rechazo.

## Fase 1 — central

- `LiquidacionFinalService`:
  - inyectar `LiquidacionItemProgramadoService` (no hay ciclo: depende de `LiquidacionSueldoService`, que no
    depende de `LiquidacionFinalService`);
  - `eliminarItem`: toma el finiquito con `repository.lockById` y revalida BORRADOR sobre lo que devuelve el
    lock (auditoría B1). Si el ítem es programado: exige `referenciaId` no nulo (A2) → `deleteById` + `flush`
    + `itemProgramadoService.anular(refId)` + `recalcularTotal`. El `flush` es defensivo: `anular` busca
    finiquitos vivos con ese programado por JPQL y el ítem ya no tiene que estar (FlushMode AUTO también lo
    haría). Si `anular` lanza, la transacción revierte todo y el mensaje sale como «No se pudo eliminar el
    ítem: <motivo>» (A1);
  - `aprobar`: lee con `lockById` en vez de `findById` (B1), así no pisa un total recalculado en paralelo.
    Orden de locks: finiquito → programado, igual que `pagar`;
  - `esItemProgramado(itemId)` para el resolver;
  - `editarItem` sigue con `exigirNoProgramado`; su mensaje pasa a «eliminalo del finiquito o anulalo desde
    Items programados».
- `LiquidacionFinalGraphQL.eliminarItemLiquidacionFinal`: tras `requireLiquidar`, si el ítem es programado
  → `seg.requireAnyRole(seg.LIQUIDAR)`.
- Ajustar el constructor en `ContraAsientoRrhhTest`.
- Tests nuevos (`LiquidacionFinalEliminarItemProgramadoTest`, Mockito):
  1. programado en BORRADOR → borra, hace flush, anula el programado (en ese orden) y recalcula el total;
  2. ítem no programado → borra y recalcula; no llama a `anular`;
  3. finiquito APROBADO → rechaza y no borra ni anula;
  4. `anular` lanza → sale «No se pudo eliminar el ítem: …» (el rollback real lo cubre la prueba por pantalla);
  5. programado con `referenciaId` nulo → rechaza sin borrar;
  6. el finiquito se lee con `lockById` (eliminar y aprobar).
  - resolver: acotado + ítem programado → rechazado; acotado + ítem común → pasa (se suma a la matriz
    existente o a un test propio con `RrhhSecurityFalso`).
- Batería: `./mvnw clean verify -B -DskipFlyway=true` (en copia fuera de `dev-frc` si jdtls molesta).

Sin migración. Sin cambio de schema GraphQL (misma mutation, mismo tipo de retorno).

## Fase 2 — desktop

- `liquidacion-final-dialog.component.ts`: flag `puedeEliminarProgramado = esAdmin || RRHH LIQUIDAR` en
  `ngOnInit`; `onEliminarItem` pide confirmación cuando el ítem es programado, nombrando descripción y monto
  («Se quita del finiquito y se anula el ítem programado: no vuelve al regenerar ni se puede reactivar»), y
  recién ahí llama la mutation.
- `.html`: el botón Eliminar se muestra en BORRADOR para ítems comunes (igual que hoy) y para programados si
  `puedeEliminarProgramado`. Editar sigue oculto para programados.
- Gate: `npm run check`.

## Datos nuevos

Ninguno: no nacen columnas, claves ni campos. Se escribe `liquidacion_item_programado.estado = ANULADO`, que
ya existe y ya lee `paraFiniquito` / `findPorFuncionario`.

## Compatibilidad

- Desktop viejo + central nuevo: el botón sigue oculto para programados; nada cambia.
- Desktop nuevo + central viejo: el botón aparece y el central rechaza con el mensaje de hoy; no rompe datos.
- Sin replicación: schema `rrhh` es central-only.

## Pruebas por pantalla (central 8081 dev + desktop `ng:serve`)

En la base local: armar un funcionario con un programado PENDIENTE y generar su finiquito (SQL o UI).

1. Usuario con `RRHH LIQUIDAR` (MR): eliminar el ítem programado → confirma, desaparece, total recalculado,
   programado ANULADO en Items programados; regenerar → no vuelve.
2. Cancelar la confirmación → no pasa nada.
3. Usuario solo `RRHH SUCURSAL`: el botón no aparece en el programado; por API la mutation rechaza.
4. Ítem común: se sigue eliminando como antes (sin cambios de rol).
5. Finiquito APROBADO: no hay botón; por API rechaza.
6. Forzar un rechazo de `anular` (p. ej. pasar el programado a APLICADO por SQL) → mensaje
   «No se pudo eliminar el ítem: …» y el ítem sigue en el finiquito (rollback real).

Restaurar los datos de prueba al final.

## Auditoría del plan (paso 5, 2026-11-02)

| # | Eje | Sev. | Hallazgo | Qué se hizo |
|---|---|---|---|---|
| B1 | B | alta | `eliminarItem` y `aprobar` leen el finiquito sin lock y `LiquidacionFinal` no tiene `@Version`: aprobar en paralelo puede guardar un total viejo o volver el estado | `lockById` en los dos (Fase 1) |
| B2 | B | media | anular un programado no tiene vuelta atrás por pantalla | confirmación con descripción y monto; reactivar = SQL `estado='PENDIENTE'` (anotado abajo) |
| B3 | B | media | `anular` saca el programado de una mensual BORRADOR que lo tenga | comportamiento correcto y ya existente de `anular`; se prueba por pantalla si se logra armar el caso |
| B4 | B | media | los tests Mockito no prueban rollback ni el orden flush → JPQL | prueba por pantalla contra la base local (pasos 1 y 6) |
| A1 | A | media | los rechazos de `anular` salen con textos de Items programados | envolverlos en «No se pudo eliminar el ítem: …» |
| A2 | A | media | `referenciaId` nulo en un ítem programado | guard antes de borrar + test |
| A3 | A | baja | sin ciclo de Spring (verificado por la cadena), pero Mockito no lo detecta | se confirma levantando el central |
| A4 | A | baja | roles: solo el desktop usa la mutation (PWA y mobile grepeados); ADMIN por nickname lo reconoce `hasAnyRole` | sin cambio |

## Sin verificar / riesgos
- Un programado anulado por error se reactiva solo por SQL (`estado='PENDIENTE'`).

- La mensual (`LiquidacionSueldoService.eliminarItem`) tiene la misma regla y no se toca: fuera del pedido.
- El programado #25 de producción sigue PENDIENTE: se anula por pantalla (ya no está en ningún finiquito).

## Implementación y pruebas (2026-11-02)

- Fase 1 central y Fase 2 desktop implementadas. Batería `./mvnw clean verify -B -DskipFlyway=true`: 3181 tests,
  0 fallas. `npm run check`: verde (el primero falló porque el modelo del desktop no declaraba `referenciaTipo`).
- Pruebas por pantalla contra la base local, las 6 del plan: eliminar con ADMIN (ítem fuera, total +60.000,
  programado ANULADO), regenerar no lo trae, cancelar no llama, ítem común sin confirmación, rechazo forzado
  con rollback real, rol acotado sin botón y rechazado por API, APROBADA sin botón y rechazada por API.
  Datos de prueba borrados y roles de MR restaurados.
- Auditoría del diff (paso 8): sin hallazgos altos. Media: `lockById` no refresca un finiquito ya cargado;
  hoy es la primera carga del request (`liquidacionFinal` es LAZY), quedó comentado en el código. Baja: la
  excepción re-lanzada ahora conserva la causa.
