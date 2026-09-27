# Precio especial por sucursal: plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: `superpowers:subagent-driven-development` (recomendada) o
> `superpowers:executing-plans`. Los pasos usan checkboxes (`- [ ]`).

**Objetivo:** cargar desde el desktop precios especiales por sucursal con vigencia, incluidas las
promos por presentación (2x1), sin tocar a mano la base de las filiales.

**Arquitectura:**
- Tabla nueva `productos.precio_especial_sucursal`. La escribe el central y baja `MAIN_TO_ALL`.
- Cada filial la lee con **JDBC, fuera de Hibernate**, en `PresentacionResolver` y
  `ProductoResolver`.
- Si hay un especial vigente para su sucursal, la filial devuelve **copias no administradas**:
  - del precio, con el valor especial y `activo=true`;
  - de la presentación, con `activo=true`.
- El POS mantiene su lógica de selección. Hay dos arreglos puntuales en la grilla de favoritos, que
  guardaba precios en memoria.
- Los tickets imprimen `venta_item.precio` (lo cobrado).

**Stack:**
- central: Spring Boot 2.7, graphql-java-kickstart, JPA, Flyway, JUnit 5 + Mockito 4;
- filial: Spring Boot 2.1, JUnit 5 + **Mockito 2**;
- desktop: Angular 15 + Apollo + Material.

**Spec:** `docs/manuales-implementacion/productos/SPEC-PRECIO-ESPECIAL-SUCURSAL.md`, en este mismo
directorio.

| Pieza | Ruta | Rama |
|---|---|---|
| filial | `/home/franco/dev-frc/backend/franco-system-backend-filial` | `feature/productos-precio-especial-sucursal` desde `origin/develop` |
| central | `/home/franco/dev-frc/backend/franco-system-backend-servidor` (worktree `.claude/worktrees/precio-especial-sucursal`) | `feature/productos-precio-especial-sucursal` (creada, sin upstream) |
| desktop | `/home/franco/dev-frc/frontend/frc-sistemas-integrados-angular` | `feature/productos-precio-especial-sucursal` desde `origin/develop` |

Abajo, `J` es `src/main/java/com/franco/dev` y `R` es `src/main/resources` del repo de cada tarea.

## Restricciones globales

- **Migraciones con sufijo `.1`:** central `V232.1`, filial `V104.1`.
  - **Se revalida el número contra `origin/develop` antes de CADA push de fase**, en los dos repos
    (skill `flyway-migraciones-frc`).
  - La filial tiene `spring.flyway.out-of-order=true` (`application.properties:74`).
- **Espejo sin restricciones:** en la filial no hay FK, CHECK ni UNIQUE; todas viven en el
  central. La filial **nunca escribe** la tabla.
- **OSIV en los dos backends** (`OpenEntityManagerInViewFilter`):
  - **Nunca un setter** sobre una `PrecioPorSucursal` o `Presentacion` de la filial: se construye
    una copia.
  - **La lectura de especiales en la filial no pasa por JPA.** Si falla una consulta JPA, se
    revierte su transacción y se limpia el contexto compartido del request, y la venta revienta con
    `LazyInitializationException` aunque el error se haya atajado.
- **Una falla leyendo especiales no puede impedir vender.** Se atrapa, se loguea y se devuelven los
  precios originales.
- **Interruptor:** la property `precio.especial.habilitado=false` en una filial apaga la
  sustitución sin deploy.
- **Fecha: zona fija `-03:00`** (`ZoneOffset.ofHours(-3)`; Paraguay abolió el horario de verano).
  Se usa en el lector de la filial y en `filtrar` del central. No depende del tzdb de la JVM
  (gotcha «Timezone Paraguay»). Los días son inclusivos en los dos extremos.
  - Desktop: manda `yyyy-MM-dd` con `fechaParam` y lee con `stringToLocalDate`.
  - Central: parsea con `DateUtils.stringToLocalDate` y serializa `LocalDate` con el escalar
    `Date` (`yyyy-MM-dd 00:00`).
- **Roles:** `"CREAR PRECIOS"` y `"EDITAR PRECIOS"`, con espacio. En el desktop son
  `ROLES.CREAR_PRECIOS` y `ROLES.EDITAR_PRECIOS`. ADMIN (por rol o por nickname) pasa siempre.
- **Commits:** `feat(...)` o `fix(...)`. Para planes y documentación, `docs(...)`.
- **Una fase = commit + push de la rama de feature.** Antes de cada push:
  - build de la pieza, leído del log;
  - `git status` sin archivos ajenos a la fase;
  - número de migración revalidado.
- **PR:** solo con prueba y aprobación de Franco. **Merge: nunca desde esta sesión.**

## Foco de revisión

1. **Especial sobre un precio `principal=false` con el POS en modo NOT:** el POS lo ignora, porque
   elige `principal && activo`. El diálogo lo avisa con texto (D3). No hay test automático.
2. **Borrado del precio global:** la FK `ON DELETE CASCADE` borra sus especiales, incluido el
   historial, y replica el DELETE. Es aceptado porque el precio mismo desaparece. Queda
   documentado en la migración y se prueba a mano en L.
3. **Día de corte a las 00:00 -03:** está cubierto en F2 con un reloj fijo en zona -03.
4. **"Todas" + sucursales sueltas en el diálogo:** está cubierto en D1 (`idsSucursalesSeleccionadas`).
5. **Editar un especial cortado, y la superposición excluyendo la propia fila:** está cubierto en C3.

---

## PR 1 — filial (se mergea y despliega PRIMERO)

**Alcance al mergear:** `develop` llega a las filiales alpha, `release/beta` a las **6 de farmacia**
y `master` a las **18 de bodega**. En cada caso se despliega **en ≤15 min y sin aprobación**. Este PR
cambia también, en **todas** las sucursales y aunque la tabla esté vacía:
- los tickets (F3);
- el cálculo de `precioPrincipal` (F2).

Declararlo en la descripción del PR.

### Tarea F0: rama

- [ ] **Paso 1**

```bash
cd /home/franco/dev-frc/backend/franco-system-backend-filial
git status --short            # limpio; si no, avisar a Franco antes de seguir
git fetch -q origin
git switch -c feature/productos-precio-especial-sucursal origin/develop
git branch --unset-upstream
git ls-tree -r --name-only origin/develop src/main/resources/db/migration | sed 's|.*/V||;s|__.*||' | sort -t. -k1,1n -k2,2n | tail -1
# Esperado: 103.1 → la nuestra es V104.1
```

### Tarea F1: espejo de la tabla y fuente JDBC

**Archivos:**
- Crear: `R/db/migration/V104.1__espejo_precio_especial_sucursal.sql`
- Crear: `J/service/productos/PrecioEspecialFuente.java`

**Interfaces que produce:**
- `PrecioEspecialFuente.porPrecios(long sucursalId, Collection<Long> precioIds): List<Fila>`
- `PrecioEspecialFuente.porPresentaciones(long sucursalId, Collection<Long> presentacionIds): List<Fila>`
- `PrecioEspecialFuente.Fila(Long id, Long precioId, Long presentacionId, Double precio, LocalDate
  fechaDesde, LocalDate fechaHasta, Boolean activo)`: campos `final` con getters.

La filial **no** tiene entidad JPA para esta tabla. El único lector es esta fuente.

- [ ] **Paso 1: migración espejo**

```sql
-- =====================================================================
-- precio_especial_sucursal: espejo del precio especial por sucursal
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Las promos de una sola sucursal se hacian editando a mano precio_por_sucursal en la base de
-- la filial: el cambio no subia al central y un cambio posterior del central lo pisaba o lo
-- dejaba divergente. Esta tabla la escribe el central; cada filial toma las filas de su propia
-- sucursal y sustituye el precio al devolverlo (PrecioEspecialLector, que la lee por JDBC con
-- PrecioEspecialFuente). Ver central:docs/manuales-implementacion/productos/SPEC-PRECIO-ESPECIAL-SUCURSAL.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL (V232.1)
--
-- Es MAIN_TO_ALL. Una vez que el central la agrega a central_pub, una filial SIN esta tabla corta
-- TODA su replicacion entrante (el apply worker cae en bucle con "logical replication target
-- relation does not exist" al primer especial), no solo el REFRESH. Por eso se verifica en CADA
-- filial antes de publicarla. Para una filial inalcanzable, este DDL es idempotente y se puede
-- correr a mano.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismas columnas y tipos que el central, todas nullable. PK en id (la usa el apply para ubicar
-- la fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin seed: las restricciones viven en
-- el central. El filial nunca escribe esta tabla.
-- =====================================================================
CREATE TABLE IF NOT EXISTS productos.precio_especial_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    precio_id    BIGINT,
    sucursal_id  BIGINT,
    precio       NUMERIC,
    fecha_desde  DATE,
    fecha_hasta  DATE,
    activo       BOOLEAN,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_sucursal_precio
    ON productos.precio_especial_sucursal (sucursal_id, precio_id);

COMMENT ON TABLE productos.precio_especial_sucursal IS
    'Precio especial de un precio_por_sucursal en una sucursal, con vigencia. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee.';
COMMENT ON COLUMN productos.precio_especial_sucursal.activo IS
    'Solo TRUE aplica. false o NULL = cortado.';
COMMENT ON COLUMN productos.precio_especial_sucursal.fecha_desde IS
    'NULL = desde siempre. Inclusivo, en hora de Paraguay (-03).';
COMMENT ON COLUMN productos.precio_especial_sucursal.fecha_hasta IS
    'NULL = sin fin. Inclusivo, en hora de Paraguay (-03).';
```

- [ ] **Paso 2: fuente JDBC**

```java
package com.franco.dev.service.productos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Lee productos.precio_especial_sucursal por JDBC, fuera de Hibernate.
 * <p>
 * Con OpenEntityManagerInViewFilter, una consulta JPA que falla revierte su transaccion y limpia
 * el EntityManager compartido del request: los PrecioPorSucursal ya leidos quedan con sus LAZY sin
 * inicializar y la venta revienta aunque el error se ataje. JdbcTemplate toma su propia conexion
 * del pool (no hay transaccion activa en el resolver) y una falla aca no toca ese contexto.
 */
@Component
public class PrecioEspecialFuente {

    private static final String SELECT =
            "select e.id, e.precio_id, pps.presentacion_id, e.precio, e.fecha_desde, e.fecha_hasta, e.activo " +
            "from productos.precio_especial_sucursal e " +
            "join productos.precio_por_sucursal pps on pps.id = e.precio_id " +
            "where e.sucursal_id = :sucursalId and e.activo = true and ";

    private final NamedParameterJdbcTemplate jdbc;

    public PrecioEspecialFuente(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<Fila> porPrecios(long sucursalId, Collection<Long> precioIds) {
        if (precioIds == null || precioIds.isEmpty()) return Collections.emptyList();
        return jdbc.query(SELECT + "e.precio_id in (:ids)",
                new MapSqlParameterSource("sucursalId", sucursalId).addValue("ids", precioIds), PrecioEspecialFuente::fila);
    }

    public List<Fila> porPresentaciones(long sucursalId, Collection<Long> presentacionIds) {
        if (presentacionIds == null || presentacionIds.isEmpty()) return Collections.emptyList();
        return jdbc.query(SELECT + "pps.presentacion_id in (:ids)",
                new MapSqlParameterSource("sucursalId", sucursalId).addValue("ids", presentacionIds), PrecioEspecialFuente::fila);
    }

    private static Fila fila(ResultSet rs, int n) throws SQLException {
        return new Fila(rs.getLong("id"), rs.getLong("precio_id"), (Long) rs.getObject("presentacion_id", Long.class),
                rs.getObject("precio") != null ? rs.getDouble("precio") : null,
                rs.getObject("fecha_desde", LocalDate.class), rs.getObject("fecha_hasta", LocalDate.class),
                (Boolean) rs.getObject("activo", Boolean.class));
    }

    public static final class Fila {
        private final Long id;
        private final Long precioId;
        private final Long presentacionId;
        private final Double precio;
        private final LocalDate fechaDesde;
        private final LocalDate fechaHasta;
        private final Boolean activo;

        public Fila(Long id, Long precioId, Long presentacionId, Double precio, LocalDate fechaDesde,
                    LocalDate fechaHasta, Boolean activo) {
            this.id = id;
            this.precioId = precioId;
            this.presentacionId = presentacionId;
            this.precio = precio;
            this.fechaDesde = fechaDesde;
            this.fechaHasta = fechaHasta;
            this.activo = activo;
        }

        public Long getId() { return id; }
        public Long getPrecioId() { return precioId; }
        public Long getPresentacionId() { return presentacionId; }
        public Double getPrecio() { return precio; }
        public LocalDate getFechaDesde() { return fechaDesde; }
        public LocalDate getFechaHasta() { return fechaHasta; }
        public Boolean getActivo() { return activo; }
    }
}
```

> Verificar que el bean `JdbcTemplate` de `src/main/java/database/JdbcConfig.java` se registra.
> Hay que confirmar que ese paquete, fuera de `com.franco.dev`, está en el component scan o en un
> `@Import`. Si no se registra, igual existe el `JdbcTemplate` que auto-configura Spring Boot. En
> los dos casos va sobre el mismo `DataSource`.
> Comando: `grep -rn "JdbcConfig\|scanBasePackages\|ComponentScan" src/main/java | head`.

- [ ] **Paso 2b: prueba manual de la fuente en local, no en CI.** Correr la migración sobre la
  base local de la filial (se aplica al arrancar) y ejecutar el SELECT con `psql` contra `:5552`,
  reemplazando los parámetros. El resultado esperado es 0 filas y ningún error.
- [ ] **Paso 3:** `./mvnw -q -DskipTests -DskipFlyway=true compile`. Esperado: `BUILD SUCCESS`. No
  se commitea todavía: va con F2, porque una fuente sin lector no se implementa sola.

### Tarea F2: `PrecioEspecialLector` y los resolvers

**Archivos:**
- Crear: `J/service/productos/PrecioEspecialLector.java`
- Modificar: `J/graphql/productos/resolver/PresentacionResolver.java` (`precios`, `precioPrincipal`)
- Modificar: `J/graphql/productos/resolver/ProductoResolver.java` (`presentaciones`)
- Test: `src/test/java/com/franco/dev/service/productos/PrecioEspecialLectorTest.java`
- Test: `src/test/java/com/franco/dev/graphql/productos/resolver/PresentacionResolverTest.java`

**Interfaces:**
- Consume: F1 (`PrecioEspecialFuente`, `Fila`).
- Produce:
  - `List<PrecioPorSucursal> aplicar(List<PrecioPorSucursal>)`
  - `List<Presentacion> habilitarPresentaciones(List<Presentacion>)`
  - `static boolean esVigente(Fila, LocalDate)`
  - `static final ZoneId ZONA = ZoneOffset.ofHours(-3)`

- [ ] **Paso 1: test del lector, que tiene que fallar**

