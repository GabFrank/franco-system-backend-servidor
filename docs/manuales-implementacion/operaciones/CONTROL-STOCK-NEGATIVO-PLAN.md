# Control de stock negativo — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el equipo de inventario vea, en una lista filtrable del desktop, cada producto que salió por venta o transferencia cuando su stock en la sucursal ya era 0 o negativo.

**Architecture:** El central es el único que registra: al guardar un ítem nuevo de transferencia (cubre desktop, PWA y Android) y, para las ventas, con un poller que evalúa los movimientos `VENTA` que llegan replicados de cada filial. Los registros viven en una tabla central-only. Desktop y PWA agregan el aviso con confirmación al cargar un ítem; el desktop agrega la lista.

**Tech Stack:** central Spring Boot 2.7.18 / Java 11 / GraphQL kickstart / PostgreSQL / Flyway · desktop Angular 15 + Apollo · mobile-pwa Angular 21 zoneless + vitest.

**Spec:** [`CONTROL-STOCK-NEGATIVO-DISENO.md`](CONTROL-STOCK-NEGATIVO-DISENO.md) (misma carpeta).

## Global Constraints

- Rama `feature/inventario-control-stock-negativo` en los tres repos, salida de `origin/develop`. Un PR por repo. **Central primero.**
- Migración con sufijo `.1`: `V243.1__operaciones_control_stock_negativo.sql`. Re-verificar el número tras cada rebase (`origin/develop` termina en `V242.1`; ninguna rama remota tiene `V243`).
- Migración solo aditiva. La tabla es **central-only**: no entra en `configuraciones.replication_table`.
- Criterio de registro: **stock previo ≤ 0**. El stock es el del central.
- Negativo en transferencias **respeta** `configuracionTransferencia.permitirStockNegativo`.
- Autorización manual en el resolver (issue #177): rol `VER INVENTARIO` o ADMIN.
- Registrar nunca puede romper el guardado de un ítem de transferencia: el registro va por `JdbcTemplate`, fuera del `EntityManager` del request.
- **Sucursal COMPRAS (999) queda fuera** del diálogo y del registro de transferencias: su stock es negativo por diseño (5.288 de 5.333 productos en la copia local). Decidido por Franco el 2026-10-10: el control apunta a las transferencias entre sucursales y COMPRAS es la excepción; queda como está.
- El poller corre en **hilo propio** con tope de 20 s por ciclo: las tareas `@Scheduled` del central comparten un solo hilo.
- Desktop: sin funciones en el HTML, sin módulo Angular nuevo, dark mode, diálogos por `DialogosService`.
- PWA: alias `data:` en toda operación, tres estados, sin literales fuera de tokens, sin backticks dentro de `template:`.
- Commits `feat(inventario): …` / `feat(transferencia): …` en minúsculas, español, sin punto final. Planes y docs: `docs(...)`.
- Una fase = commit + push de la rama. PR solo con aprobación de Franco. Merge nunca.
- Filial: **N/A** — el enfoque elegido no la toca `[ev: spec §Decisiones 4]`. Mobile Android: **N/A** — en mantenimiento, queda cubierta por el registro del central.

## Puntos de la spec ya verificados (2026-10-10, base local `bodega@5551`)

| # | Punto | Resultado |
|---|---|---|
| 1 | Orden de llegada | Se resuelve con solapamiento **por tiempo**: cada ciclo re-evalúa las ventas de los 15 minutos anteriores a la última ya recorrida (acotado a 5000 ids hacia atrás). La idempotencia la da el índice único por movimiento. |
| 2 | Origen de los movimientos `VENTA` | Todos nacen en la filial: 4.802.314 movimientos `VENTA`, **0 con id impar** (el central genera impares, V223.1). El central no crea movimientos `VENTA` (`grep setTipoMovimiento(TipoMovimiento.VENTA)` = 0). Una sola secuencia por sucursal. |
| 3 | Otros llamadores | `createMovimientoFromTransferenciaItem` se llama además desde `TransferenciaGraphQL` (líneas 285–486), pero son cambios de etapa de ítems que ya existen. Ítems nuevos solo entran por `saveTransferenciaItem`. Las devoluciones e inventario de la PWA no usan esa mutation. |
| 4 | Costo | 1000 ventas evaluadas: 4,5 s en sucursal 1 y 1,6 s en sucursal 6 (≈4,5 ms por venta, caché fría). Con lote 500 y solapamiento 100 el ciclo normal queda por debajo de 1 s por sucursal. No hace falta índice nuevo. |
| 5 | Pantalla de la PWA | Los ítems nuevos se cargan en `transferencia-borrador.page.ts` → `agregar()`. `transferencia-detalle` solo guarda verificaciones de etapa. La PWA hoy no lee `configuracionTransferencia`. |
| 6 | Migración | `origin/develop` termina en `V242.1` (rama rebaseada el 2026-10-10). Se usa `V243.1`. |

### Hallazgo que Franco tiene que conocer

En la copia local, **390 de las últimas 999 ventas de la sucursal 1 (39 %) y 211 de 999 en la sucursal 6 (21 %) salieron con stock previo ≤ 0**. Si producción se parece, la lista va a recibir miles de registros por día. El plan no cambia el criterio aprobado; queda anotado para decidir si hace falta agrupar por producto en una iteración posterior.

### Hallazgo aparte: descuentos de stock duplicados en la filial

Midiendo el poller apareció un bug que **no es de este trabajo** y que es en sí una causa de stock negativo: en la sucursal 1 hay 76 ítems de venta recientes con **más de un movimiento `VENTA` activo** (169 movimientos). Ejemplo: `venta_item` 1411477 tiene los movimientos 2201180, 2201204 y 2201226, los tres activos, -1 del producto 973, con 14 y 29 minutos de diferencia. Se reporta a Franco; no se corrige acá. El control registra **cada movimiento**, así que esos duplicados van a verse en la lista.

## Auditoría del plan (paso 5) — hallazgos y qué se hizo

| # | Eje | Hallazgo | Verificado | Resolución |
|---|---|---|---|---|
| A1 / B3 | A y B | El poller retendría el único hilo de `@Scheduled` (replicación, retiros, notificaciones) | `CotizacionMercadoScheduler.java:26` | Hilo propio + tope de 20 s por ciclo + sucursales menos recientes primero (Task 4) |
| A2 | A | COMPRAS dispararía diálogo y registro en casi cada ítem | 5.288/5.333 productos con stock ≤ 0; 204.208 ítems históricos desde la 999 | COMPRAS fuera del diálogo y del registro (Tasks 3 y 7). Aprobado por Franco |
| A3 | A | `V242.1` ya estaba mergeada | `origin/develop` | Rama rebaseada; `V243.1` sigue libre |
| A4 | A | El enum se commitea antes que su `.graphqls` | `SchemaEnumsSincronizadosTest` solo itera el schema | Sin riesgo: nada del schema lo expone hasta la Task 5. Se deja explícito en la Task 1 y en la tabla de datos |
| B1 | B | El índice único por ítem descartaba movimientos duplicados de una misma venta | 76 ítems con 2+ movimientos activos | Idempotencia de ventas por `(sucursal_id, movimiento_stock_id)`; la de transferencias, por ítem (Task 1) |
| B2 | B | Solapamiento de 100 ids: muy corto y solo por id | ids de 2 en 2, compartidos con todos los tipos | Solapamiento de 15 minutos de `creado_en`, acotado a 5000 ids (Task 4) |
| B4 | B | Sucursales sin ventas escanean todo su historial cada minuto | sucursal 13: 270.259 filas, 98 ms | Una sucursal sin ninguna venta se sondea una vez por hora (Task 4) |
| B5 | B | `REQUIRES_NEW` no aislaba: con open-in-view el rollback limpia el `EntityManager` del request y deja el ítem detached | `FrancoSystemsApplication.java:93` | El registro de transferencias va por `JdbcTemplate`, nunca lanza (Task 2). Además lee el stock como `numeric`, sin pasar por `Float` |
| B6 | B | El `UPDATE` de prueba del cursor podría copiarse a un servidor | — | Guardas y advertencia «solo base local» (Task 9) |

Los dos auditores no se contradijeron.

## Review Focus

1. **Filial que vuelve de estar offline**: llegan miles de ventas juntas. Esperado: el poller las procesa en lotes de 500 sin saltear ninguna ni bloquear el central. → Tarea 4, paso 6.
2. **Dos ventas concurrentes confirmadas fuera de orden**: esperado: ninguna queda sin evaluar. → Tarea 4 (solapamiento por tiempo) y paso 6.
3. **Falla al registrar una transferencia** (base caída, dato nulo): esperado: el ítem se guarda igual. → Tarea 3, test `unFalloAlRegistrarNoImpideGuardarElItem`.
4. **Usuario sin el rol** que llama la query a mano: esperado: error de autorización, sin datos. → Tarea 5, `InventarioSecurityServiceTest`.
5. **El central no responde al verificar stock** desde desktop o PWA: esperado: no se agrega el ítem y se avisa (fail-closed, #390). → Tareas 7 y 8.

## Estructura de archivos

**Central** (`backend/franco-system-backend-servidor`, worktree `.claude/worktrees/control-stock-negativo`)

| Archivo | Responsabilidad |
|---|---|
| `src/main/resources/db/migration/V243.1__operaciones_control_stock_negativo.sql` | tabla de registros + tabla de cursor |
| `domain/operaciones/enums/TipoControlStock.java` | `VENTA`, `TRANSFERENCIA` |
| `domain/operaciones/ControlStockNegativo.java` | entidad |
| `service/operaciones/ControlStockNegativoService.java` | registrar transferencia + búsqueda con filtros |
| `service/operaciones/ControlStockNegativoProcesador.java` | un ciclo del poller para una sucursal, en su transacción |
| `service/operaciones/ControlStockNegativoScheduler.java` | `@Scheduled` que despacha a un hilo propio y recorre sucursales con tope de tiempo |
| `service/operaciones/InventarioSecurityService.java` | control por rol |
| `graphql/operaciones/ControlStockNegativoGraphQL.java` | query |
| `graphql/operaciones/resolver/ControlStockNegativoResolver.java` | campo `sucursal` |
| `src/main/resources/graphql/operaciones/control-stock-negativo.graphqls` | schema |
| `graphql/operaciones/TransferenciaItemGraphQL.java` (modificar) | llamar al registro en ítems nuevos |
| `application.properties`, `application-dev.properties`, `application-ci.properties` (modificar) | property del poller |

**Desktop** (`frontend/frc-sistemas-integrados-angular`, worktree a crear)

| Archivo | Responsabilidad |
|---|---|
| `src/app/modules/operaciones/inventario/control-stock-negativo.model.ts` | modelo |
| `…/inventario/graphql/graphql-query.ts` (modificar) | query |
| `…/inventario/graphql/controlStockNegativo.gql.ts` | clase Apollo |
| `…/inventario/list-control-stock-negativo/*` | lista |
| `…/inventario/inventario.module.ts` (modificar) | declarar el componente |
| `…/inventario/inventario-dashboard/*` (modificar) | botón |
| `…/transferencia/aviso-stock.ts` + `.spec.ts` | decisión pura seguir / confirmar / bloquear |
| `…/transferencia/edit-transferencia/edit-transferencia.component.ts` (modificar) | usar la decisión |

**Mobile-pwa** (`frontend/frc-mobile-pwa`, worktree a crear)

| Archivo | Responsabilidad |
|---|---|
| `src/app/pages/transferencias/aviso-stock.ts` + `.spec.ts` | misma decisión pura |
| `src/app/graphql/transferencias/graphql-query.ts` (modificar) + `stockEnOrigen.ts`, `configuracionTransferencia.ts` | consultas |
| `src/app/pages/transferencias/transferencia.service.ts` (modificar) | `stockEnOrigen`, `permiteStockNegativo` |
| `src/app/pages/transferencias/transferencia-borrador.page.ts` (modificar) | aviso antes de guardar un ítem nuevo |
| `docs/PLAN_TESTEO_MANUAL.md`, `docs/modulos/transferencias.md` (modificar) | bloque de testeo y regla |

## Datos nuevos: quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `control_stock_negativo` filas `TRANSFERENCIA` | `ControlStockNegativoService.registrarTransferencia` (JDBC) | `ControlStockNegativoService.buscar` → `list-control-stock-negativo` |
| `control_stock_negativo` filas `VENTA` | `ControlStockNegativoProcesador.procesarSucursal` | ídem |
| `control_stock_negativo_cursor` | `ControlStockNegativoProcesador` | `ControlStockNegativoProcesador` |
| `inventario.control-stock-negativo.poller.enabled` | `application*.properties` / env `INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED` | `@ConditionalOnProperty` del scheduler |
| enum `TipoControlStock` | Java en la Task 1 (lo usa la entidad); `.graphqls` en la Task 5, que es cuando sale por GraphQL | desktop (filtro y columna) |

---

# CENTRAL

Todos los comandos se corren desde el worktree
`/home/franco/dev-frc/backend/franco-system-backend-servidor/.claude/worktrees/control-stock-negativo`.
Build rápido de fase: `./mvnw -o -q compile -DskipFlyway=true`. Tests de una clase:
`./mvnw -o test -DskipFlyway=true -Dtest=<Clase>`.

### Task 1 (Fase 1): migración, enum y entidad

**Files:**
- Create: `src/main/resources/db/migration/V243.1__operaciones_control_stock_negativo.sql`
- Create: `src/main/java/com/franco/dev/domain/operaciones/enums/TipoControlStock.java`
- Create: `src/main/java/com/franco/dev/domain/operaciones/ControlStockNegativo.java`

**Interfaces:**
- Produces: tablas `operaciones.control_stock_negativo` y `operaciones.control_stock_negativo_cursor`; `TipoControlStock {VENTA, TRANSFERENCIA}`; entidad `ControlStockNegativo` de **solo lectura** (getters Lombok: `sucursalId`, `producto`, `tipo`, `cantidad`, `stockPrevio`, `usuario`, `fecha`, `referenciaId`, `itemId`, `movimientoStockId`, `creadoEn`). Nadie la persiste por JPA: las altas van por SQL.

- [ ] **Step 1: Confirmar el número de migración**

Run: `git fetch -q origin develop && git ls-tree --name-only origin/develop src/main/resources/db/migration/ | sort -V | tail -3`
Expected: la última es `V242.1__…`. Si aparece una `V243.*`, usar el siguiente entero libre con `.1` y renombrar en todo este plan.

- [ ] **Step 2: Escribir la migración**

```sql
-- =====================================================================
-- Control de stock negativo
-- =====================================================================
-- Una fila por cada salida (venta del PDV o item de transferencia) de un
-- producto cuyo stock en la sucursal ya era 0 o negativo antes de salir.
-- La lee el equipo de inventario para ir a controlar ese stock.
--
--   VENTA          la inserta el poller del central cuando el movimiento
--                  de stock de la venta llega replicado de la filial
--   TRANSFERENCIA  la inserta saveTransferenciaItem al cargar un item nuevo
--
-- Idempotencia, distinta por tipo:
--   VENTA          por movimiento de stock: el poller re-lee un tramo en cada
--                  ciclo, y un mismo item de venta puede tener mas de un
--                  movimiento activo (descuentos duplicados) que hay que ver
--   TRANSFERENCIA  por item: se registra una sola vez, al cargarlo
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS operaciones.control_stock_negativo (
    id                   bigserial PRIMARY KEY,
    sucursal_id          bigint NOT NULL,
    producto_id          bigint NOT NULL,
    tipo                 varchar(15) NOT NULL,
    cantidad             numeric NOT NULL,
    stock_previo         numeric NOT NULL,
    usuario_id           bigint,
    fecha                timestamp with time zone NOT NULL,
    referencia_id        bigint,
    item_id              bigint NOT NULL,
    movimiento_stock_id  bigint,
    creado_en            timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_control_stock_negativo_tipo CHECK (tipo IN ('VENTA', 'TRANSFERENCIA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_control_stock_negativo_movimiento
    ON operaciones.control_stock_negativo (sucursal_id, movimiento_stock_id)
    WHERE movimiento_stock_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_control_stock_negativo_transferencia
    ON operaciones.control_stock_negativo (item_id, sucursal_id)
    WHERE tipo = 'TRANSFERENCIA';

CREATE INDEX IF NOT EXISTS idx_control_stock_negativo_fecha
    ON operaciones.control_stock_negativo (fecha);
CREATE INDEX IF NOT EXISTS idx_control_stock_negativo_sucursal_fecha
    ON operaciones.control_stock_negativo (sucursal_id, fecha);

-- Hasta que movimiento de cada sucursal ya evaluo el poller.
-- inicial_movimiento_id marca donde empezo: nada anterior se registra.
-- ultimo_creado_en es la fecha de ese ultimo movimiento: el poller re-lee los
-- 15 minutos anteriores, por las ventas que se confirman fuera de orden.
CREATE TABLE IF NOT EXISTS operaciones.control_stock_negativo_cursor (
    sucursal_id            bigint PRIMARY KEY,
    ultimo_movimiento_id   bigint NOT NULL,
    inicial_movimiento_id  bigint NOT NULL,
    ultimo_creado_en       timestamp with time zone,
    actualizado_en         timestamp with time zone NOT NULL DEFAULT now()
);
```

- [ ] **Step 3: Enum y entidad**

El enum nace acá porque la entidad lo usa; su par en el `.graphqls` llega en la Task 5, que es cuando sale por GraphQL (`SchemaEnumsSincronizadosTest` solo compara los enums que el schema declara, así que esta fase pasa el CI).

```java
package com.franco.dev.domain.operaciones.enums;

/** De donde salio el producto que se registra en el control de stock negativo. */
public enum TipoControlStock {
    VENTA,
    TRANSFERENCIA
}
```


```java
package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.operaciones.enums.TipoControlStock;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Salida de un producto cuyo stock en la sucursal ya era 0 o negativo. Central-only.
 *
 * Entidad de SOLO LECTURA: nadie la persiste por JPA. Las filas VENTA las inserta el poller y las
 * TRANSFERENCIA las inserta ControlStockNegativoService, las dos por SQL.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "control_stock_negativo", schema = "operaciones")
public class ControlStockNegativo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoControlStock tipo;

    private Double cantidad;

    @Column(name = "stock_previo")
    private Double stockPrevio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fecha;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "movimiento_stock_id")
    private Long movimientoStockId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
```

- [ ] **Step 4: Probar la migración contra la base local, sin dejar rastro**

La migración se pega dentro de una transacción que se deshace. Memoria «psql a bodega cuelga con sentencias grandes» no aplica: es `localhost`.

Run:
```bash
export PGPASSWORD="$(grep -E '^spring.datasource.password' src/main/resources/application.properties | head -1 | cut -d= -f2- | sed -E 's/^\$\{[A-Z_]+:(.*)\}$/\1/')"
{ echo 'BEGIN;'; cat src/main/resources/db/migration/V243.1__operaciones_control_stock_negativo.sql; \
  echo "INSERT INTO operaciones.control_stock_negativo (sucursal_id, producto_id, tipo, cantidad, stock_previo, fecha, item_id) VALUES (1, 1, 'TRANSFERENCIA', 1, 0, now(), 1);"; \
  echo "INSERT INTO operaciones.control_stock_negativo (sucursal_id, producto_id, tipo, cantidad, stock_previo, fecha, item_id) VALUES (1, 1, 'TRANSFERENCIA', 1, 0, now(), 1) ON CONFLICT (item_id, sucursal_id) WHERE tipo = 'TRANSFERENCIA' DO NOTHING;"; \
  echo "INSERT INTO operaciones.control_stock_negativo (sucursal_id, producto_id, tipo, cantidad, stock_previo, fecha, item_id, movimiento_stock_id) VALUES (1, 1, 'VENTA', 1, 0, now(), 1, 10), (1, 1, 'VENTA', 1, 0, now(), 1, 12);"; \
  echo "INSERT INTO operaciones.control_stock_negativo (sucursal_id, producto_id, tipo, cantidad, stock_previo, fecha, item_id, movimiento_stock_id) VALUES (1, 1, 'VENTA', 1, 0, now(), 1, 12) ON CONFLICT (sucursal_id, movimiento_stock_id) WHERE movimiento_stock_id IS NOT NULL DO NOTHING;"; \
  echo 'SELECT count(*) FROM operaciones.control_stock_negativo;'; echo 'ROLLBACK;'; } \
  | psql -h localhost -p 5551 -U franco -d bodega -X -v ON_ERROR_STOP=1 2>&1 | grep -vE 'WARNING|DETAIL|HINT'
```
Expected: `CREATE TABLE`, `CREATE INDEX` ×4, `CREATE TABLE`, `INSERT 0 1`, `INSERT 0 0` (la transferencia repetida no entra), `INSERT 0 2` (dos movimientos del mismo ítem de venta sí entran), `INSERT 0 0`, un `3`, `ROLLBACK`. Sin `ERROR`.

- [ ] **Step 5: Compilar**

Run: `./mvnw -o -q compile -DskipFlyway=true`
Expected: termina sin `ERROR`.

- [ ] **Step 6: Commit y push de la fase**

```bash
git status --short   # solo los 3 archivos de la tarea
git add src/main/resources/db/migration/V243.1__operaciones_control_stock_negativo.sql \
  src/main/java/com/franco/dev/domain/operaciones/enums/TipoControlStock.java \
  src/main/java/com/franco/dev/domain/operaciones/ControlStockNegativo.java
git commit -m "feat(inventario): tabla y entidad del control de stock negativo"
git push -u origin feature/inventario-control-stock-negativo
```

---

### Task 2 (Fase 2a): servicio — registrar una transferencia

**Files:**
- Create: `src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoService.java`
- Test: `src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoServiceTest.java`

**Interfaces:**
- Consumes: `JdbcTemplate` (bean ya usado por `FacturaCorreoService`).
- Produces: `ControlStockNegativoService.registrarTransferencia(TransferenciaItem item): boolean` (`true` si registró; **nunca lanza**) y `static boolean debeRegistrar(BigDecimal stockPrevio)`.

Por qué JDBC y no JPA: el central registra `OpenEntityManagerInViewFilter`, así que todo el request comparte un `EntityManager`. Si un `save` de JPA fallara acá, el rollback haría `clear()` de ese `EntityManager` y el ítem recién guardado quedaría detached para el resto de `saveTransferenciaItem`. `JdbcTemplate` fuera de una transacción usa su propia conexión: un fallo no toca nada del request.

- [ ] **Step 1: Test que falla**

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.EntityManager;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Una transferencia se registra en el control solo si el stock del origen ya era 0 o negativo
 * antes de cargar el item. La cantidad se guarda en unidades, no en presentaciones. Registrar es
 * un control, no parte de la operacion: nunca lanza.
 */
class ControlStockNegativoServiceTest {

    private static final long PRODUCTO_ID = 77L;
    private static final long ORIGEN_ID = 6L;

    private ControlStockNegativoService service;

    @BeforeEach
    void setUp() {
        // spy: las dos salidas a la base (stockPrevio e insertarTransferencia) se reemplazan por test.
        service = spy(new ControlStockNegativoService(mock(JdbcTemplate.class), mock(EntityManager.class)));
        doNothing().when(service).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    /** Item de 3 cajas de 12 unidades, de la sucursal 6, transferencia 900, cargado por el usuario 55. */
    private TransferenciaItem item() {
        Producto producto = new Producto();
        producto.setId(PRODUCTO_ID);
        Presentacion presentacion = new Presentacion();
        presentacion.setProducto(producto);
        presentacion.setCantidad(12D);
        Sucursal origen = new Sucursal();
        origen.setId(ORIGEN_ID);
        Transferencia transferencia = new Transferencia();
        transferencia.setId(900L);
        transferencia.setSucursalOrigen(origen);
        Usuario usuario = new Usuario();
        usuario.setId(55L);
        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(5001L);
        ti.setTransferencia(transferencia);
        ti.setPresentacionPreTransferencia(presentacion);
        ti.setCantidadPreTransferencia(3D);
        ti.setUsuario(usuario);
        return ti;
    }

    private void stockEnOrigen(String stock) {
        doReturn(new BigDecimal(stock)).when(service).stockPrevio(PRODUCTO_ID, ORIGEN_ID);
    }

    @Test
    void conStockCeroRegistraEnUnidades() {
        stockEnOrigen("0");

        assertTrue(service.registrarTransferencia(item()));

        // 3 cajas de 12 son 36 unidades; referencia = transferencia, item = el item.
        verify(service).insertarTransferencia(ORIGEN_ID, PRODUCTO_ID, 36D, new BigDecimal("0"), 55L, 900L, 5001L);
    }

    @Test
    void conStockNegativoRegistraElNumeroExacto() {
        stockEnOrigen("-4.125");

        assertTrue(service.registrarTransferencia(item()));

        verify(service).insertarTransferencia(ORIGEN_ID, PRODUCTO_ID, 36D, new BigDecimal("-4.125"), 55L, 900L, 5001L);
    }

    @Test
    void conStockPositivoNoRegistra() {
        stockEnOrigen("0.5");

        assertFalse(service.registrarTransferencia(item()));

        verify(service, never()).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    @Test
    void sinProductoOSinOrigenNoRegistraNiRevienta() {
        TransferenciaItem sinPresentacion = item();
        sinPresentacion.setPresentacionPreTransferencia(null);
        assertFalse(service.registrarTransferencia(sinPresentacion));

        TransferenciaItem sinOrigen = item();
        sinOrigen.getTransferencia().setSucursalOrigen(null);
        assertFalse(service.registrarTransferencia(sinOrigen));

        assertFalse(service.registrarTransferencia(null));
        verify(service, never()).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    @Test
    void siLaBaseFallaAlLeerElStockNoLanza() {
        doThrow(new RuntimeException("base caida")).when(service).stockPrevio(PRODUCTO_ID, ORIGEN_ID);

        assertFalse(service.registrarTransferencia(item()));
    }

    @Test
    void siLaBaseFallaAlInsertarNoLanza() {
        stockEnOrigen("0");
        doThrow(new RuntimeException("timeout")).when(service)
                .insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());

        assertFalse(service.registrarTransferencia(item()));
    }

    @Test
    void elCriterioEsStockPrevioMenorOIgualACero() {
        assertTrue(ControlStockNegativoService.debeRegistrar(BigDecimal.ZERO));
        assertTrue(ControlStockNegativoService.debeRegistrar(new BigDecimal("-0.001")));
        assertFalse(ControlStockNegativoService.debeRegistrar(new BigDecimal("0.001")));
        assertFalse(ControlStockNegativoService.debeRegistrar(null));
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./mvnw -o test -DskipFlyway=true -Dtest=ControlStockNegativoServiceTest`
Expected: FAIL de compilación, `cannot find symbol … ControlStockNegativoService`.

- [ ] **Step 3: Implementación**

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.productos.Presentacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.math.BigDecimal;

/**
 * Control de stock negativo: registra las salidas de productos cuyo stock en la sucursal ya era
 * 0 o negativo, y las lista para el equipo de inventario.
 *
 * El stock es siempre el del central (suma de movimientos activos del producto en la sucursal).
 * Las ventas no pasan por aca: las inserta {@link ControlStockNegativoProcesador}.
 *
 * Las escrituras van por JdbcTemplate y no por JPA a proposito. El central usa
 * OpenEntityManagerInViewFilter: todo el request comparte un EntityManager, y un save de JPA que
 * falle hace rollback y clear() de ese EntityManager, dejando detached el item que
 * saveTransferenciaItem acaba de guardar. JdbcTemplate usa su propia conexion.
 */
@Service
public class ControlStockNegativoService {

    private static final Logger log = LoggerFactory.getLogger(ControlStockNegativoService.class);

    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public ControlStockNegativoService(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    /** El criterio unico: se registra si antes de la salida el stock era 0 o negativo. */
    public static boolean debeRegistrar(BigDecimal stockPrevio) {
        return stockPrevio != null && stockPrevio.signum() <= 0;
    }

    /**
     * Registra el item recien cargado si el stock del origen ya era 0 o negativo.
     *
     * Es un registro de control, no parte de la operacion: nunca lanza. Cualquier falla se loguea
     * y el item se guarda igual.
     *
     * @return true si quedo registrado
     */
    public boolean registrarTransferencia(TransferenciaItem item) {
        try {
            if (item == null || item.getTransferencia() == null
                    || item.getTransferencia().getSucursalOrigen() == null) {
                return false;
            }
            Presentacion presentacion = item.getPresentacionPreTransferencia();
            if (presentacion == null || presentacion.getProducto() == null) {
                return false;
            }
            long sucursalId = item.getTransferencia().getSucursalOrigen().getId();
            long productoId = presentacion.getProducto().getId();

            BigDecimal stockPrevio = stockPrevio(productoId, sucursalId);
            if (!debeRegistrar(stockPrevio)) {
                return false;
            }
            double porPresentacion = presentacion.getCantidad() != null ? presentacion.getCantidad() : 1D;
            double cantidad = item.getCantidadPreTransferencia() != null ? item.getCantidadPreTransferencia() : 0D;
            Long usuarioId = item.getUsuario() != null ? item.getUsuario().getId() : null;

            insertarTransferencia(sucursalId, productoId, cantidad * porPresentacion, stockPrevio,
                    usuarioId, item.getTransferencia().getId(), item.getId());
            return true;
        } catch (Exception ex) {
            log.warn("Control de stock negativo: no se pudo registrar el item {} de transferencia: {}",
                    item != null ? item.getId() : null, ex.getMessage());
            return false;
        }
    }

    /** Stock del producto en la sucursal segun el central, sin pasar por Float. */
    BigDecimal stockPrevio(long productoId, long sucursalId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(cantidad), 0) FROM operaciones.movimiento_stock " +
                "WHERE producto_id = ? AND sucursal_id = ? AND estado",
                BigDecimal.class, productoId, sucursalId);
    }

    void insertarTransferencia(long sucursalId, long productoId, double cantidad, BigDecimal stockPrevio,
                               Long usuarioId, Long transferenciaId, Long itemId) {
        jdbc.update(
                "INSERT INTO operaciones.control_stock_negativo " +
                "  (sucursal_id, producto_id, tipo, cantidad, stock_previo, usuario_id, fecha, referencia_id, item_id) " +
                "VALUES (?, ?, 'TRANSFERENCIA', ?, ?, ?, now(), ?, ?) " +
                "ON CONFLICT (item_id, sucursal_id) WHERE tipo = 'TRANSFERENCIA' DO NOTHING",
                sucursalId, productoId, cantidad, stockPrevio, usuarioId, transferenciaId, itemId);
    }
}
```

El `EntityManager` no se usa todavía: lo usa `buscar` en la Task 5.

- [ ] **Step 4: Verificar que pasa**

Run: `./mvnw -o test -DskipFlyway=true -Dtest=ControlStockNegativoServiceTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`.

(El commit va al final de la Task 3: las dos forman la Fase 2.)

---

### Task 3 (Fase 2b): enganchar el registro en `saveTransferenciaItem`

**Files:**
- Modify: `src/main/java/com/franco/dev/graphql/operaciones/TransferenciaItemGraphQL.java` (campos ~línea 77 y cuerpo de `saveTransferenciaItem` ~líneas 185–198)
- Test: `src/test/java/com/franco/dev/graphql/operaciones/TransferenciaItemGraphQLControlStockTest.java`

**Interfaces:**
- Consumes: `ControlStockNegativoService.registrarTransferencia(TransferenciaItem): boolean`; el método privado ya existente `esTransferenciaDesdeCompras(TransferenciaItem)`.

- [ ] **Step 1: Test que falla**

```java
package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El control de stock negativo se consulta solo al cargar un item NUEVO, y un fallo al registrar
 * nunca impide guardar el item.
 */
class TransferenciaItemGraphQLControlStockTest {

    private static final Long ITEM_EXISTENTE = 65830L;

    private TransferenciaItemService service;
    private TransferenciaService transferenciaService;
    private ControlStockNegativoService controlStockNegativoService;
    private TransferenciaItemGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaItemService.class);
        controlStockNegativoService = mock(ControlStockNegativoService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        transferenciaService = mock(TransferenciaService.class);
        PresentacionService presentacionService = mock(PresentacionService.class);

        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "transferenciaService", transferenciaService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemLoteService", mock(TransferenciaItemLoteService.class));
        ReflectionTestUtils.setField(resolver, "presentacionService", presentacionService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", mock(MovimientoStockService.class));
        ReflectionTestUtils.setField(resolver, "controlStockNegativoService", controlStockNegativoService);

        when(usuarioService.findById(55L)).thenReturn(Optional.of(new Usuario()));
        when(transferenciaService.findById(any())).thenReturn(Optional.of(new Transferencia()));
        when(presentacionService.findById(any())).thenAnswer(i -> Optional.of(new Presentacion()));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        TransferenciaItem existente = new TransferenciaItem();
        existente.setId(ITEM_EXISTENTE);
        when(service.findById(ITEM_EXISTENTE)).thenReturn(Optional.of(existente));
    }

    private TransferenciaItemInput input(Long id) {
        TransferenciaItemInput in = new TransferenciaItemInput();
        in.setId(id);
        in.setTransferenciaId(6290L);
        in.setPresentacionPreTransferenciaId(13261L);
        in.setCantidadPreTransferencia(3D);
        in.setUsuarioId(55L);
        return in;
    }

    @Test
    void unItemNuevoConsultaElControl() {
        resolver.saveTransferenciaItem(input(null), null);
        verify(controlStockNegativoService, times(1)).registrarTransferencia(any());
    }

    /** COMPRAS tiene stock negativo por diseno: la mercaderia "nace" ahi al cargar la compra. */
    @Test
    void unItemQueSaleDeComprasNoSeRegistra() {
        com.franco.dev.domain.empresarial.Sucursal compras = new com.franco.dev.domain.empresarial.Sucursal();
        compras.setNombre(com.franco.dev.service.productos.CostosPorProductoService.SUCURSAL_COMPRAS);
        Transferencia desdeCompras = new Transferencia();
        desdeCompras.setSucursalOrigen(compras);
        when(transferenciaService.findById(any())).thenReturn(Optional.of(desdeCompras));

        resolver.saveTransferenciaItem(input(null), null);

        verify(controlStockNegativoService, never()).registrarTransferencia(any());
    }

    @Test
    void editarUnItemExistenteNoVuelveARegistrar() {
        resolver.saveTransferenciaItem(input(ITEM_EXISTENTE), null);
        verify(controlStockNegativoService, never()).registrarTransferencia(any());
    }

    @Test
    void unFalloAlRegistrarNoImpideGuardarElItem() {
        when(controlStockNegativoService.registrarTransferencia(any()))
                .thenThrow(new RuntimeException("base caida"));

        TransferenciaItem guardado = resolver.saveTransferenciaItem(input(null), null);

        assertNotNull(guardado);
        verify(service).save(any());
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./mvnw -o test -DskipFlyway=true -Dtest=TransferenciaItemGraphQLControlStockTest`
Expected: FAIL — `ReflectionTestUtils.setField` no encuentra el campo `controlStockNegativoService`.

Si el fallo es otro (por ejemplo un `NullPointerException` de un colaborador que este test no simula), comparar el `setUp` con el de `TransferenciaItemGraphQLPreservacionTest`, que es el molde, y agregar el mock que falte antes de seguir.

- [ ] **Step 3: Implementación**

En `TransferenciaItemGraphQL.java`, junto a los otros `@Autowired` (después de `transferenciaItemAlertaService`):

```java
    @Autowired
    private ControlStockNegativoService controlStockNegativoService;
```

con su import `com.franco.dev.service.operaciones.ControlStockNegativoService`.

En `saveTransferenciaItem`, reemplazar:

```java
        e = service.save(e);
        // Antes de generar el movimiento: el desglose por lote lee esta asignacion para decidir
```

por:

```java
        e = service.save(e);
        // Solo items nuevos, y no los que salen de COMPRAS: ahi el stock es negativo por diseno.
        if (existente == null && !esTransferenciaDesdeCompras(e)) {
            registrarEnControlDeStock(e);
        }
        // Antes de generar el movimiento: el desglose por lote lee esta asignacion para decidir
```

y agregar el método privado debajo de `saveTransferenciaItem`:

```java
    /**
     * Control de stock negativo: un item nuevo cuyo origen ya estaba en 0 o negativo queda
     * registrado para inventario. El servicio ya no lanza; el try/catch es la segunda red, para
     * que ni un servicio sin inyectar (tests viejos) pueda impedir que el item se guarde.
     */
    private void registrarEnControlDeStock(TransferenciaItem item) {
        try {
            controlStockNegativoService.registrarTransferencia(item);
        } catch (Exception ex) {
            log.warn("Control de stock negativo: no se pudo registrar el item {} de transferencia: {}",
                    item != null ? item.getId() : null, ex.getMessage());
        }
    }
```

Si la clase no tiene logger, agregar como primer campo:

```java
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransferenciaItemGraphQL.class);
```

- [ ] **Step 4: Verificar que pasa, junto con los tests vecinos**

Run: `./mvnw -o test -DskipFlyway=true -Dtest='TransferenciaItemGraphQL*Test,ControlStockNegativoServiceTest'`
Expected: todos en verde, incluidos `PreservacionTest` y `DesconfirmarTest` (no inyectan el servicio nuevo: el `NullPointerException` cae en el `try/catch` y solo loguea).

- [ ] **Step 5: Commit y push de la fase**

```bash
git status --short
git add src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoService.java \
  src/main/java/com/franco/dev/graphql/operaciones/TransferenciaItemGraphQL.java \
  src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoServiceTest.java \
  src/test/java/com/franco/dev/graphql/operaciones/TransferenciaItemGraphQLControlStockTest.java
git commit -m "feat(inventario): registrar transferencias con stock cero o negativo"
git push
```

---

### Task 4 (Fase 3): poller de ventas

**Files:**
- Create: `src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoProcesador.java`
- Create: `src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoScheduler.java`
- Modify: `src/main/resources/application.properties`, `application-dev.properties`, `application-ci.properties`
- Test: `src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoProcesadorTest.java`
- Test: `src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoSchedulerTest.java`

**Interfaces:**
- Produces: `ControlStockNegativoProcesador.sucursales(): List<Long>` (las que toca sondear, la menos reciente primero), `procesarSucursal(Long sucursalId): int` (filas insertadas), `static long rangoDesde(long ultimo, long inicial)`; constantes `LOTE = 500`, `SOLAPE_IDS = 5000`. `ControlStockNegativoScheduler.ciclo()` (síncrono, el que se testea) y `evaluarVentas()` (el `@Scheduled`, despacha `ciclo()` a un hilo propio).

Tres decisiones que salieron de la auditoría y que el código tiene que respetar:

1. **Hilo propio.** Las tareas `@Scheduled` del central comparten un solo hilo (`CotizacionMercadoScheduler` lo documenta). El `@Scheduled` solo encola; el trabajo corre en un executor de un hilo, con un guard para no encolar ciclos si el anterior sigue. Cada ciclo corta a los 20 s y sigue en el próximo, empezando por la sucursal que hace más tiempo no se procesa.
2. **Solapamiento por tiempo, no por ids.** Los ids de una sucursal avanzan de 2 en 2 y los comparten todos los tipos de movimiento: una venta de 30 ítems ya ocupa 60 ids. Cada ciclo re-evalúa las ventas cuyo `creado_en` cae en los 15 minutos anteriores a la última ya recorrida, mirando como mucho 5000 ids hacia atrás.
3. **Las sucursales sin ninguna venta** (depósito, COMPRAS) se sondean una vez por hora: sin ventas el cursor queda en 0 y cada sondeo recorre todo su historial (98 ms en la sucursal 13).

- [ ] **Step 1: Tests que fallan**

```java
package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El tramo que se re-lee nunca retrocede mas alla de donde arranco el control: no hay carga
 * retroactiva. Dentro de ese tramo, el filtro por fecha (15 minutos) lo aplica el SQL.
 */
class ControlStockNegativoProcesadorTest {

    @Test
    void miraHastaElSolapamientoDeIdsHaciaAtras() {
        assertEquals(100_000 - ControlStockNegativoProcesador.SOLAPE_IDS,
                ControlStockNegativoProcesador.rangoDesde(100_000, 1_000));
    }

    @Test
    void elSolapamientoNoPasaPorDebajoDelInicio() {
        assertEquals(99_000, ControlStockNegativoProcesador.rangoDesde(100_000, 99_000));
        assertEquals(100_000, ControlStockNegativoProcesador.rangoDesde(100_000, 100_000));
    }
}
```

```java
package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * El ciclo nunca propaga una excepcion, una sucursal que falla no detiene a las demas, y un ciclo
 * largo se corta: lo que falte se procesa en el siguiente.
 */
class ControlStockNegativoSchedulerTest {

    private static ControlStockNegativoProcesador procesadorCon(Long... sucursales) {
        ControlStockNegativoProcesador procesador = mock(ControlStockNegativoProcesador.class);
        when(procesador.sucursales()).thenReturn(Arrays.asList(sucursales));
        return procesador;
    }

    @Test
    void unaSucursalQueFallaNoDetieneALasDemas() {
        ControlStockNegativoProcesador procesador = procesadorCon(1L, 2L, 3L);
        when(procesador.procesarSucursal(2L)).thenThrow(new RuntimeException("timeout"));

        new ControlStockNegativoScheduler(procesador, () -> 0L).ciclo();

        verify(procesador).procesarSucursal(1L);
        verify(procesador).procesarSucursal(2L);
        verify(procesador).procesarSucursal(3L);
    }

    @Test
    void siNoSePuedenListarLasSucursalesElCicloNoRevienta() {
        ControlStockNegativoProcesador procesador = mock(ControlStockNegativoProcesador.class);
        when(procesador.sucursales()).thenThrow(new RuntimeException("sin conexion"));

        new ControlStockNegativoScheduler(procesador, () -> 0L).ciclo();

        verify(procesador, never()).procesarSucursal(anyLong());
    }

    @Test
    void pasadoElPresupuestoDeTiempoCortaYDejaElRestoParaElProximoCiclo() {
        ControlStockNegativoProcesador procesador = procesadorCon(1L, 2L, 3L);
        // Cada lectura del reloj avanza 15 s: arranque en 0, antes de la sucursal 1 van 15 s
        // (dentro del presupuesto de 20), antes de la 2 van 30 s (fuera).
        AtomicLong reloj = new AtomicLong(-15_000);

        new ControlStockNegativoScheduler(procesador, () -> reloj.addAndGet(15_000)).ciclo();

        verify(procesador).procesarSucursal(1L);
        verify(procesador, never()).procesarSucursal(2L);
        verify(procesador, never()).procesarSucursal(3L);
    }
}
```

- [ ] **Step 2: Verificar que fallan**

Run: `./mvnw -o test -DskipFlyway=true -Dtest='ControlStockNegativoProcesadorTest,ControlStockNegativoSchedulerTest'`
Expected: FAIL de compilación, faltan las dos clases.

- [ ] **Step 3: Procesador**

```java
package com.franco.dev.service.operaciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Un ciclo del control de stock negativo para una sucursal, en su propia transaccion.
 *
 * Bean separado de {@link ControlStockNegativoScheduler} a proposito: invocado por
 * {@code this.} el proxy de Spring no aplicaria {@code @Transactional} (mismo motivo que
 * {@code RetiroTesoreriaProcesador}).
 *
 * Las ventas nacen en la filial y llegan por replicacion logica, sin pasar por codigo de la
 * aplicacion: por eso se sondea. Todos los movimientos VENTA de una sucursal salen de la secuencia
 * de su filial (ids pares, V223.1), asi que un cursor por id sobre ese tipo recorre una sola serie.
 *
 * El stock previo de una venta es la suma de los movimientos activos del producto en la sucursal
 * con fecha ANTERIOR a la de la venta. No es "el stock al momento de procesar": una venta posterior
 * que ya llego no debe contar.
 *
 * Se registra por MOVIMIENTO, no por item de venta: un mismo item puede tener mas de un movimiento
 * activo (descuentos duplicados), y esos son justamente los que inventario necesita ver.
 */
@Service
public class ControlStockNegativoProcesador {

    /** Ventas nuevas que se evaluan por ciclo y por sucursal. Acota el costo cuando una filial vuelve de estar offline. */
    static final int LOTE = 500;
    /**
     * Cuantos ids hacia atras se miran buscando ventas que se confirmaron fuera de orden. Es solo
     * la cota del escaneo por el indice (sucursal_id, id): dentro de ese tramo se re-evaluan
     * unicamente las ventas de los ultimos 15 minutos, que es el filtro que importa.
     */
    static final long SOLAPE_IDS = 5000;

    @PersistenceContext
    private EntityManager em;

    static long rangoDesde(long ultimo, long inicial) {
        return Math.max(ultimo - SOLAPE_IDS, inicial);
    }

    /**
     * Sucursales que toca sondear, la que hace mas tiempo no se procesa primero (asi un ciclo que
     * se corta por tiempo no deja siempre afuera a las mismas). Una sucursal que nunca tuvo una
     * venta se sondea una vez por hora: su sondeo recorre todo su historial de movimientos.
     */
    @Transactional(readOnly = true)
    public List<Long> sucursales() {
        List<?> filas = em.createNativeQuery(
                "SELECT s.id FROM empresarial.sucursal s " +
                "LEFT JOIN operaciones.control_stock_negativo_cursor c ON c.sucursal_id = s.id " +
                "WHERE c.sucursal_id IS NULL OR c.ultimo_movimiento_id > 0 " +
                "   OR c.actualizado_en < now() - interval '1 hour' " +
                "ORDER BY c.actualizado_en ASC NULLS FIRST, s.id").getResultList();
        List<Long> ids = new ArrayList<>();
        for (Object f : filas) ids.add(((Number) f).longValue());
        return ids;
    }

    /** @return cuantas ventas se registraron en el control */
    @Transactional
    public int procesarSucursal(Long sucursalId) {
        List<?> cursor = em.createNativeQuery(
                "SELECT ultimo_movimiento_id, inicial_movimiento_id " +
                "FROM operaciones.control_stock_negativo_cursor WHERE sucursal_id = :s FOR UPDATE")
                .setParameter("s", sucursalId).getResultList();

        if (cursor.isEmpty()) {
            // Primera vez: se arranca desde la ultima venta que ya existe. Sin carga retroactiva.
            em.createNativeQuery(
                    "INSERT INTO operaciones.control_stock_negativo_cursor " +
                    "  (sucursal_id, ultimo_movimiento_id, inicial_movimiento_id, ultimo_creado_en) " +
                    "SELECT :s, COALESCE(x.id, 0), COALESCE(x.id, 0), x.creado_en " +
                    "FROM (SELECT 1) uno LEFT JOIN LATERAL (" +
                    "    SELECT id, creado_en FROM operaciones.movimiento_stock " +
                    "    WHERE sucursal_id = :s AND tipo_movimiento = 'VENTA' ORDER BY id DESC LIMIT 1) x ON true " +
                    "ON CONFLICT (sucursal_id) DO NOTHING")
                    .setParameter("s", sucursalId).executeUpdate();
            return 0;
        }

        Object[] fila = (Object[]) cursor.get(0);
        long ultimo = ((Number) fila[0]).longValue();
        long inicial = ((Number) fila[1]).longValue();

        Object hasta = em.createNativeQuery(
                "SELECT max(t.id) FROM (SELECT id FROM operaciones.movimiento_stock " +
                "  WHERE sucursal_id = :s AND id > :u AND tipo_movimiento = 'VENTA' " +
                "  ORDER BY id LIMIT :l) t")
                .setParameter("s", sucursalId).setParameter("u", ultimo).setParameter("l", LOTE)
                .getSingleResult();
        if (hasta == null) {
            // Sin ventas nuevas. Se anota la pasada: ordena el proximo ciclo y espacia el sondeo
            // de las sucursales que nunca vendieron. La venta que llego fuera de orden se levanta
            // en el proximo ciclo con ventas nuevas: el solapamiento es relativo a ultimo_creado_en.
            em.createNativeQuery(
                    "UPDATE operaciones.control_stock_negativo_cursor SET actualizado_en = now() " +
                    "WHERE sucursal_id = :s").setParameter("s", sucursalId).executeUpdate();
            return 0;
        }
        long hastaId = ((Number) hasta).longValue();

        int registradas = em.createNativeQuery(
                "INSERT INTO operaciones.control_stock_negativo " +
                "  (sucursal_id, producto_id, tipo, cantidad, stock_previo, usuario_id, fecha, " +
                "   referencia_id, item_id, movimiento_stock_id) " +
                "SELECT ms.sucursal_id, ms.producto_id, 'VENTA', -ms.cantidad, sp.stock, ms.usuario_id, " +
                "       ms.creado_en, vi.venta_id, ms.referencia, ms.id " +
                "FROM operaciones.movimiento_stock ms " +
                "CROSS JOIN LATERAL (SELECT COALESCE(SUM(p.cantidad), 0) AS stock " +
                "    FROM operaciones.movimiento_stock p " +
                "    WHERE p.producto_id = ms.producto_id AND p.sucursal_id = ms.sucursal_id " +
                "      AND p.estado AND p.creado_en < ms.creado_en) sp " +
                "LEFT JOIN operaciones.venta_item vi " +
                "    ON vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
                "WHERE ms.sucursal_id = :s AND ms.id > :desde AND ms.id <= :hasta " +
                "  AND ms.tipo_movimiento = 'VENTA' AND ms.estado AND ms.creado_en IS NOT NULL " +
                // Lo nuevo, mas lo ya recorrido de los ultimos 15 minutos (confirmado fuera de orden).
                "  AND (ms.id > :ultimo OR ms.creado_en >= (" +
                "        SELECT c.ultimo_creado_en - interval '15 minutes' " +
                "        FROM operaciones.control_stock_negativo_cursor c WHERE c.sucursal_id = :s)) " +
                "  AND sp.stock <= 0 " +
                "ON CONFLICT (sucursal_id, movimiento_stock_id) WHERE movimiento_stock_id IS NOT NULL DO NOTHING")
                .setParameter("s", sucursalId)
                .setParameter("desde", rangoDesde(ultimo, inicial))
                .setParameter("hasta", hastaId)
                .setParameter("ultimo", ultimo)
                .executeUpdate();

        em.createNativeQuery(
                "UPDATE operaciones.control_stock_negativo_cursor c " +
                "SET ultimo_movimiento_id = :h, actualizado_en = now(), " +
                "    ultimo_creado_en = COALESCE((SELECT ms.creado_en FROM operaciones.movimiento_stock ms " +
                "        WHERE ms.sucursal_id = :s AND ms.id = :h), c.ultimo_creado_en) " +
                "WHERE c.sucursal_id = :s")
                .setParameter("h", hastaId).setParameter("s", sucursalId).executeUpdate();

        return registradas;
    }
}
```

- [ ] **Step 4: Scheduler**

```java
package com.franco.dev.service.operaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Evalua las ventas que llegan replicadas de las filiales y registra en el control las que
 * salieron con stock 0 o negativo.
 *
 * Hilo propio: las tareas {@code @Scheduled} del central comparten un unico hilo (ver
 * {@code CotizacionMercadoScheduler}). Cuando una filial vuelve de estar offline este ciclo puede
 * durar decenas de segundos; corriendo en el hilo compartido frenaria la replicacion, el poller de
 * retiros y las notificaciones. El {@code @Scheduled} solo encola.
 *
 * Encendido por defecto; se apaga con
 * {@code inventario.control-stock-negativo.poller.enabled=false} (env
 * {@code INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED}). Apagado en los perfiles dev y ci.
 */
@Component
@ConditionalOnProperty(name = "inventario.control-stock-negativo.poller.enabled",
        havingValue = "true", matchIfMissing = true)
public class ControlStockNegativoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ControlStockNegativoScheduler.class);

    /** Tope de un ciclo. Lo que no entra se procesa en el siguiente, empezando por lo mas atrasado. */
    static final long PRESUPUESTO_MS = 20_000;

    private final ControlStockNegativoProcesador procesador;
    private final LongSupplier relojMs;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "control-stock-negativo");
        t.setDaemon(true);
        return t;
    });

    /** Un ciclo a la vez: la cola del executor es ilimitada y sin esto los ticks se apilarian. */
    private final AtomicBoolean corriendo = new AtomicBoolean(false);

    @Autowired
    public ControlStockNegativoScheduler(ControlStockNegativoProcesador procesador) {
        this(procesador, System::currentTimeMillis);
    }

    ControlStockNegativoScheduler(ControlStockNegativoProcesador procesador, LongSupplier relojMs) {
        this.procesador = procesador;
        this.relojMs = relojMs;
    }

    @Scheduled(
            fixedDelayString = "${inventario.control-stock-negativo.poller.fixed-delay:60000}",
            initialDelayString = "${inventario.control-stock-negativo.poller.initial-delay:120000}"
    )
    public void evaluarVentas() {
        if (!corriendo.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    ciclo();
                } finally {
                    corriendo.set(false);
                }
            });
        } catch (RuntimeException e) {
            corriendo.set(false);
            log.warn("ControlStockNegativoScheduler: no se pudo encolar el ciclo: {}", e.getMessage());
        }
    }

    /** Un ciclo completo, sincrono. Nunca propaga una excepcion. */
    void ciclo() {
        try {
            long inicio = relojMs.getAsLong();
            int total = 0;
            for (Long sucursalId : procesador.sucursales()) {
                if (relojMs.getAsLong() - inicio > PRESUPUESTO_MS) {
                    log.info("ControlStockNegativoScheduler: ciclo cortado por tiempo, sigue en el proximo");
                    break;
                }
                try {
                    total += procesador.procesarSucursal(sucursalId);
                } catch (Exception e) {
                    log.warn("ControlStockNegativoScheduler: sucursal {} no procesada: {}",
                            sucursalId, e.getMessage());
                }
            }
            if (total > 0) log.info("ControlStockNegativoScheduler: {} ventas registradas", total);
        } catch (Exception e) {
            log.warn("ControlStockNegativoScheduler: error en el ciclo: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 5: Properties**

`application.properties`, al final del bloque de `factura.correo.*`:

```properties
# Control de stock negativo: evalua las ventas replicadas de las filiales (cada 60 s).
# Kill switch por instancia: INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED=false en su .env.
inventario.control-stock-negativo.poller.enabled=${INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED:true}
```

`application-dev.properties` y `application-ci.properties`, junto a `replication.refresh.enabled=false`:

```properties
# El poller del control de stock negativo no corre en desarrollo ni en CI.
inventario.control-stock-negativo.poller.enabled=false
```

Es un cambio compartido, no un override personal: no va en `application-user-dev.properties`.

- [ ] **Step 6: Verificar tests y el SQL contra la base local**

Run: `./mvnw -o test -DskipFlyway=true -Dtest='ControlStockNegativoProcesadorTest,ControlStockNegativoSchedulerTest'`
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

El `INSERT … SELECT` no tiene test unitario: se prueba contra datos reales, dentro de una transacción que se deshace. **Solo contra la base local** (`localhost:5551`, `bodega`). Simula una filial que vuelve con 2000 ids de atraso (Review Focus 1), dos pasadas sobre el mismo tramo (Review Focus 2) y el caso de los movimientos duplicados de un mismo ítem.

```bash
export PGPASSWORD="$(grep -E '^spring.datasource.password' src/main/resources/application.properties | head -1 | cut -d= -f2- | sed -E 's/^\$\{[A-Z_]+:(.*)\}$/\1/')"
DESDE="(SELECT max(id) - 2000 FROM operaciones.movimiento_stock WHERE sucursal_id = 1 AND tipo_movimiento = 'VENTA')"
INS="INSERT INTO operaciones.control_stock_negativo (sucursal_id, producto_id, tipo, cantidad, stock_previo, usuario_id, fecha, referencia_id, item_id, movimiento_stock_id) SELECT ms.sucursal_id, ms.producto_id, 'VENTA', -ms.cantidad, sp.stock, ms.usuario_id, ms.creado_en, vi.venta_id, ms.referencia, ms.id FROM operaciones.movimiento_stock ms CROSS JOIN LATERAL (SELECT COALESCE(SUM(p.cantidad), 0) AS stock FROM operaciones.movimiento_stock p WHERE p.producto_id = ms.producto_id AND p.sucursal_id = ms.sucursal_id AND p.estado AND p.creado_en < ms.creado_en) sp LEFT JOIN operaciones.venta_item vi ON vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id WHERE ms.sucursal_id = 1 AND ms.id > $DESDE AND ms.tipo_movimiento = 'VENTA' AND ms.estado AND ms.creado_en IS NOT NULL AND sp.stock <= 0 ON CONFLICT (sucursal_id, movimiento_stock_id) WHERE movimiento_stock_id IS NOT NULL DO NOTHING;"
ESPERADO="SELECT count(*) AS esperado FROM operaciones.movimiento_stock ms CROSS JOIN LATERAL (SELECT COALESCE(SUM(p.cantidad), 0) AS stock FROM operaciones.movimiento_stock p WHERE p.producto_id = ms.producto_id AND p.sucursal_id = ms.sucursal_id AND p.estado AND p.creado_en < ms.creado_en) sp WHERE ms.sucursal_id = 1 AND ms.id > $DESDE AND ms.tipo_movimiento = 'VENTA' AND ms.estado AND ms.creado_en IS NOT NULL AND sp.stock <= 0;"
{ echo 'BEGIN;'; cat src/main/resources/db/migration/V243.1__operaciones_control_stock_negativo.sql; \
  echo '\timing on'; echo "$INS"; echo "$INS"; echo '\timing off'; echo "$ESPERADO"; \
  echo "SELECT count(*) AS filas, count(referencia_id) AS con_venta, max(stock_previo) AS max_stock, min(cantidad) AS min_cantidad, count(*) - count(DISTINCT item_id) AS movimientos_repetidos_de_un_item FROM operaciones.control_stock_negativo;"; \
  echo 'ROLLBACK;'; } | psql -h localhost -p 5551 -U franco -d bodega -X -v ON_ERROR_STOP=1 2>&1 | grep -vE 'WARNING|DETAIL|HINT'
```

Expected:
- primer `INSERT 0 N`; **segundo `INSERT 0 0`** (idempotente);
- `filas` igual a `esperado`: se registra **cada movimiento** que cumple el criterio, sin perder los duplicados de un mismo ítem;
- cada `INSERT` por debajo de ~8 s;
- `con_venta` igual a `filas`, `max_stock <= 0`, `min_cantidad > 0`;
- termina en `ROLLBACK`, sin `ERROR`.

Si `con_venta < filas`, anotar la diferencia en este plan antes de seguir: hay movimientos `VENTA` cuya `referencia` no es un `venta_item` de esa sucursal y la columna «referencia» de la lista quedaría vacía para ellos.

El resto del procesador (cursor, solapamiento por fecha, sondeo horario) se prueba con el central levantado, en la Task 9.

- [ ] **Step 7: Commit y push de la fase**

```bash
git status --short
git add src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoProcesador.java \
  src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoScheduler.java \
  src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoProcesadorTest.java \
  src/test/java/com/franco/dev/service/operaciones/ControlStockNegativoSchedulerTest.java \
  src/main/resources/application.properties src/main/resources/application-dev.properties \
  src/main/resources/application-ci.properties
git commit -m "feat(inventario): registrar ventas con stock cero o negativo"
git push
```

---

### Task 5 (Fase 4): consulta GraphQL con control por rol

**Files:**
- Create: `src/main/java/com/franco/dev/service/operaciones/InventarioSecurityService.java`
- Modify: `src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoService.java` (agregar `buscar`)
- Create: `src/main/java/com/franco/dev/graphql/operaciones/ControlStockNegativoGraphQL.java`
- Create: `src/main/java/com/franco/dev/graphql/operaciones/resolver/ControlStockNegativoResolver.java`
- Create: `src/main/resources/graphql/operaciones/control-stock-negativo.graphqls`
- Test: `src/test/java/com/franco/dev/service/operaciones/InventarioSecurityServiceTest.java`
- Test: `src/test/java/com/franco/dev/graphql/operaciones/ControlStockNegativoGraphQLSeguridadTest.java`

**Interfaces:**
- Produces (GraphQL): `controlStockNegativo(fechaInicio: String!, fechaFin: String!, sucursalId: ID, tipo: TipoControlStock, texto: String, page: Int!, size: Int!): ControlStockNegativoPage`.
- Produces (Java): `ControlStockNegativoService.buscar(LocalDateTime inicio, LocalDateTime fin, Long sucursalId, TipoControlStock tipo, String texto, int page, int size): Page<ControlStockNegativo>`; `InventarioSecurityService.requireVerInventario()`.

- [ ] **Step 1: Tests que fallan**

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El control de stock negativo lo ve quien tiene VER INVENTARIO o es superusuario. */
class InventarioSecurityServiceTest {

    private UsuarioService usuarioService;
    private RoleService roleService;
    private InventarioSecurityService seg;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        roleService = mock(RoleService.class);
        seg = new InventarioSecurityService();
        ReflectionTestUtils.setField(seg, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", roleService);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticado(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, "x", Collections.emptyList()));
        Usuario u = new Usuario();
        u.setId(601L);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(u));
        java.util.List<Role> lista = new java.util.ArrayList<>();
        for (String nombre : roles) {
            Role r = new Role();
            r.setNombre(nombre);
            lista.add(r);
        }
        when(roleService.findByUsuarioId(601L)).thenReturn(lista);
    }

    @Test
    void conElRolPasa() {
        autenticado("PRUEBANR", "VER INVENTARIO");
        assertDoesNotThrow(() -> seg.requireVerInventario());
    }

    @Test
    void elRolAdminPasa() {
        autenticado("PRUEBANR", "ADMIN");
        assertDoesNotThrow(() -> seg.requireVerInventario());
    }

    @Test
    void sinElRolNoPasa() {
        autenticado("PRUEBANR", "VER MOVIMIENTO DE STOCK");
        assertThrows(GraphQLException.class, () -> seg.requireVerInventario());
    }

    @Test
    void sinSesionNoPasa() {
        assertThrows(GraphQLException.class, () -> seg.requireVerInventario());
    }
}
```

```java
package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Sin el rol, la query no llega a leer la tabla. */
class ControlStockNegativoGraphQLSeguridadTest {

    @Test
    void sinRolNoConsulta() {
        ControlStockNegativoService service = mock(ControlStockNegativoService.class);
        InventarioSecurityService seg = mock(InventarioSecurityService.class);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVerInventario();
        ControlStockNegativoGraphQL resolver = new ControlStockNegativoGraphQL(service, seg);

        assertThrows(GraphQLException.class, () -> resolver.controlStockNegativo(
                "2026-10-01T00:00", "2026-10-10T23:59", null, null, null, 0, 15));
        verify(service, never()).buscar(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
```

- [ ] **Step 2: Verificar que fallan**

Run: `./mvnw -o test -DskipFlyway=true -Dtest='InventarioSecurityServiceTest,ControlStockNegativoGraphQLSeguridadTest'`
Expected: FAIL de compilación, faltan `InventarioSecurityService` y `ControlStockNegativoGraphQL`.

- [ ] **Step 3: `InventarioSecurityService`**

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Control de acceso por rol de las pantallas de inventario nuevas, self-contained: resuelve el
 * usuario por el nickname del SecurityContext y lee sus roles de la DB (`personas.usuario_role`).
 * No depende del marshaling de roles JWT→Authentication, roto a nivel sistema (issue #177).
 *
 * Mismo patron que {@code service.financiero.FacturacionSecurityService}. Superusuario: rol
 * "ADMIN" o el nickname "ADMIN".
 */
@Service
public class InventarioSecurityService {

    public static final String ADMIN = "ADMIN";
    public static final String VER_INVENTARIO = "VER INVENTARIO";

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    /** Ver el control de stock negativo. */
    public void requireVerInventario() { requireAnyRole(VER_INVENTARIO); }

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Set<String> currentRoles() {
        String nick = currentNickname();
        if (nick == null) return Collections.emptySet();
        Usuario u = usuarioService.findByNickname(nick).orElse(null);
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick == null) return false;
        if (ADMIN.equalsIgnoreCase(nick)) return true;
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;
        for (String r : roles) {
            if (r != null && mine.contains(r.trim().toUpperCase())) return true;
        }
        return false;
    }

    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere el rol "
                    + String.join(" o ", roles) + " para esta accion.");
        }
    }
}
```

Los imports de `Role`, `RoleService` y `UsuarioService` se copian tal cual de `FacturacionSecurityService.java` (si el paquete difiere del escrito acá, manda ese archivo).

- [ ] **Step 4: `buscar` en `ControlStockNegativoService`**

Agregar imports `com.franco.dev.domain.operaciones.ControlStockNegativo`, `com.franco.dev.domain.operaciones.enums.TipoControlStock`, `org.springframework.data.domain.Page`, `PageImpl`, `PageRequest`, `org.springframework.transaction.annotation.Transactional`, `javax.persistence.TypedQuery`, `java.time.LocalDateTime`, `java.util.List`, y el método (usa el `EntityManager` que el constructor ya recibe):

```java
    /**
     * Lista del control, la mas reciente primero.
     *
     * La consulta se arma a mano y solo con las condiciones que vienen: un parametro null en
     * JPQL contra PostgreSQL llega sin tipo y rompe el LIKE ("operator does not exist: ~~ bytea").
     * producto y usuario se traen con join fetch porque el resolver los lee fuera de la transaccion.
     */
    @Transactional(readOnly = true)
    public Page<ControlStockNegativo> buscar(LocalDateTime inicio, LocalDateTime fin, Long sucursalId,
                                             TipoControlStock tipo, String texto, int page, int size) {
        StringBuilder where = new StringBuilder(" where c.fecha >= :inicio and c.fecha <= :fin");
        if (sucursalId != null) where.append(" and c.sucursalId = :sucursalId");
        if (tipo != null) where.append(" and c.tipo = :tipo");
        String patron = texto != null && !texto.trim().isEmpty()
                ? "%" + texto.trim().toUpperCase().replace(' ', '%') + "%"
                : null;
        if (patron != null) where.append(" and upper(p.descripcion) like :patron");

        TypedQuery<ControlStockNegativo> datos = em.createQuery(
                "select c from ControlStockNegativo c join fetch c.producto p left join fetch c.usuario u"
                        + where + " order by c.fecha desc, c.id desc", ControlStockNegativo.class);
        TypedQuery<Long> total = em.createQuery(
                "select count(c) from ControlStockNegativo c join c.producto p" + where, Long.class);

        for (TypedQuery<?> q : new TypedQuery<?>[]{datos, total}) {
            q.setParameter("inicio", inicio);
            q.setParameter("fin", fin);
            if (sucursalId != null) q.setParameter("sucursalId", sucursalId);
            if (tipo != null) q.setParameter("tipo", tipo);
            if (patron != null) q.setParameter("patron", patron);
        }

        PageRequest pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        List<ControlStockNegativo> contenido = datos
                .setFirstResult((int) pagina.getOffset())
                .setMaxResults(pagina.getPageSize())
                .getResultList();
        return new PageImpl<>(contenido, pagina, total.getSingleResult());
    }
```

- [ ] **Step 5: Schema**

`src/main/resources/graphql/operaciones/control-stock-negativo.graphqls`:

```graphql
enum TipoControlStock {
    VENTA
    TRANSFERENCIA
}

"""
Salida de un producto cuyo stock en la sucursal ya era 0 o negativo.
`referenciaId` es el id de la venta o de la transferencia; `itemId`, el del item.
"""
type ControlStockNegativo {
    id: ID!
    sucursal: Sucursal
    producto: Producto
    tipo: TipoControlStock
    cantidad: Float
    stockPrevio: Float
    usuario: Usuario
    fecha: Date
    referenciaId: ID
    itemId: ID
    creadoEn: Date
}

type ControlStockNegativoPage {
    getContent: [ControlStockNegativo]
    getTotalElements: Int
    getTotalPages: Int
    getNumberOfElements: Int
    isFirst: Boolean
    isLast: Boolean
}

extend type Query {
    controlStockNegativo(fechaInicio: String!, fechaFin: String!, sucursalId: ID, tipo: TipoControlStock, texto: String, page: Int!, size: Int!): ControlStockNegativoPage
}
```

- [ ] **Step 6: Resolver de la query y del campo `sucursal`**

```java
package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.domain.operaciones.enums.TipoControlStock;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class ControlStockNegativoGraphQL implements GraphQLQueryResolver {

    private final ControlStockNegativoService service;
    private final InventarioSecurityService seg;

    public ControlStockNegativoGraphQL(ControlStockNegativoService service, InventarioSecurityService seg) {
        this.service = service;
        this.seg = seg;
    }

    public Page<ControlStockNegativo> controlStockNegativo(String fechaInicio, String fechaFin, Long sucursalId,
                                                           TipoControlStock tipo, String texto,
                                                           int page, int size) {
        seg.requireVerInventario();
        return service.buscar(DateUtils.stringToDate(fechaInicio), DateUtils.stringToDate(fechaFin),
                sucursalId, tipo, texto, page, size);
    }
}
```

```java
package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlStockNegativoResolver implements GraphQLResolver<ControlStockNegativo> {

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(ControlStockNegativo c) {
        return c.getSucursalId() != null ? sucursalService.findById(c.getSucursalId()).orElse(null) : null;
    }
}
```

El import de `GraphQLQueryResolver` / `GraphQLResolver` se copia de `NotaRemisionGraphQL.java` y de cualquier clase de `graphql/operaciones/resolver/` (el paquete depende de la versión de kickstart del repo).

- [ ] **Step 7: Verificar**

Run: `./mvnw -o test -DskipFlyway=true -Dtest='InventarioSecurityServiceTest,ControlStockNegativoGraphQLSeguridadTest,SchemaEnumsSincronizadosTest'`
Expected: todos en verde. `SchemaEnumsSincronizadosTest` confirma que `TipoControlStock` coincide entre Java y `.graphqls`.

- [ ] **Step 8: Commit y push de la fase**

```bash
git status --short
git add src/main/java/com/franco/dev/service/operaciones/InventarioSecurityService.java \
  src/main/java/com/franco/dev/service/operaciones/ControlStockNegativoService.java \
  src/main/java/com/franco/dev/graphql/operaciones/ControlStockNegativoGraphQL.java \
  src/main/java/com/franco/dev/graphql/operaciones/resolver/ControlStockNegativoResolver.java \
  src/main/resources/graphql/operaciones/control-stock-negativo.graphqls \
  src/test/java/com/franco/dev/service/operaciones/InventarioSecurityServiceTest.java \
  src/test/java/com/franco/dev/graphql/operaciones/ControlStockNegativoGraphQLSeguridadTest.java
git commit -m "feat(inventario): consulta del control de stock negativo"
git push
```

---

# DESKTOP

### Task 6 (Fase 5): lista y botón del dashboard

Preparar el worktree (el checkout principal está en otra rama con cambios ajenos):

```bash
cd /home/franco/dev-frc/frontend/frc-sistemas-integrados-angular
git fetch origin develop
git worktree add .claude/worktrees/control-stock-negativo -b feature/inventario-control-stock-negativo origin/develop
cp -al node_modules .claude/worktrees/control-stock-negativo/node_modules
cp -al app/node_modules .claude/worktrees/control-stock-negativo/app/node_modules
cd .claude/worktrees/control-stock-negativo
```

**Files** (relativos a `src/app/modules/operaciones/inventario/`):
- Create: `control-stock-negativo.model.ts`
- Modify: `graphql/graphql-query.ts`
- Create: `graphql/controlStockNegativo.gql.ts`
- Create: `list-control-stock-negativo/list-control-stock-negativo.component.{ts,html,scss}`
- Modify: `inventario.module.ts`
- Modify: `inventario-dashboard/inventario-dashboard.component.{ts,html}`

**Interfaces:**
- Consumes: query `controlStockNegativo` del central (Task 5).
- Produces: `ListControlStockNegativoComponent`, `ControlStockNegativoGQL`.

- [ ] **Step 1: Modelo**

```ts
import { Sucursal } from "../../empresarial/sucursal/sucursal.model";
import { Usuario } from "../../personas/usuarios/usuario.model";
import { Producto } from "../../productos/producto/producto.model";

export type TipoControlStock = "VENTA" | "TRANSFERENCIA";

/** Salida de un producto cuyo stock en la sucursal ya era 0 o negativo. */
export interface ControlStockNegativo {
  id: number;
  sucursal: Sucursal;
  producto: Producto;
  tipo: TipoControlStock;
  cantidad: number;
  stockPrevio: number;
  usuario: Usuario;
  fecha: Date;
  referenciaId: number;
  itemId: number;
}

export interface ControlStockNegativoPage {
  getContent: ControlStockNegativo[];
  getTotalElements: number;
  getTotalPages: number;
  getNumberOfElements: number;
  isFirst: boolean;
  isLast: boolean;
}

export interface ControlStockNegativoFiltros {
  fechaInicio: string;
  fechaFin: string;
  sucursalId: number | null;
  tipo: TipoControlStock | null;
  texto: string | null;
  page: number;
  size: number;
}
```

- [ ] **Step 2: Query y clase Apollo**

Al final de `graphql/graphql-query.ts`:

```ts
export const controlStockNegativoQuery = gql`
  query (
    $fechaInicio: String!
    $fechaFin: String!
    $sucursalId: ID
    $tipo: TipoControlStock
    $texto: String
    $page: Int!
    $size: Int!
  ) {
    data: controlStockNegativo(
      fechaInicio: $fechaInicio
      fechaFin: $fechaFin
      sucursalId: $sucursalId
      tipo: $tipo
      texto: $texto
      page: $page
      size: $size
    ) {
      getTotalPages
      getTotalElements
      getNumberOfElements
      isFirst
      isLast
      getContent {
        id
        tipo
        cantidad
        stockPrevio
        fecha
        referenciaId
        itemId
        sucursal {
          id
          nombre
        }
        producto {
          id
          descripcion
        }
        usuario {
          id
          nickname
        }
      }
    }
  }
`;
```

`graphql/controlStockNegativo.gql.ts`:

```ts
import { Injectable } from "@angular/core";
import { Query } from "apollo-angular";
import { ControlStockNegativoPage } from "../control-stock-negativo.model";
import { controlStockNegativoQuery } from "./graphql-query";

export interface ControlStockNegativoResponse {
  data: ControlStockNegativoPage;
}

@Injectable({
  providedIn: "root",
})
export class ControlStockNegativoGQL extends Query<ControlStockNegativoResponse> {
  override document = controlStockNegativoQuery;
}
```

- [ ] **Step 3: Componente**

`list-control-stock-negativo.component.ts`:

```ts
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from "@angular/core";
import { FormControl, FormGroup } from "@angular/forms";
import { PageEvent } from "@angular/material/paginator";
import { MatTableDataSource } from "@angular/material/table";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { of } from "rxjs";
import { catchError, finalize } from "rxjs/operators";

import { dateToString } from "../../../../commons/core/utils/dateUtils";
import { Tab } from "../../../../layouts/tab/tab.model";
import { NotificacionSnackbarService } from "../../../../notificacion-snackbar.service";
import { CargandoDialogService } from "../../../../shared/components/cargando-dialog/cargando-dialog.service";
import { Sucursal } from "../../../empresarial/sucursal/sucursal.model";
import { SucursalService } from "../../../empresarial/sucursal/sucursal.service";
import { ControlStockNegativo, ControlStockNegativoFiltros, TipoControlStock } from "../control-stock-negativo.model";
import { ControlStockNegativoGQL } from "../graphql/controlStockNegativo.gql";

/**
 * Control de stock negativo: productos que salieron por venta o transferencia cuando su stock en
 * la sucursal ya era 0 o negativo. Solo lectura; los registros los crea el central.
 */
@UntilDestroy()
@Component({
  selector: "app-list-control-stock-negativo",
  templateUrl: "./list-control-stock-negativo.component.html",
  styleUrls: ["./list-control-stock-negativo.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListControlStockNegativoComponent implements OnInit {
  @Input() data: Tab;

  dataSource = new MatTableDataSource<ControlStockNegativo>([]);
  fechaFormGroup = new FormGroup({
    inicio: new FormControl<Date | null>(null),
    fin: new FormControl<Date | null>(null),
  });
  sucursalControl = new FormControl<Sucursal | null>(null);
  tipoControl = new FormControl<TipoControlStock | null>(null);
  textoControl = new FormControl<string>("");

  sucursalList: Sucursal[] = [];
  readonly tipoOpciones: { value: TipoControlStock; label: string }[] = [
    { value: "VENTA", label: "Venta" },
    { value: "TRANSFERENCIA", label: "Transferencia" },
  ];
  readonly displayedColumns: string[] = [
    "fecha", "sucursal", "tipo", "producto", "cantidad", "stockPrevio", "usuario", "referencia",
  ];
  readonly pageSizeOptions = [15, 25, 50, 100];
  readonly today = new Date();
  length = 0;
  pageSize = 15;
  pageIndex = 0;
  /** true cuando la última consulta falló: la tabla vacía no significa «no hay registros». */
  huboError = false;

  constructor(
    private controlStockNegativoGQL: ControlStockNegativoGQL,
    private sucursalService: SucursalService,
    private cargandoService: CargandoDialogService,
    private notificacion: NotificacionSnackbarService,
    private cdRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.rangoPorDefecto();
    this.sucursalService
      .onGetAllSucursales()
      .pipe(untilDestroyed(this))
      .subscribe((lista) => {
        this.sucursalList = lista;
        this.cdRef.detectChanges();
      });
    this.buscar();
  }

  onFiltrar(): void {
    this.pageIndex = 0;
    this.buscar();
  }

  onResetFiltro(): void {
    this.rangoPorDefecto();
    this.sucursalControl.setValue(null);
    this.tipoControl.setValue(null);
    this.textoControl.setValue("");
    this.pageIndex = 0;
    this.buscar();
  }

  onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.buscar();
  }

  trackById(_: number, item: ControlStockNegativo): number {
    return item.id;
  }

  private rangoPorDefecto(): void {
    const fin = new Date();
    const inicio = new Date();
    inicio.setDate(fin.getDate() - 7);
    this.fechaFormGroup.setValue({ inicio, fin });
  }

  private filtros(): ControlStockNegativoFiltros {
    const inicio = new Date(this.fechaFormGroup.value.inicio ?? new Date());
    inicio.setHours(0, 0, 0, 0);
    const fin = new Date(this.fechaFormGroup.value.fin ?? this.fechaFormGroup.value.inicio ?? new Date());
    fin.setHours(23, 59, 59, 0);
    const texto = (this.textoControl.value ?? "").trim();
    return {
      fechaInicio: dateToString(inicio),
      fechaFin: dateToString(fin),
      sucursalId: this.sucursalControl.value?.id ?? null,
      tipo: this.tipoControl.value ?? null,
      texto: texto.length > 0 ? texto.toUpperCase() : null,
      page: this.pageIndex,
      size: this.pageSize,
    };
  }

  private buscar(): void {
    const { requestId } = this.cargandoService.openDialog(false, "Buscando...");
    this.controlStockNegativoGQL
      .fetch(this.filtros(), {
        fetchPolicy: "no-cache",
        errorPolicy: "all",
        // La tabla vive solo en el central: sin esto, en modo local Apollo la rutea al filial.
        context: { clientName: "servidor" },
      })
      .pipe(
        catchError(() => of(null)),
        finalize(() => this.cargandoService.closeDialog(requestId)),
        untilDestroyed(this)
      )
      .subscribe((result) => {
        const pagina = result?.data?.data;
        if (result == null || result.errors?.length || pagina == null) {
          this.huboError = true;
          this.dataSource.data = [];
          this.length = 0;
          this.notificacion.openAlgoSalioMal(
            result?.errors?.[0]?.message || "No se pudo consultar el control de stock negativo"
          );
        } else {
          this.huboError = false;
          this.dataSource.data = pagina.getContent || [];
          this.length = pagina.getTotalElements || 0;
        }
        this.cdRef.detectChanges();
      });
  }
}
```

`list-control-stock-negativo.component.html`:

```html
<app-generic-list titulo="Control de stock negativo" (filtrar)="onFiltrar()" (resetFiltro)="onResetFiltro()">
  <div filtros>
    <div fxLayout="row" fxLayoutAlign="start center" fxLayoutGap="10px" style="width: 100%">
      <div fxFlex="20%">
        <mat-form-field style="width: 100%">
          <mat-label>Rango de fecha</mat-label>
          <mat-date-range-input [formGroup]="fechaFormGroup" [rangePicker]="picker" style="width: 100%">
            <input matStartDate formControlName="inicio" placeholder="Inicio" [max]="today" />
            <input matEndDate formControlName="fin" placeholder="Fin" [max]="today" />
          </mat-date-range-input>
          <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-date-range-picker #picker></mat-date-range-picker>
        </mat-form-field>
      </div>
      <div fxFlex="20%">
        <mat-form-field style="width: 100%">
          <mat-label>Sucursal</mat-label>
          <mat-select [formControl]="sucursalControl">
            <mat-option [value]="null">Todas</mat-option>
            <mat-option *ngFor="let sucursal of sucursalList" [value]="sucursal">
              {{ sucursal.nombre }}
            </mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <div fxFlex="15%">
        <mat-form-field style="width: 100%">
          <mat-label>Tipo de movimiento</mat-label>
          <mat-select [formControl]="tipoControl">
            <mat-option [value]="null">Todos</mat-option>
            <mat-option *ngFor="let opcion of tipoOpciones" [value]="opcion.value">
              {{ opcion.label }}
            </mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <div fxFlex="35%">
        <mat-form-field style="width: 100%">
          <mat-label>Buscar por descripción del producto</mat-label>
          <input matInput [formControl]="textoControl" (keyup.enter)="onFiltrar()" autocomplete="off" />
        </mat-form-field>
      </div>
    </div>
  </div>

  <div table class="control-stock-tabla">
    <table mat-table [dataSource]="dataSource" [trackBy]="trackById" style="width: 100%">
      <ng-container matColumnDef="fecha">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Fecha</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.fecha | date: "dd/MM/yyyy HH:mm" }}</td>
      </ng-container>
      <ng-container matColumnDef="sucursal">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Sucursal</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.sucursal?.nombre }}</td>
      </ng-container>
      <ng-container matColumnDef="tipo">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Tipo</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.tipo | titlecase }}</td>
      </ng-container>
      <ng-container matColumnDef="producto">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Producto</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.producto?.descripcion }}</td>
      </ng-container>
      <ng-container matColumnDef="cantidad">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Cantidad</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.cantidad | number: "1.0-3" }}</td>
      </ng-container>
      <ng-container matColumnDef="stockPrevio">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Stock previo</th>
        <td mat-cell *matCellDef="let item" style="text-align: center" [class.stock-negativo]="item.stockPrevio < 0">
          {{ item.stockPrevio | number: "1.0-3" }}
        </td>
      </ng-container>
      <ng-container matColumnDef="usuario">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Usuario</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.usuario?.nickname }}</td>
      </ng-container>
      <ng-container matColumnDef="referencia">
        <th mat-header-cell *matHeaderCellDef style="text-align: center">Nro. venta / transferencia</th>
        <td mat-cell *matCellDef="let item" style="text-align: center">{{ item.referenciaId }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns; sticky: true"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="displayedColumns.length" style="text-align: center; padding: 24px">
          <span *ngIf="huboError">No se pudo consultar. Reintentá con «Filtrar».</span>
          <span *ngIf="!huboError">No hay registros para los filtros elegidos.</span>
        </td>
      </tr>
    </table>
    <mat-paginator [length]="length" [pageSize]="pageSize" [pageIndex]="pageIndex"
      [pageSizeOptions]="pageSizeOptions" (page)="onPage($event)" showFirstLastButtons></mat-paginator>
  </div>
</app-generic-list>
```

Antes de dar el HTML por bueno, abrir `list-productos-vencidos.component.html` completo y confirmar los nombres de los slots de `app-generic-list` (`filtros`, y el de la tabla): si el slot de contenido no se llama `table`, usar el nombre que usa ese archivo.

`list-control-stock-negativo.component.scss`:

```scss
.control-stock-tabla {
  width: 100%;
  overflow: auto;
}

.stock-negativo {
  color: #ef5350;
  font-weight: 600;
}
```

- [ ] **Step 4: Declarar en el módulo**

En `inventario.module.ts`: importar `ListControlStockNegativoComponent` y agregarlo al arreglo `declarations`, al lado de `ListProductosVencidosComponent`.

- [ ] **Step 5: Botón en el dashboard**

En `inventario-dashboard.component.ts`:

```ts
import { ListControlStockNegativoComponent } from '../list-control-stock-negativo/list-control-stock-negativo.component';
import { MainService } from '../../../../main.service';
import { ROLES } from '../../../personas/roles/roles.enum';
```

agregar `private mainService: MainService` al constructor, el flag y el método:

```ts
  /** Calculado una vez: el HTML no llama funciones. La seguridad real la aplica el central. */
  puedeVerControlStock = false;

  ngOnInit(): void {
    this.puedeVerControlStock = this.mainService.tieneAlgunRol([ROLES.VER_INVENTARIO]);
  }

  onControlStockNegativo(){
    this.tabService.addTab(new Tab(ListControlStockNegativoComponent, 'Control de stock negativo', null, InventarioDashboardComponent))
  }
```

(Si `ngOnInit` ya tiene cuerpo en `origin/develop`, se agrega la línea del flag, no se reemplaza el método. Si `MainService` ya está inyectado, no se duplica.)

En `inventario-dashboard.component.html`, después del último `<div fxFlex="30%" …>` de botones:

```html
      <div fxFlex="30%" style="height: 250px" *ngIf="puedeVerControlStock">
        <app-boton nombre="Control de stock negativo" icon="trending_down" [iconSize]="3"
          (clickEvent)="onControlStockNegativo()"></app-boton>
      </div>
```

- [ ] **Step 6: Verificar en `ng serve`**

Run: `npm run ng:serve` (en background) y esperar `Compiled successfully`.
Expected: sin errores de compilación. La prueba funcional va en la Tarea 9.

- [ ] **Step 7: Commit y push de la fase**

`npm run check` va una sola vez, al final de la Tarea 7 (regla del repo: AOT al cierre, no por fase). Esta fase se pushea con `ng serve` limpio y `npm run verificar:imports` en verde.

```bash
npm run verificar:imports
git status --short
git add src/app/modules/operaciones/inventario
git commit -m "feat(inventario): lista de control de stock negativo"
git push -u origin feature/inventario-control-stock-negativo
```

---

### Task 7 (Fase 6): aviso de stock en transferencias (desktop)

**Files** (relativos a `src/app/modules/operaciones/transferencia/`):
- Create: `aviso-stock.ts`
- Test: `aviso-stock.spec.ts`
- Modify: `edit-transferencia/edit-transferencia.component.ts` (`onEjecutarGuardadoItem`, ~líneas 1905–1967)

**Interfaces:**
- Consumes: `esSucursalCompras(sucursal)` de `src/app/modules/empresarial/sucursal/sucursal-compras.util.ts` (el componente ya lo importa).
- Produces: `decidirAvisoStock(stock: number, permitirNegativo: boolean, origenCompras: boolean): 'SEGUIR' | 'CONFIRMAR' | 'BLOQUEAR'`, `necesitaConfiguracion(stock: number): boolean`.

COMPRAS conserva exactamente el comportamiento de hoy: sin diálogo; el negativo se bloquea o pasa según la configuración. El central tampoco registra lo que sale de COMPRAS (Task 3).

- [ ] **Step 1: Test que falla**

`aviso-stock.spec.ts`:

```ts
import { decidirAvisoStock, necesitaConfiguracion } from "./aviso-stock";

describe("decidirAvisoStock", () => {
  it("con stock positivo sigue sin avisar", () => {
    expect(decidirAvisoStock(0.5, false, false)).toBe("SEGUIR");
    expect(decidirAvisoStock(10, true, false)).toBe("SEGUIR");
  });

  it("con stock 0 pide confirmación, permita o no el negativo", () => {
    expect(decidirAvisoStock(0, false, false)).toBe("CONFIRMAR");
    expect(decidirAvisoStock(0, true, false)).toBe("CONFIRMAR");
  });

  it("con stock negativo bloquea si la configuración no lo permite", () => {
    expect(decidirAvisoStock(-3, false, false)).toBe("BLOQUEAR");
  });

  it("con stock negativo pide confirmación si la configuración lo permite", () => {
    expect(decidirAvisoStock(-3, true, false)).toBe("CONFIRMAR");
  });

  it("desde COMPRAS nunca pide confirmación: se comporta como antes", () => {
    expect(decidirAvisoStock(0, false, true)).toBe("SEGUIR");
    expect(decidirAvisoStock(-3, true, true)).toBe("SEGUIR");
    expect(decidirAvisoStock(-3, false, true)).toBe("BLOQUEAR");
  });

  it("solo el negativo necesita leer la configuración", () => {
    expect(necesitaConfiguracion(-1)).toBeTrue();
    expect(necesitaConfiguracion(0)).toBeFalse();
    expect(necesitaConfiguracion(4)).toBeFalse();
  });
});
```

- [ ] **Step 2: Verificar que falla**

Run: `npx ng test --watch=false --include='**/transferencia/aviso-stock.spec.ts'`
Expected: FAIL, `Cannot find module './aviso-stock'`. (Karma no corre en el CI: este test es red local.)

- [ ] **Step 3: Implementación**

`aviso-stock.ts`:

```ts
/**
 * Qué hacer al cargar un ítem de transferencia según el stock del origen.
 *
 * - stock positivo: sigue.
 * - stock 0: aviso con confirmación.
 * - stock negativo: bloquea, salvo que la configuración de transferencias lo permita; en ese
 *   caso, aviso con confirmación.
 *
 * Lo que se confirma queda registrado por el central en el control de stock negativo.
 *
 * La sucursal COMPRAS queda fuera del aviso y del registro: su stock es negativo por diseño (la
 * mercadería "nace" ahí al cargar la compra). Conserva la regla de siempre: el negativo se bloquea
 * o pasa según la configuración, sin preguntar.
 */
export type DecisionAvisoStock = "SEGUIR" | "CONFIRMAR" | "BLOQUEAR";

export function decidirAvisoStock(
  stock: number,
  permitirNegativo: boolean,
  origenCompras: boolean
): DecisionAvisoStock {
  if (stock > 0) return "SEGUIR";
  if (origenCompras) return stock < 0 && !permitirNegativo ? "BLOQUEAR" : "SEGUIR";
  if (stock === 0) return "CONFIRMAR";
  return permitirNegativo ? "CONFIRMAR" : "BLOQUEAR";
}

/** La configuración solo hace falta para decidir un negativo: el 0 no la consulta. */
export function necesitaConfiguracion(stock: number): boolean {
  return stock < 0;
}
```

- [ ] **Step 4: Usarla en `edit-transferencia`**

Agregar el import `import { decidirAvisoStock, necesitaConfiguracion } from "../aviso-stock";`.

En `onEjecutarGuardadoItem`, reemplazar el cuerpo del `next: (stock) => { … }` (desde `if (stock == null)` hasta el cierre del `else` que llama a `procederConGuardadoItem`) por:

```ts
          next: (stock) => {
            if (stock == null) {
              this.cargandoService.closeDialog(requestId);
              this.notificacionService.openAlgoSalioMal("No se pudo verificar el stock del producto: no se agregó.");
              return;
            }
            if (!necesitaConfiguracion(stock)) {
              this.cargandoService.closeDialog(requestId);
              this.resolverAvisoStock(stock, false, ocultarStock, avisoNegativo);
              return;
            }
            this.configuracionTransferenciaService.onGetConfiguracion().subscribe({
              next: (config) => {
                this.cargandoService.closeDialog(requestId);
                this.resolverAvisoStock(stock, !!config?.permitirStockNegativo, ocultarStock, avisoNegativo);
              },
              error: () => {
                // Sin la configuración no se puede saber si el negativo está permitido: se bloquea, como hoy.
                this.cargandoService.closeDialog(requestId);
                this.notificacionService.openWarn(avisoNegativo(stock));
                this.onClear();
              },
            });
          },
```

y agregar el método debajo de `onEjecutarGuardadoItem`:

```ts
  /**
   * Stock 0 o negativo permitido: aviso con confirmación. Al continuar se guarda el ítem y el
   * central lo deja registrado en el control de stock negativo.
   */
  private resolverAvisoStock(
    stock: number,
    permitirNegativo: boolean,
    ocultarStock: boolean,
    avisoNegativo: (stock: number) => string
  ): void {
    const decision = decidirAvisoStock(
      stock,
      permitirNegativo,
      esSucursalCompras(this.selectedTransferencia?.sucursalOrigen)
    );
    if (decision === "SEGUIR") {
      this.procederConGuardadoItem();
      return;
    }
    if (decision === "BLOQUEAR") {
      this.notificacionService.openWarn(avisoNegativo(stock));
      this.onClear();
      return;
    }
    const mensaje =
      stock === 0
        ? "El producto tiene stock 0 en la sucursal de origen."
        : ocultarStock
        ? "El producto tiene stock negativo en la sucursal de origen."
        : `El producto tiene stock negativo (${stock}) en la sucursal de origen.`;
    this.dialogoService
      .confirm("Atención", mensaje, "¿Está seguro de continuar?")
      .pipe(untilDestroyed(this))
      .subscribe((res) => {
        if (res) {
          this.procederConGuardadoItem();
        } else {
          this.onClear();
        }
      });
  }
```

`ocultarStock` y `avisoNegativo` son las constantes locales que `onEjecutarGuardadoItem` ya define: se pasan tal cual. El bloque `error: (err) => { … }` de la consulta de stock (fail-closed de #390) no se toca.

- [ ] **Step 5: Verificar test y AOT**

Run: `npx ng test --watch=false --include='**/transferencia/aviso-stock.spec.ts'`
Expected: `6 specs, 0 failures`.

Run: `npm run check 2>&1 | tee /tmp/claude-1000/-home-franco-dev-frc/500aef54-1a15-43cc-a531-d080715b5265/scratchpad/desktop-check.log`
Expected: aparece `Build at:` y **ningún** `error` después. Los warnings de CommonJS y autoprefixer son preexistentes. Leer el log entero.

- [ ] **Step 6: Commit y push de la fase**

```bash
git status --short
git add src/app/modules/operaciones/transferencia/aviso-stock.ts \
  src/app/modules/operaciones/transferencia/aviso-stock.spec.ts \
  src/app/modules/operaciones/transferencia/edit-transferencia/edit-transferencia.component.ts
git commit -m "feat(transferencia): avisar al cargar un producto con stock cero o negativo"
git push
```

---

# MOBILE-PWA

### Task 8 (Fase 7): aviso de stock al agregar un ítem

Preparar el worktree:

```bash
cd /home/franco/dev-frc/frontend/frc-mobile-pwa
git fetch origin develop
git worktree add .claude/worktrees/control-stock-negativo -b feature/inventario-control-stock-negativo origin/develop
cp -al node_modules .claude/worktrees/control-stock-negativo/node_modules
cd .claude/worktrees/control-stock-negativo
```

Antes de escribir: leer `CLAUDE.md`, `docs/PATRONES.md` (§6 «no hay» vs «no pude preguntar») y `docs/modulos/transferencias.md`.

COMPRAS no necesita tratamiento acá: en la PWA no participa de transferencias (`transferencia-nueva.page.ts:43`: «SERVIDOR y COMPRAS no participan»).

**Files:**
- Create: `src/app/pages/transferencias/aviso-stock.ts`
- Test: `src/app/pages/transferencias/aviso-stock.spec.ts`
- Modify: `src/app/graphql/transferencias/graphql-query.ts`
- Create: `src/app/graphql/transferencias/stockEnOrigen.ts`, `src/app/graphql/transferencias/configuracionTransferencia.ts`
- Modify: `src/app/pages/transferencias/transferencia.service.ts`
- Modify: `src/app/pages/transferencias/transferencia-borrador.page.ts` (`agregar()`, ~líneas 267–320)
- Modify: `docs/PLAN_TESTEO_MANUAL.md`, `docs/modulos/transferencias.md`

**Interfaces:**
- Consumes (central, ya existen): `stockPorProducto(id: ID!, sucId: ID): Float`, `configuracionTransferencia: ConfiguracionTransferencia!`.
- Produces: `decidirAvisoStock`, `mensajeAvisoStock`, `TransferenciaService.stockEnOrigen(productoId, sucursalId): Observable<number>`, `TransferenciaService.permiteStockNegativo(): Observable<boolean>`.

- [ ] **Step 1: Test que falla**

`aviso-stock.spec.ts`:

```ts
import { describe, expect, it } from 'vitest';

import { decidirAvisoStock, mensajeAvisoStock } from './aviso-stock';

describe('decidirAvisoStock', () => {
  it('con stock positivo sigue sin avisar', () => {
    expect(decidirAvisoStock(0.5, false)).toBe('SEGUIR');
  });

  it('con stock 0 pide confirmación, permita o no el negativo', () => {
    expect(decidirAvisoStock(0, false)).toBe('CONFIRMAR');
    expect(decidirAvisoStock(0, true)).toBe('CONFIRMAR');
  });

  it('con stock negativo bloquea si la configuración no lo permite', () => {
    expect(decidirAvisoStock(-3, false)).toBe('BLOQUEAR');
  });

  it('con stock negativo pide confirmación si la configuración lo permite', () => {
    expect(decidirAvisoStock(-3, true)).toBe('CONFIRMAR');
  });
});

describe('mensajeAvisoStock', () => {
  it('distingue el 0 del negativo y dice el número', () => {
    expect(mensajeAvisoStock(0)).toContain('stock 0');
    expect(mensajeAvisoStock(-3)).toContain('-3');
  });
});
```

- [ ] **Step 2: Verificar que falla**

Run: `npx vitest run src/app/pages/transferencias/aviso-stock.spec.ts`
Expected: FAIL, no resuelve `./aviso-stock`. (`npm test` mata a `npm start`: no tener el server levantado.)

- [ ] **Step 3: Implementación de la decisión**

`aviso-stock.ts`:

```ts
/**
 * Qué hacer al cargar un ítem de transferencia según el stock del origen.
 *
 * Misma regla que el desktop: stock positivo sigue; stock 0 pide
 * confirmación; stock negativo bloquea salvo que la configuración de
 * transferencias lo permita, y entonces pide confirmación.
 *
 * Lo que se confirma queda registrado por el central en el control de stock
 * negativo, que mira el equipo de inventario.
 */
export type DecisionAvisoStock = 'SEGUIR' | 'CONFIRMAR' | 'BLOQUEAR';

export function decidirAvisoStock(stock: number, permitirNegativo: boolean): DecisionAvisoStock {
  if (stock > 0) {
    return 'SEGUIR';
  }
  if (stock === 0) {
    return 'CONFIRMAR';
  }
  return permitirNegativo ? 'CONFIRMAR' : 'BLOQUEAR';
}

export function mensajeAvisoStock(stock: number): string {
  return stock === 0
    ? 'El producto tiene stock 0 en la sucursal de origen.'
    : 'El producto tiene stock negativo (' + stock + ') en la sucursal de origen.';
}
```

- [ ] **Step 4: Consultas**

Al final de `src/app/graphql/transferencias/graphql-query.ts`:

```ts
/**
 * Stock de un producto en una sucursal, según el central. Es el número contra
 * el que se decide el aviso al cargar un ítem.
 */
export const stockEnOrigenQuery = gql`
  query ($id: ID!, $sucId: ID) {
    data: stockPorProducto(id: $id, sucId: $sucId)
  }
`;

/** Solo el permiso de stock negativo de la configuración de transferencias. */
export const configuracionTransferenciaQuery = gql`
  query {
    data: configuracionTransferencia {
      id
      permitirStockNegativo
    }
  }
`;
```

`stockEnOrigen.ts`:

```ts
import { Injectable } from '@angular/core';
import { Query } from 'src/app/core/graphql/gql-base';

import { stockEnOrigenQuery } from './graphql-query';

export interface Response {
  data?: number;
}

@Injectable({ providedIn: 'root' })
export class StockEnOrigenGQL extends Query<Response> {
  document = stockEnOrigenQuery;
}
```

`configuracionTransferencia.ts`:

```ts
import { Injectable } from '@angular/core';
import { Query } from 'src/app/core/graphql/gql-base';

import { configuracionTransferenciaQuery } from './graphql-query';

export interface ConfiguracionTransferencia {
  id?: number;
  permitirStockNegativo?: boolean;
}

export interface Response {
  data?: ConfiguracionTransferencia;
}

@Injectable({ providedIn: 'root' })
export class ConfiguracionTransferenciaGQL extends Query<Response> {
  document = configuracionTransferenciaQuery;
}
```

- [ ] **Step 5: Servicio**

En `transferencia.service.ts`, junto a los otros `inject(...)`:

```ts
  private readonly stockEnOrigenGQL = inject(StockEnOrigenGQL);
  private readonly configuracionGQL = inject(ConfiguracionTransferenciaGQL);
```

con sus imports, y los métodos (agregar `map` de `rxjs` si no está importado):

```ts
  /**
   * Stock del producto en la sucursal, según el central.
   *
   * ⚠️ **Un error viaja por el canal de error, no como 0.** Un 0 acá dispara
   * el aviso de «stock 0»: devolverlo cuando la consulta falló afirmaría algo
   * que nadie dijo. El llamador no agrega el ítem si esto falla.
   */
  stockEnOrigen(productoId: number, sucursalId: number): Observable<number> {
    return this.datos.consultar<number>(
      this.stockEnOrigenGQL,
      { id: productoId, sucId: sucursalId },
      { mostrarCarga: true },
    );
  }

  /** `true` si la configuración de transferencias permite cargar con stock negativo. */
  permiteStockNegativo(): Observable<boolean> {
    return this.datos
      .consultar<ConfiguracionTransferencia>(this.configuracionGQL, undefined, { mostrarCarga: true })
      .pipe(map((config) => config?.permitirStockNegativo === true));
  }
```

Antes de dejarlo, abrir `core/graphql/datos.service.ts` y confirmar el nombre de las opciones de `OpcionesOperacion` (`mostrarCarga`, `notificarError`): usar las que existan. Si `consultar` trata un `data: null` como error, está bien: es el comportamiento que se quiere.

- [ ] **Step 6: Usarlo en `agregar()` del borrador**

En `transferencia-borrador.page.ts`, importar `decidirAvisoStock` y `mensajeAvisoStock` de `./aviso-stock`, y `firstValueFrom` de `rxjs` si falta. Dentro de `agregar()`, reemplazar la llamada final `this.guardar(itemDePreTransferencia({ … }))` por:

```ts
    const input = itemDePreTransferencia({ /* los mismos argumentos que hoy */ });
    if (!(await this.puedeCargarse(seleccion.producto?.id, t.sucursalOrigen?.id))) {
      return;
    }
    this.guardar(input);
```

(los argumentos de `itemDePreTransferencia` no cambian: se mueve la llamada a una constante) y agregar el método privado:

```ts
  /**
   * Aviso de stock antes de cargar un ítem NUEVO. Editar uno ya cargado no
   * pasa por acá: el control se registra una sola vez, al cargarlo.
   *
   * ⚠️ **Si no se pudo consultar, no se carga.** «No pude preguntar» no es
   * «hay stock»: mismo criterio que el escritorio (#390).
   */
  private async puedeCargarse(
    productoId: number | undefined,
    sucursalOrigenId: number | undefined,
  ): Promise<boolean> {
    if (productoId == null || sucursalOrigenId == null) {
      return true;
    }

    let stock: number;
    let permitirNegativo = false;
    try {
      stock = await firstValueFrom(this.servicio.stockEnOrigen(productoId, sucursalOrigenId));
      if (stock < 0) {
        permitirNegativo = await firstValueFrom(this.servicio.permiteStockNegativo());
      }
    } catch {
      this.notificacion.danger('No se pudo verificar el stock del producto: no se agregó.');
      return false;
    }
    if (stock == null || Number.isNaN(Number(stock))) {
      this.notificacion.danger('No se pudo verificar el stock del producto: no se agregó.');
      return false;
    }

    const decision = decidirAvisoStock(Number(stock), permitirNegativo);
    if (decision === 'SEGUIR') {
      return true;
    }
    if (decision === 'BLOQUEAR') {
      this.notificacion.warn(
        'El producto tiene stock negativo (' + stock + ') y no puede ser transferido.',
      );
      return false;
    }
    return this.dialogo.confirmar({
      titulo: 'Atención',
      mensaje: mensajeAvisoStock(Number(stock)) + ' ¿Está seguro de continuar?',
      confirmar: 'Continuar',
    });
  }
```

Si `DatosService` ya notifica el error por su cuenta, pasar `notificarError: false` en las dos consultas del servicio para no mostrar dos avisos.

`editar()` no se toca.

- [ ] **Step 7: Documentación del repo**

En `docs/modulos/transferencias.md`, sección de carga de ítems, agregar:

```markdown
### Aviso de stock al cargar un ítem

Al agregar un producto se consulta su stock en la sucursal de origen (`stockPorProducto`, central):

- stock > 0: se carga sin avisar;
- stock 0: diálogo «¿Está seguro de continuar?»;
- stock negativo: se bloquea, salvo que `configuracionTransferencia.permitirStockNegativo` sea
  verdadero; en ese caso, el mismo diálogo.

Si la consulta falla no se carga el ítem. Editar un ítem ya cargado no vuelve a preguntar.
Lo que se confirma lo registra el central en el control de stock negativo (desktop → Inventario).
```

En `docs/PLAN_TESTEO_MANUAL.md`, agregar el bloque (y sumar 5 a la tabla de totales):

```markdown
### Transferencias — aviso de stock al cargar un ítem

| # | Caso | Esperado |
|---|---|---|
| 1 | Agregar un producto con stock positivo en origen | Se carga sin diálogo |
| 2 | Agregar un producto con stock 0 y tocar «Continuar» | Se carga; aparece en Control de stock negativo del desktop |
| 3 | Agregar un producto con stock 0 y cancelar | No se carga |
| 4 | Agregar un producto con stock negativo, config sin permitir | Aviso «no puede ser transferido»; no se carga |
| 5 | Agregar con el central caído o sin red | Aviso «No se pudo verificar el stock»; no se carga |
```

- [ ] **Step 8: Verificar**

Run: `npx vitest run src/app/pages/transferencias/aviso-stock.spec.ts`
Expected: `5 passed`.

Run: `npm test 2>&1 | tail -15`
Expected: toda la batería en verde.

Run: `npm run build 2>&1 | tee /tmp/claude-1000/-home-franco-dev-frc/500aef54-1a15-43cc-a531-d080715b5265/scratchpad/pwa-build.log | tail -20`
Expected: build completo, sin `ERROR`. Es el gate real: typechequea las plantillas.

- [ ] **Step 9: Commit y push de la fase**

Commits convencionales **en inglés** en este repo.

```bash
git status --short
git add src/app/pages/transferencias/aviso-stock.ts src/app/pages/transferencias/aviso-stock.spec.ts \
  src/app/graphql/transferencias/graphql-query.ts src/app/graphql/transferencias/stockEnOrigen.ts \
  src/app/graphql/transferencias/configuracionTransferencia.ts \
  src/app/pages/transferencias/transferencia.service.ts \
  src/app/pages/transferencias/transferencia-borrador.page.ts \
  docs/PLAN_TESTEO_MANUAL.md docs/modulos/transferencias.md
git commit -m "feat(transferencias): warn when adding an item with zero or negative stock"
git push -u origin feature/inventario-control-stock-negativo
```

---

# CIERRE (pasos 8 a 12 del ciclo)

### Task 9: prueba de runtime, auditoría del diff, batería, build y entrega

- [ ] **Step 1: Levantar el central local** (paso 9 del ciclo)

```bash
ss -ltnp | grep -E ':8081\b'   # si hay algo, mirar el cwd del proceso antes de tocarlo: puede ser de otra sesión
grep -n '^server.port' src/main/resources/application-user-dev.properties   # tiene que decir 8081
./mvnw -o spring-boot:run -Dspring-boot.run.profiles=dev -DskipFlyway=true \
  "-Dspring-boot.run.arguments=--inventario.control-stock-negativo.poller.enabled=true --inventario.control-stock-negativo.poller.initial-delay=20000 --debug"
```

Expected en el log: `Started FrancoSystemsApplication`; Flyway aplica **solo** `243.1` (avisar a Franco: crea dos tablas en su `bodega` local); en el reporte de condiciones `ReplicationPublicationSyncScheduler` y `ReplicationRefreshScheduler` en **Negative matches** y `ControlStockNegativoScheduler` en Positive matches.

- [ ] **Step 2: Probar el poller con datos locales**

Tras el primer ciclo existe una fila de cursor por sucursal y la tabla sigue vacía (sin retroactivo):

```bash
psql -h localhost -p 5551 -U franco -d bodega -X -Atc "select count(*) from operaciones.control_stock_negativo_cursor; select count(*) from operaciones.control_stock_negativo;"
```

Simular que llegan 600 movimientos de la sucursal 1.

> ⚠️ **SOLO en la base local de esta máquina.** Este `UPDATE` no se corre nunca contra un servidor: en la VM de producción el puerto 5551 es el cluster de farmacia. Por eso la primera sentencia corta el script si la conexión no es local.

```bash
psql -h localhost -p 5551 -U franco -d bodega -X -At -v ON_ERROR_STOP=1 <<'SQL'
DO $$ BEGIN
  IF inet_server_addr() IS NOT NULL AND host(inet_server_addr()) NOT IN ('127.0.0.1', '::1') THEN
    RAISE EXCEPTION 'No es la base local: %', inet_server_addr();
  END IF;
END $$;
UPDATE operaciones.control_stock_negativo_cursor
   SET ultimo_movimiento_id = ultimo_movimiento_id - 1200,
       inicial_movimiento_id = inicial_movimiento_id - 1200
 WHERE sucursal_id = 1 AND ultimo_movimiento_id > 1200;
SQL
```

Expected: `UPDATE 1`. Se resta también a `inicial` porque si no el tramo queda cortado ahí. A los ~60 s: `ControlStockNegativoScheduler: N ventas registradas` en el log, filas `VENTA` en la tabla, y en el ciclo siguiente ningún movimiento repetido (`select sucursal_id, movimiento_stock_id, count(*) from operaciones.control_stock_negativo group by 1,2 having count(*) > 1` vacío). El hilo del log es `control-stock-negativo`, no el del scheduler compartido.

Sondeo horario: `select sucursal_id, ultimo_movimiento_id, actualizado_en from operaciones.control_stock_negativo_cursor where ultimo_movimiento_id = 0` — esas sucursales no cambian su `actualizado_en` entre dos ciclos seguidos.

- [ ] **Step 3: Probar el desktop** (`npm run ng:serve`, Chrome en `localhost:4200`, config apuntando al central **8081** — memoria «Chrome localhost:4200 apunta al alpha»: cambiar y restaurar al terminar)

- Inventario → «Control de stock negativo»: lista con las ventas del paso 2; probar los cuatro filtros y el paginador.
- Con `PRUEBANR` (601) sin el rol `VER INVENTARIO`: el botón no aparece.
- Transferencia abierta → agregar un producto con stock 0 en el origen: sale el diálogo; «Continuar» guarda el ítem y aparece una fila `TRANSFERENCIA` en la lista; cancelar no guarda.
- Producto con stock negativo: bloquea con la config en falso; con la config en verdadero, diálogo y registro.
- Transferencia con origen COMPRAS: ningún diálogo y ninguna fila nueva en la lista.

Elegir los productos con una consulta a la base local, no inventarlos.

- [ ] **Step 4: Probar la PWA** (`npm start`, `localhost:4300`, servidor = central local)

Recorrer los 5 casos del bloque agregado a `PLAN_TESTEO_MANUAL.md`.

- [ ] **Step 5: Auditoría del diff** (paso 8 del ciclo, §2.2) — tres fijos + Condicional B, con subagentes `sonnet` que reciben el diff, este plan, la skill de dominio y `gotchas.md`:
  - Fijo 1 — autorización y fuga de datos por el resolver (`controlStockNegativo`, tipo `Usuario` expuesto).
  - Fijo 2 — esquema, migración y espejo (central-only: confirmar que **no** necesita espejo en filial).
  - Fijo 3 — contrato con los clientes (query nueva; nada eliminado).
  - Condicional B — estado distribuido (el poller lee una tabla replicada).

- [ ] **Step 6: Batería completa** (paso 9)

Central: `./mvnw clean verify -B -DskipFlyway=true` → `BUILD SUCCESS`.
Desktop: `npm run verificar:imports && npm run build:prod && npm run electron:serve-tsc && xvfb-run -a npm run verificar:arranque`.
PWA: `npm test && npm run build`.

- [ ] **Step 7: Rebase y número de migración** (paso 10)

En los tres repos: `git fetch origin develop && git log --oneline HEAD..origin/develop`. Si hay commits, rebasear, **re-verificar `V243.1`** (Task 1, Step 1) y repetir el Step 6.

- [ ] **Step 8: Documentación** (paso 11)

Central: agregar a este plan una sección «Resultado» con lo que se desvió y lo que quedó sin verificar; agregar al `CLAUDE.md` del central una sección corta «Control de stock negativo» (tabla central-only, poller, kill switch `INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED`). Commit `docs(inventario): …`.

- [ ] **Step 9: Entrega** (paso 12)

Decirle a Franco dónde probar, con un caso real de su base. **Esperar su aprobación explícita y preguntar aparte si se abren los PRs.** Orden: central → desktop → PWA, todos draft contra `develop`. Mergear a `develop` del central **no despliega** alpha (`deploy-auto.yml` nunca corre): hace falta `gh workflow run Deploy`, que decide Franco.

## Qué queda sin verificar

- **Volumen en producción.** La medición es sobre una copia local con datos hasta abril–septiembre de 2026. Se verifica mirando `count(*)` por día en alpha tras el primer día de uso.
- **Costo de `buscar` con la tabla llena.** La tabla está vacía en local; el `like '%…%'` sobre la descripción junto con el `count` recorre todo el rango de fechas. Se mide en la Task 9 con lo que cargue el poller, y de nuevo en alpha.
- **Reloj de las cajas.** `creado_en` del movimiento sale de `VentaItem.creadoEn` en la filial; si el PDV manda su hora, el «stock previo» depende del reloj de la caja.
- **Desfase de reloj entre filial y central.** El stock previo ordena por `creado_en`; una transferencia (hora del central) y una venta (hora de la filial) a segundos de distancia pueden evaluarse en orden inverso. Impacto: un registro de más o de menos en el borde. No se corrige.
- **`venta_item` sin encontrar.** Si el Step 6 de la Task 4 da `con_venta < filas`, esas filas quedan sin número de venta.
- **App Android.** No se prueba: queda cubierta por el registro del central, sin aviso.
- **Deploy.** La property nueva tiene default en `application.properties`: no requiere crear la variable en los servidores.

## Rollback

Migración aditiva: un JAR anterior ignora las dos tablas. Para apagar solo el poller sin redeploy: `INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED=false` en el `.env` de la instancia y reiniciar. El registro de transferencias no tiene interruptor: está aislado por `try/catch` y transacción propia.

## Agregado durante la ejecución (2026-10-10): filtro por stock previo

Franco pidió, con la lista ya funcionando, un selector «stock 0 / stock negativo / todos».

- **Task 10 (central):** argumento opcional `stock: FiltroStockControl` (`CERO`, `NEGATIVO`; sin valor = todos) en la query `controlStockNegativo`, entre `texto` y `page`. Enum Java + `.graphqls` en el mismo commit. El filtro es `stock_previo = 0` o `stock_previo < 0`. Sin migración.
- **Task 11 (desktop):** selector «Stock previo» en la lista (Todos / Stock 0 / Stock negativo), enviado como `stock`.

Compatibilidad: el argumento es opcional; un desktop que no lo manda sigue funcionando contra el central nuevo. Un desktop nuevo contra un central sin el argumento falla solo en la lista (query inválida) — mismo orden de entrega que el resto: central primero.

| Dato | Escribe | Lee |
|---|---|---|
| argumento `stock` | `list-control-stock-negativo` (selector) | `ControlStockNegativoService.buscar` |

## Agregado durante la ejecución (2026-10-10): stock actual

Franco preguntó qué pasa con un registro cuando un inventario posterior corrige el stock. El registro es historial y no se borra; se decidió (opción 2) mostrar al lado el **stock actual**.

- **Task 12 (central):** campo calculado `stockActual: Float` en el tipo `ControlStockNegativo`, por resolver de campo (mismo cálculo que `stockPorProducto`). Sin migración.
- **Task 11 (desktop):** columna «Stock actual» (verde si ya es positivo, rojo si sigue en 0 o negativo); la columna existente pasa a llamarse «Stock al salir».

**Filtro por «ya regularizados»: no se implementa ahora** (criterio del controlador, delegado por Franco). Medido en la base local, filtrar por el stock actual cuesta ~11 ms por registro (2,7 s para 239 filas); con miles de registros por día la consulta tardaría minutos. Hacerlo bien requiere mantener el stock actual en una tabla (`operaciones.stock_por_producto_sucursal` existe pero está vacía en el central) o un índice nuevo sobre `movimiento_stock` (5,6 M de filas, tabla replicada). Queda como mejora posterior; con la columna, el equipo ya ve en cada fila si el caso sigue abierto.

| Dato | Escribe | Lee |
|---|---|---|
| campo `stockActual` | `ControlStockNegativoResolver.stockActual` (calculado) | columna «Stock actual» de `list-control-stock-negativo` |
