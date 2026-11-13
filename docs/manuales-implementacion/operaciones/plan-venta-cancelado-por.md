# Plan — quién canceló una venta (central + desktop)

Rama `feature/venta-cancelado-por` en central y desktop, desde `origin/develop` (2026-11-13).

## Qué se pide

En el detalle expandible de la lista de ventas, si la venta está `CANCELADA`, mostrar
«Cancelado por: NICKNAME — dd/MM/yyyy HH:mm». Si no hay registro (cancelaciones anteriores a este
cambio): «Cancelado por: sin registro». Si la venta no está cancelada, no se muestra nada.

## Decisión: tabla aparte, solo en el central

`operaciones.venta` se replica en las dos direcciones (V113). Una columna nueva ahí obliga a
coordinar el despliegue central/filiales (la réplica se corta si llega una columna que el otro lado
no tiene). La cancelación solo ocurre en el central (`VentaService.cancelarVenta`; el filial la
rechaza desde el 2026-09-28), así que el registro vive en una tabla nueva del central, **no
registrada en `configuraciones.replication_table`** (mismo criterio que `financiero.factura_legal_correo`,
V240.1). El filial no se toca.

`EventoEmisor` ya emite la cancelación con el usuario, pero es un webhook que se apaga
(`encendido()`): no sirve como registro.

## Fase 1 — central

**Migración `V274.1__operaciones_venta_cancelacion.sql`** (mayor en develop: `V273.1`; ningún PR
abierto trae `V274+`):

```sql
CREATE TABLE IF NOT EXISTS operaciones.venta_cancelacion (
    id          bigserial PRIMARY KEY,
    venta_id    bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    estado      operaciones.venta_estado NOT NULL,   -- estado en que quedó: CANCELADA o CONCLUIDA (reactivada)
    usuario_id  bigint REFERENCES personas.usuario(id),
    creado_en   timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS venta_cancelacion_venta_idx ON operaciones.venta_cancelacion (venta_id, sucursal_id, id DESC);
```

- Sin FK a `operaciones.venta`: la venta llega por réplica; una FK haría fallar el apply de un
  `DELETE` replicado y cortaría la suscripción.
- `estado` reutiliza el enum `venta_estado` (ya mapeado en Java): guarda también las reactivaciones,
  así queda el historial completo cancelar → habilitar → cancelar.
- Verificar el nombre real del tipo PG antes de escribir la migración.

**Código:**

| Archivo | Cambio |
|---|---|
| `domain/operaciones/VentaCancelacion.java` | entidad nueva (id IDENTITY, ventaId, sucursalId, estado, usuario, creadoEn). `@TypeDef venta_estado` repetido en la clase; `creadoEn = LocalDateTime.now()` en Java (Hibernate manda NULL explícito y el `DEFAULT` no se aplica; además la sesión PG de prod no está en Asunción) |
| `repository/operaciones/VentaCancelacionRepository.java` | `findFirstByVentaIdAndSucursalIdOrderByIdDesc` |
| `service/operaciones/VentaService.cancelarVenta` | al final del `try` (después de `darDeBajaFactura`, antes del evento), guardar la fila con `usuarioActual()` (ya existe) en la misma transacción. Cubre los 4 llamadores: `VentaGraphQL.cancelarVenta`, `VentaCreditoGraphQL.cancelarVentaCredito` y los dos de `FacturaLegalGraphQL.cancelarFacturaLegal` |
| `graphql/operaciones/resolver/VentaResolver` | campo `cancelacion(Venta)`: si la venta está `CANCELADA`, la última fila si su estado es `CANCELADA`; si no, `null` |
| `graphql/operaciones/venta.graphqls` | `type VentaCancelacion { id: ID, usuario: Usuario, creadoEn: Date }` y `cancelacion: VentaCancelacion` en `Venta` |

Sin control de rol nuevo: el campo cuelga de `Venta`, que ya lee cualquier usuario con acceso a la lista;
expone solo el nickname de quien canceló.

**Tests:** unidad del criterio del resolver (venta no cancelada → null; cancelada con última fila
`CANCELADA` → esa fila; cancelada cuya última fila es `CONCLUIDA` o sin filas → null), y que
`cancelarVenta` guarde una fila con el estado nuevo y el usuario. `VentaServiceFacturaCancelacionTest` usa `@InjectMocks`: hay que agregarle `@Mock VentaCancelacionRepository` o sus 3 tests caen por NPE; los tests nuevos van ahí. Batería: `./mvnw clean verify -B -DskipFlyway=true`.

## Fase 2 — desktop

Las listas también se piden a filiales, que no tienen el campo: va en **una consulta aparte**, como
`ventaTieneSorteoQuery`, y solo contra el central.

