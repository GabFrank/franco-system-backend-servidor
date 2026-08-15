# Pagar Vale — vales de RRHH pagados con el motor de CPP

Estado: implementado (2026-08-15). Reemplaza en el hub de egresos de la Caja Mayor la opción
**"Registrar Vale"** por **"Pagar Vale"**.

## El problema

`Registrar Vale` era un atajo: creaba el vale y lo confirmaba en una sola transacción
(`ValeService.crearValeConfirmado`), posteando un egreso directo de caja. Eso dejaba dos huecos:

1. **No se podía pagar un vale ya registrado.** El mobile crea vales en estado `SOLICITADO`
   (`RrhhMobileService`), y no había forma de entregarles la plata desde tesorería.
2. **Solo efectivo de caja mayor.** Sin cuenta bancaria, sin cheque, sin multi-moneda —
   a diferencia de "Pagar Gasto", que ya tenía todo eso.

## El diseño: puente `Vale ↔ SolicitudPago(tipo = RRHH)`

Un gasto pagable es una `SolicitudPago` de `tipo = GASTO`, y el motor de CPP
(`PagoProveedorService.procesarEvento`) sabe pagarla con cualquier combinación de formas de pago.
El vale reusa exactamente ese motor: su **obligación de pago** se representa con una
`SolicitudPago` de `tipo = RRHH` — un valor que ya existía en el enum y estaba sin usar.

```
rrhh.vale (SOLICITADO)  ──solicitud_pago_id──▶  SolicitudPago(RRHH, SOLICITADO)
                                                          │
                                          pagarValesMixto │ (motor CPP)
                                                          ▼
                                                SolicitudPago CONCLUIDO
                                                          │ hook de sincronización
                                                          ▼
                                                rrhh.vale CONFIRMADO
```

- **El vale sigue siendo la verdad de RRHH.** La liquidación descuenta leyendo `rrhh.vale`
  (estado `CONFIRMADO`); la solicitud es solo el documento pagable.
- **Cero lógica de pago nueva.** Cheques, banco, ajuste FX, reparto FIFO y anulación por evento
  vienen gratis del motor existente.
- `ValeService.sincronizarDesdeSolicitudPago` es el espejo de
  `PreGastoService.sincronizarDesdeSolicitudPago`, y lo llama el motor en los mismos dos puntos
  (al pagar y al anular).

### Consecuencia asumida sobre el ledger

El movimiento de caja de un vale pagado por este flujo queda **`PAGO_PROVEEDOR` / `origenTipo = PAGO_CPP`**,
no `EGRESO` / `RRHH_VALE` como el atajo viejo. Es el precio de reusar el motor. Se preserva
`vale.movimiento_caja_virtual_id` (el FK inverso que usa la trazabilidad) apuntando al movimiento
consolidado, y la descripción del movimiento es `VALE #id - FUNCIONARIO - MOTIVO`.

**La anulación va por `anularPagoCpp`**, no por `anularVale`: `TesoreriaService.anular` bloquea
todo origen que no sea `MANUAL`. En la UI es ⋮ → Anular sobre la fila de caja del pago.

## Reglas propias (dónde se aparta de "Pagar Gasto")

| Regla | Por qué |
|---|---|
| **Un vale se paga entero o no se paga.** El backend rechaza el pago parcial y la columna "Monto a pagar" es de solo lectura. | La liquidación descuenta `vale.monto` **completo**. Entregar 400.000 de un vale de 1.000.000 sacaría plata de la caja que nunca se recupera del sueldo. |
| **`ValeService.anular` rechaza un vale con `solicitud_pago_id` y estado `CONFIRMADO`.** | `revertirEgresoCaja` hace `return` si no hay `cajaVirtualId`: anularía el vale **sin devolver la plata**, en silencio. La reversión correcta es anular el pago. |
| **Un vale sin moneda no es pagable.** Fila no seleccionable con chip `sin moneda`, más guard en el backend. | Hay vales viejos con `moneda_id NULL` (verificado en la base de desarrollo). Sin moneda no se puede armar la obligación ni saber en qué moneda sale la plata. |
| Selección multi-vale: solo exige **misma moneda** (el funcionario puede variar). | Mismo criterio que gastos. Con varios vales la etiqueta del movimiento pasa a `Pago de vales (N)`. |

## Qué se tocó

**Backend (`central`)**

