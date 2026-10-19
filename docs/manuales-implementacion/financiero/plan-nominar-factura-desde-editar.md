# Plan — nominar una factura electrónica desde «Editar factura legal» (issue #433)

Rama `fix/facturacion-nominar-desde-editar` en **central** y **desktop**. Filial: N/A (ver «Alcance»).

## Qué pasa hoy

1. El diálogo `edit-factura-legal-dialog` llama a `updateFacturaLegal` con el `clienteId`.
   `FacturaLegalService.update` guarda el cliente en la factura.
2. Después llama a `nominarFacturaElectronica`, que rechaza si `factura.getCliente() != null`
   («La factura ya está nominada»). El cliente lo acaba de guardar el paso 1: el evento nunca sale.
3. La factura queda con cliente en la base y sin nominar en SIFEN. Un segundo intento lo frena
   `update` («No se puede editar una factura electrónica que ya está nominada»): queda trabada.

Otros tres problemas en el mismo camino:

- `SifenEventoService.nominarReceptor` **no lanza** cuando SIFEN rechaza, falla el envío o queda
  pendiente: guarda el evento y vuelve normal, y el resolver devuelve `true` («nominada correctamente»).
- Hasta el 17/09/2026 las innominadas se emitían con el **cliente genérico 0 «SIN NOMBRE»** (73.923 en
  la base local). El diálogo las trata como nominadas y nunca intenta nominarlas.
- Cuando una nominación pendiente se aprueba después y se reconcilia al consultar el DE
  (`SifenService.procesarEventoNominacion`, rama «el evento ya existe»), el evento pasa a APROBADO pero
  la factura **no** recibe el cliente. Después `nominarReceptor` dice «ya nominado» y queda trabada.

## Fuente de verdad: el XML emitido

Si la factura salió innominada no se deduce del cliente actual (lo pisa el bug, y la persona de un
cliente puede cambiar). Se lee del XML que se mandó a SIFEN (`documento_electronico.xml_firmado`,
o `xml_original`): una innominada lleva `<iTipIDRec>5</iTipIDRec>` («Innominado»). Verificado en la
base local: las 534.782 DE tienen XML; una del cliente 0 trae `iTipIDRec=5`, una nominada trae
`dRucRec`. Esto además deja nominar las facturas que el bug ya trabó (cliente guardado, DE innominado).

La doble nominación la sigue frenando `nominarReceptor` (`tieneNominacionAprobada`).

## Decisión

**Una electrónica no se edita: se nomina.** Cliente, nombre, RUC y dirección de una electrónica cambian
solo cuando SIFEN aprueba la nominación, así la base no queda distinta de lo que SIFEN tiene.

## Fases

### Fase 1 — central

**`FacturaLegalGraphQL.nominarFacturaElectronica`**
- Pierde el `@Transactional`: solo orquesta. Así `nominarReceptor` commitea su transacción y el error
  que lanza el resolver después **no** borra el evento RECHAZADO ni la respuesta de SIFEN (hallazgo A1).
- «Ya nominada» = el XML del DE **no** trae `iTipIDRec` 5. Sin DE o sin XML → rechaza («No se encontró
  el documento electrónico»).
- Destino: `SifenReceptorHelper.determinarConfiguracionReceptor(cliente, total)`; si da `INNOMINADO`
  → «Elegí un cliente identificado: con «SIN NOMBRE» no se nomina», sin llamar a SIFEN.
- Después de `nominarReceptor`, lee el evento activo de mayor id (`findActivosByCdcDocumento`):
  `APROBADO` → `true`; `RECHAZADO` / `ERROR_ENVIO` → error con el mensaje de SIFEN; `PENDIENTE` → `false`.

