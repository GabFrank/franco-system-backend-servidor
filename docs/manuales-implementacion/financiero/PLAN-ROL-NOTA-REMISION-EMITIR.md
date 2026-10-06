# Plan — rol `NOTA REMISION EMITIR`

Pedido de Franco, 2026-10-06. Rama `feature/financiero-rol-nota-remision-emitir` (central) y la
homónima en el desktop.

## Qué se pide

Un rol independiente para que un usuario pueda **generar e imprimir la nota de remisión desde la
lista de transferencias**, sin que ese rol le abra nada del menú lateral de Financiero.

Hoy no se puede: la opción de la lista se muestra con `FACTURACION EMITIR`
(`list-transferencia.component.ts`, `puedeEmitirNotaRemision`), y ese mismo rol está en los
`visibilityRoles` del grupo Financiero y de «Notas de remisión», «Notas de crédito» y facturas
(`side-mini-variant.component.ts`). Del lado del central, `FacturacionSecurityService` solo conoce
`FACTURACION VER` y `FACTURACION EMITIR`.

## Decisiones (Franco, 2026-10-06)

- Nombre del rol: **`NOTA REMISION EMITIR`**.
- Alcance: **solo crear (con su envío a SIFEN) e imprimir**. No reenvía ni anula: una nota que SIFEN
  no recibió la reenvía alguien con `FACTURACION EMITIR` desde Financiero.
- `FACTURACION VER` / `FACTURACION EMITIR` no cambian en nada.

## Diseño

