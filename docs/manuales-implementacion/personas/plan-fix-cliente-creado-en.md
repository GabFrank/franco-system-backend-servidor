# Plan — editar un cliente no borra `creado_en`

Rama `fix/clientes-creado-en` (desde `develop`). Una sola fase, solo central.

## Problema

`ClienteGraphQL.saveCliente` arma el `Cliente` con `ModelMapper` desde `ClienteInput`. El esquema
GraphQL acepta `creadoEn` y el desktop lo manda, pero la clase Java `ClienteInput` no tiene el campo,
así que el valor se descarta. `ClienteService.save` solo lo completa en altas (`id == null`): cada
edición escribe `creado_en = NULL`.

Los otros caminos que guardan un cliente existente (`PersonaSyncController.guardarCliente`,
`FuncionarioGraphQL.saveFuncionario`, `ClienteService.buscarOCrear…`, `FacturaLegalService`) parten
de la fila guardada o son altas: no tienen el problema.

## Fase 1 — conservar `creadoEn` en la edición

- `ClienteGraphQL.saveCliente`: junto a la línea que conserva `saldoActual`, copiar
  `creadoEn` de `previo` (la fila guardada que ya lee `protegerCredito`).
- No se agrega `creadoEn` a `ClienteInput`: la fecha de alta no la decide el cliente.
- Test: `ClienteCreditoRolTest.editarConservaLaFechaDeAlta` (revertir el fix y verlo fallar).

## Datos nuevos

Ninguno. Sin migración, sin cambio de esquema GraphQL.

## Fuera de alcance

- Los `creado_en` que ya quedaron en null en producción no se pueden recuperar (no hay auditoría);
  rellenarlos con una fecha aproximada sería una decisión aparte.
- Filial: `saveUsuario` / `saveFuncionario` tienen el mismo patrón; queda para otro cambio.
- Mismo patrón en `saveCliente` con `sucursal`: el `toInput()` del desktop
  (`clientes/cliente.model.ts`) no manda `sucursalId`, así que cada edición deja
  `cliente.sucursal_id` en null. Se avisa al usuario; no entra en este fix.

## Auditoría del plan (paso 5)

- Eje A (contrato y propagación): sin cambio de esquema GraphQL; `personas.cliente` es MAIN_TO_ALL
  y el UPDATE pasa a llevar la fecha real, lo que también arregla las filiales; solo el desktop llama
  `saveCliente`. Aplicado: el riesgo de `sucursal` va a «Fuera de alcance».
- Eje B (reversibilidad): `previo` nunca es null en edición (`protegerCredito` corta si el id no
  existe); sin migración, rollback del JAR seguro. Aplicado: el test le da al cliente guardado una
  fecha fija, si no compara null con null y pasa con o sin fix.

## Auditoría del diff (paso 8)

- Fijo 1 (autorización): sin cambio; `creadoEn` sale de la base, no del input. Deuda previa anotada:
  con VER PERSONAS se puede cambiar `sucursal`/`usuario`/`activo` de cualquier cliente sin control
  de sucursal.
- Fijo 2 (esquema y espejo): sin migración ni cambio de entidad; el UPDATE replicado deja de llevar
  NULL. Las filas ya en NULL necesitarían un backfill aparte.
- Fijo 3 (contrato): `.graphqls` sin cambios; desktop y PWA usan `saveCliente` y no ven otra cosa
  que la fecha conservada.
- Condicionales: N/A, el diff no toca maquinaria de release ni DDL replicado.

## Verificación (04/11)

- Test nuevo: falla con el fix revertido (`expected <2024-03-15T10:30> but was <null>`), pasa con él.
- `./mvnw clean verify -B -DskipFlyway=true`: 3223 tests, 0 fallos.
- Por pantalla (central de la rama en 8081 + desktop web, usuario MR), confirmado en `bodega` local:
  - editar el cliente 6378: `creado_en` sigue en 2026-08-26 10:06 y la dirección cambia;
  - alta de un cliente de prueba: `creado_en` = hora del alta; editarlo la conserva.
  - Datos de prueba borrados y dirección del 6378 restaurada.