```java
package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.PrecioEspecialFuente.Fila;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PrecioEspecialLectorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);
    private PrecioEspecialFuente fuente;
    private Environment env;
    private PrecioEspecialLector lector;

    /** Reloj fijo a una hora dada de HOY en zona Paraguay. */
    private PrecioEspecialLector lectorA(int hora, int minuto) {
        Instant instante = HOY.atTime(hora, minuto).atOffset(ZoneOffset.ofHours(-3)).toInstant();
        return new PrecioEspecialLector(fuente, env, Clock.fixed(instante, ZoneOffset.UTC));
    }

    @BeforeEach
    public void setUp() {
        fuente = mock(PrecioEspecialFuente.class);
        env = mock(Environment.class);
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("precio.especial.habilitado", "true")).thenReturn("true");
        lector = lectorA(12, 0);
    }

    private static PrecioPorSucursal precio(Long id, double valor, boolean activo) {
        PrecioPorSucursal p = new PrecioPorSucursal();
        p.setId(id);
        p.setPrecio(valor);
        p.setActivo(activo);
        p.setPrincipal(true);
        return p;
    }

    private static Fila fila(Long id, Long precioId, Long presentacionId, double valor, LocalDate desde, LocalDate hasta, Boolean activo) {
        return new Fila(id, precioId, presentacionId, valor, desde, hasta, activo);
    }

    private void porPrecios(Fila... filas) {
        when(fuente.porPrecios(eq(7L), anyCollection())).thenReturn(Arrays.asList(filas));
    }

    @Test
    public void sinEspecialesDevuelveLosMismosObjetos() {
        PrecioPorSucursal p = precio(10L, 6000, true);
        porPrecios();
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
    }

    @Test
    public void especialVigenteDevuelveCopiaConPrecioYActivoSinTocarElOriginal() {
        PrecioPorSucursal p = precio(10L, 6000, false);
        porPrecios(fila(1L, 10L, 50L, 5000, null, null, true));
        PrecioPorSucursal r = lector.aplicar(Collections.singletonList(p)).get(0);
        assertNotSame(p, r);
        assertEquals(10L, r.getId());
        assertEquals(5000.0, r.getPrecio());
        assertTrue(r.getActivo());
        assertTrue(r.getPrincipal());
        assertEquals(6000.0, p.getPrecio(), "el original no se toca: OSIV lo flushearia");
        assertFalse(p.getActivo());
    }

    @Test
    public void losExtremosDeLaVigenciaSonInclusivos() {
        assertTrue(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, HOY, HOY, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, HOY.plusDays(1), null, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, HOY.minusDays(1), true), HOY));
    }

    @Test
    public void elDiaSeCortaALasCeroHorasDeParaguayNoDeLaJvm() {
        porPrecios(fila(1L, 10L, 50L, 5000, null, HOY, true));
        assertEquals(5000.0, lectorA(23, 59).aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio());
        // 00:30 del dia siguiente en Paraguay (03:30 UTC): ya no aplica
        Instant manana = HOY.plusDays(1).atTime(0, 30).atOffset(ZoneOffset.ofHours(-3)).toInstant();
        PrecioEspecialLector l = new PrecioEspecialLector(fuente, env, Clock.fixed(manana, ZoneOffset.UTC));
        assertEquals(6000.0, l.aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio());
    }

    @Test
    public void cortadoActivoNuloOPrecioNoPositivoNoAplica() {
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, null, false), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, null, null), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 0, null, null, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(new Fila(1L, 10L, 50L, null, null, null, true), HOY));
    }

    @Test
    public void conDosVigentesGanaElDeMenorId() {
        porPrecios(fila(9L, 10L, 50L, 4000, null, null, true), fila(3L, 10L, 50L, 5000, null, null, true));
        assertEquals(5000.0, lector.aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio());
    }

    @Test
    public void consultaSoloLaSucursalPropia() {
        porPrecios();
        lector.aplicar(Collections.singletonList(precio(10L, 6000, true)));
        verify(fuente).porPrecios(eq(7L), eq(Collections.singletonList(10L)));
    }

    @Test
    public void sinSucursalIdOConElInterruptorApagadoNoConsulta() {
        PrecioPorSucursal p = precio(10L, 6000, true);
        when(env.getProperty("sucursalId")).thenReturn(null);
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("precio.especial.habilitado", "true")).thenReturn("false");
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
        verifyZeroInteractions(fuente);   // Boot 2.1 trae Mockito 2: no hay verifyNoInteractions
    }

    @Test
    public void siLaLecturaFallaDevuelveLosPreciosOriginales() {
        when(fuente.porPrecios(anyLong(), anyCollection())).thenThrow(new RuntimeException("relation does not exist"));
        PrecioPorSucursal p = precio(10L, 6000, true);
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
    }

    @Test
    public void presentacionInactivaConEspecialVigenteSaleActivaEnCopiaYLasDemasNoSeTocan() {
        Presentacion dosPorUno = new Presentacion();
        dosPorUno.setId(50L);
        dosPorUno.setActivo(false);
        dosPorUno.setCantidad(2.0);
        Presentacion otraInactiva = new Presentacion();
        otraInactiva.setId(51L);
        otraInactiva.setActivo(false);
        Presentacion activa = new Presentacion();
        activa.setId(52L);
        activa.setActivo(true);
        when(fuente.porPresentaciones(eq(7L), anyCollection()))
                .thenReturn(Collections.singletonList(fila(1L, 20L, 50L, 6000, null, null, true)));
        List<Presentacion> r = lector.habilitarPresentaciones(Arrays.asList(dosPorUno, otraInactiva, activa));
        assertNotSame(dosPorUno, r.get(0));
        assertTrue(r.get(0).getActivo());
        assertEquals(2.0, r.get(0).getCantidad());
        assertFalse(dosPorUno.getActivo());
        assertSame(otraInactiva, r.get(1));
        assertSame(activa, r.get(2));
        // una sola consulta, solo con las inactivas
        verify(fuente).porPresentaciones(eq(7L), eq(Arrays.asList(50L, 51L)));
    }
}
```

- [ ] **Paso 2:** `./mvnw -q test -DskipFlyway=true -Dtest=PrecioEspecialLectorTest`. Esperado: falla
  de compilación, porque `PrecioEspecialLector` no existe.

- [ ] **Paso 3: el lector**

```java
package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.PrecioEspecialFuente.Fila;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aplica los precios especiales de ESTA sucursal a los precios y presentaciones que se devuelven
 * por GraphQL (SPEC-PRECIO-ESPECIAL-SUCURSAL.md en el central).
 * <p>
 * <b>Nunca modifica las entidades recibidas.</b> El filial tiene OpenEntityManagerInViewFilter:
 * una entidad devuelta por el repositorio sigue administrada todo el request y un
 * {@code @Transactional} posterior (saveVenta) flushearia un setter a la tabla global. Se
 * devuelven copias nuevas con el mismo id, asi venta_item.precio_id sigue apuntando al precio real.
 * <p>
 * <b>Una falla aca no puede impedir vender:</b> la lectura va por JDBC (PrecioEspecialFuente) y
 * cualquier excepcion devuelve lo recibido, es decir el precio global.
 * <p>
 * El dia se evalua en -03 fijo (Paraguay no tiene horario de verano), no en la zona de la JVM:
 * hay filiales con tzdb viejo que aplican -04 (gotcha "Timezone Paraguay").
 * <p>
 * {@code precio.especial.habilitado=false} apaga todo sin deploy.
 */
@Service
public class PrecioEspecialLector {

    private static final Logger log = LoggerFactory.getLogger(PrecioEspecialLector.class);
    public static final ZoneId ZONA = ZoneOffset.ofHours(-3);

    private final PrecioEspecialFuente fuente;
    private final Environment env;
    private final Clock clock;

    @Autowired
    public PrecioEspecialLector(PrecioEspecialFuente fuente, Environment env) {
        this(fuente, env, Clock.systemUTC());
    }

    PrecioEspecialLector(PrecioEspecialFuente fuente, Environment env, Clock clock) {
        this.fuente = fuente;
        this.env = env;
        this.clock = clock;
    }

    @PostConstruct
    void informar() {
        log.info("Precio especial: sucursalId={}, habilitado={}, hoy={} (zona {})",
                sucursalIdPropia(), habilitado(), hoy(), ZONA);
    }

    public List<PrecioPorSucursal> aplicar(List<PrecioPorSucursal> precios) {
        if (precios == null || precios.isEmpty()) return precios;
        Long sucursalId = sucursalIdPropia();
        if (sucursalId == null || !habilitado()) return precios;
        try {
            List<Long> ids = precios.stream().filter(Objects::nonNull).map(PrecioPorSucursal::getId)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (ids.isEmpty()) return precios;
            Map<Long, Fila> vigentes = vigentesPorClave(fuente.porPrecios(sucursalId, ids), Fila::getPrecioId);
            if (vigentes.isEmpty()) return precios;
            List<PrecioPorSucursal> resultado = new ArrayList<>(precios.size());
            for (PrecioPorSucursal p : precios) {
                Fila e = p != null ? vigentes.get(p.getId()) : null;
                resultado.add(e != null ? copiaConEspecial(p, e) : p);
            }
            return resultado;
        } catch (Exception ex) {
            log.error("No se pudieron aplicar los precios especiales; se usan los globales: {}", ex.getMessage(), ex);
            return precios;
        }
    }

    /** Presentaciones inactivas con algun precio con especial vigente aca salen como copias activas. */
    public List<Presentacion> habilitarPresentaciones(List<Presentacion> presentaciones) {
        if (presentaciones == null || presentaciones.isEmpty()) return presentaciones;
        Long sucursalId = sucursalIdPropia();
        if (sucursalId == null || !habilitado()) return presentaciones;
        List<Long> inactivas = presentaciones.stream()
                .filter(p -> p != null && p.getId() != null && !Boolean.TRUE.equals(p.getActivo()))
                .map(Presentacion::getId).collect(Collectors.toList());
        if (inactivas.isEmpty()) return presentaciones;
        try {
            Set<Long> habilitadas = vigentesPorClave(fuente.porPresentaciones(sucursalId, inactivas), Fila::getPresentacionId).keySet();
            if (habilitadas.isEmpty()) return presentaciones;
            List<Presentacion> resultado = new ArrayList<>(presentaciones.size());
            for (Presentacion p : presentaciones) {
                resultado.add(p != null && habilitadas.contains(p.getId()) && !Boolean.TRUE.equals(p.getActivo())
                        ? copiaActiva(p) : p);
            }
            return resultado;
        } catch (Exception ex) {
            log.error("No se pudieron evaluar presentaciones con precio especial: {}", ex.getMessage(), ex);
            return presentaciones;
        }
    }

    private Map<Long, Fila> vigentesPorClave(List<Fila> filas, java.util.function.Function<Fila, Long> clave) {
        LocalDate hoy = hoy();
        Map<Long, Fila> vigentes = new HashMap<>();
        for (Fila f : filas) {
            if (!esVigente(f, hoy) || clave.apply(f) == null) continue;
            Fila actual = vigentes.get(clave.apply(f));
            // Dos vigentes para el mismo precio no deberian existir (el central lo valida); si una
            // carrera los deja, gana el de menor id para que todas las cajas coincidan.
            if (actual == null || f.getId() < actual.getId()) vigentes.put(clave.apply(f), f);
        }
        return vigentes;
    }

    static boolean esVigente(Fila f, LocalDate hoy) {
        return f != null && f.getId() != null && f.getPrecioId() != null
                && Boolean.TRUE.equals(f.getActivo())
                && f.getPrecio() != null && f.getPrecio() > 0
                && (f.getFechaDesde() == null || !f.getFechaDesde().isAfter(hoy))
                && (f.getFechaHasta() == null || !f.getFechaHasta().isBefore(hoy));
    }

    private LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA));
    }

    private boolean habilitado() {
        return !"false".equalsIgnoreCase(String.valueOf(env.getProperty("precio.especial.habilitado", "true")).trim());
    }

    private static PrecioPorSucursal copiaConEspecial(PrecioPorSucursal o, Fila e) {
        return new PrecioPorSucursal(o.getId(), o.getPrincipal(), o.getPresentacion(), o.getTipoPrecio(),
                o.getSucursal(), e.getPrecio(), o.getCreadoEn(), o.getUsuario(), true);
    }

    private static Presentacion copiaActiva(Presentacion o) {
        return new Presentacion(o.getId(), o.getDescripcion(), o.getCantidad(), true, o.getPrincipal(),
                o.getCreadoEn(), o.getProducto(), o.getTipoPresentacion(), o.getUsuario());
    }

    private Long sucursalIdPropia() {
        String valor = env.getProperty("sucursalId");
        if (valor == null) return null;
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

> Los constructores de las copias usan el orden de campos verificado:
> - `PrecioPorSucursal(id, principal, presentacion, tipoPrecio, sucursal, precio, creadoEn, usuario, activo)`
> - `Presentacion(id, descripcion, cantidad, activo, principal, creadoEn, producto, tipoPresentacion, usuario)`
>
> Si alguien agrega un campo a esas entidades, la compilación rompe acá, y es a propósito.

- [ ] **Paso 4:** `./mvnw -q test -DskipFlyway=true -Dtest=PrecioEspecialLectorTest`. Esperado: 10
  tests, 0 fallos.

- [ ] **Paso 5: test de `precioPrincipal`, que tiene que fallar.** Hoy el método llama a la query
  nativa y no pasa por el lector.

```java
package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioEspecialLector;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PresentacionResolverTest {

    @Mock private ImageService imageService;
    @Mock private CodigoService codigoService;
    @Mock private PrecioPorSucursalService precioPorSucursalService;
    @Mock private PrecioEspecialLector precioEspecialLector;
    @InjectMocks private PresentacionResolver resolver;

    @BeforeEach
    void setUp() { MockitoAnnotations.initMocks(this); }

    private static PrecioPorSucursal precio(long id, double valor, boolean principal) {
        PrecioPorSucursal p = new PrecioPorSucursal();
        p.setId(id);
        p.setPrecio(valor);
        p.setPrincipal(principal);
        p.setActivo(true);
        return p;
    }

    @Test
    void preciosPasaPorElLectorYPrincipalSaleDeLaListaResuelta() {
        Presentacion pr = new Presentacion();
        pr.setId(50L);
        List<PrecioPorSucursal> globales = Arrays.asList(precio(11L, 7000, false), precio(10L, 6000, true));
        List<PrecioPorSucursal> resueltos = Arrays.asList(precio(11L, 7000, false), precio(10L, 5000, true));
        when(precioPorSucursalService.findByPresentacionId(50L)).thenReturn(globales);
        when(precioEspecialLector.aplicar(globales)).thenReturn(resueltos);

        assertSame(resueltos, resolver.precios(pr));
        PrecioPorSucursal principal = resolver.precioPrincipal(pr);
        assertEquals(10L, principal.getId());
        assertEquals(5000.0, principal.getPrecio());
        verify(precioPorSucursalService, never()).findPrincipalByPrecionacionId(anyLong());
    }
}
```

`anyLong` sale de `org.mockito.ArgumentMatchers.anyLong`. Hay que agregarlo al import estático.

- [ ] **Paso 6: conectar los resolvers.**

En `PresentacionResolver`, agregar el campo y reemplazar los dos métodos:

```java
    @Autowired
    private PrecioEspecialLector precioEspecialLector;

    public List<PrecioPorSucursal> precios(Presentacion p){
        return precioEspecialLector.aplicar(precioPorSucursalService.findByPresentacionId(p.getId()));
    }

    /**
     * El principal sale de la lista ya resuelta, para que refleje el precio especial. La query
     * nativa findPrincipalByPresentacionId hacia select * sobre un join presentacion/precio con
     * columnas homonimas (id, activo, principal): el id mapeado podia ser el de la presentacion.
     */
    public PrecioPorSucursal precioPrincipal(Presentacion p){
        for (PrecioPorSucursal precio : precios(p)) {
            if (precio != null && Boolean.TRUE.equals(precio.getPrincipal())) return precio;
        }
        return null;
    }
```

En `ProductoResolver`, cambiar el método `presentaciones`:

```java
    @Autowired
    private PrecioEspecialLector precioEspecialLector;

    public List<Presentacion> presentaciones(Producto p){
        return precioEspecialLector.habilitarPresentaciones(presentacionService.findByProductoId(p.getId()));
    }
```

- [ ] **Paso 7:** correr `./mvnw -q clean verify -B -DskipFlyway=true 2>&1 | tail -30`. Esperado:
  `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Paso 8: revalidar la migración, commitear y pushear (fase F1-F2)**

