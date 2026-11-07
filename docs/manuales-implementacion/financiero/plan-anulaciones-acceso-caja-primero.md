# Plan: en las anulaciones por módulo, el acceso a la caja va primero

Repo: central. Rama `fix/financiero-entrada-varia-orden-validaciones` desde `origin/develop`. Sin migración ni cambios
de GraphQL.

## 1. Qué cambia

`TesoreriaService.revertir` exige el permiso de escritura sobre la caja antes de mirar el estado del movimiento.
Las anulaciones por módulo llegan a `revertir` recién al final; antes validan el estado del documento y el límite de
antigüedad. Este cambio les pone la misma regla: después del lock del documento, `requireEscrituraCaja` sobre las cajas
que la anulación va a revertir, y recién entonces el estado, el límite y el resto de los controles.

| Servicio | Cajas que se validan primero |
|---|---|
| `EntradaVariaService.anular` | la caja de la entrada (se inyecta `TesoreriaSecurityService`; cambia el constructor y su test) |
| `OperacionFinancieraService.anular` | las cajas de origen y destino que tenga. En `TRANSFERENCIA_ENTRE_CAJAS` el destino conserva su mensaje actual («este envío lo anula quien administra la caja que lo recibió»); en los otros tipos, el genérico. `exigirPermitidaEnModoAcotado` queda después |
| `PagoProveedorService.anularPagoCpp` | las cajas de los movimientos de caja de **todos** los detalles del pago (también los ya anulados), antes del estado y antes de «sin detalles activos». Los detalles de banco o cheque no tienen caja: sin cambio |
| `RetiroVerificacionService.anular` | la caja del retiro mientras la verificación está vigente (`Retiro.cajaVirtualId`, que también cubre la verificación contada en cero) y, si no, la del último movimiento de caja del retiro (finder nuevo en `MovimientoCajaVirtualRepository` sin el filtro de activos). Se valida antes del primer control; la relectura después del lock (#376) no se toca |

El resultado final no cambia para quien tiene acceso. Las llamadas de permiso del modo acotado quedan como están
(duplicadas e inocuas: no limpiarlas). El permiso queda después del lock del documento (un usuario sin acceso espera
ese lock, igual que hoy).

## 2. Tests

Patrón existente: `mock(TesoreriaSecurityService)` con `doThrow(...).when(seguridad).requireEscrituraCaja(caja)`.

- Por servicio: usuario sin acceso y documento ya anulado → la respuesta es la del permiso, y
  `verify(..., never())` sobre la lectura del estado. En pago, con todos los detalles anulados. En retiro, una
  verificación anulada (stub del finder nuevo) y una vigente contada en cero.
- Operación financiera: el mensaje especial sigue en transferencia entre cajas; un `RETIRO_BANCARIO` sin acceso al
  destino recibe el genérico.
- Ajustes: `EntradaVariaServiceTest` (constructor y caja en el helper), `seguridad` como campo en
  `PagoProveedorServiceTest` y `RetiroVerificacionAnularTest`, `verify(times)` en `PagoProveedorServiceRolCajaTest` si
  hace falta.
- Revertir el cambio y ver que los tests nuevos fallan.
- Batería: `./mvnw clean verify -B -DskipFlyway=true` (en una copia fuera de `dev-frc`, por jdtls).
- Por pantalla (central de la rama + desktop): con `GERENTE12` (rol de caja, lectura sin escritura en la caja 18)
  intentar anular en la 18 una entrada varia, una operación financiera, un pago y una verificación de retiro (con
  movimientos) → mensaje de permiso; con MR las cuatro siguen anulándose y la segunda vez sigue «ya está anulada».

## 3. Lo que no cambia
- El desktop. Los roles que pide cada mutation. Las reversas. Los textos de los mensajes.

## 4. Datos nuevos
Ninguno (un método de repositorio de solo lectura).

## 5. Auditoría del plan (paso 5)
- Eje A: llamadores (resolvers, `AnulacionPagoRrhhService`, resolución de casos de retiro) siempre con usuario; los
  procesos de sistema y ADMIN no cambian. Mayores en pago (detalles anulados) y retiro (caja de una verificación
  anulada o en cero). Incorporados.
- Eje B: sin locks nuevos (las consultas de permiso son lecturas planas); rollback sin estado. Mayores: constructor
  de `EntradaVariaService` y finder de retiro. Incorporados, con los ajustes de tests.

## 6. Sin verificar
- Verificaciones de retiro en cero ya anuladas: no hay caja de dónde tomarla; quedan como hoy.
