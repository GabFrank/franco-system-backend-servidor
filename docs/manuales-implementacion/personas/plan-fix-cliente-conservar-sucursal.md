# Plan — editar un cliente no borra su sucursal

Rama `fix/clientes-conservar-sucursal` (desde `develop`). Una sola fase, solo central. Independiente
del PR #566 (`creado_en`), aunque toca el mismo método.

## Problema

`ClienteGraphQL.saveCliente` arma el `Cliente` con ModelMapper desde `ClienteInput` y solo asigna la
sucursal si `input.sucursalId != null`. El desktop (`clientes/cliente.model.ts`, `toInput()`) copia
el cliente entero que trajo la query (incluida `sucursal { id }`) pero no pasa `sucursalId` al
input, así que cada edición escribe `cliente.sucursal_id = NULL`. Se replica a las filiales
(`personas.cliente` es MAIN_TO_ALL).

Hoy 25 de 5936 clientes de la `bodega` local tienen sucursal; la ponen el alta desde el filial
(`PersonaSyncController`) y el alta automática de `saveFuncionario`. Ningún código decide nada con
ella, pero es el dato de dónde se dio de alta el cliente y se pierde sin aviso.

El resto de los campos que manda el desktop salen de la misma query y viajan completos; la
sucursal es el único que falta.

## Fase 1 — conservar la sucursal en la edición

- `ClienteGraphQL.saveCliente`: si `input.sucursalId == null` y es una edición, conservar
  `previo.getSucursal()` (mismo criterio que ya se usa con `activo`).
- Arreglo en el central y no en el desktop: cubre desktops viejos y la PWA sin release del cliente.
- Efecto: ya no se puede borrar la sucursal mandando `sucursalId: null`. Ninguna pantalla lo hace.
- Test: `ClienteCreditoRolTest.editarConservaLaSucursal` (revertir el fix y verlo fallar).

## Auditoría del plan (paso 5)

- Eje A (contrato): el único llamador de `saveCliente` es `add-cliente-dialog` (desktop); nunca
  manda `sucursalId`, nadie lo usa para borrar la sucursal y ningún código decide con ella. La PWA
  solo declara la mutation. Aplicado: test del caso inverso (con `sucursalId` la cambia) y del alta.
  El filial tiene el mismo patrón en su `saveCliente`, pero el desktop guarda clientes contra el
  central (`servidor=true` por defecto): queda anotado, no se toca.
- Eje B (reversibilidad): `previo` existe en toda edición; asignar el proxy LAZY no lo inicializa y
  el request corre con OpenEntityManagerInView. Aplicado: la línea va junto al `if (sucursalId)`,
  lejos de la de `creadoEn` del #566, para no chocar al mergear.

## Auditoría del diff (paso 8)

- Fijo 1 (autorización): sin cambio; la sucursal conservada sale de la base y el tipo `Cliente` ya
  la exponía. Sin `LazyInitializationException` en la prueba por pantalla.
- Fijo 2 (esquema y espejo): sin migración ni cambio de entidad. La FK de las filiales no corre
  riesgo: solo se reenvía un `sucursal_id` que la filial ya recibió cuando se asignó.
- Fijo 3 (contrato): `.graphqls` sin cambios; nadie manda `sucursalId: null` para borrar.
  Aplicado: los tests nuevos van en el medio del archivo y no al final, donde el #566 agrega el suyo.
- Condicionales: N/A, el diff no toca release ni DDL replicado.

## Datos nuevos

Ninguno. Sin migración ni cambio de esquema GraphQL.

## Fuera de alcance

- Las sucursales que ya quedaron en NULL no se recuperan.
- Desktop: `toInput()` podría mandar `sucursalId`; no hace falta con este arreglo.

## Verificación (04/11)

- Tests nuevos: `editarSinSucursalConservaLaGuardada` falla con el fix revertido (esperaba la
  sucursal 13, vino `null`) y pasa con él; `editarConSucursalLaCambia` y
  `elAltaSinSucursalQuedaSinSucursal` pasan en los dos casos.
- `./mvnw clean verify -B -DskipFlyway=true`: 3225 tests, 0 fallos.
- Por pantalla (central de la rama en 8081 + desktop web, usuario MR), confirmado en `bodega` local:
  - editar el cliente 6255 (sucursal 12, funcionario con crédito): la sucursal sigue en 12 y la
    dirección cambia;
  - editar el 6378 (sin sucursal): sigue en NULL.
  - Como esta rama no tiene el fix del #566, las dos ediciones dejaron `creado_en` en NULL (el bug
    del otro PR). Se restauraron `creado_en` y direcciones originales.