```bash
git fetch -q origin develop
git ls-tree -r --name-only origin/develop src/main/resources/db/migration | sed 's|.*/V||;s|__.*||' | sort -t. -k1,1n -k2,2n | tail -1
# si es >= 104, renombrar la nuestra al entero siguiente con git mv y avisar a Franco
git add src/main/resources/db/migration/V104.1__espejo_precio_especial_sucursal.sql \
  src/main/java/com/franco/dev/service/productos/PrecioEspecialFuente.java \
  src/main/java/com/franco/dev/service/productos/PrecioEspecialLector.java \
  src/main/java/com/franco/dev/graphql/productos/resolver/PresentacionResolver.java \
  src/main/java/com/franco/dev/graphql/productos/resolver/ProductoResolver.java \
  src/test/java/com/franco/dev/service/productos/PrecioEspecialLectorTest.java \
  src/test/java/com/franco/dev/graphql/productos/resolver/PresentacionResolverTest.java
git status --short   # nada más
git commit -m "feat(productos): precio especial por sucursal resuelto en la filial"
git push -u origin feature/productos-precio-especial-sucursal
```

### Tarea F3: los tickets imprimen el precio cobrado

**Archivos:**
- Crear: `J/service/operaciones/PrecioCobrado.java`
- Test: `src/test/java/com/franco/dev/service/operaciones/PrecioCobradoTest.java`
- Test: `src/test/java/com/franco/dev/graphql/operaciones/resolver/VentaItemResolverTest.java`
- Modificar las siguientes líneas de `develop` (re-ubicarlas con `grep -n "getPrecioVenta().getPrecio()"`):
  - `J/graphql/operaciones/VentaGraphQL.java` 659 y 661 (`printTicket58mm`)
  - `J/graphql/financiero/FacturaLegalGraphQL.java` 779, 781 y 790 (`generarFacturaAutoImpreso`)
  - `J/graphql/financiero/VentaCreditoGraphQL.java` 274, 275 y 283 (`printTicket58mm`)
  - `J/graphql/operaciones/resolver/VentaItemResolver.java` 27 (`valorTotal`)
- **No tocar:**
  - `VentaGraphQL.itemsFacturaSilenciosa` (L364, #144);
  - `VentaItemService.saveAndSend` (L104);
  - `ImpresionService` 798-807 y `VentaResolver` L55, que son código comentado.

**Interfaces:** produce `static Double PrecioCobrado.de(VentaItem vi)`, que devuelve el precio
unitario **bruto** cobrado. En el cliente, `vi.precio` es bruto y `valorDescuento` va aparte, así que
el descuento se sigue restando donde ya se restaba.

- [ ] **Paso 1: tests que tienen que fallar.**
  - `PrecioCobradoTest`: el que falla por compilación.
  - `VentaItemResolverTest`: el que falla **por comportamiento** con el código viejo (da 12000).

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrecioCobradoTest {

    static VentaItem item(Double cobrado, Double lista) {
        VentaItem vi = new VentaItem();
        vi.setPrecio(cobrado);
        if (lista != null) {
            PrecioPorSucursal p = new PrecioPorSucursal();
            p.setPrecio(lista);
            vi.setPrecioVenta(p);
        }
        return vi;
    }

    @Test
    public void usaLoCobradoAunqueLaListaSeaOtra() {
        assertEquals(5000.0, PrecioCobrado.de(item(5000.0, 6000.0)));
    }

    @Test
    public void ventaViejaSinPrecioCobradoCaeALaLista() {
        assertEquals(6000.0, PrecioCobrado.de(item(null, 6000.0)));
    }

    @Test
    public void sinNingunoDevuelveNull() {
        assertNull(PrecioCobrado.de(item(null, null)));
        assertNull(PrecioCobrado.de(null));
    }
}
```

```java
package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VentaItemResolverTest {

    @Test
    void valorTotalUsaElPrecioCobradoYNoElDeListaVigente() {
        VentaItem vi = new VentaItem();
        vi.setPrecio(5000.0);
        vi.setCantidad(2.0);
        PrecioPorSucursal lista = new PrecioPorSucursal();
        lista.setPrecio(6000.0);
        vi.setPrecioVenta(lista);
        assertEquals(10000.0, new VentaItemResolver().valorTotal(vi));   // el codigo viejo da 12000
    }
}
```

- [ ] **Paso 2:** `./mvnw -q test -DskipFlyway=true -Dtest='PrecioCobradoTest,VentaItemResolverTest'`.
  Esperado: `PrecioCobradoTest` no compila. Si se comenta ese archivo, `VentaItemResolverTest` falla
  con `expected 10000.0 but was 12000.0`.

- [ ] **Paso 3: implementación**

```java
package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;

/**
 * Precio unitario bruto que se cobro en un item de venta.
 * <p>
 * {@code venta_item.precio} es lo que el POS cobro. {@code precioVenta.precio} es el precio de
 * lista VIGENTE: puede haber cambiado despues de la venta, o no coincidir con lo cobrado si la
 * sucursal tenia un precio especial. Solo las ventas viejas sin {@code precio} caen a la lista.
 * El descuento no se resta aca: cada ticket lo sigue restando como antes.
 */
public final class PrecioCobrado {

    private PrecioCobrado() {
    }

    public static Double de(VentaItem vi) {
        if (vi == null) return null;
        if (vi.getPrecio() != null) return vi.getPrecio();
        return vi.getPrecioVenta() != null ? vi.getPrecioVenta().getPrecio() : null;
    }
}
```

- [ ] **Paso 4: reemplazos.** En cada línea listada, `vi.getPrecioVenta().getPrecio()` pasa a
  `PrecioCobrado.de(vi)`. En `VentaItemResolver`, `v.getPrecioVenta().getPrecio()` pasa a
  `PrecioCobrado.de(v)`. Agregar el import `com.franco.dev.service.operaciones.PrecioCobrado`. El
  resto de cada expresión no cambia, incluido `- vi.getValorDescuento()`. Ejemplo:

```java
                String valorUnitario = df
                        .format(PrecioCobrado.de(vi).intValue() - vi.getValorDescuento().intValue());
                String valorTotal = String.valueOf(
                        df.format((PrecioCobrado.de(vi).intValue() - vi.getValorDescuento().intValue())
                                * vi.getCantidad().doubleValue()));
```

  Verificación: `grep -rn "getPrecioVenta()\.getPrecio()" src/main/java`. Solo pueden quedar:
  - `VentaGraphQL` (itemsFacturaSilenciosa);
  - `VentaItemService:104`;
  - `ImpresionService` (comentado);
  - `VentaResolver` (comentado);
  - `PrecioCobrado`.

- [ ] **Paso 5:** `./mvnw -q clean verify -B -DskipFlyway=true 2>&1 | tail -30`. Esperado:
  `BUILD SUCCESS`.

- [ ] **Paso 6: revalidar la migración (como F2 Paso 8), commitear y pushear**

```bash
git add src/main/java/com/franco/dev/service/operaciones/PrecioCobrado.java \
  src/test/java/com/franco/dev/service/operaciones/PrecioCobradoTest.java \
  src/test/java/com/franco/dev/graphql/operaciones/resolver/VentaItemResolverTest.java \
  src/main/java/com/franco/dev/graphql/operaciones/VentaGraphQL.java \
  src/main/java/com/franco/dev/graphql/financiero/FacturaLegalGraphQL.java \
  src/main/java/com/franco/dev/graphql/financiero/VentaCreditoGraphQL.java \
  src/main/java/com/franco/dev/graphql/operaciones/resolver/VentaItemResolver.java
git status --short
git commit -m "fix(ventas): los tickets imprimen el precio cobrado y no el de lista vigente"
git push
```

---

## PR 2 — central (se despliega DESPUÉS del checklist de filiales de su canal)

Worktree: `/home/franco/dev-frc/backend/franco-system-backend-servidor/.claude/worktrees/precio-especial-sucursal`.

### Tarea C1: migración, entidad y repositorio

**Archivos:**
- Crear: `R/db/migration/V232.1__precio_especial_sucursal.sql`
- Crear: `J/domain/productos/PrecioEspecialSucursal.java`
- Crear: `J/repository/productos/PrecioEspecialSucursalRepository.java`

**Interfaces que produce:**
- La entidad `PrecioEspecialSucursal`, con los campos:
  - `id`
  - `precioPorSucursal` (`@ManyToOne`, columna `precio_id`)
  - `sucursal`
  - `precio` (Double)
  - `fechaDesde`, `fechaHasta` (LocalDate)
  - `activo`
  - `usuario`
  - `creadoEn`
  - y el método `getUsuarioNickname()`.
- En el repositorio:
  - `findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(Long, Long)`
  - `findByPrecioPorSucursalIdOrderByIdDesc(Long)`
  - `filtrar(Long sucursalId, String texto, boolean soloVigentes, LocalDate hoy, Pageable)`

- [ ] **Paso 1: migración**

```sql
-- =====================================================================
-- precio_especial_sucursal: precio especial por sucursal con vigencia
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Las promos de una sola sucursal (Heineken a 5000 en la 1; 2x1 solo en algunas) se hacian
-- editando a mano precio_por_sucursal en la base de la filial. precio_por_sucursal.sucursal_id
-- es decorativo (el resolver lo pisa con la property, 0) y su UNIQUE (presentacion_id,
-- tipo_precio_id) no admite un precio por sucursal. Esta tabla guarda el especial aparte: cada
-- filial toma los de su sucursal y sustituye el valor al devolver el precio. Ver
-- docs/manuales-implementacion/productos/SPEC-PRECIO-ESPECIAL-SUCURSAL.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V104.1) VA ANTES, EN CADA FILIAL
--
-- Es MAIN_TO_ALL. Apenas entra a central_pub, una filial SIN la tabla corta TODA su replicacion
-- entrante al primer especial (apply worker en bucle: "logical replication target relation does
-- not exist"). En alpha el alta a la publicacion es AUTOMATICA ~2 min despues del arranque
-- (ReplicationPublicationSyncScheduler, REPLICATION_SYNC_ENABLED=true): el checklist de filiales
-- va ANTES del deploy. En farmacia/bodega el alta es manual y NO se usa el boton "Sincronizar
-- publicaciones" (publica todas las pendientes y solo refresca filiales con IP cargada): ver el
-- plan, seccion Despliegue.
--
-- copy_data=false: un especial cargado antes de que una filial refresque su suscripcion NO le
-- llega nunca. Ningun especial se carga hasta verificar srsubstate='r' en todas.
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA: ver V231.1 (mismo criterio).
--
-- ESTE ES EL LADO PUBLISHER: las restricciones viven aca. La superposicion de vigencias se valida
-- en PrecioEspecialSucursalService (EXCLUDE pediria btree_gist en cada base).
-- ON DELETE CASCADE desde precio_por_sucursal: borrar un precio borra sus especiales (y su
-- historial) y replica el DELETE. Aceptado: el precio mismo deja de existir.
-- =====================================================================
SET lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS productos.precio_especial_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    precio_id    BIGINT    NOT NULL,
    sucursal_id  BIGINT    NOT NULL,
    precio       NUMERIC   NOT NULL,
    fecha_desde  DATE,
    fecha_hasta  DATE,
    activo       BOOLEAN   NOT NULL DEFAULT TRUE,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_precio_especial_sucursal_precio FOREIGN KEY (precio_id)
        REFERENCES productos.precio_por_sucursal (id) ON DELETE CASCADE,
    CONSTRAINT fk_precio_especial_sucursal_sucursal FOREIGN KEY (sucursal_id)
        REFERENCES empresarial.sucursal (id),
    CONSTRAINT fk_precio_especial_sucursal_usuario FOREIGN KEY (usuario_id)
        REFERENCES personas.usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_precio_especial_sucursal_precio CHECK (precio > 0),
    CONSTRAINT ck_precio_especial_sucursal_rango CHECK (
        fecha_desde IS NULL OR fecha_hasta IS NULL OR fecha_hasta >= fecha_desde),
    CONSTRAINT ck_precio_especial_sucursal_no_central CHECK (sucursal_id <> 0)
);

CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_sucursal_precio
    ON productos.precio_especial_sucursal (sucursal_id, precio_id);
CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_precio
    ON productos.precio_especial_sucursal (precio_id);

COMMENT ON TABLE productos.precio_especial_sucursal IS
    'Precio especial de un precio_por_sucursal en una sucursal, con vigencia opcional (dias inclusivos, -03). MAIN_TO_ALL: cada filial aplica solo los de su sucursal. Corte de emergencia: UPDATE ... SET activo=false WHERE activo.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('productos.precio_especial_sucursal', 'MAIN_TO_ALL', 'Precio especial por sucursal', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
```

> Verificar que `SET lock_timeout` no rompa Flyway en el central: Flyway corre cada migración en
> una transacción y `SET` vale hasta el fin de la sesión. Buscar un antecedente con
> `grep -rln "lock_timeout" src/main/resources/db/migration`. Si no hay, usar `SET LOCAL
> lock_timeout = '5s';`, que dura solo lo que la transacción de la migración.

- [ ] **Paso 2: entidad**

```java
package com.franco.dev.domain.productos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Precio especial de un {@link PrecioPorSucursal} en una sucursal, con vigencia opcional.
 * Lo escribe solo el central; baja MAIN_TO_ALL y cada filial lo aplica al devolver el precio.
 * El central nunca lo aplica: la ficha del producto muestra siempre el precio global.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "precio_especial_sucursal", schema = "productos")
public class PrecioEspecialSucursal implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "precio_id", nullable = false)
    private PrecioPorSucursal precioPorSucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "precio", nullable = false)
    private Double precio;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Solo el nickname: el tipo Usuario completo trae password. */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