**`SifenEventoService`**
- Helper `aplicarReceptorNominado(factura, cliente)`: pone el cliente, `nombre` = nombre del receptor
  que va en el evento (`config.getNombreReceptor()`), `ruc` = número de documento **sin DV** (como se
  guarda al emitir: `ruc=80061439`, `dRucRec=80061439`, `dDVRec=9`), y la dirección de la persona solo
  si no está en blanco (no pisa la que se cargó a mano).
- `nominarReceptor` lo usa en las dos ramas de aprobación (`Aprobado` y `0600` con protocolo).
- El marcado `activo=false` de los eventos previos se mueve **después** de la respuesta de SIFEN.
  `SifenException` es checked y no hace rollback: hoy un corte de red deja los previos inactivos sin
  evento nuevo.

**`SifenService.procesarEventoNominacion`** (consulta del DE): en la rama «el evento ya existe», al
pasar a APROBADO aplica `aplicarReceptorNominado(factura, evento.getCliente())`. En la rama de
recuperación reemplaza el `setCliente` suelto por el helper. Cierra el caso del PENDIENTE que se
aprueba más tarde.

**`FacturaLegalService.update`**: una electrónica se rechaza siempre: «Una factura electrónica no se
edita: para asignarle un cliente hay que nominarla». Único llamador: `updateFacturaLegal`.

**Tests** (JUnit + Mockito, patrón de `SifenEventoServiceCancelarTest` / `CancelarFacturaLegalPlazoTest`):
- `NominarFacturaElectronicaTest` (resolver): XML innominado con cliente nulo, con cliente 0 y con
  cliente identificado (trabada por el bug) → llama a `nominarReceptor`; XML nominado → «ya nominada»,
  no llama; destino «SIN NOMBRE» → rechaza sin llamar; evento APROBADO → `true`; RECHAZADO → error con
  el mensaje; PENDIENTE → `false`.
- `AplicarReceptorNominadoTest`: contribuyente (RUC sin DV, nombre) y no contribuyente; dirección en
  blanco no pisa la de la factura.
- `FacturaLegalServiceUpdateTest`: electrónica con y sin cliente → rechaza sin guardar; papel → guarda.
- Revertir cada fix y comprobar que su test falla. Los que ya pasan con el código viejo (APROBADO →
  `true`, electrónica nominada → rechaza) quedan como regresión y se marcan así.
- Lo transaccional (A1) Mockito no lo ve: se prueba por pantalla con SQL (prueba 4).

### Fase 2 — desktop

`edit-factura-legal-dialog.component.ts`:
- `esInnominadaComputed` = sin cliente, o cliente cuya persona se llama «SIN NOMBRE» o tiene documento
  vacío/«0» (la query ya trae `cliente.persona.nombre/documento`).
- `puedeEditarComputed` para una electrónica = `esInnominadaComputed` (hoy mira `nombre`).
- `onGuardar` con electrónica: no llama a `updateFacturaLegal`; exige un cliente elegido que no sea
  «SIN NOMBRE» y llama directo a `nominarFacturaElectronica`.
  - `true` → «Factura nominada en SIFEN»;
  - `false` → «SIFEN recibió la nominación pero todavía no la aprobó: revisá antes de repetir»;
  - error de rechazo → «No se nominó» (el motivo ya lo muestra el genérico);
  - corte de red → aviso actual («no se pudo confirmar si se nominó en SIFEN»).
- Papel: sin cambios.

Una factura trabada por el bug (cliente identificado, DE innominado) el desktop la ve como nominada:
se destraba poniendo `cliente_id` en null por SQL y nominando desde el diálogo. No se agrega un campo
GraphQL para eso.

### Fase 3 — desktop: ajustes de pantalla pedidos en la prueba (19/10)

Pedido de Franco al probar: el estado del diálogo en color, y el CDC del listado que se corta.

