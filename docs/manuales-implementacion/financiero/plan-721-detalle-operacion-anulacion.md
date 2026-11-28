# Plan — desk #721 §4: quién registró y quién anuló una operación financiera

Rama `feature/financiero-detalle-operacion-anulacion` en central y desktop, desde `develop`.

## Qué falta

El resto de #721 ya está en `develop` (central #622, desktop #739). Queda §4: el diálogo
`OperacionFinancieraDetalleDialog` no muestra **el usuario que registró** la operación ni **motivo,
usuario y fecha de la anulación**.

## De dónde salen los datos (sin migración)

- **Quién registró:** `financiero.operacion_financiera.usuario_id` ya existe y lo llena
  `registrarOperacionFinanciera` (`seg.currentUsuario()`). La entidad lo mapea; el `.graphqls` no lo expone.
  Base local: 25 operaciones, 0 sin usuario.
- **Anulación:** `OperacionFinancieraService.anular` no guarda nada en la operación salvo `anulado=true`, pero
  cada pata pasa por `TesoreriaService.revertir` / `BancoLedgerService.revertir`, que crean un
  contra-movimiento con `origen_tipo='ANULACION'`, `origen_id` = id del movimiento original,
  `usuario_id` = quien anuló, `creado_en`, y la descripción `ANULACION: <motivo> (mov #N)`.
  Base local: las 20 operaciones anuladas tienen contra-movimiento (caja y/o banco), todas con ese formato.
  Sirve también para las anuladas antes de este cambio, sin backfill.

## Fases

### Fase 1 — central (PR primero)

1. `operacion-financiera.graphqls`:
   - `type OperacionFinanciera` gana `usuario: Usuario` y `anulacion: OperacionFinancieraAnulacion`.
   - `type OperacionFinancieraAnulacion { motivo: String, usuario: Usuario, fecha: Date }`.
   Solo campos nuevos; nada se quita ni se renombra.
2. `OperacionFinancieraAnulacion` (DTO, `graphql/financiero`).
3. `OperacionFinancieraFieldResolver implements GraphQLResolver<OperacionFinanciera>` con
   `anulacion(op)`: `null` si la operación no está anulada; si no, el primer contra-movimiento de caja
   (por id) y, si la operación no tiene patas de caja, el primero de banco. El motivo se saca de la
   descripción con el formato de `revertir` (`^ANULACION: (.*) \(mov #\d+\)$`, `DOTALL`, codicioso: aguanta
   paréntesis y saltos de línea en el motivo); si no matchea (p. ej. descripción bancaria truncada a 300), la
   descripción entera; en blanco → `null`.
4. Dos consultas JPQL, una en `MovimientoCajaVirtualRepository` y otra en `MovimientoBancarioRepository`:
   contra-movimientos `ANULACION` cuyo `origenId` es un movimiento `OPERACION_FINANCIERA` de esa operación
   (incluye los originales ya inactivos/anulados), `order by id asc`, y se toma el primero (determinista).
   Devuelven la entidad: el `usuario` LAZY se carga dentro del `OpenEntityManagerInViewFilter`
   (`FrancoSystemsApplication.java:101`), igual que `MovimientoBancario.usuario` hoy.
5. Seguridad: el campo cuelga de `operacionFinanciera(id)` / `operacionesFinancieras` / alta y anulación,
   que ya exigen `requireVer()` / `requireOperarCaja()`. No se agrega una puerta nueva. `Usuario` ya lo
   expone `MovimientoBancario.usuario` con el mismo tipo.

Tests (`OperacionFinancieraFieldResolverTest`, Mockito como `MovimientoBancarioFieldResolverTest`):
no anulada → null sin tocar repos; anulada con pata de caja → motivo/usuario/fecha de ella; solo banco →
del banco; descripción con formato → motivo limpio (también con paréntesis y con salto de línea); sin
formato o truncada → descripción entera; motivo en blanco → null; sin contra → null.

### Fase 2 — desktop

1. `graphql-query.ts`: `operacionFinancieraQuery` (solo el detalle; el listado y el alta no cambian)
   pide además `usuario { id persona { nombre } }` y `anulacion { motivo fecha usuario { id persona { nombre } } }`.
   Se conserva la query de hoy como `operacionFinancieraBaseQuery`.