```

- [ ] **Paso 3: repositorio**

```java
package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PrecioEspecialSucursalRepository extends HelperRepository<PrecioEspecialSucursal, Long> {

    default Class<PrecioEspecialSucursal> getEntityClass() {
        return PrecioEspecialSucursal.class;
    }

    List<PrecioEspecialSucursal> findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(Long precioId, Long sucursalId);

    List<PrecioEspecialSucursal> findByPrecioPorSucursalIdOrderByIdDesc(Long precioId);

    @Query(value = "select e from PrecioEspecialSucursal e " +
            "join e.precioPorSucursal pps join pps.presentacion pr join pr.producto prod " +
            "where (:sucursalId is null or e.sucursal.id = :sucursalId) " +
            "and (:texto is null or UPPER(prod.descripcion) like %:texto%) " +
            "and (:soloVigentes = false or (e.activo = true " +
            "     and (e.fechaDesde is null or e.fechaDesde <= :hoy) " +
            "     and (e.fechaHasta is null or e.fechaHasta >= :hoy))) " +
            "order by e.id desc",
            countQuery = "select count(e) from PrecioEspecialSucursal e " +
                    "join e.precioPorSucursal pps join pps.presentacion pr join pr.producto prod " +
                    "where (:sucursalId is null or e.sucursal.id = :sucursalId) " +
                    "and (:texto is null or UPPER(prod.descripcion) like %:texto%) " +
                    "and (:soloVigentes = false or (e.activo = true " +
                    "     and (e.fechaDesde is null or e.fechaDesde <= :hoy) " +
                    "     and (e.fechaHasta is null or e.fechaHasta >= :hoy)))")
    Page<PrecioEspecialSucursal> filtrar(@Param("sucursalId") Long sucursalId,
                                         @Param("texto") String texto,
                                         @Param("soloVigentes") boolean soloVigentes,
                                         @Param("hoy") LocalDate hoy,
                                         Pageable pageable);
}
```

- [ ] **Paso 4:** `./mvnw -q -DskipTests -DskipFlyway=true compile`. Esperado: `BUILD SUCCESS`. Sin
  commit: va junto con C4.

### Tarea C2: `PrecioSecurityService`

**Archivos:**
- Crear: `J/service/productos/PrecioSecurityService.java`
- Test: `src/test/java/com/franco/dev/service/productos/PrecioSecurityServiceTest.java`

**Interfaces que produce:**
- `Usuario currentUsuario()`
- `boolean hasAnyRole(String...)`
- `void requireGestionar()`, que lanza `graphql.GraphQLException`
- las constantes `CREAR = "CREAR PRECIOS"`, `EDITAR = "EDITAR PRECIOS"` y `ADMIN = "ADMIN"`

- [ ] **Paso 1: test que falla.** Sigue el mismo patrón que `FacturacionSecurityServiceTest`.

```java
package com.franco.dev.service.productos;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PrecioSecurityServiceTest {

    @Mock private UsuarioService usuarioService;
    @Mock private RoleService roleService;
    @InjectMocks private PrecioSecurityService seg;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() { mocks = MockitoAnnotations.openMocks(this); }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private void autenticar(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList()));
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(usuario));
        List<Role> lista = Arrays.stream(roles).map(n -> { Role r = new Role(); r.setNombre(n); return r; })
                .collect(Collectors.toList());
        when(roleService.findByUsuarioId(7L)).thenReturn(lista);
    }

    @Test
    void conCrearPreciosPasa() {
        autenticar("ana", "CREAR PRECIOS");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void conEditarPreciosEnMinusculaPasa() {
        autenticar("ana", "editar precios ");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void adminPasaSinRolesDePrecio() {
        autenticar("ana", "ADMIN");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void sinRolDePrecioSeRechaza() {
        autenticar("ana", "VENTA TOUCH");
        GraphQLException e = assertThrows(GraphQLException.class, () -> seg.requireGestionar());
        assertTrue(e.getMessage().contains("CREAR PRECIOS"));
    }

    @Test
    void sinAutenticacionSeRechaza() {
        assertThrows(GraphQLException.class, () -> seg.requireGestionar());
    }
}
```

- [ ] **Paso 2:** `./mvnw -q test -DskipFlyway=true -Dtest=PrecioSecurityServiceTest`. Esperado:
  falla de compilación.

- [ ] **Paso 3: implementación.** Tiene la forma de `FacturacionSecurityService`.

```java
package com.franco.dev.service.productos;

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
 * Seguridad a mano de las escrituras de precios especiales, como en todo este repo: no hay
 * {@code @PreAuthorize} aplicado y {@code @AdminSecured} esta roto (issue #177). Roles con espacio,
 * los mismos que usa el desktop (ROLES.CREAR_PRECIOS / ROLES.EDITAR_PRECIOS).
 */
@Service
public class PrecioSecurityService {

    public static final String ADMIN = "ADMIN";
    public static final String CREAR = "CREAR PRECIOS";
    public static final String EDITAR = "EDITAR PRECIOS";

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public Usuario currentUsuario() {
        String nick = currentNickname();
        if (nick == null) return null;
        return usuarioService.findByNickname(nick).orElse(null);
    }

    private Set<String> currentRoles() {
        Usuario u = currentUsuario();
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;
        for (String r : roles) {
            if (r != null && mine.contains(r.trim().toUpperCase())) return true;
        }
        return false;
    }

    /** Crear, editar o cortar un precio especial. */
    public void requireGestionar() {
        if (!hasAnyRole(CREAR, EDITAR)) {
            throw new GraphQLException("No autorizado: se requiere el rol " + CREAR + " o " + EDITAR + " para esta acción.");
        }
    }
}
```

- [ ] **Paso 4:** `./mvnw -q test -DskipFlyway=true -Dtest=PrecioSecurityServiceTest`. Esperado:
  5 tests, 0 fallos.

### Tarea C3: `PrecioEspecialSucursalService`

**Archivos:**
- Crear: `J/graphql/productos/input/PrecioEspecialSucursalInput.java`
- Crear: `J/service/productos/PrecioEspecialSucursalService.java`
- Test: `src/test/java/com/franco/dev/service/productos/PrecioEspecialSucursalServiceTest.java`

**Interfaces:**
- **Consume:**
  - C1;
  - `PrecioPorSucursalService.findById(Long)`. Devuelve `null` si el id es null.
  - `SucursalService.findById(Long)`.
- **Produce:**
  - `@Transactional` (Spring):
    - `crear(PrecioEspecialSucursalInput, Usuario): List<PrecioEspecialSucursal>`
    - `editar(Long, Double, String, String, Usuario): PrecioEspecialSucursal`
    - `cortar(Long, Usuario): PrecioEspecialSucursal`
  - `porPrecio(Long): List<PrecioEspecialSucursal>`
  - `filtrar(Long, String, Boolean, int, int): Page<PrecioEspecialSucursal>`
  - `static boolean seSuperponen(LocalDate, LocalDate, LocalDate, LocalDate)`

- [ ] **Paso 1: input**

```java
package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.List;

@Data
public class PrecioEspecialSucursalInput {
    private Long precioId;
    private List<Long> sucursalIds;
    private Double precio;
    /** yyyy-MM-dd o null. */
    private String fechaDesde;
    /** yyyy-MM-dd o null. */
    private String fechaHasta;
}
```

- [ ] **Paso 2: test que falla**

```java
package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.repository.productos.PrecioEspecialSucursalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrecioEspecialSucursalServiceTest {

    private PrecioEspecialSucursalRepository repository;
    private PrecioPorSucursalService precioService;
    private SucursalService sucursalService;
    private PrecioEspecialSucursalService service;
    private PrecioPorSucursal heineken;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(PrecioEspecialSucursalRepository.class);
        precioService = mock(PrecioPorSucursalService.class);
        sucursalService = mock(SucursalService.class);
        service = new PrecioEspecialSucursalService(repository, precioService, sucursalService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        heineken = new PrecioPorSucursal();
        heineken.setId(10L);
        heineken.setPrecio(6000.0);
        when(precioService.findById(10L)).thenReturn(Optional.of(heineken));
        for (long id : new long[]{1L, 3L}) {
            Sucursal s = new Sucursal();
            s.setId(id);
            s.setNombre("SUC " + id);
            when(sucursalService.findById(id)).thenReturn(Optional.of(s));
        }
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(any(), any())).thenReturn(Collections.emptyList());
        autor = new Usuario();
        autor.setId(3L);
    }

    private static PrecioEspecialSucursalInput input(Double precio, String desde, String hasta, Long... sucursales) {
        PrecioEspecialSucursalInput in = new PrecioEspecialSucursalInput();
        in.setPrecioId(10L);
        in.setPrecio(precio);
        in.setFechaDesde(desde);
        in.setFechaHasta(hasta);
        in.setSucursalIds(Arrays.asList(sucursales));
        return in;
    }

    private static PrecioEspecialSucursal existente(Long id, Long sucursalId, LocalDate desde, LocalDate hasta) {
        PrecioEspecialSucursal e = new PrecioEspecialSucursal();
        e.setId(id);
        Sucursal s = new Sucursal();
        s.setId(sucursalId);
        s.setNombre("SUC " + sucursalId);
        e.setSucursal(s);
        e.setFechaDesde(desde);
        e.setFechaHasta(hasta);
        e.setActivo(true);
        e.setPrecio(5500.0);
        return e;
    }

    @Test
    void creaUnaFilaPorSucursalConAutorYFechas() {
        List<PrecioEspecialSucursal> r = service.crear(input(5000.0, "2026-10-01", "2026-10-31", 1L, 3L), autor);
        assertEquals(2, r.size());
        assertEquals(1L, r.get(0).getSucursal().getId());
        assertEquals(3L, r.get(1).getSucursal().getId());
        assertSame(heineken, r.get(0).getPrecioPorSucursal());
        assertEquals(5000.0, r.get(0).getPrecio());
        assertEquals(LocalDate.of(2026, 10, 1), r.get(0).getFechaDesde());
        assertEquals(LocalDate.of(2026, 10, 31), r.get(0).getFechaHasta());
        assertTrue(r.get(0).getActivo());
        assertSame(autor, r.get(0).getUsuario());
        assertNotNull(r.get(0).getCreadoEn());
    }

    @Test
    void sucursalesRepetidasSeGuardanUnaVez() {
        assertEquals(1, service.crear(input(5000.0, null, null, 1L, 1L), autor).size());
    }

    @Test
    void siUnaSucursalSeSuperponeNoSeGuardaNinguna() {
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(10L, 3L))
                .thenReturn(Collections.singletonList(existente(99L, 3L, LocalDate.of(2026, 10, 15), null)));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.crear(input(5000.0, "2026-10-01", "2026-10-31", 1L, 3L), autor));
        assertTrue(e.getMessage().contains("SUC 3"));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaPrecioNoPositivoRangoInvertidoSinSucursalesYSucursalCentral() {
        assertThrows(GraphQLException.class, () -> service.crear(input(0.0, null, null, 1L), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, "2026-10-31", "2026-10-01", 1L), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, null, null), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, null, null, 0L), autor));
        verify(repository, never()).save(any());
    }

    @Test
    void superposicionConRangosAbiertos() {
        LocalDate a = LocalDate.of(2026, 10, 1), b = LocalDate.of(2026, 10, 31);
        assertTrue(PrecioEspecialSucursalService.seSuperponen(null, null, a, b));
        assertTrue(PrecioEspecialSucursalService.seSuperponen(a, b, b, null));      // comparten el ultimo dia
        assertFalse(PrecioEspecialSucursalService.seSuperponen(a, b, b.plusDays(1), null));
        assertFalse(PrecioEspecialSucursalService.seSuperponen(null, a.minusDays(1), a, b));
    }

    @Test
    void editarExcluyeLaPropiaFilaDeLaSuperposicion() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        fila.setPrecioPorSucursal(heineken);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(10L, 1L)).thenReturn(Collections.singletonList(fila));
        PrecioEspecialSucursal r = service.editar(5L, 4500.0, "2026-10-01", null, autor);
        assertEquals(4500.0, r.getPrecio());
        assertEquals(LocalDate.of(2026, 10, 1), r.getFechaDesde());
    }

    @Test
    void noSeEditaUnoCortado() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        fila.setActivo(false);
        fila.setPrecioPorSucursal(heineken);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        assertThrows(GraphQLException.class, () -> service.editar(5L, 4500.0, null, null, autor));
    }

    @Test
    void cortarDesactivaSinBorrarYEsIdempotente() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        assertFalse(service.cortar(5L, autor).getActivo());
        assertFalse(service.cortar(5L, autor).getActivo());
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void lasEscriturasSonTransaccionalesDeSpring() throws Exception {
        Class<?> s = PrecioEspecialSucursalService.class;
        assertNotNull(s.getMethod("crear", PrecioEspecialSucursalInput.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("editar", Long.class, Double.class, String.class, String.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("cortar", Long.class, Usuario.class).getAnnotation(Transactional.class));
    }
}
```

- [ ] **Paso 3:** `./mvnw -q test -DskipFlyway=true -Dtest=PrecioEspecialSucursalServiceTest`.
  Esperado: falla de compilación.

- [ ] **Paso 4: implementación**

```java
package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.repository.productos.PrecioEspecialSucursalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Alta, edicion y corte de precios especiales por sucursal (SPEC-PRECIO-ESPECIAL-SUCURSAL.md).
 * <p>
 * La superposicion se valida aca y no en la base: dos especiales activos del mismo precio y
 * sucursal no pueden compartir un dia. Un alta con varias sucursales es todo o nada: se valida
 * todo antes del primer save. "Hoy" es -03 fijo, igual que PrecioEspecialLector del filial.
 */
@Service
public class PrecioEspecialSucursalService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneOffset ZONA = ZoneOffset.ofHours(-3);

    private final PrecioEspecialSucursalRepository repository;
    private final PrecioPorSucursalService precioService;
    private final SucursalService sucursalService;

    public PrecioEspecialSucursalService(PrecioEspecialSucursalRepository repository,
                                         PrecioPorSucursalService precioService,
                                         SucursalService sucursalService) {
        this.repository = repository;
        this.precioService = precioService;
        this.sucursalService = sucursalService;
    }

    @Transactional
    public List<PrecioEspecialSucursal> crear(PrecioEspecialSucursalInput input, Usuario autor) {
        if (input == null) throw new GraphQLException("Faltan los datos del precio especial.");
        validarPrecio(input.getPrecio());
        LocalDate desde = DateUtils.stringToLocalDate(input.getFechaDesde());
        LocalDate hasta = DateUtils.stringToLocalDate(input.getFechaHasta());
        validarRango(desde, hasta);
        // findById(null) de CrudService devuelve null, no Optional.empty()
        PrecioPorSucursal precio = input.getPrecioId() == null ? null
                : precioService.findById(input.getPrecioId()).orElse(null);
        if (precio == null) throw new GraphQLException("El precio " + input.getPrecioId() + " no existe.");
        Set<Long> ids = new LinkedHashSet<>(input.getSucursalIds() != null ? input.getSucursalIds() : Collections.emptyList());
        ids.remove(null);
        if (ids.isEmpty()) throw new GraphQLException("Elegí al menos una sucursal.");

        List<Sucursal> sucursales = new ArrayList<>();
        for (Long sucursalId : ids) {
            if (sucursalId == 0L) throw new GraphQLException("El central (sucursal 0) no vende: no lleva precio especial.");
            Sucursal s = sucursalService.findById(sucursalId).orElse(null);
            if (s == null) throw new GraphQLException("La sucursal " + sucursalId + " no existe.");
            validarSinSuperposicion(precio.getId(), s, desde, hasta, null);
            sucursales.add(s);
        }

        List<PrecioEspecialSucursal> creados = new ArrayList<>();
        for (Sucursal s : sucursales) {
            PrecioEspecialSucursal e = new PrecioEspecialSucursal();
            e.setPrecioPorSucursal(precio);
            e.setSucursal(s);
            e.setPrecio(input.getPrecio());
            e.setFechaDesde(desde);
            e.setFechaHasta(hasta);
            e.setActivo(true);
            e.setUsuario(autor);
            e.setCreadoEn(LocalDateTime.now());
            creados.add(repository.save(e));
        }
        return creados;
    }

    @Transactional
    public PrecioEspecialSucursal editar(Long id, Double precio, String fechaDesde, String fechaHasta, Usuario autor) {
        PrecioEspecialSucursal e = buscar(id);
        if (!Boolean.TRUE.equals(e.getActivo())) {
            throw new GraphQLException("El precio especial " + id + " está cortado: cargá uno nuevo.");
        }
        validarPrecio(precio);
        LocalDate desde = DateUtils.stringToLocalDate(fechaDesde);
        LocalDate hasta = DateUtils.stringToLocalDate(fechaHasta);
        validarRango(desde, hasta);
        validarSinSuperposicion(e.getPrecioPorSucursal().getId(), e.getSucursal(), desde, hasta, e.getId());
        e.setPrecio(precio);
        e.setFechaDesde(desde);
        e.setFechaHasta(hasta);
        e.setUsuario(autor);
        return repository.save(e);
    }

    @Transactional
    public PrecioEspecialSucursal cortar(Long id, Usuario autor) {
        PrecioEspecialSucursal e = buscar(id);
        if (Boolean.FALSE.equals(e.getActivo())) return e;
        e.setActivo(false);
        e.setUsuario(autor);
        return repository.save(e);
    }

    public List<PrecioEspecialSucursal> porPrecio(Long precioId) {
        return repository.findByPrecioPorSucursalIdOrderByIdDesc(precioId);
    }

    public Page<PrecioEspecialSucursal> filtrar(Long sucursalId, String texto, Boolean soloVigentes, int page, int size) {
        String t = texto != null && !texto.trim().isEmpty() ? texto.trim().toUpperCase() : null;
        return repository.filtrar(sucursalId, t, Boolean.TRUE.equals(soloVigentes), LocalDate.now(ZONA), PageRequest.of(page, size));
    }

    /** Dos rangos de dias inclusivos (null = abierto) comparten al menos un dia. */
    static boolean seSuperponen(LocalDate d1, LocalDate h1, LocalDate d2, LocalDate h2) {
        boolean empiezaAntesDeQueTermineElOtro = d1 == null || h2 == null || !d1.isAfter(h2);
        boolean elOtroEmpiezaAntesDeQueTermine = d2 == null || h1 == null || !d2.isAfter(h1);
        return empiezaAntesDeQueTermineElOtro && elOtroEmpiezaAntesDeQueTermine;
    }

    private void validarSinSuperposicion(Long precioId, Sucursal sucursal, LocalDate desde, LocalDate hasta, Long excluirId) {
        for (PrecioEspecialSucursal otro : repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(precioId, sucursal.getId())) {
            if (excluirId != null && excluirId.equals(otro.getId())) continue;
            if (seSuperponen(desde, hasta, otro.getFechaDesde(), otro.getFechaHasta())) {
                throw new GraphQLException("La sucursal " + sucursal.getNombre() + " ya tiene un precio especial ("
                        + otro.getPrecio() + ") " + rango(otro.getFechaDesde(), otro.getFechaHasta())
                        + ". Cortalo o elegí otras fechas.");
            }
        }
    }

    private static String rango(LocalDate desde, LocalDate hasta) {
        return (desde != null ? "desde el " + desde.format(DIA) : "desde siempre")
                + (hasta != null ? " hasta el " + hasta.format(DIA) : " sin fecha de fin");
    }

    private static void validarPrecio(Double precio) {
        if (precio == null || precio <= 0) throw new GraphQLException("El precio especial tiene que ser mayor a cero.");
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new GraphQLException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
    }

    private PrecioEspecialSucursal buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new GraphQLException("El precio especial " + id + " no existe."));
    }
}
```

- [ ] **Paso 5:** `./mvnw -q test -DskipFlyway=true -Dtest='PrecioEspecialSucursalServiceTest,PrecioSecurityServiceTest'`.
  Esperado: 14 tests, 0 fallos.

### Tarea C4: API GraphQL

**Archivos:**
- Crear: `R/graphql/productos/producto/precio-especial-sucursal.graphqls`
- Crear: `J/graphql/productos/PrecioEspecialSucursalGraphQL.java`
- Test: `src/test/java/com/franco/dev/graphql/productos/PrecioEspecialSucursalGraphQLSeguridadTest.java`

**Contrato con el desktop:** lo definen el schema y el resolver de abajo. `page` y `size` son
`Integer`, no primitivos, para que un `null` explícito no rompa.

- [ ] **Paso 1: schema**

```graphql
type PrecioEspecialSucursal {
    id: ID!
    precioPorSucursal: PrecioPorSucursal
    sucursal: Sucursal
    precio: Float
    fechaDesde: Date
    fechaHasta: Date
    activo: Boolean
    creadoEn: Date
    usuarioNickname: String
}