El rol nuevo es **acotado al origen TRANSFERENCIA** y eso se hace cumplir en el central, no solo
escondiendo el menú: el desktop es UX, la seguridad es el backend (regla #10 del desktop, issue #177).

### Central — `FacturacionSecurityService`

- Constante `REMISION_EMITIR = "NOTA REMISION EMITIR"`.
- `requireVerRemisionDeTransferencia()` → `VER`, `EMITIR` o `REMISION_EMITIR`.
- `requireEmitirRemision(OrigenNotaRemision origen)` → pasa con `EMITIR`; con solo `REMISION_EMITIR`
  pasa únicamente si `origen == TRANSFERENCIA`; si no, «No autorizado».
- `tieneEmisionCompleta()` → `EMITIR` o superusuario. Es lo que distingue al actor acotado.
- `requireVer()` y `requireEmitir()` quedan como están (los usan nota de crédito, configuración de
  facturación y el resto de nota de remisión). `anular` sigue con `requireEmitir()`.

El usuario necesita además `VER TRANSFERENCIA` para llegar a la lista: el rol nuevo no la abre.

### Central — qué operación acepta el rol nuevo

| Operación | Hoy | Con `NOTA REMISION EMITIR` |
|---|---|---|
| `notasRemisionPorTransferencias`, `notaRemisionPorTransferencia` | `requireVer` | sí |
| `prellenarNotaRemision` | `requireEmitir` | sí, solo origen `TRANSFERENCIA` |
| `localesDeSalida` (buscador del diálogo) | `requireEmitir` | sí. Le abre la dirección y ciudad fiscal de todas las sucursales con timbrado: es lo que el buscador necesita |
| `saveNotaRemision` → `NotaRemisionService.crear` | `requireEmitir` | sí, con las validaciones de «Alta acotada» |
| `generarYEnviarNotaRemision` | `requireEmitir` | sí, solo si la nota es de una transferencia, está activa y **todavía no tiene DE** |
| `imprimirNotaRemision` | `requireVer` | sí, solo si la nota cargada es de una transferencia |
| `notaRemision`, `notaRemisiones`, `notaRemisionItems`, `documentoElectronicoDeNotaRemision` | `requireVer` | **no** |
| `reenviarNotaRemision`, `anularNotaRemision` | `requireEmitir` | **no** |
| Todo lo de nota de crédito y configuración de facturación | — | **no** |

En `generarYEnviar` e `imprimir` el alcance depende de la nota, así que el control va en dos
tiempos: primero el rol (rechaza antes de tocar nada) y, **inmediatamente después de leer la nota y
antes de `crearDocumento` o de cualquier envío**, el origen. Para el actor acotado, «no existe» y
«no es de transferencia» responden el mismo mensaje, para no servir de oráculo de qué notas hay.

### Central — alta acotada (`crear` sin `FACTURACION EMITIR`)

`validar` hoy solo exige que `transferenciaId` no sea nulo: quien llame a `saveNotaRemision` directo
con `origen: TRANSFERENCIA` y un id cualquiera emite una nota libre. La comprobación de que la
transferencia existe y sale de esa sucursal está solo en el prellenado. Para el actor acotado,
`crear` exige además:

- la transferencia existe;
- `nota.sucursalId` es la sucursal de origen de la transferencia;
- `motivoEmision == TRASLADO_ENTRE_LOCALES` y `facturaLegalId == null`;
- el RUC del receptor es el del timbrado (traslado entre locales: la propia empresa);
- `usuarioId` se toma del usuario autenticado, no del input.

Con `FACTURACION EMITIR` el alta no cambia.

**Riesgo residual aceptado**: los ítems no se cotejan contra los de la transferencia (el diálogo
permite ajustarlos). Un actor acotado puede declarar otra mercadería, pero solo dentro de una
transferencia real de su sucursal y una sola vez por transferencia.

### Central — `generarYEnviar` no es un reenvío para el rol nuevo

`generarYEnviarNotaRemision` reenvía si el DE ya existe, y sin las guardas de `reenviar` (aprobado,
lote vivo). Abrirlo tal cual le daría al rol nuevo el reenvío que se decidió no darle. Para el actor
acotado pasa solo en el primer envío: nota activa y sin DE. Con DE existente responde que el reenvío
lo hace facturación. Con `FACTURACION EMITIR` no cambia.

### Central — migración `V236.1__rol_nota_remision_emitir.sql`

`INSERT` idempotente por nombre en `personas.role`, igual que V225.1 pero comparando
`upper(trim(nombre))` (la convención de la tabla tiene nombres con espacio final). Aditiva. **Sin espejo en
filial**: no hay DDL, y `personas.role` es `MAIN_TO_ALL` (la fila baja sola por `central_pub`). La
migración solo crea el rol; asignarlo es por la pantalla de usuarios, después del deploy.

### Desktop

- `roles.enum.ts`: `NOTA_REMISION_EMITIR = "NOTA REMISION EMITIR"`.
- `list-transferencia.component.ts`: `puedeEmitirNotaRemision` acepta también el rol nuevo.
- `add-nota-remision-dialog.component.ts`: si el envío falla, hoy dice «Reintentá con «Reenviar»
  desde la lista de notas de remisión», pantalla que este usuario no tiene. Sin `FACTURACION EMITIR`
  el mensaje pasa a ser que la nota quedó creada sin aprobar y que avise a facturación.
- `side-mini-variant.component.ts`: **no se toca**. El rol no entra en ningún `visibilityRoles` ni
  `openTabIfAuthorized`, que es justamente lo pedido.

## Datos nuevos

| Dato | Quién lo escribe | Quién lo lee |
|---|---|---|
| Fila `NOTA REMISION EMITIR` en `personas.role` | migración `V236.1` (central) | `FacturacionSecurityService.hasAnyRole` (central) y `MainService.tieneAlgunRol` en `list-transferencia` (desktop) |
| `personas.usuario_role` con ese rol | pantalla de usuarios del desktop (ya existe) | los mismos dos |

## Fases

### Fase 1 — central (un commit + push)

- `V236.1`, `FacturacionSecurityService`, `NotaRemisionGraphQL`, `NotaRemisionService.crear`,
  `NotaRemisionPrellenadoService`.
- Tests existentes que hay que ajustar porque cambia el método de seguridad que se llama:
  `NotaRemisionGraphQLSeguridadTest.lasQueriesExigenElRolDeLectura` (stub del método nuevo) y los
  `verify(seg).requireEmitir()` de `NotaRemisionServiceTest` sobre `crear`.
- Los tests de rechazo verifican además que no se tocó nada: `verifyNoInteractions` del envío y
  `never()` sobre `crearDocumento`. Un rechazo que pasa porque el mock no devolvió la nota no prueba
  nada: la nota se stubbea.
- Tests nuevos:
  - `crear` acotado: transferencia inexistente, de otra sucursal, motivo distinto, receptor ajeno →
    rechaza; `usuarioId` sale del autenticado.
  - `generarYEnviar` acotado con DE existente (cualquier estado) o nota inactiva → rechaza sin enviar.
  - `FacturacionSecurityServiceTest`: el rol nuevo ve por transferencia; emite con origen
    `TRANSFERENCIA`; no emite con `MANUAL`; no pasa `requireVer()` ni `requireEmitir()`;
    `FACTURACION EMITIR` sigue emitiendo cualquier origen.
  - `NotaRemisionGraphQLSeguridadTest`: con el rol nuevo se rechazan listado, reenviar y anular;
    `generarYEnviar` e `imprimir` rechazan una nota que no es de transferencia.
  - `NotaRemisionServiceTest`: `crear` con origen `MANUAL` y solo el rol nuevo se rechaza antes de
    tomar el lock del timbrado.
- Gate: `./mvnw clean verify -B -DskipFlyway=true`, leído del log.

### Fase 2 — desktop (un commit + push)

- Enum + flag de la lista.
- Gate: `npm run check` (AOT), leído del log.
- `N/A tests para desktop porque su CI no corre ninguno`.

### Prueba de runtime (antes de auditar el diff)

Central local con perfil `dev` (con `replication.sync/refresh` apagados) + desktop con
`ng serve -c web`, tres usuarios:

1. Solo `VER TRANSFERENCIA` + `NOTA REMISION EMITIR`: ve la opción en la lista, crea la nota de una
   transferencia, la imprime; **no ve el grupo Financiero**; el buscador global no le ofrece «Notas
   de remisión».
2. Con `FACTURACION EMITIR`: todo igual que hoy (lista y menú).
3. Sin ninguno de los dos: no ve la opción.

Y por GraphQL directo, con el token del usuario 1: `notaRemisiones`, `reenviarNotaRemision`,
`anularNotaRemision`, `prellenarNotaRemision(origen: "MANUAL")`, `saveNotaRemision` con una
transferencia de otra sucursal y un segundo `generarYEnviarNotaRemision` sobre la nota recién
emitida devuelven «No autorizado».

## Orden de despliegue

Central primero (merge a `develop` + `gh workflow run Deploy`, que el deploy automático no se
dispara), desktop después. No hay filial.

- Desktop viejo + central nuevo: no conoce el rol, no muestra la opción. Nada se rompe.
- Desktop nuevo + central viejo: el rol todavía no existe en `personas.role`, nadie lo tiene.

Antes de promover, en alpha:

- `max(id)` de `personas.role` contra `last_value` de `personas.role_id_seq` (si la secuencia quedó
  atrás por un rol creado con id a mano, el `INSERT` falla por PK y el deploy hace rollback);
- que la fila del rol llegó a una filial del canal.

## Rollback

La migración es un `INSERT` de referencia: el JAR anterior ignora el rol. Si se revierte el central
con usuarios ya asignados, el desktop nuevo les muestra la opción y el central viejo les responde
«No autorizado» **ya al cargar la lista de transferencias** (`notasRemisionPorTransferencias` corre
en cada página), no recién al abrir la opción. No destructivo, pero el orden de reversión es:
primero sacarles el rol por la pantalla de usuarios, después revertir el JAR.

## Estado a medias que el rol no destraba

Nota creada y SIFEN sin responder o rechazando: la nota queda activa, la transferencia no admite
otra, y el usuario del rol no puede reenviar ni anular (decisión tomada). La lista le ofrece
«Imprimir» y hoy `imprimirNotaRemision` genera el KuDE sin mirar si SIFEN aprobó. Lo destraba alguien
con `FACTURACION EMITIR` desde Financiero; nadie recibe aviso automático. **Decidido por Franco
(2026-10-06)**: el rol nuevo imprime la nota aunque SIFEN todavía no la haya aprobado, igual que hoy.

## Auditoría del plan (paso 5, 2026-10-06)

Dos auditores independientes, ejes A (contrato y propagación) y B (reversibilidad y estado). No se
contradijeron. Los dos hallazgos altos los encontraron ambos por separado y se comprobaron en el
código antes de aceptarlos.

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| `generarYEnviar` es un reenvío sin guardas: el rol nuevo reenviaría | A y B, alto | Aceptado: solo primer envío para el actor acotado |
| «Acotar por origen» era una etiqueta que declara el cliente | A y B, alto | Aceptado: sección «Alta acotada». Ítems sin cotejar, como riesgo residual |
| Callejón sin salida si SIFEN falla; imprime KuDE no aprobado | B, medio-alto | Mensaje del diálogo corregido. La impresión no se bloquea (decisión de Franco): en bodega las notas del central pueden quedar `EN_LOTE` sin que nadie consulte el lote, y exigir `APROBADO` dejaría sin imprimir notas válidas |
| Tests existentes quedan en rojo o pasan sin probar | B, medio | Aceptado: listados en la fase 1 |
| El control en dos tiempos puede filtrar existencia o dejar un DE creado | B, medio | Aceptado: orden y mensaje fijados |
| Migración: `trim`, secuencia, llegada a filiales | A y B, medio | Aceptado: `upper(trim())` y dos chequeos en alpha |
| `usuarioId` lo manda el cliente | A, medio | Aceptado para el actor acotado |
| `imprimir` acepta notas anuladas y de otra sucursal | A, medio | No se cambia: es lectura, igual que hoy con `FACTURACION VER` |
| Rollback: el error aparece al cargar la lista | B, bajo-medio | Aceptado: texto y orden de reversión |
| `localesDeSalida` abre datos de todas las sucursales; hace falta `VER TRANSFERENCIA` | A, bajo | Declarado en el diseño |

Confirmado por lectura (eje A): los roles de la pantalla de usuarios salen de la base, así que el
rol aparece solo con la migración; ni el filial ni la PWA conocen roles de notas; los buscadores de
chofer y vehículo del diálogo no tienen control de rol; la impresión desde la lista no pide
`documentoElectronicoDeNotaRemision`.

## Sin verificar todavía

- Lo anterior está leído, no visto en red: se confirma en la prueba de runtime con el usuario 1.
- Que `personas.role` esté hoy en `central_pub` en cada canal (se comprueba en alpha, ver arriba).
- El número `V236.1` se re-verifica contra `origin/develop` antes de cada push.
