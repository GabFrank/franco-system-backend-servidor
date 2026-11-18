# Plan #602: el alta de moneda muestra un mensaje claro cuando la base la rechaza

Issue: GabFrank/franco-system-backend-servidor#602. Rama: `fix/moneda-alta-error-claro` (desde `develop` 51238d7e).
Pieza: **central** solamente.

## Problema

Desktop, Financiero › Configuración › Monedas › Adicionar. Si el `INSERT` choca con una restricción, el aviso muestra el
error de Hibernate tal cual («could not execute statement; SQL [n/a]; constraint [moneda_pkey]; nested exception is
org.hibernate.exception.ConstraintViolationException…»). En alpha (10/11/2026) fue la secuencia
`financiero.moneda_id_seq` atrasada (en 1 con máximo 4), ya corregida; el runbook «Moneda por módulo» (B3) pide un
mensaje claro, no un error de Hibernate.

`MonedaGraphQL.saveMoneda` ya traduce el **código ISO repetido** antes de guardar («El codigo ISO USD ya lo tiene
DOLAR.»), pero cualquier rechazo en el `save` (`DataIntegrityViolationException` al hacer commit la transacción de
`CrudService.save`) sube crudo.

Restricciones de `financiero.moneda` (alpha): `moneda_pkey`, `uq_moneda_codigo_iso` (índice único), `uq_moneda_principal`
(una sola principal), FK a `general.pais` y `personas.usuario`. El resolver no puede romper las dos últimas ni la de
principal (las resuelve por id y conserva `principal`); el ISO repetido puede llegar igual si dos guardan a la vez.

## Enfoque

El patrón que ya usa el repo (`LiquidacionConceptoService`, `ChequeGestionService`): capturar la
`DataIntegrityViolationException` del `save`, **loguear el detalle** y lanzar un `GraphQLException` con el mensaje según
la restricción (nombre de `org.hibernate.exception.ConstraintViolationException.getConstraintName()`, y si no viene, el
texto de la causa):

| Restricción | Mensaje |
|---|---|
| `moneda_pkey` | «No se pudo guardar la moneda: la numeracion de monedas de la base quedo atrasada. Avisa a soporte.» (corto: el snackbar del desktop dura 3 s) |
| `uq_moneda_codigo_iso` | «El codigo ISO {iso} ya lo tiene otra moneda.» (mismo texto que la validación previa, sin el nombre) |
| `uq_moneda_principal` | «Ya hay otra moneda principal.» |
| otra | «No se pudo guardar la moneda: la base la rechazó. El detalle quedó en el registro del servidor.» |

El detalle (restricción y causa) va al log con `warn`, con el id y la denominación.

**Descartado:**
- Corregir la secuencia sola al chocar (`setval`) y reintentar: la issue pide el mensaje; ajustar secuencias de una
  tabla que replica a las filiales desde la aplicación es otro alcance (la memoria de la máquina tiene casos donde una
  secuencia mal movida trabó la réplica).
- Un manejador global de `DataIntegrityViolationException` en `GraphqlExceptionHandler`: cambiaría los mensajes de
  todas las pantallas; esta issue es de moneda.

## Fase única

- `MonedaGraphQL.saveMoneda`: `try { service.save(e) } catch (DataIntegrityViolationException)` → helper
  `static String mensajeRechazo(DataIntegrityViolationException, String iso)` (package-private) + log.
- **Test** `MonedaGraphQLRechazoTest` (unitario, mocks): para cada restricción el mensaje de la tabla (con
  `ConstraintViolationException` de Hibernate con nombre, y sin nombre pero con el texto de PG en la causa); el
  detalle de Hibernate no aparece en el mensaje; un save que funciona sigue devolviendo la moneda. Falla con el código
  viejo (sale la `DataIntegrityViolationException`).

## Datos nuevos

Ninguno: sin migración ni cambio de GraphQL.

## Contrato y canales (eje A)

Mismo mutation; cambia solo el texto del error. Cliente: desktop (ABM de monedas), que muestra el mensaje con «Ups! Algo
salió mal en operacion: …». Sin orden de despliegue.

## Pruebas de runtime