type PrecioEspecialSucursalPage {
    getTotalPages: Int
    getTotalElements: Int
    getNumberOfElements: Int
    isFirst: Boolean
    isLast: Boolean
    hasNext: Boolean
    hasPrevious: Boolean
    getContent: [PrecioEspecialSucursal]
}

input PrecioEspecialSucursalInput {
    precioId: Int!
    sucursalIds: [Int]!
    precio: Float!
    fechaDesde: String
    fechaHasta: String
}

extend type Query {
    preciosEspecialesPorPrecio(precioId: Int!): [PrecioEspecialSucursal]
    filterPreciosEspeciales(sucursalId: Int, texto: String, soloVigentes: Boolean, page: Int = 0, size: Int = 15): PrecioEspecialSucursalPage
}

extend type Mutation {
    savePreciosEspeciales(input: PrecioEspecialSucursalInput!): [PrecioEspecialSucursal]!
    editarPrecioEspecial(id: Int!, precio: Float!, fechaDesde: String, fechaHasta: String): PrecioEspecialSucursal!
    cortarPrecioEspecial(id: Int!): PrecioEspecialSucursal!
}
```

- [ ] **Paso 2: test de seguridad, que tiene que fallar**

```java
package com.franco.dev.graphql.productos;

import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.service.productos.PrecioEspecialSucursalService;
import com.franco.dev.service.productos.PrecioSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PrecioEspecialSucursalGraphQLSeguridadTest {

    @Mock private PrecioSecurityService seg;
    @Mock private PrecioEspecialSucursalService service;
    @InjectMocks private PrecioEspecialSucursalGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    void lasTresEscriturasExigenRolDePrecios() {
        assertThrows(GraphQLException.class, () -> resolver.savePreciosEspeciales(new PrecioEspecialSucursalInput()));
        assertThrows(GraphQLException.class, () -> resolver.editarPrecioEspecial(1L, 5000.0, null, null));
        assertThrows(GraphQLException.class, () -> resolver.cortarPrecioEspecial(1L));
        verifyNoInteractions(service);
    }
}
```

- [ ] **Paso 3: resolver**

```java
package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.service.productos.PrecioEspecialSucursalService;
import com.franco.dev.service.productos.PrecioSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Precios especiales por sucursal. Las escrituras exigen CREAR PRECIOS o EDITAR PRECIOS; las
 * queries quedan abiertas, como el resto de productos.
 */
@Component
@AllArgsConstructor
public class PrecioEspecialSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final PrecioEspecialSucursalService service;
    private final PrecioSecurityService seg;

    public List<PrecioEspecialSucursal> preciosEspecialesPorPrecio(Long precioId) {
        return service.porPrecio(precioId);
    }

    public Page<PrecioEspecialSucursal> filterPreciosEspeciales(Long sucursalId, String texto, Boolean soloVigentes,
                                                               Integer page, Integer size) {
        return service.filtrar(sucursalId, texto, soloVigentes, page != null ? page : 0, size != null ? size : 15);
    }

    public List<PrecioEspecialSucursal> savePreciosEspeciales(PrecioEspecialSucursalInput input) {
        seg.requireGestionar();
        return service.crear(input, seg.currentUsuario());
    }

    public PrecioEspecialSucursal editarPrecioEspecial(Long id, Double precio, String fechaDesde, String fechaHasta) {
        seg.requireGestionar();
        return service.editar(id, precio, fechaDesde, fechaHasta, seg.currentUsuario());
    }

    public PrecioEspecialSucursal cortarPrecioEspecial(Long id) {
        seg.requireGestionar();
        return service.cortar(id, seg.currentUsuario());
    }
}
```

- [ ] **Paso 4:** `./mvnw -q clean verify -B -DskipFlyway=true 2>&1 | tail -30`. Esperado:
  `BUILD SUCCESS` y `SchemaEnumsSincronizadosTest` en verde (no hay enums nuevos). Como el CI no
  levanta el schema, **arrancar el central local** con perfil `dev` y confirmar en el log que no
  hay `SchemaError`/`FieldResolverError`. Además, que Flyway aplicó `232.1`: avisar a Franco,
  porque se modificó su base local.

- [ ] **Paso 5: revalidar el número de migración, commitear y pushear (fase C1-C4)**

```bash
git fetch -q origin develop
git ls-tree -r --name-only origin/develop src/main/resources/db/migration | sed 's|.*/V||;s|__.*||' | sort -t. -k1,1n -k2,2n | tail -1
# si es >= 232, renombrar la nuestra al entero siguiente con git mv y avisar a Franco
git add src/main/resources/db/migration/V232.1__precio_especial_sucursal.sql \
  src/main/resources/graphql/productos/producto/precio-especial-sucursal.graphqls \
  src/main/java/com/franco/dev/domain/productos/PrecioEspecialSucursal.java \
  src/main/java/com/franco/dev/repository/productos/PrecioEspecialSucursalRepository.java \
  src/main/java/com/franco/dev/graphql/productos/input/PrecioEspecialSucursalInput.java \
  src/main/java/com/franco/dev/graphql/productos/PrecioEspecialSucursalGraphQL.java \
  src/main/java/com/franco/dev/service/productos/PrecioSecurityService.java \
  src/main/java/com/franco/dev/service/productos/PrecioEspecialSucursalService.java \
  src/test/java/com/franco/dev/service/productos/PrecioSecurityServiceTest.java \
  src/test/java/com/franco/dev/service/productos/PrecioEspecialSucursalServiceTest.java \
  src/test/java/com/franco/dev/graphql/productos/PrecioEspecialSucursalGraphQLSeguridadTest.java
git status --short
git commit -m "feat(productos): precio especial por sucursal con vigencia"
git push -u origin feature/productos-precio-especial-sucursal
```

### Tarea C5: reimpresiones del central con el precio cobrado

**Archivos:**
- Crear: `J/service/operaciones/PrecioCobrado.java`, con el mismo código que F3 Paso 3. El paquete
  existe en los dos repos.
- Test: `src/test/java/com/franco/dev/service/operaciones/PrecioCobradoTest.java`, con el mismo
  código que en F3.
- Modificar:
  - `J/graphql/operaciones/VentaGraphQL.java`, líneas 319, 321 y 330;
  - `J/graphql/financiero/VentaCreditoGraphQL.java`, líneas 376, 378 y 388.
- **No tocar:**
  - `VentaResolver.java:76`: está comentado;
  - el `VentaItemResolver.valorTotal` del central: ya usa `vi.precio`.

- [ ] **Paso 1:** crear el test y correr `./mvnw -q test -DskipFlyway=true -Dtest=PrecioCobradoTest`.
  Esperado: falla de compilación.
- [ ] **Paso 2:** crear `PrecioCobrado`. Esperado: 3 tests, 0 fallos.
- [ ] **Paso 3:** reemplazar `vi.getPrecioVenta().getPrecio()` por `PrecioCobrado.de(vi)` en las 6
  líneas y agregar el import. Verificar con `grep -rn "getPrecioVenta()\.getPrecio()" src/main/java`:
  solo quedan `VentaResolver.java:76` (comentado) y `PrecioCobrado`.
- [ ] **Paso 4:** `./mvnw -q clean verify -B -DskipFlyway=true 2>&1 | tail -30`. Esperado: `BUILD SUCCESS`.
- [ ] **Paso 5: commit y push**

```bash
git add src/main/java/com/franco/dev/service/operaciones/PrecioCobrado.java \
  src/test/java/com/franco/dev/service/operaciones/PrecioCobradoTest.java \
  src/main/java/com/franco/dev/graphql/operaciones/VentaGraphQL.java \
  src/main/java/com/franco/dev/graphql/financiero/VentaCreditoGraphQL.java
git status --short
git commit -m "fix(ventas): la reimpresion de tickets usa el precio cobrado"
git push
```

---

## PR 3 — desktop (último; su merge a cada rama espera al central de ese canal)

Repo: `/home/franco/dev-frc/frontend/frc-sistemas-integrados-angular`. `A` = `src/app`.

**Limitación que se documenta y no se resuelve:** el desktop **web** (alpha, farmacia o
bodega `.desk`) y cualquier Electron con `serverIp` apuntando al central mandan las consultas
`servidor=false` al central, que no sustituye (`configuracion.service.ts:729-741`,
`aplicarOverrideWeb`). Esas cajas cobran el precio global. El diálogo (D3) lo aclara.

### Tarea D0: rama

- [ ] **Paso 1**

```bash
cd /home/franco/dev-frc/frontend/frc-sistemas-integrados-angular
git status --short          # limpio; si no, avisar a Franco
git fetch -q origin
git switch -c feature/productos-precio-especial-sucursal origin/develop
git branch --unset-upstream
```

### Tarea D1: util de estado, fechas y sucursales

**Archivos:**
- Crear: `A/modules/productos/precio-especial/precio-especial.util.ts`
- Test: `A/modules/productos/precio-especial/precio-especial.util.spec.ts`

**Interfaces que produce:**
- `type EstadoPrecioEspecial = 'VIGENTE' | 'PROGRAMADO' | 'VENCIDO' | 'CORTADO'`
- `estadoPrecioEspecial(e, hoy: Date)`
- `fechaParam(d: Date | null): string | null`
- `idsSucursalesSeleccionadas(seleccion, todas): number[]`

- [ ] **Paso 1: spec que falla**

```ts
import { estadoPrecioEspecial, fechaParam, idsSucursalesSeleccionadas } from './precio-especial.util';

describe('precio-especial.util', () => {
  const hoy = new Date(2026, 9, 10, 15, 30);

  it('cortado gana a cualquier fecha', () => {
    expect(estadoPrecioEspecial({ activo: false }, hoy)).toBe('CORTADO');
  });

  it('sin fechas y activo es vigente', () => {
    expect(estadoPrecioEspecial({ activo: true }, hoy)).toBe('VIGENTE');
  });

  it('los extremos son inclusivos y el backend manda "yyyy-MM-dd 00:00"', () => {
    expect(estadoPrecioEspecial({ activo: true, fechaDesde: '2026-10-10 00:00', fechaHasta: '2026-10-10 00:00' }, hoy)).toBe('VIGENTE');
    expect(estadoPrecioEspecial({ activo: true, fechaDesde: '2026-10-11 00:00' }, hoy)).toBe('PROGRAMADO');
    expect(estadoPrecioEspecial({ activo: true, fechaHasta: '2026-10-09 00:00' }, hoy)).toBe('VENCIDO');
  });

  it('fechaParam no corre el dia por UTC', () => {
    expect(fechaParam(new Date(2026, 9, 1, 23, 50))).toBe('2026-10-01');
    expect(fechaParam(null)).toBeNull();
  });

  it('"Todas" se expande sin el central y sin duplicados; ids como numero', () => {
    const todas = [{ id: '0' }, { id: '1' }, { id: '3' }];
    expect(idsSucursalesSeleccionadas([null, { id: '1' }], todas)).toEqual([1, 3]);
    expect(idsSucursalesSeleccionadas([{ id: '3' }, { id: 3 }], todas)).toEqual([3]);
    expect(idsSucursalesSeleccionadas([], todas)).toEqual([]);
  });
});
```

- [ ] **Paso 2: correr el spec.** Karma no corre en esta máquina, así que se usa esbuild + node con
  el **procedimiento exacto** de `docs/manuales-implementacion/financiero/VENTA-TARJETA-QR-CUPON.md:111-128`:
  bundle con `node_modules/.bin/esbuild <spec> --bundle --platform=node --format=cjs
  --outfile=$CLAUDE_JOB_DIR/tmp/spec.js '--external:@angular/*' --external:rxjs`, y después
  `NODE_PATH=$(pwd)/node_modules node -r @angular/compiler -r <shim jasmine> $CLAUDE_JOB_DIR/tmp/spec.js`.
  - El shim se escribe como indica ese documento, con `window` y `location` globales.
  - Esperado en esta corrida: falla el bundle, porque el util todavía no existe.
  - **No se declara verde sin ver los 5 specs pasados.**

- [ ] **Paso 3: implementación**

```ts
import { formatDate } from '@angular/common';
import { stringToLocalDate } from '../../../commons/core/utils/dateUtils';

export type EstadoPrecioEspecial = 'VIGENTE' | 'PROGRAMADO' | 'VENCIDO' | 'CORTADO';

