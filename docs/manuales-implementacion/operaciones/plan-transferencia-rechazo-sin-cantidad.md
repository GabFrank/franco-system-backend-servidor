# Plan — rechazar un ítem sin cantidad de la etapa (issue #474)

Rama: `fix/transferencias-rechazo-sin-cantidad` (desde `develop` e9298928). Pieza: central.

## Qué pasa hoy

`desconfirmarTransferenciaItem` vacía cantidad, presentación y motivos de la etapa (preparación,
transporte o recepción) y desactiva su movimiento. Si después se **rechaza** el ítem sin volver a
cargar cantidad, `saveTransferenciaItem` → `MovimientoStockService.createMovimientoFromTransferenciaItem`
revienta:

| Etapa | Línea que revienta | Por qué |
|---|---|---|
| `PREPARACION_MERCADERIA` | `e.getCantidadPreparacion() * e.getPresentacionPreparacion().getCantidad()` | cantidad/presentación `null` |
| `TRANSPORTE_VERIFICACION` | `e.getCantidadTransporte() * e.getPresentacionTransporte().getCantidad()` | ídem |
| `RECEPCION_EN_VERIFICACION` | `e.getCantidadRecepcion() * ...` y además `ms = movimientoStockSalida; ms.setEstado(...)` | ídem, y salida `null` si no existe |

La issue reporta la de preparación; las otras dos son el mismo código con el mismo disparador
(desconfirmar vacía las tres etapas igual).

**Transacción:** en `develop` `saveTransferenciaItem` ya corre en un `TransactionTemplate` (#470),
así que la excepción hace rollback del ítem. Lo que describe la issue («el rechazo queda guardado»)
es de un build anterior a #470. Se verifica en la prueba de runtime, no se cambia código.

## Qué se hace

Solo en `createMovimientoFromTransferenciaItem`. La regla mira dos cosas: si a la etapa le falta
cantidad o presentación (`== null`; un 0 con presentación sigue como hoy) y si el ítem está
rechazado:

- **Rechazado:** el movimiento de esa etapa **no se recalcula**. El existente queda con
  `estado = false` y, si no existe, no se crea.
- **No rechazado:** `GraphQLException` «El item N no tiene cantidad verificada en <etapa>: verificalo
  o rechazalo antes de seguir». Hoy ese caso lo corta la NPE, por ejemplo un avance de etapa con un
  ítem sin verificar. Se sigue cortando, pero con un mensaje claro. Si pasara de largo, la mercadería
  viajaría sin descontar stock (hallazgo del eje A).

Qué pasa en cada etapa:

| Etapa | Caso | Resultado |
|---|---|---|
| Preparación | rechazado, con salida | salida `estado=false` |
| Preparación | rechazado, sin salida | no se crea nada |
| Transporte | rechazado, con salida | salida `estado=false` |
| Recepción | rechazado, con entrada | entrada `estado=false` |
| Recepción | rechazado, sin entrada | no se crea nada |

En recepción, la salida no se recalcula en cantidad, solo en estado (`!esRechazado`, como hoy). Si
no existe, no se toca; antes daba NPE.
- Con cantidad y presentación, todo queda igual que hoy.
- El desglose por lote ya maneja `estado=false` (`sincronizarEstado`) y `null` (lo saltea).

**No se toca:** schema GraphQL, clientes, migraciones, `avanzarEtapa` (copia cantidades antes de
llamar). Sin datos nuevos: no hay tabla de escritor/lector.

## Fases

| Fase | Commit | Tests |
|---|---|---|
| 0 | `docs(transferencias): plan para rechazar un item sin cantidad de la etapa` | — |
| 1 | `fix(transferencias): rechazar un item sin cantidad no revienta al generar el movimiento` | ver abajo |

Tests nuevos (`MovimientoStockServiceTransferenciaTest`, servicio con repositorio mockeado):

- preparación rechazada sin cantidad con salida existente → salida `estado=false`, sin NPE;
- preparación rechazada sin cantidad y sin salida → no guarda movimiento;
- transporte rechazado sin cantidad → salida `estado=false`;
- recepción rechazada sin cantidad, sin entrada → no crea entrada; salida `estado=false`;
- recepción sin salida y con cantidad → crea la entrada sin NPE;
- preparación con cantidad → salida con la cantidad negativa de siempre (regresión);
- preparación y recepción sin cantidad **y sin rechazo** → `GraphQLException` con el id, sin guardar;
- volver a cargar cantidad tras el rechazo → la misma salida vuelve a `estado=true` con la cantidad nueva;
- producto con lote rechazado sin cantidad → `sincronizarEstado` sobre la salida desactivada.

Se revierte el fix y se comprueba que los de NPE fallan: hecho, 8 de 10 fallan con `develop` y los
2 de regresión pasan.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Tratar igual «rechazado» y «sin verificar» deja pasar un avance de etapa con un ítem sin cantidad, y la mercadería viaja sin descontar stock | aceptado: sin rechazo se corta con un mensaje claro |
| A | No queda claro si el 0 cuenta como «sin cantidad» | fijado: solo `null`. El 0 sigue como hoy |
| A | Cambio de origen: la salida vieja no se encuentra | ya pasa hoy, queda fuera de alcance |
| B | El rollback del ítem ante la excepción ya está en `develop` (#470) | confirmado leyendo el código; se prueba en runtime |
| B | Faltan tests para re-cargar cantidad y para producto con lote | agregados |
| B | El texto de recepción es ambiguo | aclarado: la salida solo cambia de estado |

## Verificación

- `./mvnw clean verify -B -DskipFlyway=true` (copia fuera de `dev-frc`, por jdtls).
- Runtime: central local (8084 si el 8081 es de otra sesión) + desktop web, transferencia en
  preparación: Desconfirmar → Rechazar (FALTA_PRODUCTO) → sin error, la fila queda rechazada y el
  movimiento `estado=false`. Lo mismo en recepción. Y un caso con error forzado para ver que el
  ítem no queda guardado (rollback de #470).

## Sin verificar

- Transporte por pantalla, si el desktop no ofrece desconfirmar en esa etapa: se cubre por test y
  por API.