- **Estado con color en el diálogo.** La clase que hoy calcula `getEstadoClass` del listado pasa a
  `factura-legal-estado.ts` (`claseEstadoFactura`) y la usan el listado y el diálogo
  (`estadoClaseComputed`, calculada en `updateComputedProperties`, nunca función en el HTML). El
  diálogo muestra el estado con el mismo badge que el listado: verde aprobado/activa, naranja
  pendiente/en lote, rojo rechazado/inactiva, gris cancelado. El bloque `.estado-badge` se copia al
  scss del diálogo (`edit-factura-legal-dialog.component.scss`, el que referencia el `.ts`): los estilos
  del listado están encapsulados y no llegan (auditoría A y B).
- **Filtros del listado.** Fila 2: nombre 40%, RUC 30%, número de venta 30%. Fila 3: CDC 60%, tipo 12%,
  estado 12%, «Sin nombre» 16% (hoy esa fila suma 120%; con 50% el CDC perdía el último dígito en
  pantalla de 1512 px). Los pasos 3 y 4 del tutorial siguen colgados
  de nombre y RUC.
- **Búsqueda por CDC** (bug visto en la prueba): `FacturaLegalByCdcGQL` no pide `cdc`, `activo`,
  `numeroFactura` ni `documentoElectronico`, así que una electrónica aprobada sale «PAPEL / ACTIVA», sin
  número, y el menú la trata como de papel (habilita «Cancelar» por la regla de papel). Se le agregan
  los mismos campos que pide la query del listado por fecha. Commit aparte.

Tests: spec de `claseEstadoFactura` (los 7 casos). Prueba por pantalla: diálogo con electrónica
aprobada (verde), papel activa (verde), búsqueda por CDC muestra tipo/estado/número correctos, el CDC
entero visible en el filtro.

## Tabla de datos nuevos

Ningún campo, columna ni clave nueva. Cambia **quién escribe** campos existentes:

| Dato | Escribe (antes) | Escribe (después) | Lee |
|---|---|---|---|
| `factura_legal.cliente_id` de una electrónica | `FacturaLegalService.update`, `nominarReceptor`, `procesarEventoNominacion` (recuperación) | `aplicarReceptorNominado`, llamado al aprobar desde `nominarReceptor` y `procesarEventoNominacion` (las dos ramas) | listado, diálogo, correo de facturas |
| `factura_legal.nombre/ruc/direccion` de una electrónica | `FacturaLegalService.update` | `aplicarReceptorNominado` | listado (filtro «SIN NOMBRE»), KuDE, Excel |

Sin migración, sin enum nuevo, sin cambio de schema GraphQL (`nominarFacturaElectronica` sigue
devolviendo `Boolean!`).

## Alcance

- **Filial: N/A.** El DE y sus eventos nacen en el central (`documento_electronico` y
  `evento_nominacion_de` son `MAIN_TO_ALL`), el desktop nomina contra el central y el filial no expone
  ninguna de las dos mutations. `factura_legal` es `BRANCH_TO_MAIN` con
  `replicate_central_to_branch_with_filter`: lo que escribe el central vuelve a su filial. El filial
  tiene su propio `procesarEventoNominacion` (`SifenService.java:1080`) con el mismo `setCliente` sin
  nombre/RUC; no se toca (sin evidencia de que consulte eventos de nominación en producción).
- **PWA y mobile: N/A** — ninguna usa estas mutations (grep en `origin/develop`).

## Orden de despliegue y rollback

Central primero, después desktop.
- Desktop viejo + central nuevo: el `update` rechaza antes de nominar. Sigue sin poder nominar, como
  hoy, y ya no puede corregir nombre/RUC de una electrónica «SIN NOMBRE» (es la decisión). Se avisa en el PR.
- Desktop nuevo + central viejo (rollback): el central viejo devuelve `true` aunque SIFEN rechace. Es la
  ventana de riesgo del rollback; se anota en el PR.
- Sin datos que revertir. Una nominación aprobada no se deshace en SIFEN.

## Pruebas por pantalla (paso 9)