function soloDia(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

function aDia(valor: any): number | null {
  if (valor == null || valor === '') return null;
  const d = valor instanceof Date ? valor : stringToLocalDate(String(valor));
  return soloDia(d);
}

/**
 * Mismo criterio que la filial (PrecioEspecialLector.esVigente): dias inclusivos, null abierto.
 * Es solo informativo: lo que cobra la caja lo decide la filial con su propio "hoy" (-03).
 */
export function estadoPrecioEspecial(
  e: { activo: boolean; fechaDesde?: any; fechaHasta?: any },
  hoy: Date
): EstadoPrecioEspecial {
  if (!e?.activo) return 'CORTADO';
  const h = soloDia(hoy);
  const desde = aDia(e.fechaDesde);
  const hasta = aDia(e.fechaHasta);
  if (desde != null && desde > h) return 'PROGRAMADO';
  if (hasta != null && hasta < h) return 'VENCIDO';
  return 'VIGENTE';
}

/** yyyy-MM-dd en hora local: toISOString correria el dia en Paraguay (UTC-3). */
export function fechaParam(d: Date | null): string | null {
  return d == null ? null : formatDate(d, 'yyyy-MM-dd', 'en-US');
}

/** null en la seleccion = "Todas" (sin la sucursal 0). Los ids llegan como string (GraphQL ID). */
export function idsSucursalesSeleccionadas(
  seleccion: ({ id: any } | null)[],
  todas: { id: any }[]
): number[] {
  const elegidas = (seleccion || []).includes(null) ? todas : (seleccion || []);
  const ids = elegidas.filter((s) => s != null).map((s) => Number(s.id)).filter((id) => id !== 0 && !isNaN(id));
  return Array.from(new Set(ids));
}
```

- [ ] **Paso 4:** volver a correr el spec. Esperado: 5 specs pasados.
- [ ] **Paso 5:** `node_modules/.bin/tsc -p src/tsconfig.spec.json --noEmit 2>&1 | grep precio-especial`.
  Esperado: sin salida.

### Tarea D2: modelo, GraphQL y servicio

**Archivos a crear** (todos en `A/modules/productos/precio-especial/`):
- `precio-especial.model.ts`
- `graphql/graphql-query.ts`
- `graphql/precio-especial.gql.ts`
- `precio-especial.service.ts`

**Interfaces que produce:** `PrecioEspecialService`, con los métodos `onPorPrecio`, `onFiltrar`,
`onCrear`, `onEditar` y `onCortar`. Todos van al central, porque la filial solo lee la tabla por
replicación.

**Verificado por la auditoría:**
- `onCustomQuery` usa `variables: data` (`generic-crud.service.ts:150-153`).
- `onSaveCustom` hace `gql.mutate(data, …)` (L557-565).
- Ninguno de los dos envuelve en `entity`.

- [ ] **Paso 1: modelo**

```ts
import { Sucursal } from '../../empresarial/sucursal/sucursal.model';
import { PrecioPorSucursal } from '../precio-por-sucursal/precio-por-sucursal.model';

export class PrecioEspecialSucursal {
  id: number;
  precioPorSucursal: PrecioPorSucursal;
  sucursal: Sucursal;
  precio: number;
  /** "yyyy-MM-dd 00:00" desde el backend; leer con stringToLocalDate. */
  fechaDesde: string;
  fechaHasta: string;
  activo: boolean;
  creadoEn: string;
  usuarioNickname: string;
}

export class PrecioEspecialSucursalInput {
  precioId: number;
  sucursalIds: number[];
  precio: number;
  fechaDesde: string | null;
  fechaHasta: string | null;
}
```

- [ ] **Paso 2: queries.** Todas llevan el alias `data:`.

```ts
import gql from 'graphql-tag';

const campos = `
  id precio fechaDesde fechaHasta activo creadoEn usuarioNickname
  sucursal { id nombre }
  precioPorSucursal {
    id precio activo principal
    tipoPrecio { id descripcion }
    presentacion { id descripcion cantidad activo producto { id descripcion } }
  }
`;

export const preciosEspecialesPorPrecioQuery = gql`
  query preciosEspecialesPorPrecio($precioId: Int!) {
    data: preciosEspecialesPorPrecio(precioId: $precioId) { ${campos} }
  }
`;

export const filterPreciosEspecialesQuery = gql`
  query filterPreciosEspeciales($sucursalId: Int, $texto: String, $soloVigentes: Boolean, $page: Int, $size: Int) {
    data: filterPreciosEspeciales(sucursalId: $sucursalId, texto: $texto, soloVigentes: $soloVigentes, page: $page, size: $size) {
      getContent { ${campos} }
      getTotalElements
    }
  }
`;

export const savePreciosEspecialesMutation = gql`
  mutation savePreciosEspeciales($input: PrecioEspecialSucursalInput!) {
    data: savePreciosEspeciales(input: $input) { ${campos} }
  }
`;

export const editarPrecioEspecialMutation = gql`
  mutation editarPrecioEspecial($id: Int!, $precio: Float!, $fechaDesde: String, $fechaHasta: String) {
    data: editarPrecioEspecial(id: $id, precio: $precio, fechaDesde: $fechaDesde, fechaHasta: $fechaHasta) { ${campos} }
  }
`;

export const cortarPrecioEspecialMutation = gql`
  mutation cortarPrecioEspecial($id: Int!) {
    data: cortarPrecioEspecial(id: $id) { ${campos} }
  }
`;
```

- [ ] **Paso 3: clases GQL**

```ts
import { Injectable } from '@angular/core';
import { Mutation, Query } from 'apollo-angular';
import {
  cortarPrecioEspecialMutation, editarPrecioEspecialMutation, filterPreciosEspecialesQuery,
  preciosEspecialesPorPrecioQuery, savePreciosEspecialesMutation,
} from './graphql-query';

@Injectable({ providedIn: 'root' })
export class PreciosEspecialesPorPrecioGQL extends Query<any> { document = preciosEspecialesPorPrecioQuery; }

@Injectable({ providedIn: 'root' })
export class FilterPreciosEspecialesGQL extends Query<any> { document = filterPreciosEspecialesQuery; }

@Injectable({ providedIn: 'root' })
export class SavePreciosEspecialesGQL extends Mutation<any> { document = savePreciosEspecialesMutation; }

@Injectable({ providedIn: 'root' })
export class EditarPrecioEspecialGQL extends Mutation<any> { document = editarPrecioEspecialMutation; }

@Injectable({ providedIn: 'root' })
export class CortarPrecioEspecialGQL extends Mutation<any> { document = cortarPrecioEspecialMutation; }
```

- [ ] **Paso 4: servicio**

```ts
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PageInfo } from '../../../app.component';
import { GenericCrudService } from '../../../generics/generic-crud.service';
import {
  CortarPrecioEspecialGQL, EditarPrecioEspecialGQL, FilterPreciosEspecialesGQL,
  PreciosEspecialesPorPrecioGQL, SavePreciosEspecialesGQL,
} from './graphql/precio-especial.gql';
import { PrecioEspecialSucursal, PrecioEspecialSucursalInput } from './precio-especial.model';

/** Todo va al central (servidor = true): la filial solo lee la tabla por replicacion. */
@Injectable({ providedIn: 'root' })
export class PrecioEspecialService {
  constructor(
    private genericService: GenericCrudService,
    private porPrecioGQL: PreciosEspecialesPorPrecioGQL,
    private filterGQL: FilterPreciosEspecialesGQL,
    private saveGQL: SavePreciosEspecialesGQL,
    private editarGQL: EditarPrecioEspecialGQL,
    private cortarGQL: CortarPrecioEspecialGQL
  ) {}

  onPorPrecio(precioId: number): Observable<PrecioEspecialSucursal[]> {
    return this.genericService.onCustomQuery(this.porPrecioGQL, { precioId: Number(precioId) }, true);
  }

  onFiltrar(params: { sucursalId?: number; texto?: string; soloVigentes?: boolean; page: number; size: number }):
    Observable<PageInfo<PrecioEspecialSucursal>> {
    return this.genericService.onCustomQuery(this.filterGQL, params, true, null, true);
  }

  onCrear(input: PrecioEspecialSucursalInput): Observable<PrecioEspecialSucursal[]> {
    return this.genericService.onSaveCustom(this.saveGQL, { input }, true, { avisarExito: true });
  }

  onEditar(id: number, precio: number, fechaDesde: string | null, fechaHasta: string | null): Observable<PrecioEspecialSucursal> {
    return this.genericService.onSaveCustom(this.editarGQL, { id: Number(id), precio, fechaDesde, fechaHasta }, true, { avisarExito: true });
  }

  onCortar(id: number): Observable<PrecioEspecialSucursal> {
    return this.genericService.onSaveCustom(this.cortarGQL, { id: Number(id) }, true, { avisarExito: true });
  }
}
```

- [ ] **Paso 5:** `npm run check 2>&1 | tail -20`. Esperado: sin `Error:`.

### Tarea D3: diálogo "Precio especial por sucursal" y su acceso desde la ficha

**Archivos:**
- Crear: `A/modules/productos/precio-especial/precio-especial-dialog/precio-especial-dialog.component.{ts,html,scss}`
- Modificar:
  - `A/modules/productos/productos.module.ts`: declaración;
  - `A/modules/productos/producto/edit-producto/producto.component.ts`;
  - `A/modules/productos/producto/edit-producto/producto.component.html`: tabla de precios, L1477-1572.

**Verificado por la auditoría:** la ficha carga precios del **central** (`producto.component.ts:343`
y `:994`, con `servidor` por defecto en `true`), así que muestra el global.

- [ ] **Paso 1: componente**

```ts
import { Component, Inject, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { stringToLocalDate } from '../../../../commons/core/utils/dateUtils';
import { DialogosService } from '../../../../shared/components/dialogos/dialogos.service';
import { Sucursal } from '../../../empresarial/sucursal/sucursal.model';
import { SucursalService } from '../../../empresarial/sucursal/sucursal.service';
import { Presentacion } from '../../presentacion/presentacion.model';
import { evaluarMargenPrecio, MARGEN_MINIMO_PORCENTAJE } from '../../precio-por-sucursal/margen-precio.util';
import { PrecioPorSucursal } from '../../precio-por-sucursal/precio-por-sucursal.model';
import { PrecioEspecialSucursal } from '../precio-especial.model';
import { PrecioEspecialService } from '../precio-especial.service';
import { estadoPrecioEspecial, fechaParam, idsSucursalesSeleccionadas } from '../precio-especial.util';

export class PrecioEspecialDialogData {
  precio: PrecioPorSucursal;
  presentacion: Presentacion;
  costoMedio?: number;
}

@UntilDestroy()
@Component({
  selector: 'app-precio-especial-dialog',
  templateUrl: './precio-especial-dialog.component.html',
  styleUrls: ['./precio-especial-dialog.component.scss'],
})
export class PrecioEspecialDialogComponent implements OnInit {
  sucursalControl = new FormControl<(Sucursal | null)[]>([], Validators.required);
  precioControl = new FormControl<number>(null, [Validators.required, Validators.min(1)]);
  desdeControl = new FormControl<Date>(null);
  hastaControl = new FormControl<Date>(null);
  sucursalList: Sucursal[] = [];
  dataSource = new MatTableDataSource<PrecioEspecialSucursal>([]);
  displayedColumns = ['sucursal', 'precio', 'vigencia', 'estado', 'acciones'];
  /** Especial que se esta editando; null = alta. */
  editando: PrecioEspecialSucursal = null;
  hoy = new Date();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PrecioEspecialDialogData,
    private matDialogRef: MatDialogRef<PrecioEspecialDialogComponent>,
    private service: PrecioEspecialService,
    private sucursalService: SucursalService,
    private dialogosService: DialogosService
  ) {}

  ngOnInit(): void {
    this.sucursalService.onGetAllSucursales(true).pipe(untilDestroyed(this)).subscribe((res) => {
      this.sucursalList = (res || []).filter((s) => Number(s.id) !== 0);
    });
    this.cargar();
  }

  cargar(): void {
    this.service.onPorPrecio(this.data.precio.id).pipe(untilDestroyed(this)).subscribe((res) => {
      this.dataSource.data = res || [];
    });
  }

  estado(e: PrecioEspecialSucursal) {
    return estadoPrecioEspecial(e, this.hoy);
  }

  vigencia(e: PrecioEspecialSucursal): string {
    const f = (v: string) => (v ? stringToLocalDate(v).toLocaleDateString('es-PY') : null);
    return `${f(e.fechaDesde) ?? 'siempre'} → ${f(e.fechaHasta) ?? 'sin fin'}`;
  }

  onEditar(e: PrecioEspecialSucursal): void {
    this.editando = e;
    this.sucursalControl.setValue(this.sucursalList.filter((s) => Number(s.id) === Number(e.sucursal?.id)));
    this.sucursalControl.disable();
    this.precioControl.setValue(e.precio);
    this.desdeControl.setValue(e.fechaDesde ? stringToLocalDate(e.fechaDesde) : null);
    this.hastaControl.setValue(e.fechaHasta ? stringToLocalDate(e.fechaHasta) : null);
  }

  onNuevo(): void {
    this.editando = null;
    this.sucursalControl.enable();
    this.sucursalControl.setValue([]);
    this.precioControl.setValue(null);
    this.desdeControl.setValue(null);
    this.hastaControl.setValue(null);
  }

  onCortar(e: PrecioEspecialSucursal): void {
    this.dialogosService
      .confirm('Cortar precio especial', `¿Cortar el precio especial de ${e.sucursal?.nombre}?`,
        'La sucursal vuelve al precio global desde el próximo escaneo.')
      .pipe(untilDestroyed(this))
      .subscribe((ok) => {
        if (ok) this.service.onCortar(e.id).pipe(untilDestroyed(this)).subscribe(() => this.cargar());
      });
  }

  onGuardar(): void {
    if (this.precioControl.invalid || (!this.editando && this.ids().length === 0)) return;
    const evaluacion = evaluarMargenPrecio(Number(this.precioControl.value), this.data.costoMedio, this.data.presentacion?.cantidad);
    if (evaluacion?.debeAvisar) {
      this.dialogosService
        .confirm('Margen por debajo del mínimo',
          `El precio especial deja un margen de ${evaluacion.margenPorcentaje.toFixed(1).replace('.', ',')}% sobre el costo.`,
          `Se espera un margen mínimo de ${MARGEN_MINIMO_PORCENTAJE}%.`)
        .pipe(untilDestroyed(this))
        .subscribe((ok) => ok && this.guardar());
      return;
    }
    this.guardar();
  }

  private ids(): number[] {
    return idsSucursalesSeleccionadas(this.sucursalControl.value || [], this.sucursalList);
  }

  private guardar(): void {
    const precio = Number(this.precioControl.value);
    const desde = fechaParam(this.desdeControl.value);
    const hasta = fechaParam(this.hastaControl.value);
    const op = this.editando
      ? this.service.onEditar(this.editando.id, precio, desde, hasta)
      : this.service.onCrear({ precioId: Number(this.data.precio.id), sucursalIds: this.ids(), precio, fechaDesde: desde, fechaHasta: hasta });
    op.pipe(untilDestroyed(this)).subscribe({
      next: (res) => {
        if (res != null) {
          this.onNuevo();
          this.cargar();
        }
      },
      // onSaveCustom ya mostro el error de negocio (superposicion, rol) en un snackbar.
      error: () => {},
    });
  }

  onCerrar(): void {
    this.matDialogRef.close(true);
  }
}
```

> Antes de dar por buena la confirmación, verificar en `DialogosService.confirm` qué devuelve al
> aceptar cuando se lo llama con 3 argumentos (título, mensaje, submensaje). `adicionar-precio-dialog`
> lo llama con 8 argumentos, y ahí el significado de cada botón depende de la posición. Si con 3
> argumentos no devuelve `true` al aceptar, pasar los botones explícitos igual que
> `adicionar-precio-dialog` y ajustar la condición `ok`.

- [ ] **Paso 2: template**

```html
<div style="padding: 20px; min-width: 720px">
  <h3 style="text-align: center; margin-top: 0">
    Precio especial por sucursal · {{ data.presentacion?.descripcion || ('x' + data.presentacion?.cantidad) }}
    · {{ data.precio?.tipoPrecio?.descripcion | uppercase }}
  </h3>
  <p style="opacity: 0.7; margin-top: 0">
    Precio global: {{ data.precio?.precio | number: '1.0-0' }}.
    Aplica solo en las sucursales elegidas, durante la vigencia, en las cajas conectadas al
    servidor de la sucursal. Si el precio global está inactivo (promo 2x1), el especial lo
    habilita en esas sucursales.
    <span *ngIf="!data.precio?.principal">Este precio no es el principal: en cajas configuradas sin
      tipos de precio solo se usa el principal.</span>
  </p>

  <div fxLayout="row wrap" fxLayoutGap="12px" fxLayoutAlign="start center">
    <mat-form-field fxFlex="35%">
      <mat-label>Sucursales</mat-label>
      <mat-select [formControl]="sucursalControl" multiple>
        <mat-option [value]="null">Todas</mat-option>
        <mat-option *ngFor="let s of sucursalList" [value]="s">{{ s.id }} - {{ s.nombre | titlecase }}</mat-option>
      </mat-select>
    </mat-form-field>
    <mat-form-field fxFlex="15%">
      <mat-label>Precio especial</mat-label>
      <input matInput type="number" [formControl]="precioControl" autocomplete="off" />
    </mat-form-field>
    <mat-form-field fxFlex="18%">
      <mat-label>Desde (opcional)</mat-label>
      <input matInput [matDatepicker]="pDesde" [formControl]="desdeControl" />
      <mat-datepicker-toggle matSuffix [for]="pDesde"></mat-datepicker-toggle>
      <mat-datepicker #pDesde></mat-datepicker>
    </mat-form-field>
    <mat-form-field fxFlex="18%">
      <mat-label>Hasta (opcional)</mat-label>
      <input matInput [matDatepicker]="pHasta" [formControl]="hastaControl" [min]="desdeControl.value" />
      <mat-datepicker-toggle matSuffix [for]="pHasta"></mat-datepicker-toggle>
      <mat-datepicker #pHasta></mat-datepicker>
    </mat-form-field>
  </div>
  <div fxLayout="row" fxLayoutGap="12px">
    <button mat-raised-button color="primary" (click)="onGuardar()" [disabled]="precioControl.invalid">
      {{ editando ? 'Guardar cambios' : 'Agregar' }}
    </button>
    <button mat-button *ngIf="editando" (click)="onNuevo()">Cancelar edición</button>
  </div>

  <table mat-table [dataSource]="dataSource" class="mat-elevation-z8" style="width: 100%; margin-top: 16px">
    <ng-container matColumnDef="sucursal">
      <th mat-header-cell *matHeaderCellDef>Sucursal</th>
      <td mat-cell *matCellDef="let e">{{ e.sucursal?.id }} - {{ e.sucursal?.nombre | titlecase }}</td>
    </ng-container>
    <ng-container matColumnDef="precio">
      <th mat-header-cell *matHeaderCellDef>Precio</th>
      <td mat-cell *matCellDef="let e">{{ e.precio | number: '1.0-0' }}</td>
    </ng-container>
    <ng-container matColumnDef="vigencia">
      <th mat-header-cell *matHeaderCellDef>Vigencia</th>
      <td mat-cell *matCellDef="let e">{{ vigencia(e) }}</td>
    </ng-container>
    <ng-container matColumnDef="estado">
      <th mat-header-cell *matHeaderCellDef>Estado</th>
      <td mat-cell *matCellDef="let e">{{ estado(e) }}</td>
    </ng-container>
    <ng-container matColumnDef="acciones">
      <th mat-header-cell *matHeaderCellDef></th>
      <td mat-cell *matCellDef="let e">
        <ng-container *ngIf="e.activo">
          <mat-icon style="cursor: pointer" matTooltip="Editar" (click)="onEditar(e)">edit</mat-icon>
          <mat-icon style="cursor: pointer; color: red" matTooltip="Cortar" (click)="onCortar(e)">block</mat-icon>
        </ng-container>
      </td>
    </ng-container>
    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
  </table>

  <div style="text-align: right; margin-top: 16px">
    <button mat-raised-button (click)="onCerrar()">Cerrar</button>
  </div>