2. `OperacionFinancieraService.onGetOperacion`: pide la del detalle con `LECTURA_ESTRICTA` (sin aviso del
   genérico, el error GraphQL llega como `{ message, errors }` al `error:`; con `PROPAGAR_ERROR_DE_RED` el
   genérico mostraría «Ups! Algo salió mal» y emitiría `null`, y el reintento nunca correría — eje A #1).
   - Error GraphQL (trae `errors`): se repite con la query base y el `errorConf` de hoy, que da el mismo
     comportamiento que hoy (incluido el aviso si el error es otro, p. ej. «No autorizado»). Así un central
     anterior a la fase 1 (`Validation error (FieldUndefined@[data/usuario]) : Field 'usuario' in type
     'OperacionFinanciera' is undefined`) carga el detalle como siempre, sin aviso.
   - Error de red: se propaga, como hoy.
   - Si el error de validación llegara como HTTP 400 (error de red), se ve en la prueba 6 y se ajusta.
3. Modelo: `usuario?: Usuario`, `anulacion?: { motivo, usuario, fecha }`.
4. Diálogo: en metadatos, «Registrada por». Si `anulado` y hay `anulacion`, un bloque «Anulada el
   dd/MM/yyyy HH:mm por X · Motivo: …». Anulada sin `anulacion` (sin contra-movimiento, o central viejo): queda
   solo el «Anulada» del título, como hoy, sin aviso de error. Textos precalculados en el componente (nada de
   funciones en el HTML).

Tests: N/A para desktop porque su CI no corre tests [ev: desktop:.github/workflows/ci.yml]. Gate `npm run check`.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `OperacionFinanciera.usuario` (GraphQL) | ya existe: `registrarOperacionFinanciera` | diálogo de detalle (desktop) |
| `OperacionFinanciera.anulacion` (GraphQL, derivado) | ya existe: `TesoreriaService.revertir` / `BancoLedgerService.revertir` vía `anular` | diálogo de detalle (desktop) |

Ninguna columna, tabla, enum ni migración nueva. Nada replicado.

## Orden y compatibilidad

- Central primero. Desktop nuevo contra central viejo: cae a la query base (sin los datos nuevos, igual
  que hoy). Desktop viejo contra central nuevo: no pide los campos, sin cambios.
- Rollback del central: no hay estado que deshacer.

## Pruebas por pantalla (local: central 8081 dev, desktop `ng serve -c web`)

1. Depósito activo: «Ver detalle» desde banco y desde caja → «Registrada por».
2. Operación anulada con motivo (ej. op 27 «PRUEBA») → bloque de anulación con motivo, usuario y fecha.
3. Anulada sin motivo (op 20) → motivo «Anulación operación financiera #20».
4. Transferencia bancaria anulada (solo patas de banco), si hay; si no, anular una de prueba.
5. Anular desde el diálogo y releer → el bloque aparece.
6. Compatibilidad: desktop nuevo contra central de `develop` sin la fase 1 → el detalle carga como hoy y
   **sin** el aviso rojo. Es el caso normal, no el raro: el desktop sale por su canal solo y el central se
   despliega a mano (eje A #2). En el PR del desktop: «mergear después del central».

## Auditoría (paso 5)

- Eje A: #1 alta (fallback inalcanzable con `PROPAGAR_ERROR_DE_RED`) → corregido en fase 2 punto 2. #2 media
  (desktop antes que central es lo normal) → prueba 6 sin aviso + nota en el PR. #3 media (validación como
  HTTP 400) → se verifica en la prueba 6. #4–#6 bajas: sin otros consumidores (PWA/mobile/filial 0), `Usuario`
  ya expuesto por `MovimientoBancario.usuario` con el mismo gate, sin efecto en replicación.
- Eje B: #1 media (parseo/largo 300 del banco) → regex `DOTALL`, blanco → null, tests. #2 (anulada sin contra) →
  el diálogo deja solo «Anulada». #4 (determinismo) → `order by id`, el primero. #5 lazy → cubierto por OSIV
  (verificado `FrancoSystemsApplication.java:101`). #3, #6, #7 bajas sin acción. Preexistente fuera de alcance:
  un motivo largo hace fallar la anulación de una pata bancaria (`varchar(300)`, sin recorte en
  `BancoLedgerService.registrar`).

## Sin verificar / riesgos

- La lectura del motivo depende del texto que arma `revertir`; si alguien cambia ese formato, se ve la
  descripción entera (no se rompe).
- Hallazgo preexistente fuera de alcance: anotado aparte, en las notas privadas de seguridad.