Central 8081 (perfil dev) + desktop web contra la base local. **Decisión de Franco (19/10): el
rechazo se prueba contra SIFEN de producción** (`sifen.enabled=true`, `sifen.ambiente=PROD`): «si se
mandan a SIFEN, las rechaza». Para acotar el riesgo de una aprobación real (irreversible) se nomina la
innominada más vieja (10/2025) y siempre al cliente de prueba DIEGO PAULINHO AMARILLA MERCADO. El
resultado APROBADO / PENDIENTE se fuerza con un parche local sin commitear sobre la respuesta de
`Sifen.recepcionEvento`.

1. Electrónica sin cliente → elegir cliente identificado → nominada (forzado); listado muestra nombre y
   RUC; reabrir el diálogo → ya no deja editar.
2. Electrónica con cliente 0 → ídem.
3. Elegir un cliente «SIN NOMBRE» → rechaza sin llamar a SIFEN.
4. Rechazo de SIFEN (test, real) → «No se nominó» + motivo; la factura sigue sin cliente; **por SQL el
   evento RECHAZADO quedó guardado** con la respuesta (A1).
5. PENDIENTE (forzado) → aviso «todavía no la aprobó»; la factura sigue sin cliente.
6. Factura en papel → editar nombre/RUC → guarda como antes.
7. `updateFacturaLegal` sobre una electrónica → rechaza con el mensaje nuevo.
8. Factura «trabada» (cliente identificado + XML innominado, armada por SQL) → `nominarFacturaElectronica`
   por GraphQL la acepta.

## Qué queda sin verificar

- Si SIFEN de producción acepta nominar facturas viejas (las del cliente 0 van desde 10/2025). Si hay
  plazo, SIFEN rechaza y el usuario ve el motivo.
- Nadie consulta solo un evento `PENDIENTE`: se reconcilia cuando alguien consulta el DE. Con este fix,
  esa consulta ya deja el cliente puesto.
- Cuántas facturas trabó el bug en producción: buscar electrónicas con cliente identificado y XML con
  `iTipIDRec=5`. Se mira en bodega y farmacia antes del PR (solo lectura).
- Una excepción después de que SIFEN aprobó (commit fallido) deja SIFEN nominado y la factura sin cliente.
  Los datos a copiar se calculan antes del envío, así que solo queda el commit; se recupera consultando el DE.
- Permisos: `nominarFacturaElectronica` sigue protegida solo por login, como hoy (#177).

## Hallazgos de la auditoría del plan

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| A1 | A y B | Lanzar el error dentro del `@Transactional` del resolver borra el evento RECHAZADO | Resolver sin `@Transactional`; prueba 4 con SQL |
| A2 | B | Falla después de que SIFEN aprobó → rollback con SIFEN nominado | Datos calculados antes del envío; recuperación por consulta del DE |
| A3 | B | «Innominada» por cliente actual da falsos positivos/negativos y lanza con ≥ 7M | Se decide por el XML emitido |
| M1 | B | El PENDIENTE que se aprueba después no pone el cliente | `procesarEventoNominacion` usa el helper en las dos ramas |
| M2 | B | Formato de nombre/RUC/dirección indefinido | RUC sin DV, nombre del receptor, dirección solo si no está en blanco |
| 2 | A | `procesarEventoNominacion` pone cliente sin nombre/RUC | Helper compartido |
| 3 | A | `puedeEditarComputed` mira el nombre y no el cliente | Se alinea a `esInnominadaComputed` |
| — | B | `SifenException` checked deja los previos inactivos sin evento nuevo | Desactivar después de la respuesta |
| M3 / 4 | A y B | Replicación bidireccional de `factura_legal` puede pisar con una imagen vieja | Igual que hoy con `cliente_id`; no verificado contra una filial |
| M4 | B | El desktop no comprueba el `true` (rollback del central) | No se hace: el central va primero; se anota en el PR |
| 5 | A | `findFirst` sin orden | Se toma el de mayor id |