</div>
```

`precio-especial-dialog.component.scss` queda vacío.

- [ ] **Paso 3: declarar el componente en `productos.module.ts`.** Agregar el import y sumarlo a
  `declarations`.

- [ ] **Paso 4: acceso desde la ficha.** En `producto.component.ts`:

```ts
import { PrecioEspecialDialogComponent, PrecioEspecialDialogData } from '../../precio-especial/precio-especial-dialog/precio-especial-dialog.component';
import { ROLES } from '../../../personas/roles/roles.enum';
// ...
  puedeGestionarPrecios = false;
// en ngOnInit, al principio (usar el nombre real del MainService inyectado en el constructor):
    const roles: string[] = this.mainService?.usuarioActual?.roles || [];
    this.puedeGestionarPrecios = [ROLES.ADMIN, ROLES.CREAR_PRECIOS, ROLES.EDITAR_PRECIOS].some((r) => roles.includes(r));
// metodo nuevo, junto a onDeletePrecio:
  onPrecioEspecial(precio: PrecioPorSucursal, presentacionIndex: number) {
    const data = new PrecioEspecialDialogData();
    data.precio = precio;
    data.presentacion = this.presentacionesDataSource.data[presentacionIndex];
    data.costoMedio = this.selectedProducto?.costo?.costoMedio;
    this.matDialog.open(PrecioEspecialDialogComponent, { data, disableClose: true });
  }
```

`precioColumnsToDisplay` suma `"especial"` antes de `"eliminar"`. En el html, antes del
`ng-container matColumnDef="eliminar"`:

```html
                          <ng-container matColumnDef="especial">
                            <th mat-header-cell *matHeaderCellDef></th>
                            <td mat-cell *matCellDef="let element">
                              <mat-icon *ngIf="puedeGestionarPrecios" style="cursor: pointer"
                                matTooltip="Precio especial por sucursal"
                                (click)="onPrecioEspecial(element, presentacionIndex); $event.stopPropagation()">local_offer</mat-icon>
                            </td>
                          </ng-container>
```

- [ ] **Paso 5:** `npm run check 2>&1 | tail -20`. Esperado: sin `Error:`.

- [ ] **Paso 6: commit y push (fase D1-D3)**

```bash
git add src/app/modules/productos/precio-especial \
  src/app/modules/productos/productos.module.ts \
  src/app/modules/productos/producto/edit-producto/producto.component.ts \
  src/app/modules/productos/producto/edit-producto/producto.component.html
git status --short
git commit -m "feat(productos): precio especial por sucursal desde la ficha del producto"
git push -u origin feature/productos-precio-especial-sucursal
```

### Tarea D4: pantalla general "Precios especiales"

**Archivos:**
- Crear: `A/modules/productos/precio-especial/list-precio-especial/list-precio-especial.component.{ts,html,scss}`
- Modificar:
  - `A/modules/productos/productos.module.ts`;
  - `A/shared/components/side-mini-variant/side-mini-variant.component.ts`: grupo Productos
    (L669-688), `switch` (L998) e import;
  - `A/shared/widgets/search-bar-dialog/search-bar.service.ts`.

- [ ] **Paso 1: componente**

```ts
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { PageInfo } from '../../../../app.component';
import { stringToLocalDate } from '../../../../commons/core/utils/dateUtils';
import { DialogosService } from '../../../../shared/components/dialogos/dialogos.service';
import { Sucursal } from '../../../empresarial/sucursal/sucursal.model';
import { SucursalService } from '../../../empresarial/sucursal/sucursal.service';
import { PrecioEspecialSucursal } from '../precio-especial.model';
import { PrecioEspecialService } from '../precio-especial.service';
import { estadoPrecioEspecial } from '../precio-especial.util';

@UntilDestroy()
@Component({
  selector: 'app-list-precio-especial',
  templateUrl: './list-precio-especial.component.html',
  styleUrls: ['./list-precio-especial.component.scss'],
})
export class ListPrecioEspecialComponent implements OnInit {
  @ViewChild(MatPaginator) paginator: MatPaginator;
  dataSource = new MatTableDataSource<PrecioEspecialSucursal>([]);
  displayedColumns = ['id', 'sucursal', 'producto', 'presentacion', 'tipoPrecio', 'global', 'precio', 'vigencia', 'estado', 'usuario', 'acciones'];
  selectedPageInfo: PageInfo<PrecioEspecialSucursal>;
  pageIndex = 0;
  pageSize = 15;
  sucursalIdControl = new FormControl<number>(null);
  textoControl = new FormControl<string>(null);
  soloVigentesControl = new FormControl<boolean>(true);
  sucursales: Sucursal[] = [];
  hoy = new Date();

  constructor(
    private service: PrecioEspecialService,
    private sucursalService: SucursalService,
    private dialogosService: DialogosService
  ) {}

  ngOnInit(): void {
    this.sucursalService.onGetAllSucursales(true).pipe(untilDestroyed(this))
      .subscribe((res) => (this.sucursales = (res || []).filter((s) => Number(s.id) !== 0)));
    this.onGetData();
  }

  onGetData(): void {
    const params: any = { page: this.pageIndex, size: this.pageSize, soloVigentes: !!this.soloVigentesControl.value };
    if (this.sucursalIdControl.value != null) params.sucursalId = Number(this.sucursalIdControl.value);
    if (this.textoControl.value) params.texto = this.textoControl.value;
    this.service.onFiltrar(params).pipe(untilDestroyed(this)).subscribe((res) => {
      if (res) {
        this.selectedPageInfo = res;
        this.dataSource.data = res.getContent ?? [];
      }
    });
  }

  onFilter(): void {
    this.pageIndex = 0;
    if (this.paginator) this.paginator.pageIndex = 0;
    this.onGetData();
  }

  onLimpiarFiltros(): void {
    this.sucursalIdControl.setValue(null);
    this.textoControl.setValue(null);
    this.soloVigentesControl.setValue(true);
    this.onFilter();
  }

  handlePageEvent(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.onGetData();
  }

  estado(e: PrecioEspecialSucursal) {
    return estadoPrecioEspecial(e, this.hoy);
  }

  vigencia(e: PrecioEspecialSucursal): string {
    const f = (v: string) => (v ? stringToLocalDate(v).toLocaleDateString('es-PY') : null);
    return `${f(e.fechaDesde) ?? 'siempre'} → ${f(e.fechaHasta) ?? 'sin fin'}`;
  }

  onCortar(e: PrecioEspecialSucursal): void {
    this.dialogosService
      .confirm('Cortar precio especial', `¿Cortar el precio especial de ${e.sucursal?.nombre}?`,
        'La sucursal vuelve al precio global desde el próximo escaneo.')
      .pipe(untilDestroyed(this))
      .subscribe((ok) => {
        if (ok) this.service.onCortar(e.id).pipe(untilDestroyed(this)).subscribe(() => this.onGetData());
      });
  }
}
```

- [ ] **Paso 2: template**

```html
<app-generic-list titulo="Precios especiales" (filtrar)="onFilter()" (resetFiltro)="onLimpiarFiltros()"
  [isAdicionar]="false" [data]="dataSource.data" style="height: 100%">
  <div filtros>
    <div fxLayout="row wrap" fxLayoutGap="12px" fxLayoutAlign="start center" style="padding: 8px 0">
      <mat-form-field appearance="outline" fxFlex="20%">
        <mat-label>Sucursal</mat-label>
        <mat-select [formControl]="sucursalIdControl">
          <mat-option [value]="null">Todas</mat-option>
          <mat-option *ngFor="let s of sucursales" [value]="s.id">{{ s.id }} - {{ s.nombre | titlecase }}</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" fxFlex="30%">
        <mat-label>Producto</mat-label>
        <input matInput [formControl]="textoControl" (keyup.enter)="onFilter()" autocomplete="off" />
      </mat-form-field>
      <mat-slide-toggle [formControl]="soloVigentesControl" (change)="onFilter()">Solo vigentes hoy</mat-slide-toggle>
    </div>
  </div>
  <div table fxFlex fxLayout="column" style="min-height: 0; height: 100%">
    <div fxFlex style="overflow: auto; min-height: 0">
      <table mat-table [dataSource]="dataSource" class="mat-elevation-z8">
        <ng-container matColumnDef="id"><th mat-header-cell *matHeaderCellDef>ID</th><td mat-cell *matCellDef="let e">{{ e.id }}</td></ng-container>
        <ng-container matColumnDef="sucursal"><th mat-header-cell *matHeaderCellDef>Sucursal</th><td mat-cell *matCellDef="let e">{{ e.sucursal?.id }} - {{ e.sucursal?.nombre | titlecase }}</td></ng-container>
        <ng-container matColumnDef="producto"><th mat-header-cell *matHeaderCellDef>Producto</th><td mat-cell *matCellDef="let e">{{ e.precioPorSucursal?.presentacion?.producto?.descripcion }}</td></ng-container>
        <ng-container matColumnDef="presentacion"><th mat-header-cell *matHeaderCellDef>Presentación</th><td mat-cell *matCellDef="let e">{{ e.precioPorSucursal?.presentacion?.descripcion || ('x' + e.precioPorSucursal?.presentacion?.cantidad) }}</td></ng-container>
        <ng-container matColumnDef="tipoPrecio"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let e">{{ e.precioPorSucursal?.tipoPrecio?.descripcion | uppercase }}</td></ng-container>
        <ng-container matColumnDef="global"><th mat-header-cell *matHeaderCellDef>Global</th><td mat-cell *matCellDef="let e">{{ e.precioPorSucursal?.precio | number: '1.0-0' }}</td></ng-container>
        <ng-container matColumnDef="precio"><th mat-header-cell *matHeaderCellDef>Especial</th><td mat-cell *matCellDef="let e">{{ e.precio | number: '1.0-0' }}</td></ng-container>
        <ng-container matColumnDef="vigencia"><th mat-header-cell *matHeaderCellDef>Vigencia</th><td mat-cell *matCellDef="let e">{{ vigencia(e) }}</td></ng-container>
        <ng-container matColumnDef="estado"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let e">{{ estado(e) }}</td></ng-container>
        <ng-container matColumnDef="usuario"><th mat-header-cell *matHeaderCellDef>Usuario</th><td mat-cell *matCellDef="let e">{{ e.usuarioNickname }}</td></ng-container>
        <ng-container matColumnDef="acciones">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let e">
            <mat-icon *ngIf="e.activo" style="cursor: pointer; color: red" matTooltip="Cortar" (click)="onCortar(e)">block</mat-icon>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
    </div>
    <mat-paginator [pageSizeOptions]="[15, 25, 50, 100]" [pageIndex]="pageIndex" (page)="handlePageEvent($event)"
      [length]="selectedPageInfo?.getTotalElements" showFirstLastButtons style="width: 100%"></mat-paginator>
  </div>
</app-generic-list>
```

`list-precio-especial.component.scss` queda vacío.

- [ ] **Paso 3:** declarar `ListPrecioEspecialComponent` en `productos.module.ts`.
- [ ] **Paso 4: menú.** En el grupo Productos de `side-mini-variant.component.ts`, agregar el ítem y
  el case:

```ts
        { name: 'Precios especiales', icon: 'local_offer', action: 'list-precio-especial', visibilityRoles: [ROLES.ADMIN, ROLES.CREAR_PRECIOS, ROLES.EDITAR_PRECIOS] }
```

```ts
      case "list-precio-especial":
        this.openTabIfAuthorized([ROLES.CREAR_PRECIOS, ROLES.EDITAR_PRECIOS], ListPrecioEspecialComponent, "Precios especiales");
        break;
```

  Para el import, tomar como modelo el de `ListVentaTarjetaComponent` (L81) y ajustar la ruta
  relativa a `modules/productos/precio-especial/list-precio-especial/list-precio-especial.component`.

- [ ] **Paso 5:** en `search-bar.service.ts`, dentro de `componenteList`, agregar la línea de abajo
  con su import:

```ts
    { title: 'Precios especiales', component: ListPrecioEspecialComponent, visibilityRoles: [ROLES.CREAR_PRECIOS, ROLES.EDITAR_PRECIOS] },
```

- [ ] **Paso 6:** `npm run check 2>&1 | tail -20`. Esperado: sin `Error:`.
- [ ] **Paso 7: commit y push**

```bash
git add src/app/modules/productos/precio-especial/list-precio-especial \
  src/app/modules/productos/productos.module.ts \
  src/app/shared/components/side-mini-variant/side-mini-variant.component.ts \
  src/app/shared/widgets/search-bar-dialog/search-bar.service.ts
