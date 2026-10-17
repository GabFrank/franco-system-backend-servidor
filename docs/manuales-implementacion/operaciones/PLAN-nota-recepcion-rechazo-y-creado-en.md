# Plan — saveNotaRecepcion: esNotaRechazo y creado_en (issue #439)

Rama: `fix/nota-recepcion-rechazo-y-creado-en` (desde `origin/develop` c9b68bd9). Solo central.

## Problema

1. `NotaRecepcionInput.java` no tiene `esNotaRechazo` aunque el `.graphqls` lo declara: el valor que
   manda el desktop se descarta y toda nota queda `es_nota_rechazo = false`. Consecuencia:
   `PedidoRepository.getValorTotalNotasRecepcionByPedidoId` suma las notas de rechazo al valor de
   las notas del pedido, el chip «Nota de Rechazo» y los bloqueos del diálogo no aparecen.
2. `saveNotaRecepcion` solo asigna `creadoEn` si viene en el input, y ni la entidad ni la columna
   tienen default. El diálogo «Rechazar ítem» no lo manda → `creado_en` nulo.
3. (Encontrado en el análisis) El save arma la entidad desde el input y `super.save` hace merge:
   una edición que no mande `creadoEn` (o, tras el fix 1, `esNotaRechazo`) pisa el valor guardado
   con null.

## Fase 1 — fix en el resolver (un commit `fix(compras): ...`)

- `NotaRecepcionInput.java`: `private Boolean esNotaRechazo;`.
- `NotaRecepcionGraphQL.saveNotaRecepcion`, después del mapeo:
  - edición (`id != null`) y falta `creadoEn` o `esNotaRechazo` → leer la nota guardada
    (`service.findById`) y conservar sus valores;
  - alta sin `creadoEn` → `LocalDateTime.now()`; alta sin `esNotaRechazo` → `false`.
- Sin cambios en el schema GraphQL ni en clientes: el campo ya existe en el `.graphqls` y el desktop
  ya lo manda (`NotaRecepcion.toInput()`, `rechazar-item-dialog.component.ts:444`).

### Tabla de datos

| Dato | Escribe | Lee |
|---|---|---|
| `nota_recepcion.es_nota_rechazo` (ya existía) | desktop `rechazar-item-dialog` → `saveNotaRecepcion` | `PedidoRepository.getValorTotalNotasRecepcionByPedidoId`; desktop `add-edit-nota-recepcion-dialog` (chip, bloqueos), `rechazar-item-dialog.generarNumeroNotaRechazo` |
| `nota_recepcion.creado_en` (ya existía) | `saveNotaRecepcion` (input o ahora) | listados/consultas del desktop |

Migraciones: ninguna (las dos columnas existen: `V0__initial_schema.sql` y `V86.5`). Espejo en filial: N/A,
sin DDL; la tabla y sus columnas ya existen en el filial.

### Tests (Fase 1)

`NotaRecepcionGraphQLSaveTest` (Mockito, sin contexto):
- alta con `esNotaRechazo=true` → entidad guardada con `true`;
- alta sin `esNotaRechazo` → `false` (no null);
- alta sin `creadoEn` → no null;
- alta con `creadoEn` → respeta el del input;
- edición sin `creadoEn` ni `esNotaRechazo` → conserva los de la nota guardada;
- edición con `esNotaRechazo=false` explícito sobre una guardada en `true` → `false` (null ≠ false);
- edición con un solo campo ausente → conserva solo ese;
- edición con id inexistente → defaults de alta, sin excepción.

Revertir el fix y comprobar que fallan los de alta con rechazo, sin `creadoEn` y la edición.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| ModelMapper 2.3.0 copia el `Boolean` null del input sobre el `= false` de la entidad (probado por B) | B | el default `false` en altas es obligatorio; test «alta sin esNotaRechazo» lo cubre (con el código viejo pasa: no discrimina, se mantiene igual como regresión) |
| `findById(null)` devuelve null y un id inexistente da Optional vacío | B | solo se llama con `id != null` y con `ifPresent` |
| Un `toInput()` sobre una nota cargada sin el campo mandaría `false` y bajaría un `true` | A | verificado falso: la edición (`add-edit-nota-recepcion-dialog.component.ts:1486`) parte de `data.nota`, y todas las queries de nota piden `esNotaRechazo`. Se respeta el valor explícito (regla de B), no hubo contradicción que arbitrar |
| El texto decía que el filial no tiene la tabla | A | corregido arriba |
| Las notas de rechazo viejas siguen en `false` y cuentan en el total del pedido | A | fuera de alcance (arreglo de datos aparte) |
| Fuera de alcance: una edición pisa con null `valor` (no está en el input; lo recalcula `actualizarValorNota`) y cualquier campo que el cliente no mande | B | anotado, no se toca |

## Prueba de runtime

Central local propio en 8084 (perfil dev; el 8081 y el 4200 son de otra sesión) + desktop web en 4201, usuario MR:
- Gestión de Compras → Recepción Documental → ⋮ de un ítem pendiente → Rechazar ítem → «Crear Nota
  de Rechazo»: en la base `es_nota_rechazo = t` y `creado_en` no nulo; el chip «Nota de Rechazo» se
  ve al abrirla.
- Editar y guardar una nota común existente: `creado_en` y `es_nota_rechazo` no cambian.
- Crear una nota común desde el diálogo de nota: `es_nota_rechazo = f`, `creado_en` no nulo.
Datos de prueba creados en la base local se dejan (base local no llega a prod).

## Fuera de alcance / sin verificar

- Corregir las notas ya guardadas mal en producción (rechazos con `false`, `creado_en` nulo). Se
  informa cuántas hay si se puede consultar; el arreglo de datos va aparte.
- frc-mobile (legacy) también llama `saveNotaRecepcion`; con el fix, si no manda los campos, el alta
  queda `false`/ahora y la edición conserva: no empeora.

## Auditoría del diff (paso 8)

Fijos 1, 2 y 3; condicionales A y B N/A (no toca release; `nota_recepcion` no está en ninguna
publicación de la base local). Sin bloqueantes. Anotado:
- una nota vieja con `creado_en` nulo toma la hora de su primera edición (aproximación, no la fecha real);
- `findById` y `save` no comparten transacción: dos ediciones simultáneas podrían pisarse (improbable);
- hallazgos de autorización: se informan por privado, no en el PR (repo público).

## Resultado de la prueba de runtime (2026-10-17)

Central del worktree en 8084 + desktop `develop` web en 4201, base `bodega` local:
- Rechazar ítem → «Crear Nota de Rechazo»: nota 22 con `es_nota_rechazo = t`, `creado_en` completado;
  la nota abre en solo lectura con el ítem RECHAZADO.
- Rechazar ítem → «Crear Nueva Nota»: nota 23 con `f` y `creado_en` completado.
- «+ Crear Nota»: nota 24 con `f` y `creado_en` del cliente.
- Editar la nota común 488504 (id 12) desde el diálogo: `creado_en` y el flag sin cambios.
- `saveNotaRecepcion` sin `creadoEn` ni `esNotaRechazo` sobre la nota 22 (como el mobile legacy):
  conserva `true` y la fecha.
Datos de prueba: ítem de pedido 8 (pedido 5) y notas 22-24 en la base local.
