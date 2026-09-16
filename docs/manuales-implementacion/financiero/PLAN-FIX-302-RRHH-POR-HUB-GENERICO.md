# Plan — fix #302: obligaciones de pago RRHH pagadas por el diálogo de compras

Issue: GabFrank/franco-system-backend-servidor#302. Rama `fix/tesoreria-rrhh-solo-por-hub-rrhh` desde
`origin/develop` `c5a3f07f` (ya trae #303).

## Decisión (usuario, 2026-09-16)

- **Se cierra el PAGO** de obligaciones RRHH por el camino genérico de compras: no se listan y no se pagan.
- **La anulación queda como está**: `anularPagoCpp` sigue con rol de tesorería. Anular devuelve la plata y deja el
  documento APROBADO; la caja es de tesorería y el dashboard ya anula así todos los pagos consolidados.

## Análisis (paso 3)

| Hecho | Evidencia |
|---|---|
| `SolicitudPago` tipo `RRHH` es **uno solo** para vale, liquidación, finiquito y aguinaldo | `TipoSolicitudPago`; `PagoProveedorService.conceptoRrhh` (`:190-197`) |
| `listarPendientes` (modo COMPRAS del desktop, `solicitudesPagoPendientes`) solo excluye `GASTO` | `PagoProveedorService.java:318-330`; desktop `pagar-compras-dialog.component.ts:338` |
| Entradas genéricas de pago, solo `requirePagarCpp` (`CPP_PAGAR` o `GESTIONAR` de tesorería): `pagarSolicitudesMixto` → `pagarLoteMixto`, `pagarSolicitud` → `pagar`, `pagarSolicitudesLoteCajaMayor` → `pagarLoteCajaMayor` | `PagoProveedorGraphQL.java:58-86`; `TesoreriaSecurityService.java:52` |
| `pagarLoteMixto` es **compartido**: lo llaman también el hub de RRHH (`RRHH PAGAR`) y el de vales (`RRHH APROBAR`) | `PagoRrhhTesoreriaService.java:159`; `ValeTesoreriaService.java:177` |
| `pagar` y `pagarLoteCajaMayor` solo los llama el resolver genérico | grep |
| Por el camino genérico no corren las validaciones del hub RRHH (pago entero, cuotas vigentes #300, obligación alineada) | `PagoRrhhTesoreriaService.validarYSaldo` / `asegurarSolicitud` |
| Mobile y mobile-pwa no usan estas operaciones | grep |

## Cambios

### Fase 1 — central

1. `PagoProveedorService.listarPendientes`: excluye `GASTO` **y `RRHH`** (cada uno tiene su modo en el diálogo).
2. Guarda privada `exigirSinObligacionesRrhh(Collection<Long> solicitudIds)`: carga las solicitudes y, si alguna es
   `RRHH`, lanza «La solicitud #n es una obligacion de pago de RRHH: se paga desde su modo (vale, liquidacion,
   finiquito o aguinaldo) en el dialogo de pagos.» Sin montos ni datos del funcionario. Los ids que no encuentra los
   ignora: `procesarEvento` ya lanza «Solicitud de pago no encontrada».
3. **Seguro por defecto** (auditoría B-2): la guarda va en las **tres entradas públicas** `pagar`, `pagarLoteCajaMayor`
   y `pagarLoteMixto`. Los dos hubs pasan a un método explícito `pagarLoteMixtoObligacionesRrhh(pagos, usuario)` (sin
   guarda, javadoc: solo para `PagoRrhhTesoreriaService` y `ValeTesoreriaService`, que ya exigen su rol RRHH). Así, un
   llamador nuevo de `pagarLoteMixto` queda cerrado sin que nadie se acuerde. El resolver genérico no cambia.
4. `PagoRrhhTesoreriaService.pagarRrhhMixto` y `ValeTesoreriaService` llaman al método nuevo; el mock del motor en
   `PagoRrhhTesoreriaServiceCuotaTest` se ajusta.

Sin cambio de schema GraphQL ni migración.

### Tests (`PagoProveedorServiceTest`, Mockito)

| Caso | Esperado | Con el código viejo |
|---|---|---|
| `listarPendientes` con solicitudes COMPRA, GASTO y RRHH | solo COMPRA | incluye RRHH → **falla** |
| `pagarLoteMixto` con una solicitud RRHH | rechaza sin mover plata | pasa → **falla** |
| `pagar` con solicitud RRHH | rechaza sin mover plata | pasa → **falla** |
| `pagarLoteCajaMayor` con solicitud RRHH | rechaza sin mover plata | pasa → **falla** |
| `pagarLoteMixtoObligacionesRrhh` (hubs) con solicitud RRHH | sigue funcionando | no existe |
| Id inexistente en la guarda | la ignora; `procesarEvento` da su error de siempre | — |
| Hub RRHH con obligación `PARCIAL` heredada del camino genérico (auditoría B-3/B-5) | paga exactamente el saldo restante y la concluye | — (comportamiento existente, sin test) |

Build: `./mvnw clean verify -B -DskipFlyway=true` leído del log.

### Implementación (fase 1) — resultado de los tests

- Guarda en `pagar`, `pagarLoteCajaMayor` y `pagarLoteMixto`; los hubs (`PagoRrhhTesoreriaService`, `ValeTesoreriaService`)
  llaman a `pagarLoteMixtoObligacionesRrhh`. El único llamador que queda de `pagarLoteMixto` es el resolver genérico.
- Tests: 7 casos nuevos en `PagoProveedorServiceTest` y 1 en `PagoRrhhTesoreriaServiceCuotaTest` (obligación `PARCIAL`
  heredada, se paga por el saldo restante); `ValeTesoreriaServiceTest` y `PagoRrhhTesoreriaServiceCuotaTest` ajustados al
  nombre nuevo → 30/30 verdes en las tres clases.
- **Con el fix neutralizado** (guarda no-op, listado solo sin `GASTO`): **fallan los 4 casos esperados** (listado, `pagar`,
  `pagarLoteMixto`, `pagarLoteCajaMayor`). Siguen pasando hubs, gasto por el genérico e id inexistente.

### Desktop

**N/A** en código: el modo COMPRAS deja de listar obligaciones RRHH, que siguen en sus modos (VALES, LIQUIDACION,
FINIQUITO, AGUINALDO). La mutation genérica rechazaría igual si alguien la llamara.

## Prueba de runtime (paso 9) — gate antes del PR

Central local contra `bodega`. Con una liquidación APROBADA con obligación `SOLICITADO` (pagar por el hub y anular
el pago desde la caja):
1. `solicitudesPagoPendientes` no la lista.
2. `pagarSolicitudesMixto` con esa solicitud → rechaza sin movimiento.
3. `pagarRrhhMixto` la paga igual.

### Resultado (2026-09-16, `1fe0a2b1`, central local contra `bodega`, mutations desde la página con la sesión del usuario)

| Paso | Resultado |
|---|---|
| Anular el pago #5 de la liquidación #488 (#300) → #488 `APROBADA`, obligación #3 `SOLICITADO` | pago #5 `CANCELADO` |
| `solicitudesPagoPendientes` | no incluye la #3; 0 filas de tipo RRHH |
| `pagarSolicitudesMixto` con la #3 | **rechaza**: «La solicitud #3 es una obligación de pago de RRHH: se paga desde su modo (vale, liquidación, finiquito o aguinaldo) en el diálogo de pagos.» |
| `pagarSolicitudesLoteCajaMayor` con la #3 | **rechaza** con el mismo mensaje |
| `pagarRrhhMixto` liquidación #488 | pago #6 `CONCLUIDO` |
| Base | solo mov 25 (AJUSTE de la anulación) y 26 (pago del hub): **los rechazos no movieron caja**; #488 `PAGADA`, obligación #3 `CONCLUIDO` |
| Log del central | solo los dos rechazos esperados |

## Datos nuevos

Ninguno.

## Persistencia, replicación, filial

Sin migración. `N/A para filial`: no tiene hub de tesorería ni estas operaciones.

## Orden de despliegue

Un PR (central). **`deploy-auto.yml` no se dispara**: deploy manual del workflow «Deploy». Compatible con cualquier desktop.

## Riesgos conocidos

- Una obligación RRHH que alguien estuviera pagando por el modo COMPRAS (costumbre) desaparece de esa lista: se paga
  desde su modo. Es el comportamiento buscado.
- `anularPagoCpp` sigue sin exigir rol RRHH (decisión del usuario).
- `pagarLoteMixtoObligacionesRrhh` es público sin guarda: solo lo pueden llamar servicios que ya exigieron un rol RRHH
  (javadoc). El riesgo quedó del lado del método nuevo y nombrado, no del de uso común.
- Obligaciones RRHH `PARCIAL` pagadas antes por el camino genérico dejan de verse en COMPRAS: se terminan de pagar por
  su hub, que calcula sobre el saldo restante (`PagoRrhhTesoreriaService.saldoPendiente`).

## Qué queda sin verificar

- Datos en producción: pagos RRHH hechos por el camino genérico (se reconocen por `Pago` con obligaciones `RRHH` y
  movimiento `origen_tipo` de RRHH pero sin pasar por el hub; no hay marca que los distinga).

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| A-1 | A | Único consumidor de `solicitudesPagoPendientes` y de las mutations genéricas: `pagar-compras-dialog`; sin uso en mobile/mobile-pwa | grep desktop/mobile | Sin cambio |
| A-2 | A · alta | Confirma el hueco: `TESORERIA CPP PAGAR` sin `RRHH PAGAR` paga obligaciones RRHH; ningún modo legítimo del diálogo usa el genérico para RRHH o vales | `PagoProveedorGraphQL.java:58-86`; `PagoRrhhTesoreriaGraphQL.java:49`; `pagar-compras-dialog.component.ts:940-948` | Es lo que cierra el plan |
| A-3 | A | **El modo GASTOS paga por `pagarSolicitudesMixto`** (no tiene mutation propia) | `onPagarMixto` para GASTOS | **Aplicado**: la guarda solo excluye `RRHH`; `GASTO` sigue pagándose por el genérico (test) |
| A-4 | A · media | `PagoGraphQL.savePago` permite marcar un `Pago` como CANCELADO sin revertir caja | **Confirmado**: `PagoGraphQL.java:32-46` sin ningún `require*` (solo el aspecto de autenticación); `ModelMapper` copia el `estado` del input. Afecta a cualquier `Pago`, no solo RRHH | Fuera de alcance: issue aparte |
| A-5 | A | `actualizarEstadoSolicitudPago` ya rechaza marcar pagada a mano; cheques y gastos sin caminos hacia RRHH; sin schema/env | verificado | Sin cambio |
| B-1 | B · baja | Tipo de solicitud inmutable (solo `setTipo` en las altas); ids no encontrados en la guarda | `SolicitudPagoService.java:376,412,433` | **Aplicado**: la guarda ignora ids no encontrados |
| B-2 | B · media | Guarda en un método nuevo deja `pagarLoteMixto` público sin guarda: un resolver nuevo reabre el hueco | Correcto | **Aplicado**: guarda en `pagarLoteMixto`; los hubs usan `pagarLoteMixtoObligacionesRrhh` |
| B-3/B-5 | B · media | Obligación RRHH `PARCIAL` heredada: ¿queda trabada? Falta test | No queda trabada: el hub calcula sobre el saldo restante | **Aplicado**: test + caso en la prueba de runtime |
| B-4/B-6 | B | Rollback limpio (sin migración); mensaje sin datos sensibles y accionable | verificado | Sin cambio |

## Auditoría del diff (paso 8)

| # | Fijo | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| D-1 | 2 · media | La guarda hacía `findById` antes del `lockById` del motor: la solicitud quedaba en el contexto de persistencia y la query con lock devuelve esa instancia **sin refrescar** → un pago parcial concurrente calcularía el saldo con un `montoPagado` viejo. Invisible a Mockito | Correcto: antes `lockById` era la primera lectura en la transacción | **Aplicado**: `SolicitudPagoRepository.findTipoById` (proyección `select s.tipo`, no gestiona la entidad); test `un_gasto_se_sigue_pagando_por_el_camino_generico` verifica `findTipoById` y `never().findById`. Con la guarda en `findById`, ese test falla |
| D-2 | 1 · alta | `anularPagoCpp` anula pagos RRHH con rol de tesorería | Ya decidido por el usuario (sección Decisión) | Sin cambio |
| D-3 | 1 · alta | `detalleDePago` (`requireVer` de tesorería) muestra monto y descripción de obligaciones RRHH | Preexistente y no ampliado por el diff: el mismo rol ya ve los movimientos de caja con la etiqueta del pago (`PagoProveedorService` `m.setDescripcion(etiquetaPago)`) | Sin cambio (riesgo conocido) |
| D-4 | 1 | Hubs exigen su rol RRHH en el resolver antes del service; `tipo` es `NOT NULL` desde V54 (backfill a `GASTO`); mensaje sin datos sensibles | verificado | Sin cambio |
| D-5 | 3 | Ningún modo del diálogo ni otra pantalla depende de que el listado genérico traiga RRHH; GASTOS sigue por el genérico; único llamador restante de `pagarLoteMixto` es el resolver; compatible con desktops viejos | grep desktop/central | Sin cambio |
| D-6 | 3 · info | `PagarComprasService.onPagarLote` (desktop) sin llamador | Preexistente | Fuera de alcance |

Tras D-1: 3 clases 30/30; `./mvnw -o clean verify -B -DskipFlyway=true` → 659/659, BUILD SUCCESS. Central reiniciado
(la query JPQL nueva arranca sin error): `pagarSolicitudesMixto` y `pagarSolicitudesLoteCajaMayor` con la #3 siguen
rechazando con el mensaje de RRHH; `solicitudesPagoPendientes` vacío.