git status --short
git commit -m "feat(productos): pantalla general de precios especiales"
git push
```

### Tarea D5: la grilla de favoritos cobra con precios frescos de la filial

Hoy la grilla de favoritos (venta táctil por categorías) carga los precios **una vez**, al crear
`PdvCategoriaService` (`pdv-categoria.service.ts:32-57`, con el timer comentado). Después cobra
con eso: `venta-touch.component.ts:568-596` hace `item.precio = respuesta.data?.precio?.precio`.
Además, su botón "Actualizar" (`onRefresh`, L68-86) pide los grupos al **central**, porque en L75
`servidor` queda en `true`. El central no sustituye precios, así que "Actualizar" borraría los
especiales de la caja.

**Archivos:**
- Modificar: `A/modules/pdv/comercial/venta-touch/pdv-categoria/pdv-categoria.service.ts`, L75
- Modificar: `A/modules/pdv/comercial/venta-touch/venta-touch.component.ts`: el constructor y
  `onGridCardClick` (L568-596)

- [ ] **Paso 1: `onRefresh` pide a la filial.** En L75:

```ts
            this.onGetGrupoProductosPorGrupoId(gr.id, false)
```

- [ ] **Paso 2: al tocar un grupo, se vuelve a pedir ese grupo a la filial antes de abrir la
  selección.** Es una consulta por toque en la LAN, con el mismo criterio que el escaneo por código
  (`no-cache`). En `venta-touch.component.ts`:
  - inyectar `private pdvCategoriaService: PdvCategoriaService` en el constructor. Es
    `providedIn: 'root'`, así que es la misma instancia que usa la grilla. Import:
    `./pdv-categoria/pdv-categoria.service`;
  - partir `onGridCardClick` así:

```ts
  /** Evita abrir dos selecciones si se toca dos veces mientras llega el grupo. */
  private cargandoGrupo = false;

  onGridCardClick(grupo: PdvGrupo) {
    this.mostrarPrecios = false;
    if (this.cargandoGrupo || this.isDialogOpen) return;
    this.cargandoGrupo = true;
    // Los favoritos se cargan al abrir el POS: se vuelven a pedir a la filial para que un precio
    // especial nuevo, cortado o vencido valga desde este toque. Si la consulta falla, se usa lo
    // que ya estaba cargado.
    this.pdvCategoriaService
      .onGetGrupoProductosPorGrupoId(grupo.id, false)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: (res) => {
          if (res != null) grupo.pdvGruposProductos = res;
          this.cargandoGrupo = false;
          this.abrirSeleccionProductos(grupo);
        },
        error: () => {
          this.cargandoGrupo = false;
          this.abrirSeleccionProductos(grupo);
        },
      });
  }

  private abrirSeleccionProductos(grupo: PdvGrupo) {
    // (cuerpo actual de onGridCardClick desde "let descripcion = grupo.descripcion;" hasta el final, sin cambios)
  }
```

  El cuerpo de `abrirSeleccionProductos` es **el código actual** de `onGridCardClick`, desde
  `let descripcion = grupo.descripcion;` hasta el final, movido sin cambios. `this.mostrarPrecios =
  false;` queda en `onGridCardClick`.

- [ ] **Paso 3:** `npm run check 2>&1 | tail -20`. Esperado: sin `Error:`.
- [ ] **Paso 4: commit y push**

```bash
git add src/app/modules/pdv/comercial/venta-touch/pdv-categoria/pdv-categoria.service.ts \
  src/app/modules/pdv/comercial/venta-touch/venta-touch.component.ts
git status --short
git commit -m "fix(pdv): favoritos cobran con precios frescos de la filial y Actualizar no va al central"
git push
```

**Límite, documentado:** un desktop **viejo** sigue cobrando por favoritos lo que cargó al abrir el
POS. Después de cargar o cortar un especial, esas cajas tienen que reiniciar el POS (ver Despliegue).

---

## Tarea L: prueba local de punta a punta (antes de pedir PR)

Levantar según la skill `frc-fullstack`:
- central :8081 y filial :8082 con perfil `dev`;
- desktop con `ng serve -c web` apuntando a la filial local (memoria «Probar PDV local contra
  filial»).

Precauciones:
- **Central local:** apagar `replication.sync` y `replication.refresh`, porque sin perfil toca las
  filiales de producción.
- **Réplica:** no corre en local. Cada especial que se cree en el central se replica **a mano**,
  copiando la fila a la base local de la filial (`:5552 general`). Es la única escritura manual y
  es solo en local.
- **Flyway:** mirar su log en los dos backends y decir qué migraciones se aplicaron a las bases
  locales.

- [ ] **Heineken.**
  1. Crear un especial de 5000 sobre el precio principal, en la sucursal de la filial local, y
     copiarlo a la filial.
  2. Escanear: da 5000.
  3. Ticket en `ticket_soporte`: sale 5000.
  4. Cambiar `sucursalId` de la filial local y reiniciar: da el precio global.
- [ ] **2x1.**
  1. Crear la presentación 2x1 y su precio, los dos inactivos.
  2. Crear el especial y copiarlo a la filial.
  3. La 2x1 aparece en el buscador del PDV.
  4. Escanear 2 unidades: el ítem se parte en la 2x1.
  5. Cortar el especial y copiar el UPDATE a la filial.
  6. Escanear de nuevo: sale el unitario.
- [ ] **Favoritos.** Con un producto en la grilla de favoritos:
  1. Cortar el especial: el toque siguiente cobra el global, sin reiniciar el POS.
  2. "Actualizar" no borra un especial vigente.
- [ ] **Vigencia futura:** con `desde` = mañana, no aplica hoy.
- [ ] **Roles:** un usuario sin CREAR/EDITAR PRECIOS no ve el ícono, y la mutation directa responde
  "No autorizado".
- [ ] **Resiliencia:** con `ALTER TABLE productos.precio_especial_sucursal RENAME TO x;` en la
  filial local, escanear y vender por favoritos. Tiene que cobrar el precio global y el log tiene
  que mostrar el error. Después, deshacer el rename.
- [ ] **Interruptor:** con `precio.especial.habilitado=false` y la filial reiniciada, se cobra el
  precio global aunque haya un especial vigente.
- [ ] **Borrado en cascada:** borrar desde la ficha un precio que tiene especial. La fila del
  especial desaparece del central.
- [ ] **Registro:** anotar los resultados en la sección «Registro» y pedirle a Franco que pruebe.
  **El PR se abre solo con su aprobación.**

## Despliegue (esta sesión no lo ejecuta: hace falta merge y eso nunca se hace desde acá)

**0. Antes de nada: inventario de promos hechas a mano.**
- Las promos de hoy son `UPDATE` directos en `precio_por_sucursal` y `presentacion` de cada
  filial. Esos cambios quedan en la base.
- Cuando un especial vence, la filial devuelve el valor **que está en su base**: el tocado a mano,
  no el global. Y un 2x1 activado a mano sigue activo después de "cortar".
- Para cada filial del canal:
  1. Exportar `select id, precio, activo from productos.precio_por_sucursal order by id` y
     `select id, activo, principal from productos.presentacion order by id` a CSV, con `psql -c
     "\copy (...) to ..."`.
  2. Exportar lo mismo del central.
  3. Compararlos con `diff`.
- Cada diferencia se convierte en un especial desde la pantalla nueva, o se revierte en la filial.
  La decisión es de Franco.

**1. Filial, PR 1.**
- Mergear a `develop`: llega a las filiales alpha en ≤15 min y sin aprobación.
- **Checklist por host**, contra el Postgres de la filial (gotcha «Llegar al PostgreSQL de una
  filial»):

```sql
select to_regclass('productos.precio_especial_sucursal') as tabla,
       exists(select 1 from flyway_schema_history where version = '104.1' and success) as migracion;
```

- Además, en cada host:
  - `/api/version` tiene que ser igual al release;
  - `update.log` no tiene que tener «Rolling back».
- Si hubo rollback, la tabla existe pero corre el JAR viejo: esa sucursal cobra el global. Retirar
  el release o fijar su `.channel` para cortar el loop de reintentos (240 s sin venta cada 15 min).
- **Filiales inalcanzables:** Farmacia 5 (apagada), Farmacia 6, Suc. Fiesta 25 (nómade), Bodega 4
  (Windows). A cada una se le corre a mano el DDL de V104.1, que es idempotente, o se anota
  explícitamente que queda afuera y por qué.
- Agregar la tabla al checklist de `runbooks/filial-nomade.md`.

**2. Central, PR 2.**
- **Alpha:** el alta a la publicación es **automática**, ~2 min después del arranque
  (`REPLICATION_SYNC_ENABLED=true` en mauro). Por eso el checklist del paso 1 tiene que estar
  completo **antes** de `gh workflow run Deploy` para alpha.
- **Farmacia y bodega:** **no usar el botón "Sincronizar publicaciones"**: publica todas las
  tablas pendientes de `replication_table` y solo refresca las filiales con IP cargada. El alta es
  manual, con una sentencia por `-c` (gotcha de `psql -c`):
  1. En el central:
     - `ALTER TABLE productos.precio_especial_sucursal REPLICA IDENTITY FULL;`
     - `ALTER PUBLICATION central_pub ADD TABLE productos.precio_especial_sucursal;`
  2. En **cada** filial: `ALTER SUBSCRIPTION <sub central→filial> REFRESH PUBLICATION WITH (copy_data = false);`
  3. Verificar `srsubstate = 'r'` para la tabla en `pg_subscription_rel` de cada filial.
- El deploy del central es manual (`gh workflow run Deploy`), porque `deploy-auto.yml` no se
  dispara nunca.

**3. Desktop, PR 3.**
- A `develop` (alpha) va después del paso 2 de alpha.
- **El merge a `release/beta` espera** a que el central de farmacia esté desplegado y la tabla en
  `r`. **El de `master` espera** lo mismo para bodega.
- Si el desktop va adelante, pueden pasar dos cosas:
  - contra un central viejo, la pantalla da error;
  - contra un central nuevo sin publicar, los especiales se guardan "con éxito" y nunca llegan a
    las cajas.

**4. Primer especial.**
- Recién después del paso 3 y del inventario del paso 0.
- Al día siguiente, revisar `pg_stat_subscription_stats.apply_error_count` en cada filial. Tiene
  que seguir igual.
- **Cajas con desktop viejo:** después de cargar o cortar un especial, reiniciar el POS en las que
  venden por favoritos.

**5. Promoción a beta/stable:** mismo orden (filial → central → desktop), con merge commit y nunca
squash. No se hace un viernes.

**Recuperación.** Si una filial cortó su réplica por no tener la tabla:
1. Correr el DDL de V104.1 en esa filial.
2. `ALTER SUBSCRIPTION ... REFRESH PUBLICATION WITH (copy_data = false)`.
3. Backfill de las filas de su sucursal con el patrón `pg_dump --data-only` del gotcha «El seed de
   una tabla MAIN_TO_ALL nueva…».
4. Verificar `apply_error_count`.

Lo mismo aplica si una suscripción se recrea: los especiales anteriores no le llegan
(`copy_data=false`), así que se vuelven a tocar o se hace backfill.

**Apagado de emergencia**, de menor a mayor:
1. Cortar desde la pantalla.
2. En el central: `UPDATE productos.precio_especial_sucursal SET activo = false WHERE activo;`.
3. En una filial con la réplica rota: `precio.especial.habilitado=false` y reiniciar.
4. Para retirar la tabla de la publicación:
   - `replication_table.enabled=false`;
   - `ALTER PUBLICATION central_pub DROP TABLE productos.precio_especial_sucursal;`.

   **Nunca** dropearla en una filial mientras esté publicada.

## Registro de la auditoría (paso 5) y desvíos

2026-09-27. Spec aprobada por Franco. Auditoría con dos agentes que corrieron sin verse.

| # | Hallazgo | Eje | Sev. | Qué se hizo |
|---|---|---|---|---|
| 1 | Una filial sin la tabla, una vez publicada, corta toda su réplica entrante; y `copy_data=false` pierde las filas cargadas antes del REFRESH | A+B | ALTA | Checklist por host, filiales inalcanzables, recuperación, header de V104.1/V232.1 corregido (Despliegue 1, 2 y 4) |
| 2 | El `try/catch` del lector no protege la venta bajo OSIV si la consulta es JPA | B | ALTA | La lectura pasa a JDBC (`PrecioEspecialFuente`); se prueba la resiliencia en L |
| 3 | Favoritos: precios en memoria y "Actualizar" contra el central | A+B | ALTA | Tarea D5 nueva; límite documentado para desktops viejos |
| 4 | El gate por `flyway_schema_history` no prueba que corra el JAR nuevo | B | MEDIA | `/api/version` + `update.log` en el checklist |
| 5 | Sin forma de apagar fuera de la API | B | MEDIA | Property `precio.especial.habilitado` + SQL de corte + retiro de la publicación |
| 6 | Zona y reloj de cada servidor | A+B | MEDIA | Zona fija -03 en el lector y en `filtrar`, con test. **Divergencia:** B proponía `current_date` de la base; se eligió -03 en Java porque A reporta filiales que graban en UTC. Arbitra Franco |
| 7 | La parte fix no tenía un test que fallara por comportamiento | B | MEDIA | `VentaItemResolverTest` (12000 → 10000) y `PresentacionResolverTest` |
| 8 | En farmacia/bodega, "Sincronizar publicaciones" publica todo lo pendiente | A | MEDIA | Botón prohibido; alta manual paso a paso |
| 9 | Desktop adelantado a su central | A | MEDIA | Merge del desktop condicionado por canal |
| 10 | Promos manuales ya existentes en las filiales | A | MEDIA | Inventario previo (Despliegue 0) |
| 11 | El desktop web o apuntado al central cobra el global | A | MEDIA | Documentado en la spec y en el diálogo |
| 12 | Número de migración de la filial revalidado solo en F0 | B | BAJA | Revalidación antes de cada push |
| 13 | `ON DELETE CASCADE` borra el historial | B | BAJA | **Se mantiene** (el precio desaparece); documentado en la migración y probado en L. La alternativa RESTRICT dejaría no borrables los precios con especiales viejos |
| 14 | `lock_timeout` en V232.1 | B | BAJA | Agregado, con verificación de `SET` y `SET LOCAL` |
| 15 | `precioPrincipal` cambia aunque la tabla esté vacía | A+B | BAJA | Test + declararlo en el PR |
| 16 | Rendimiento: N+1 en presentaciones inactivas | B | BAJA | `habilitarPresentaciones` hace una sola consulta. El caché por request queda descartado (YAGNI; el costo es aceptable) |
| 17 | `page`/`size` primitivos | A | BAJA | `Integer` |
| 18 | Etiquetas de góndola, compras y garantía muestran el global | A | BAJA | Fuera de alcance, anotado |
| 19 | Las reimpresiones de ventas viejas ahora muestran lo cobrado | A | BAJA | Declararlo en el PR |

Considerado y no adoptado:
- **Que `crear` rechace si la tabla no está en `central_pub`.** Impediría probar en local, donde no
  hay publicación. Lo cubre el gate de merge del desktop.
- **`hasta` obligatorio.** Franco eligió vigencia opcional.

Otros desvíos y notas:
- La skill `flyway-migraciones-frc` dice que la filial no tiene `out-of-order`, pero lo tiene en
  `true` (`application.properties:74`). Gana el código.
- El clon de `frc-cicd` está atrasado un commit (#28), que no afecta.

Fuera de alcance, anotado:
- el desmarcado del principal ocurre solo en la filial local (`adicionar-precio-dialog.component.ts:240`);
- el P.T. del ticket de venta a crédito sale sin descuento (`VentaCreditoGraphQL` filial L283);
- `itemsFacturaSilenciosa` (#144) trata `vi.precio` como neto, pero el cliente lo manda bruto. Hoy
  es inocuo, porque el descuento por ítem está bloqueado;
- `savePrecioPorSucursal` del central llama dos veces a `service.save(e)`.