| Archivo | Cambio |
|---|---|
| `venta/graphql/graphql-query.ts` + `ventaCancelacion.ts` | query `venta(id, sucId) { id cancelacion { creadoEn usuario { id nickname } } }` |
| `venta/venta.service.ts` | `onGetCancelacion(id, sucId)` con `servidor = true` fijo (el `onTieneSorteo` que sirve de modelo va al filial por defecto), silenciosa; error → `undefined` |
| `list-venta` y `generic-list-venta` (ts + html) | al expandir una venta `CANCELADA`, pedir la cancelación y guardarla en la fila (`cancelacionCargada`, `canceladoPor`, `canceladoEn`); al cancelar/habilitar desde la lista, invalidarla para que se relea. En el detalle, `*ngIf` con propiedades, sin funciones en el HTML |
| modelo `Venta` del desktop | campos nuevos |

Error de red o rechazo: no se muestra la línea (no «sin registro», que afirmaría algo falso).

**Tests:** el desktop no tiene batería en gate; `npm run check` (AOT) al final.

## Hallazgos de la auditoría (paso 5)

Eje B:
- **H1 — un fallo del INSERT aborta la cancelación** (el `catch` de `cancelarVenta` relanza). Se
  acepta: misma transacción, sin `REQUIRES_NEW` (dejaría filas huérfanas si la TX principal
  revierte). Las causas posibles (mapeo del enum, tabla ausente) se cubren copiando el mapeo de
  `Venta.java` (`@TypeDef venta_estado` + `@Type`) y con la prueba por pantalla; la tabla no puede
  faltar porque Flyway corre antes de levantar el contexto. El INSERT va al final del `try` para no
  meterse entre pasos que ya mandaron algo afuera.
- **H3 — `usuario` null**: el desktop muestra «Cancelado por: sin registro — fecha». La parte de
  entidad detached no aplica: un `@ManyToOne` sin cascada solo usa el id.
- **H4/H5 — carrera o restauraciones por SQL** pueden dejar la última fila desfasada del estado:
  el resolver devuelve null y se ve «sin registro». Aceptado. En las pruebas, borrar las filas de
  prueba al restaurar estados.
- **H6 — compatibilidad**: JAR viejo ignora la tabla (`ignore-missing-migrations=true`); desktop
  viejo no pide el campo. Plan de rollback para el PR: «tabla nueva aditiva, el JAR viejo la ignora».

Eje A:
- **MAJOR — tests existentes**: `VentaServiceFacturaCancelacionTest` (`@InjectMocks`) cae por NPE con el
  repositorio nuevo sin mock. Agregado a Fase 1.
- **Modelo `onTieneSorteo` va al filial por defecto**: `onGetCancelacion` fija `servidor = true`.
- **`creadoEn` y `@TypeDef`**: se setean en la entidad (ver Fase 1).
- **Cuarto llamador** `VentaCreditoGraphQL.cancelarVentaCredito`: cubierto por el código, agregado a las pruebas.
- **Revertir por SQL deja atribución vieja**: al restaurar estados de prueba, borrar sus filas de
  `venta_cancelacion`. Operaciones fuera de la app no quedan registradas; documentado.
- Verificado sin riesgo: la tabla no entra en publicaciones (`LogicalReplicationService` arma
  `FOR TABLE` solo con `replication_table`); `V274.1` libre; `VentaCancelacion`/`cancelacion` no existen
  en ningún cliente; la query `venta(id, sucId)` no tiene control de rol distinto a `ventaPorId`.

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `venta_cancelacion` (fila) | `VentaService.cancelarVenta` (central) | `VentaResolver.cancelacion` |
| `Venta.cancelacion` (GraphQL) | `VentaResolver.cancelacion` | `VentaService.onGetCancelacion` → `list-venta`, `generic-list-venta` |

## Despliegue

Central primero, desktop después. La tabla no viaja a ninguna filial, así que no hay orden de réplica.
Un desktop nuevo contra un central viejo: la consulta aparte falla y la línea no se muestra.

## Pruebas por pantalla (paso 9)

Central local 8081 + desktop `ng serve -c web`, usuario MR:
1. Cancelar una venta desde Ventas → expandir → «Cancelado por: MR — fecha».
2. Habilitarla → expandir → sin línea. Volver a cancelar → línea con la fecha nueva.
3. Una venta cancelada antes del cambio → «sin registro».
4. Igual desde la lista de ventas de una caja.
5. Cancelar una venta a crédito (`cancelarVentaCredito`) → fila registrada.
6. Cancelar factura «con la venta» (`cancelarFacturaLegal`) → la fila se registra (por SQL o pantalla).
Restaurar los estados de las ventas usadas al final.

## Sin verificar

- Filiales con versiones anteriores al rechazo del 28/09 podrían replicar cancelaciones sin fila: solo se vería «sin registro».