| Archivo | Cambio |
|---|---|
| `V198.5__rrhh_vale_solicitud_pago.sql` | Columna `rrhh.vale.solicitud_pago_id` + índice único parcial. Aditiva. |
| `domain/rrhh/Vale.java` | Campo `solicitudPagoId` (FK plana). |
| `repository/rrhh/ValeRepository.java` | `findBySolicitudPagoId`, `findByEstadoOrderByFechaDescIdDesc`. |
| `service/operaciones/SolicitudPagoService.java` | `crearSolicitudVale(...)` (`tipo = RRHH`, estado `SOLICITADO`). |
| `service/rrhh/ValeService.java` | `sincronizarDesdeSolicitudPago(...)` + guard en `anular`. |
| `service/financiero/ValeTesoreriaService.java` (nuevo) | `listarValesPendientes`, `crearValeParaPago`, `pagarValesMixto`. |
| `service/financiero/PagoProveedorService.java` | 2 hooks de sincronización + etiqueta del movimiento para vales. |
| `graphql/rrhh/ValeTesoreriaGraphQL.java` + `vale-tesoreria.graphqls` | `valesPendientes`, `crearValeParaPago`, `pagarValesMixto`, type `ValePendiente`. |

**Sin ciclo de beans:** `PagoProveedorService → ValeService`, y `ValeService` no conoce el motor de
pago (el que lo usa es `ValeTesoreriaService`). Por eso el hook vive en `ValeService`.

**Seguridad:** `RrhhSecurityService` — `requireVer()` para listar, `APROBAR` para crear y pagar.
Es **paridad exacta** con `crearValeConfirmado`, que es lo que el hub hacía antes: no amplía ni
restringe privilegios.

**Desktop**

"Pagar Gasto" no es un diálogo aparte: es `PagarComprasDialogComponent` con `modo: 'GASTOS'`.
Se agregó un tercer modo **`VALES`** al mismo componente (título, columnas, filtros, fuente de datos
y vista de alta ya eran condicionales por modo). Se eliminó `registrar-vale-dialog/`, que quedó sin
referencias.

Columnas: `N° · Funcionario · Motivo · Descripción · Saldo pendiente · Monto a pagar`.
Filtros: `N° · Funcionario · Motivo`, más el botón **+ Nuevo Vale** (funcionario, motivo, moneda,
monto, es adelanto, observación) que crea el vale **pendiente de pago**, sin mover plata.

## Cómo probarlo

Entorno local según `ENTORNO_LOCAL.md` (central contra `bodega_producto_devoluciones` en 5551).

1. Caja Mayor → **Egreso** → **Pagar Vale**.
2. **+ Nuevo Vale** → funcionario, monto, guardar. Aparece en la lista con saldo, **sin** movimiento de caja.
3. Seleccionar el vale → Siguiente → forma de pago (caja mayor, banco, o mixta) → Revisar → Confirmar.
4. Verificar: vale en `CONFIRMADO`, movimiento en la caja con descripción `VALE #id - FUNCIONARIO - MOTIVO`,
   saldo de caja bajado por el monto exacto.
5. ⋮ → **Anular** sobre esa fila: el vale vuelve a `SOLICITADO`, la solicitud a `SOLICITADO` con
   `monto_pagado = 0`, y el saldo se restituye.

**Verificado end-to-end el 2026-08-15** contra el central local: pago simple, pago mixto
(caja mayor + cuenta bancaria), anulación con reversión exacta del saldo, rechazo de pago parcial,
rechazo de vale sin moneda y rechazo de `anularVale` sobre un vale pagado desde tesorería.

> ⚠️ Sirviendo el desktop como web (`ng serve`), los pasos 2 y 3 del stepper quedan fuera del área
> visible del diálogo. **No es de este cambio**: pasa igual en el modo COMPRAS, que no se tocó.
> El contenido está en el DOM y el flujo funciona; es un problema de layout del stepper en browser.

## Tests

- `ValeTesoreriaServiceTest` (6) — listado con saldo, alta sin mover plata, rechazo de vale no pendiente,
  rechazo de pago parcial, creación perezosa de la obligación, reutilización de la existente.
- `ValeServiceSincronizacionTest` (5) — CONCLUIDO ⇒ CONFIRMADO + linkeo del movimiento, anulación ⇒
  SOLICITADO, no toca un vale `DESCONTADO`, ignora solicitudes que no son de vale, guard de `anular`.