Central de la rama contra la **bodega local** (perfil `dev`, Flyway, réplica y jobs apagados) y el desktop web contra
ese central:
1. **Secuencia atrasada** (lo de alpha): anotar `last_value`/`is_called` de `financiero.moneda_id_seq`, `setval` a 1,
   Adicionar una moneda desde el desktop → mensaje claro; restaurar la secuencia al valor anotado. Cada intento fallido
   consume un número: un solo intento, y verificar que no quedó ninguna moneda nueva.
2. **Alta que funciona** con la secuencia bien: la moneda se crea; borrarla al final (y volver la secuencia al valor
   anotado si hace falta).
3. ISO repetido: el mensaje de siempre (validación previa), sin cambio.

## Resultado de las pruebas (11/11/2026)

**Tests:** `MonedaGraphQLRechazoTest` 7/7 en verde (más `MonedaGraphQLSaveTest` 5/5). Con el `save` sin `try/catch`
fallan los 6 de rechazo (sale la `DataIntegrityViolationException`); el alta que funciona pasa igual.

**Runtime:** central de la rama contra la bodega local (perfil `dev`, Flyway, réplica y jobs apagados) y el desktop web
contra él (Financiero › Configuración › Monedas › Adicionar), usuario MR.
1. **Secuencia atrasada:** `moneda_id_seq` anotada (`5`, `is_called=false`) y puesta en 1. Alta «ZZ PRUEBA 602» →
   «Ups! Algo salió mal en operacion: No se pudo guardar la moneda: la numeracion de monedas de la base quedo atrasada.
   Avisa a soporte.» No se creó ninguna moneda; el log tiene el detalle (`warn`, denominación y causa).
   **Hibernate no trajo el nombre de la restricción**: el PostgreSQL local responde en castellano («llave duplicada
   viola restricción de unicidad «moneda_pkey»») y el extractor de Hibernate no lo reconoce. La encontró el respaldo
   por el texto de la causa.
2. **Alta que funciona** (secuencia restaurada): se crea la moneda 5.
3. **ISO repetido:** alta con ISO ZZZ (moneda 6) y otra con «zzz» → «El codigo ISO ZZZ ya lo tiene ZZ PRUEBA 602 B.»,
   igual que antes.

Al terminar: monedas 5 y 6 borradas, secuencia en `5`/`false`, 4 monedas como al principio. La bodega local tiene slots
de réplica de la filial 24 local inactivos: recibirán el alta y la baja (neto cero).

## Auditoría del diff (paso 8)

Fijo 1 (permisos): `saveMoneda` sigue con `seg.requireGestionar()` primero. Fijo 2 (migraciones): N/A. Fijo 3
(operaciones/contrato): mismo mutation; solo cambia el texto del error y se agrega un log; el `catch` no consulta la
base. Condicionales: `moneda` es MAIN_TO_ALL, pero un rechazo no deja nada para replicar.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Llamadores: solo el desktop (`moneda.service.ts` → `add-moneda-dialog`); nadie depende del tipo ni del texto de la excepción | Sin cambio |
| A2/B1 | El resolver no es transaccional; `CrudService.save` sí: el rechazo sale como `DataIntegrityViolationException` ya revertida (alta: en el INSERT por IDENTITY; edición: al commit). Sin transacción externa (OSIV solo comparte la sesión) | `try/catch` alrededor del `save`, sin consultar nada en el `catch` |
| A3 | Nombre de restricción puede venir null, con esquema o mayúsculas | Se recorre la cadena de causas; `contains` en minúsculas; si falta, el texto de PostgreSQL |
| A4 | El desktop muestra el texto tal cual («Ups! Algo salió mal: …»), snackbar de 3 s | Mensaje de la secuencia acortado |
| B2 | Nada de SQL ni nombres internos al usuario; la causa (con `Key (id)=…`) solo al log en `warn` | Así; test que lo verifica |
| B3 | `moneda` es MAIN_TO_ALL pero las secuencias no replican y el INSERT fallido se revierte: el `setval` local no llega a nadie | Restaurar con `setval(seq, last_value, is_called)` |
| B4 | Único modo realista de `moneda_pkey`: alta (id null) con la secuencia atrasada | Así en runtime |

## Sin verificar

- `deleteMoneda` (`CrudService.deleteById`): el rechazo por FK sale al commit, fuera de su `try`, también crudo.
  Fuera de alcance.
