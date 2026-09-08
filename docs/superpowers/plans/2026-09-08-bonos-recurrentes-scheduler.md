# Bonos recurrentes: scheduler generador — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un bono marcado como recurrente se genere solo todos los meses, en vez de tener que cargarlo a mano funcionario por funcionario.

**Architecture:** Una tabla plantilla `rrhh.bono_recurrente` y un job diario que, para cada plantilla activa, crea el `Bono` del mes corriente si todavia no existe. El bono generado es una fila normal de `rrhh.bono`, asi que entra a la liquidacion por el camino que ya existe y **`LiquidacionSueldoService` no se toca**. La idempotencia la garantiza un indice unico parcial sobre `(bono_recurrente_id, periodo)`.

**Tech Stack:** Spring Boot + JPA/Hibernate + Flyway + graphql-java-kickstart (central); Angular 15 + Material + Apollo (desktop). Tests: JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-08-bonos-recurrentes-scheduler-design.md`

## Global Constraints

- Repos: central `franco-system-backend-servidor`, desktop `frc-sistemas-integrados-angular`. Misma rama en ambos: `feat/rrhh-bonos-recurrentes`, salida de `origin/develop` recien fetcheada.
- Migracion Flyway: **solo `ADD COLUMN` / `CREATE TABLE` / `CREATE INDEX`**. Prohibido `DROP`, `RENAME` y cambio de tipo (CLAUDE.md del backend).
- Numeracion Flyway: **siguiente entero, siempre con sufijo `.1`** (nunca `.0`, y nunca incrementar el decimal sobre un mismo entero). El mayor en `origin/develop` al escribir este plan es `V219.5`, asi que la nuestra es `V220.1`. **Re-verificar despues de cualquier rebase sobre `origin/develop`**. Invocar el skill `flyway-migraciones-frc` antes de crear el archivo.
- Tipos de commit permitidos: `feat`, `fix`, `refactor`, `docs`, `chore`. Nunca `style`, `test`, `perf` ni `ci`.
- Frecuencia soportada por el generador: **solo `MENSUAL`**. Las otras cuatro del enum `BonoFrecuencia` quedan sin generacion automatica.
- Formato de `periodo`: string `"YYYY-MM"` (`YearMonth.toString()`).
- Fecha del bono generado: **dia 1 del periodo** (`periodo.atDay(1)`).
- Permisos GraphQL: lectura `seg.requireVer()`, escritura `seg.requireAnyRole(seg.GESTIONAR)`.
- Paginacion: formato estandar del repo (`getTotalPages`, `getTotalElements`, `getNumberOfElements`, `isFirst`, `isLast`, `hasNext`, `hasPrevious`, `getContent`, `getPageable`), filtrado siempre en el backend.
- No pushear ni abrir PR hasta que el usuario haya probado en la app corriendo y haya dicho que si, en una pregunta que trate solo del push.

---

### Task 1: Migracion, entity y repositorios

**Files:**
- Create: `src/main/resources/db/migration/V220.1__rrhh_bono_recurrente.sql`
- Create: `src/main/java/com/franco/dev/domain/rrhh/BonoRecurrente.java`
- Create: `src/main/java/com/franco/dev/repository/rrhh/BonoRecurrenteRepository.java`
- Modify: `src/main/java/com/franco/dev/domain/rrhh/Bono.java` (agregar 2 campos despues de `liquidacionId`)
- Modify: `src/main/java/com/franco/dev/repository/rrhh/BonoRepository.java` (agregar 1 metodo)

**Interfaces:**
- Consumes: nada (primera task).
- Produces:
  - `BonoRecurrente` con getters/setters Lombok: `getId()`, `getFuncionario()`, `getTipo()`, `getMonto()`, `getFrecuencia()`, `getMotivo()`, `getActivo()`, `getUsuario()`, `getAutorizadoPor()`, `getCreadoEn()`.
  - `Bono.getBonoRecurrenteId()` / `setBonoRecurrenteId(Long)`, `Bono.getPeriodo()` / `setPeriodo(String)`.
  - `BonoRecurrenteRepository.idsActivos()` → `List<Long>`
  - `BonoRecurrenteRepository.findPage(Long funcionarioId, Boolean activo, Pageable)` → `Page<BonoRecurrente>`
  - `BonoRepository.existsByBonoRecurrenteIdAndPeriodo(Long, String)` → `boolean`

- [ ] **Step 1: Confirmar el numero de migracion libre**

Mirar `origin/develop`, **no el working tree**: la rama local puede estar atrasada y el numero elegido quedaria pisado.

Run:
```bash
git fetch origin develop -q
git ls-tree -r --name-only origin/develop src/main/resources/db/migration \
  | sed 's|.*/V||;s|__.*||' | sort -t. -k1,1n -k2,2n | tail -3
```
Expected: la mayor es `219.5`, asi que la nuestra es `V220.1` — siguiente entero, sufijo `.1`.

Si develop avanzo, tomar el entero siguiente al mayor **ignorando el decimal** y ajustar el nombre del archivo en todos los pasos siguientes. No incrementar el decimal: si la mayor es `V220.3`, la proxima es `V221.1`, nunca `V220.4`.

- [ ] **Step 2: Crear la migracion**

`src/main/resources/db/migration/V220.1__rrhh_bono_recurrente.sql`:

```sql
-- Plantilla de bono recurrente. El bono real sigue viviendo en rrhh.bono;
-- esta tabla solo define que se genera, para quien y cada cuanto.
CREATE TABLE IF NOT EXISTS rrhh.bono_recurrente (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT NOT NULL REFERENCES personas.funcionario(id),
    tipo              VARCHAR(30),
    monto             NUMERIC(18,2) NOT NULL DEFAULT 0,
    frecuencia        VARCHAR(20) NOT NULL DEFAULT 'MENSUAL',
    motivo            TEXT,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id        BIGINT REFERENCES personas.usuario(id),
    autorizado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bono_recurrente_funcionario
    ON rrhh.bono_recurrente(funcionario_id);

-- Trazabilidad del bono generado hacia su plantilla, y clave de idempotencia.
ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS bono_recurrente_id BIGINT
    REFERENCES rrhh.bono_recurrente(id);
ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS periodo VARCHAR(7);

-- El indice parcial es la idempotencia real: si el cron y un saveBonoRecurrente
-- corren a la vez, la base impide el duplicado. Los bonos manuales
-- (bono_recurrente_id NULL) quedan fuera de la restriccion.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bono_recurrente_periodo
    ON rrhh.bono(bono_recurrente_id, periodo)
    WHERE bono_recurrente_id IS NOT NULL;
```

- [ ] **Step 3: Crear la entity `BonoRecurrente`**

`src/main/java/com/franco/dev/domain/rrhh/BonoRecurrente.java`:

```java
package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Plantilla de bono recurrente. No es un bono: define que bono hay que crear
 * cada periodo. El bono real lo genera BonoRecurrenteService en rrhh.bono.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bono_recurrente", schema = "rrhh")
public class BonoRecurrente implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id", nullable = false)
    private Funcionario funcionario;

    @Enumerated(EnumType.STRING)
    private BonoTipo tipo;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private BonoFrecuencia frecuencia;

    private String motivo;

    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
```

- [ ] **Step 4: Agregar los dos campos a `Bono`**

En `src/main/java/com/franco/dev/domain/rrhh/Bono.java`, inmediatamente despues del campo `liquidacionId`:

```java
    /** Plantilla que genero este bono. NULL en los bonos cargados a mano. */
    @Column(name = "bono_recurrente_id")
    private Long bonoRecurrenteId;

    /** "YYYY-MM". Clave de idempotencia del generador: no se mueve aunque
     *  despues alguien edite la fecha del bono. NULL en los bonos manuales. */
    @Column(name = "periodo")
    private String periodo;
```

- [ ] **Step 5: Crear `BonoRecurrenteRepository`**

`src/main/java/com/franco/dev/repository/rrhh/BonoRecurrenteRepository.java`:

```java
package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BonoRecurrenteRepository extends HelperRepository<BonoRecurrente, Long> {

    default Class<BonoRecurrente> getEntityClass() {
        return BonoRecurrente.class;
    }

    /** Solo los ids: el scheduler itera y llama al service una vez por plantilla. */
    @Query("select b.id from BonoRecurrente b where b.activo = true order by b.id asc")
    List<Long> idsActivos();

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select b from BonoRecurrente b where " +
            "(:funcionarioId is null or b.funcionario.id = :funcionarioId) and " +
            "(:activo is null or b.activo = :activo) " +
            "order by b.id desc")
    Page<BonoRecurrente> findPage(@Param("funcionarioId") Long funcionarioId,
                                  @Param("activo") Boolean activo,
                                  Pageable pageable);
}
```

- [ ] **Step 6: Agregar el metodo de idempotencia a `BonoRepository`**

En `src/main/java/com/franco/dev/repository/rrhh/BonoRepository.java`, despues de `findByFuncionarioIdOrderByFechaDesc`:

```java
    /** Idempotencia del generador: ¿ya existe el bono de esta plantilla para este periodo? */
    boolean existsByBonoRecurrenteIdAndPeriodo(Long bonoRecurrenteId, String periodo);
```

- [ ] **Step 7: Compilar**

Run: `./mvnw -o -q -DskipTests -DskipFlyway=true compile`
Expected: termina sin errores. Si falla por `AssignedIdentityGenerator` o por el import de `Identifiable`, comparar contra `Bono.java`, que usa exactamente el mismo patron.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V220.1__rrhh_bono_recurrente.sql \
        src/main/java/com/franco/dev/domain/rrhh/BonoRecurrente.java \
        src/main/java/com/franco/dev/domain/rrhh/Bono.java \
        src/main/java/com/franco/dev/repository/rrhh/BonoRecurrenteRepository.java \
        src/main/java/com/franco/dev/repository/rrhh/BonoRepository.java
git commit -m "feat(rrhh): tabla plantilla de bono recurrente y trazabilidad en bono"
```

---

### Task 2: `BonoRecurrenteService.generarUno`

Es el corazon de la feature. Se escribe con tests primero.

**Files:**
- Create: `src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteService.java`
- Test: `src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteServiceTest.java`

**Interfaces:**
- Consumes: `BonoRecurrenteRepository.idsActivos()`, `BonoRepository.existsByBonoRecurrenteIdAndPeriodo(Long, String)`, `Bono.setBonoRecurrenteId(Long)`, `Bono.setPeriodo(String)` (Task 1).
- Produces:
  - `BonoRecurrenteService.plantillasActivas()` → `List<Long>`
  - `BonoRecurrenteService.generarUno(Long plantillaId, YearMonth periodo)` → `Optional<Bono>`
  - `BonoRecurrenteService.save(BonoRecurrente)` → `BonoRecurrente` (heredado de `CrudService`, con defaults)

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteServiceTest.java`:

```java
package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * El generador de bonos recurrentes crea plata: un duplicado se paga dos veces y
 * un bono de un funcionario egresado se paga sin que nadie lo pida. Los casos de
 * abajo son justamente esos.
 */
class BonoRecurrenteServiceTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    private BonoRecurrenteRepository plantillaRepository;
    private BonoRepository bonoRepository;
    private BonoRecurrenteService service;

    @BeforeEach
    void setUp() {
        plantillaRepository = mock(BonoRecurrenteRepository.class);
        bonoRepository = mock(BonoRepository.class);
        service = new BonoRecurrenteService(plantillaRepository, bonoRepository);
        when(bonoRepository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(anyLong(), anyString())).thenReturn(false);
    }

    private Funcionario funcionario(boolean activo, LocalDateTime fechaEgreso) {
        Funcionario f = new Funcionario();
        f.setId(7L);
        f.setActivo(activo);
        f.setFechaEgreso(fechaEgreso);
        return f;
    }

    private BonoRecurrente plantilla(boolean activo, BonoFrecuencia frecuencia, Funcionario f) {
        BonoRecurrente p = new BonoRecurrente();
        p.setId(1L);
        p.setFuncionario(f);
        p.setTipo(BonoTipo.PRODUCTIVIDAD);
        p.setMonto(new BigDecimal("150000"));
        p.setMotivo("TRANSPORTE");
        p.setFrecuencia(frecuencia);
        p.setActivo(activo);
        when(plantillaRepository.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void generaElBonoDelPeriodoConFechaDelDiaUnoYMarcaLaTrazabilidad() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));

        Optional<Bono> res = service.generarUno(1L, MARZO);

        assertTrue(res.isPresent());
        Bono b = res.get();
        assertEquals(LocalDate.of(2026, 3, 1), b.getFecha());
        assertEquals("2026-03", b.getPeriodo());
        assertEquals(1L, b.getBonoRecurrenteId());
        assertEquals(7L, b.getFuncionario().getId());
        assertEquals(new BigDecimal("150000"), b.getMonto());
        assertEquals(BonoTipo.PRODUCTIVIDAD, b.getTipo());
        assertEquals("TRANSPORTE", b.getMotivo());
        assertEquals(Boolean.TRUE, b.getEsRecurrente());
        assertEquals(BonoFrecuencia.MENSUAL, b.getFrecuencia());
        assertEquals(Boolean.FALSE, b.getAnulado());
        assertNull(b.getLiquidacionId());
        verify(bonoRepository).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElBonoDelPeriodoYaExiste() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(1L, "2026-03")).thenReturn(true);

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void dosLlamadasSeguidasGeneranUnSoloBono() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));
        // Primera llamada genera; a partir de ahi el bono existe.
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(1L, "2026-03"))
                .thenReturn(false).thenReturn(true);

        assertTrue(service.generarUno(1L, MARZO).isPresent());
        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, times(1)).save(any(Bono.class));
    }

    @Test
    void noGeneraSiLaPlantillaEstaInactiva() {
        plantilla(false, BonoFrecuencia.MENSUAL, funcionario(true, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElFuncionarioEstaInactivo() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(false, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElFuncionarioTieneEgresoAnteriorAlPeriodo() {
        // Defensivo: activo quedo en true pero el egreso ya esta cargado.
        plantilla(true, BonoFrecuencia.MENSUAL,
                funcionario(true, LocalDateTime.of(2026, 1, 15, 0, 0)));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraFrecuenciasDistintasDeMensual() {
        plantilla(true, BonoFrecuencia.ANUAL, funcionario(true, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiLaPlantillaNoExiste() {
        when(plantillaRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(service.generarUno(99L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void diciembreNoSeCorreDeMes() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));

        Bono b = service.generarUno(1L, YearMonth.of(2026, 12)).orElseThrow();

        assertEquals("2026-12", b.getPeriodo());
        assertEquals(LocalDate.of(2026, 12, 1), b.getFecha());
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o -q -DskipFlyway=true test -Dtest=BonoRecurrenteServiceTest`
Expected: FALLA en compilacion, con "cannot find symbol: class BonoRecurrenteService".

- [ ] **Step 3: Escribir el service**

`src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteService.java`:

```java
package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Genera los bonos de las plantillas recurrentes.
 *
 * El bono generado es una fila normal de rrhh.bono: entra a la liquidacion por
 * el mismo camino que un bono cargado a mano (LiquidacionSueldoService filtra
 * por fecha dentro del periodo y liquidacionId nulo), asi que ese service no se
 * toca.
 */
@Service
public class BonoRecurrenteService extends CrudService<BonoRecurrente, BonoRecurrenteRepository, Long> {

    private final BonoRecurrenteRepository repository;
    private final BonoRepository bonoRepository;

    public BonoRecurrenteService(BonoRecurrenteRepository repository, BonoRepository bonoRepository) {
        this.repository = repository;
        this.bonoRepository = bonoRepository;
    }

    @Override
    public BonoRecurrenteRepository getRepository() {
        return repository;
    }

    public Page<BonoRecurrente> findPage(Long funcionarioId, Boolean activo, Pageable pageable) {
        return repository.findPage(funcionarioId, activo, pageable);
    }

    /**
     * Ids de las plantillas candidatas. Devuelve ids y no entidades a proposito:
     * el scheduler itera esta lista y llama a generarUno() una vez por plantilla,
     * de modo que cada generacion tenga su propia transaccion.
     */
    public List<Long> plantillasActivas() {
        return repository.idsActivos();
    }

    /**
     * Genera (o no) el bono de UNA plantilla para UN periodo.
     *
     * Es idempotente: si el bono de ese periodo ya existe devuelve vacio sin
     * escribir. Ademas del chequeo, el indice unico parcial
     * uq_bono_recurrente_periodo impide el duplicado si dos procesos corren a la vez.
     */
    @Transactional
    public Optional<Bono> generarUno(Long plantillaId, YearMonth periodo) {
        if (plantillaId == null || periodo == null) return Optional.empty();

        Optional<BonoRecurrente> opt = repository.findById(plantillaId);
        if (opt.isEmpty()) return Optional.empty();
        BonoRecurrente p = opt.get();

        if (!Boolean.TRUE.equals(p.getActivo())) return Optional.empty();
        if (p.getFrecuencia() != BonoFrecuencia.MENSUAL) return Optional.empty();

        Funcionario f = p.getFuncionario();
        if (f == null) return Optional.empty();
        LocalDate primerDia = periodo.atDay(1);
        if (!Boolean.TRUE.equals(f.getActivo())) return Optional.empty();
        // Defensivo: cubre la fila que llega con egreso cargado y activo sin actualizar.
        if (f.getFechaEgreso() != null && !f.getFechaEgreso().toLocalDate().isAfter(primerDia)) {
            return Optional.empty();
        }

        String clavePeriodo = periodo.toString(); // "2026-03"
        if (bonoRepository.existsByBonoRecurrenteIdAndPeriodo(p.getId(), clavePeriodo)) {
            return Optional.empty();
        }

        Bono b = new Bono();
        b.setFuncionario(f);
        b.setTipo(p.getTipo());
        b.setMonto(p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO);
        b.setFecha(primerDia);
        b.setMotivo(p.getMotivo());
        b.setEsRecurrente(true);
        b.setFrecuencia(BonoFrecuencia.MENSUAL);
        b.setAnulado(false);
        b.setLiquidacionId(null);
        b.setBonoRecurrenteId(p.getId());
        b.setPeriodo(clavePeriodo);
        b.setUsuario(p.getUsuario());
        b.setAutorizadoPor(p.getAutorizadoPor());

        return Optional.of(bonoRepository.save(b));
    }

    @Override
    public BonoRecurrente save(BonoRecurrente entity) {
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getFrecuencia() == null) entity.setFrecuencia(BonoFrecuencia.MENSUAL);
        if (entity.getMonto() == null) entity.setMonto(BigDecimal.ZERO);
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }
}
```

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `./mvnw -o -q -DskipFlyway=true test -Dtest=BonoRecurrenteServiceTest`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteService.java \
        src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteServiceTest.java
git commit -m "feat(rrhh): generacion idempotente del bono mensual desde la plantilla recurrente"
```

---

### Task 3: `BonoRecurrenteScheduler`

**Files:**
- Create: `src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteScheduler.java`
- Test: `src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteSchedulerTest.java`

**Interfaces:**
- Consumes: `BonoRecurrenteService.plantillasActivas()`, `BonoRecurrenteService.generarUno(Long, YearMonth)` (Task 2).
- Produces: `BonoRecurrenteScheduler.generarBonosDelMes()` → `void`, y `generarPeriodo(YearMonth)` → `int` (cantidad generada; existe para poder testear sin depender de la fecha del sistema).

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteSchedulerTest.java`:

```java
package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * El bucle vive en el scheduler y no dentro del service para que cada plantilla
 * tenga su propia transaccion (llamar generarUno desde otro metodo del mismo
 * bean se saltea el proxy de Spring y el @Transactional no aplica).
 *
 * Este test es el que fija esa decision: si alguien mueve el bucle adentro del
 * service, una plantilla que explota se lleva puestas a las demas y esto falla.
 */
class BonoRecurrenteSchedulerTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    @Test
    void unaPlantillaQueFallaNoImpideQueSeGenerenLasDemas() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of(1L, 2L, 3L));
        when(service.generarUno(1L, MARZO)).thenReturn(Optional.of(new Bono()));
        when(service.generarUno(2L, MARZO)).thenThrow(new RuntimeException("funcionario roto"));
        when(service.generarUno(3L, MARZO)).thenReturn(Optional.of(new Bono()));

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(2, scheduler.generarPeriodo(MARZO));
        verify(service).generarUno(3L, MARZO);
    }

    @Test
    void noCuentaLasPlantillasQueNoGeneraronNada() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of(1L, 2L));
        when(service.generarUno(1L, MARZO)).thenReturn(Optional.of(new Bono()));
        when(service.generarUno(2L, MARZO)).thenReturn(Optional.empty());

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(1, scheduler.generarPeriodo(MARZO));
    }

    @Test
    void sinPlantillasActivasNoHaceNada() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of());

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(0, scheduler.generarPeriodo(MARZO));
        verify(service, never()).generarUno(anyLong(), any());
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o -q -DskipFlyway=true test -Dtest=BonoRecurrenteSchedulerTest`
Expected: FALLA en compilacion, con "cannot find symbol: class BonoRecurrenteScheduler".

- [ ] **Step 3: Escribir el scheduler**

`src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteScheduler.java`:

```java
package com.franco.dev.service.rrhh;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * Job diario que genera los bonos del mes corriente a partir de las plantillas
 * recurrentes activas.
 *
 * Corre todos los dias, no solo el dia 1: generarUno() es idempotente, asi que
 * las corridas siguientes son no-ops baratas. Eso hace que el job se auto-repare
 * — si el backend estuvo caido o desplegando el dia 1, el del dia 2 genera igual,
 * con fecha del dia 1, y el bono entra a la liquidacion del mes correcto.
 *
 * El bucle vive aca y no dentro del service para que cada llamada a generarUno()
 * cruce el proxy de Spring y tenga su propia transaccion: una plantilla que falla
 * no arrastra a las demas.
 *
 * 06:30 porque PrestamoCuotaScheduler ya ocupa las 06:00 y PenalizacionScheduler
 * las 05:00.
 */
@Service
@AllArgsConstructor
public class BonoRecurrenteScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BonoRecurrenteScheduler.class);

    private final BonoRecurrenteService service;

    @Scheduled(cron = "${rrhh.bono.recurrente.cron:0 30 6 * * ?}")
    public void generarBonosDelMes() {
        generarPeriodo(YearMonth.now());
    }

    /** Separado de generarBonosDelMes() para poder testear sin depender del reloj. */
    public int generarPeriodo(YearMonth periodo) {
        int generados = 0;
        for (Long id : service.plantillasActivas()) {
            try {
                if (service.generarUno(id, periodo).isPresent()) generados++;
            } catch (Exception e) {
                LOGGER.error("BonoRecurrenteScheduler: error en plantilla {} periodo {}", id, periodo, e);
            }
        }
        if (generados > 0) {
            LOGGER.info("BonoRecurrenteScheduler: {} bonos generados para {}", generados, periodo);
        }
        return generados;
    }
}
```

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `./mvnw -o -q -DskipFlyway=true test -Dtest=BonoRecurrenteSchedulerTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/franco/dev/service/rrhh/BonoRecurrenteScheduler.java \
        src/test/java/com/franco/dev/service/rrhh/BonoRecurrenteSchedulerTest.java
git commit -m "feat(rrhh): job diario que genera los bonos recurrentes del mes"
```

---

### Task 4: API GraphQL de plantillas

**Files:**
- Create: `src/main/resources/graphql/rrhh/bono-recurrente.graphqls`
- Create: `src/main/java/com/franco/dev/graphql/rrhh/input/BonoRecurrenteInput.java`
- Create: `src/main/java/com/franco/dev/graphql/rrhh/BonoRecurrenteGraphQL.java`

**Interfaces:**
- Consumes: `BonoRecurrenteService.findById/findPage/save/generarUno` (Task 2), `RrhhSecurityService.requireVer()` y `requireAnyRole(seg.GESTIONAR)`, `FuncionarioService.findById`, `UsuarioService.findById`.
- Produces: queries `bonoRecurrente(id)`, `bonosRecurrentesPage(...)`; mutations `saveBonoRecurrente(bonoRecurrente: BonoRecurrenteInput!)`, `cambiarEstadoBonoRecurrente(id: ID!, activo: Boolean!)`.

- [ ] **Step 1: Crear el schema**

`src/main/resources/graphql/rrhh/bono-recurrente.graphqls`:

```graphql
type BonoRecurrente {
    id: ID!
    funcionario: Funcionario
    tipo: BonoTipo
    monto: Float
    frecuencia: BonoFrecuencia
    motivo: String
    activo: Boolean
    autorizadoPor: Usuario
    creadoEn: Date
}

input BonoRecurrenteInput {
    id: ID
    funcionarioId: Int
    tipo: BonoTipo
    monto: Float
    frecuencia: BonoFrecuencia
    motivo: String
    activo: Boolean
    autorizadoPorId: Int
    usuarioId: Int
}

type BonoRecurrentePage {
    getTotalPages: Int
    getTotalElements: Int
    getNumberOfElements: Int
    isFirst: Boolean
    isLast: Boolean
    hasNext: Boolean
    hasPrevious: Boolean
    getContent: [BonoRecurrente]
    getPageable: Pageable
}

extend type Query {
    bonoRecurrente(id: ID!): BonoRecurrente
    bonosRecurrentesPage(page: Int = 0, size: Int = 20, funcionarioId: ID, activo: Boolean): BonoRecurrentePage
}

extend type Mutation {
    saveBonoRecurrente(bonoRecurrente: BonoRecurrenteInput!): BonoRecurrente!
    cambiarEstadoBonoRecurrente(id: ID!, activo: Boolean!): BonoRecurrente
}
```

Los enums `BonoTipo` y `BonoFrecuencia` **no** se redeclaran: ya viven en `vacaciones-aguinaldo-bonos.graphqls` y duplicarlos rompe el arranque.

- [ ] **Step 2: Crear el input**

`src/main/java/com/franco/dev/graphql/rrhh/input/BonoRecurrenteInput.java`:

```java
package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonoRecurrenteInput {
    private Long id;
    private Long funcionarioId;
    private BonoTipo tipo;
    private BigDecimal monto;
    private BonoFrecuencia frecuencia;
    private String motivo;
    private Boolean activo;
    private Long autorizadoPorId;
    private Long usuarioId;
}
```

- [ ] **Step 3: Crear el resolver**

`src/main/java/com/franco/dev/graphql/rrhh/BonoRecurrenteGraphQL.java`:

```java
package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.graphql.rrhh.input.BonoRecurrenteInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.BonoRecurrenteService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Optional;

@Component
public class BonoRecurrenteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private BonoRecurrenteService service;

    @Autowired
    private RrhhSecurityService seg;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<BonoRecurrente> bonoRecurrente(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<BonoRecurrente> bonosRecurrentesPage(int page, int size, Long funcionarioId, Boolean activo) {
        seg.requireVer();
        return service.findPage(funcionarioId, activo, PageRequest.of(page, size));
    }

    public BonoRecurrente saveBonoRecurrente(BonoRecurrenteInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        BonoRecurrente e = input.getId() != null
                ? service.findById(input.getId()).orElse(new BonoRecurrente())
                : new BonoRecurrente();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setTipo(input.getTipo());
        e.setMonto(input.getMonto());
        e.setFrecuencia(input.getFrecuencia());
        e.setMotivo(input.getMotivo());
        if (input.getActivo() != null) e.setActivo(input.getActivo());
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));

        BonoRecurrente guardado = service.save(e);

        // Alta a mitad de mes: se genera el bono del mes corriente por la misma
        // ruta que usa el job, no por una copia. Si el job ya lo genero, el
        // chequeo de idempotencia de generarUno() lo deja pasar sin duplicar.
        service.generarUno(guardado.getId(), YearMonth.now());

        return guardado;
    }

    public BonoRecurrente cambiarEstadoBonoRecurrente(Long id, Boolean activo) {
        seg.requireAnyRole(seg.GESTIONAR);
        BonoRecurrente e = service.findById(id)
                .orElseThrow(() -> new GraphQLException("Bono recurrente no encontrado"));
        e.setActivo(Boolean.TRUE.equals(activo));
        return service.save(e);
    }
}
```

- [ ] **Step 4: Correr la suite completa del backend**

Run: `./mvnw -o -q -DskipFlyway=true test`
Expected: PASS. Presta atencion a `SchemaEnumsSincronizadosTest` y `SchemaSinCamposDuplicadosTest`: si el schema nuevo redeclara un enum o un campo, fallan ahi y no en runtime.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/graphql/rrhh/bono-recurrente.graphqls \
        src/main/java/com/franco/dev/graphql/rrhh/input/BonoRecurrenteInput.java \
        src/main/java/com/franco/dev/graphql/rrhh/BonoRecurrenteGraphQL.java
git commit -m "feat(rrhh): api graphql de plantillas de bono recurrente"
```

---

### Task 5: Limpieza del bono manual (backend)

El toggle "Recurrente" del bono manual nunca hizo nada. Ahora que la recurrencia vive en la plantilla, se saca de la entrada para que no vuelva a prometer algo que no pasa. Los campos quedan en la tabla y en el tipo de salida, seteados por el generador.

**Files:**
- Modify: `src/main/java/com/franco/dev/graphql/rrhh/input/BonoInput.java` (quitar 2 campos)
- Modify: `src/main/java/com/franco/dev/graphql/rrhh/BonoGraphQL.java:67-68` (quitar 2 lineas)
- Modify: `src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls` (quitar 2 lineas del `input BonoInput`)

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `BonoInput` sin `esRecurrente` ni `frecuencia`. El tipo de salida `Bono` los conserva.

- [ ] **Step 1: Verificar que ningun otro cliente los usa**

Run:
```bash
grep -rl "esRecurrente\|BonoInput\|saveBono" \
  ../../frontend/frc-mobile-pwa/src ../../frontend/frc-mobile/src 2>/dev/null
```
Expected: sin salida. El unico consumidor es el desktop. Si aparece algo, **frenar** y avisar antes de tocar el input.

- [ ] **Step 2: Quitar los campos del input Java**

En `src/main/java/com/franco/dev/graphql/rrhh/input/BonoInput.java`, borrar estas dos lineas y el import de `BonoFrecuencia`:

```java
    private Boolean esRecurrente;
    private BonoFrecuencia frecuencia;
```

- [ ] **Step 3: Quitar el mapeo del resolver**

En `src/main/java/com/franco/dev/graphql/rrhh/BonoGraphQL.java`, borrar:

```java
        if (input.getEsRecurrente() != null) e.setEsRecurrente(input.getEsRecurrente());
        e.setFrecuencia(input.getFrecuencia());
```

`BonoService.save` ya defaultea `esRecurrente = false` cuando viene nulo, asi que un bono manual queda correctamente marcado como no recurrente.

- [ ] **Step 4: Quitar los campos del `input BonoInput` en el schema**

En `src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls`, dentro de `input BonoInput`, borrar:

```graphql
    esRecurrente: Boolean
    frecuencia: BonoFrecuencia
```

**No tocar** el `type Bono`: ahi los dos campos se quedan, porque el generador los setea y la grilla los muestra.

- [ ] **Step 5: Correr la suite y verificar**

Run: `./mvnw -o -q -DskipFlyway=true test`
Expected: PASS. El desktop se ajusta en la Task 8; hasta entonces manda campos que el input ya no declara, lo cual **rompe la mutation en runtime** — por eso las dos tasks se prueban juntas al final y no se pushea nada en el medio.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/franco/dev/graphql/rrhh/input/BonoInput.java \
        src/main/java/com/franco/dev/graphql/rrhh/BonoGraphQL.java \
        src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls
git commit -m "refactor(rrhh): sacar la recurrencia del input de bono manual"
```

---

### Task 6: Modelo y operaciones Apollo (desktop)

A partir de aca se trabaja en `frc-sistemas-integrados-angular`, rama `feat/rrhh-bonos-recurrentes`.

**Files:**
- Create: `src/app/modules/rrhh/bono-recurrente/bono-recurrente.model.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/graphql/graphql-query.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/graphql/BonosRecurrentesPage.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/graphql/SaveBonoRecurrente.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/graphql/CambiarEstadoBonoRecurrente.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/bono-recurrente.service.ts`

**Interfaces:**
- Consumes: la API de la Task 4.
- Produces:
  - clase `BonoRecurrente` con `toInput(): any`
  - `BonoRecurrenteService.onGetPage(page, size, funcionarioId?, activo?, servidor?)` → `Observable<any>`
  - `BonoRecurrenteService.onSave(input, servidor?)` → `Observable<BonoRecurrente>`
  - `BonoRecurrenteService.onCambiarEstado(id, activo, servidor?)` → `Observable<BonoRecurrente>`

- [ ] **Step 1: Crear el modelo**

`src/app/modules/rrhh/bono-recurrente/bono-recurrente.model.ts`:

```typescript
import { Funcionario } from '../../personas/funcionarios/funcionario.model';
import { BonoTipo, BonoFrecuencia } from '../bono/bono.model';

export class BonoRecurrente {
  id: number;
  funcionario: Funcionario;
  tipo: BonoTipo;
  monto: number;
  frecuencia: BonoFrecuencia;
  motivo: string;
  activo: boolean;

  toInput(): any {
    return {
      id: this.id,
      funcionarioId: this.funcionario?.id,
      tipo: this.tipo,
      monto: this.monto,
      frecuencia: this.frecuencia,
      motivo: this.motivo,
      activo: this.activo
    };
  }
}
```

- [ ] **Step 2: Crear las queries**

`src/app/modules/rrhh/bono-recurrente/graphql/graphql-query.ts`:

```typescript
import gql from "graphql-tag";

const FIELDS = `id funcionario { id persona { id nombre } } tipo monto frecuencia motivo activo creadoEn`;

export const bonosRecurrentesPageQuery = gql`
  query ($page: Int, $size: Int, $funcionarioId: ID, $activo: Boolean) {
    data: bonosRecurrentesPage(page: $page, size: $size, funcionarioId: $funcionarioId, activo: $activo) {
      getTotalPages
      getTotalElements
      getNumberOfElements
      isFirst
      isLast
      hasNext
      hasPrevious
      getContent { ${FIELDS} }
    }
  }
`;
export const saveBonoRecurrenteMutation = gql`
  mutation saveBonoRecurrente($entity: BonoRecurrenteInput!) {
    data: saveBonoRecurrente(bonoRecurrente: $entity) { ${FIELDS} }
  }
`;
export const cambiarEstadoBonoRecurrenteMutation = gql`
  mutation cambiarEstadoBonoRecurrente($id: ID!, $activo: Boolean!) {
    data: cambiarEstadoBonoRecurrente(id: $id, activo: $activo) { ${FIELDS} }
  }
`;
```

- [ ] **Step 3: Crear las tres clases Apollo**

`src/app/modules/rrhh/bono-recurrente/graphql/BonosRecurrentesPage.ts`:

```typescript
import { Injectable } from '@angular/core';
import { Query } from 'apollo-angular';
import { PageInfo } from '../../../../app.component';
import { BonoRecurrente } from '../bono-recurrente.model';
import { bonosRecurrentesPageQuery } from './graphql-query';

@Injectable({ providedIn: 'root' })
export class BonosRecurrentesPageGQL extends Query<PageInfo<BonoRecurrente>> { document = bonosRecurrentesPageQuery; }
```

`src/app/modules/rrhh/bono-recurrente/graphql/SaveBonoRecurrente.ts`:

```typescript
import { Injectable } from '@angular/core';
import { Mutation } from 'apollo-angular';
import { BonoRecurrente } from '../bono-recurrente.model';
import { saveBonoRecurrenteMutation } from './graphql-query';

export interface Response { data: BonoRecurrente; }

@Injectable({ providedIn: 'root' })
export class SaveBonoRecurrenteGQL extends Mutation<Response> { document = saveBonoRecurrenteMutation; }
```

`src/app/modules/rrhh/bono-recurrente/graphql/CambiarEstadoBonoRecurrente.ts`:

```typescript
import { Injectable } from '@angular/core';
import { Mutation } from 'apollo-angular';
import { BonoRecurrente } from '../bono-recurrente.model';
import { cambiarEstadoBonoRecurrenteMutation } from './graphql-query';

export interface Response { data: BonoRecurrente; }

@Injectable({ providedIn: 'root' })
export class CambiarEstadoBonoRecurrenteGQL extends Mutation<Response> { document = cambiarEstadoBonoRecurrenteMutation; }
```

- [ ] **Step 4: Crear el service**

`src/app/modules/rrhh/bono-recurrente/bono-recurrente.service.ts`:

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GenericCrudService } from '../../../generics/generic-crud.service';
import { BonosRecurrentesPageGQL } from './graphql/BonosRecurrentesPage';
import { SaveBonoRecurrenteGQL } from './graphql/SaveBonoRecurrente';
import { CambiarEstadoBonoRecurrenteGQL } from './graphql/CambiarEstadoBonoRecurrente';
import { BonoRecurrente } from './bono-recurrente.model';

@Injectable({ providedIn: 'root' })
export class BonoRecurrenteService {
  constructor(
    private genericService: GenericCrudService,
    private bonosRecurrentesPageGQL: BonosRecurrentesPageGQL,
    private saveBonoRecurrenteGQL: SaveBonoRecurrenteGQL,
    private cambiarEstadoBonoRecurrenteGQL: CambiarEstadoBonoRecurrenteGQL
  ) { }

  /** Padron del SaaS: lista paginada y filtrada en el backend. */
  onGetPage(page: number, size: number, funcionarioId?: number, activo?: boolean,
            servidor = true): Observable<any> {
    return this.genericService.onCustomQuery(this.bonosRecurrentesPageGQL,
      { page, size, funcionarioId, activo }, servidor);
  }

  onSave(input: any, servidor = true): Observable<BonoRecurrente> {
    return this.genericService.onSave<BonoRecurrente>(this.saveBonoRecurrenteGQL, input, null, null, servidor);
  }

  onCambiarEstado(id: number, activo: boolean, servidor = true): Observable<BonoRecurrente> {
    return this.genericService.onSaveCustom<BonoRecurrente>(this.cambiarEstadoBonoRecurrenteGQL,
      { id, activo }, servidor);
  }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/app/modules/rrhh/bono-recurrente/
git commit -m "feat(rrhh): modelo y operaciones apollo de bonos recurrentes"
```

---

### Task 7: Pantalla de bonos recurrentes (desktop)

**Files:**
- Create: `src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.html`
- Create: `src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.scss`
- Create: `src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.ts`
- Create: `src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.html`
- Create: `src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.scss`
- Modify: `src/app/modules/rrhh/rrhh.module.ts` (2 imports + 2 declarations)
- Modify: `src/app/shared/components/side-mini-variant/side-mini-variant.component.ts` (import, item de menu tras "Bonos", case del switch)

**Interfaces:**
- Consumes: `BonoRecurrenteService` y `BonoRecurrente` (Task 6).
- Produces: `ListBonoRecurrenteComponent`, `EditBonoRecurrenteDialogComponent`.

- [ ] **Step 1: Crear el dialog**

`src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.ts`:

```typescript
import { Component, Inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { BonoTipo } from '../../bono/bono.model';
import { BonoRecurrente } from '../bono-recurrente.model';
import { BonoRecurrenteService } from '../bono-recurrente.service';
import { Funcionario } from '../../../personas/funcionarios/funcionario.model';

export interface BonoRecurrenteDialogData {
  funcionarioId: number;
  bonoRecurrente: BonoRecurrente;
}

@UntilDestroy()
@Component({
  selector: 'app-edit-bono-recurrente-dialog',
  templateUrl: './edit-bono-recurrente-dialog.component.html',
  styleUrls: ['./edit-bono-recurrente-dialog.component.scss']
})
export class EditBonoRecurrenteDialogComponent implements OnInit {

  formGroup: FormGroup;

  tipoOptions: BonoTipo[] = ['CUMPLEANIOS', 'NAVIDAD', 'DESEMPENIO', 'PRODUCTIVIDAD', 'OTRO'];

  funcionarioControl = new FormControl(null, [Validators.required]);
  tipoControl = new FormControl('OTRO', [Validators.required]);
  montoControl = new FormControl(0, [Validators.required, Validators.min(1)]);
  motivoControl = new FormControl(null);
  activoControl = new FormControl(true);

  editandoId: number = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) private data: BonoRecurrenteDialogData,
    private dialogRef: MatDialogRef<EditBonoRecurrenteDialogComponent>,
    private bonoRecurrenteService: BonoRecurrenteService
  ) { }

  ngOnInit(): void {
    this.formGroup = new FormGroup({
      funcionario: this.funcionarioControl,
      tipo: this.tipoControl,
      monto: this.montoControl,
      motivo: this.motivoControl,
      activo: this.activoControl
    });
    const edit = this.data?.bonoRecurrente;
    if (edit != null) {
      this.editandoId = edit.id;
      this.funcionarioControl.setValue(edit.funcionario?.id);
      this.tipoControl.setValue(edit.tipo);
      this.montoControl.setValue(edit.monto);
      this.motivoControl.setValue(edit.motivo);
      this.activoControl.setValue(edit.activo);
    } else if (this.data?.funcionarioId != null) {
      this.funcionarioControl.setValue(this.data.funcionarioId);
    }
  }

  onCancelar() {
    this.dialogRef.close(null);
  }

  onGuardar() {
    if (this.formGroup.invalid) { return; }
    const b = new BonoRecurrente();
    b.id = this.editandoId;
    const func = new Funcionario();
    func.id = this.funcionarioControl.value;
    b.funcionario = func;
    b.tipo = this.tipoControl.value as BonoTipo;
    b.monto = this.montoControl.value;
    b.motivo = this.motivoControl.value ? this.motivoControl.value.toUpperCase() : null;
    b.activo = this.activoControl.value ?? true;
    // El generador solo soporta MENSUAL; no se ofrece elegir para no volver a
    // prometer una recurrencia que no se aplica.
    b.frecuencia = 'MENSUAL';

    this.bonoRecurrenteService.onSave(b.toInput())
      .pipe(untilDestroyed(this))
      .subscribe(res => { if (res != null) this.dialogRef.close(res); });
  }
}
```

`src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.html`:

```html
<div class="dialog-container" fxLayout="column" fxLayoutGap="10px">
  <h2 mat-dialog-title>{{ editandoId ? 'Editar' : 'Nuevo' }} bono recurrente</h2>

  <form [formGroup]="formGroup" fxLayout="column" fxLayoutGap="12px">
    <app-select-funcionario
      [funcionarioId]="funcionarioControl.value"
      (idSelected)="funcionarioControl.setValue($event)">
    </app-select-funcionario>
    <span class="error-funcionario" *ngIf="funcionarioControl.touched && funcionarioControl.hasError('required')">
      Seleccione un funcionario
    </span>

    <div fxLayout="row" fxLayoutGap="10px">
      <mat-form-field fxFlex="50%">
        <mat-label>Tipo</mat-label>
        <mat-select [formControl]="tipoControl" required>
          <mat-option *ngFor="let t of tipoOptions" [value]="t">{{ t }}</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field fxFlex="50%">
        <mat-label>Monto</mat-label>
        <input matInput type="number" [formControl]="montoControl" required />
      </mat-form-field>
    </div>

    <mat-form-field style="width: 100%">
      <mat-label>Motivo</mat-label>
      <input matInput [formControl]="motivoControl" />
    </mat-form-field>

    <div fxLayout="row" fxLayoutAlign="start center" fxLayoutGap="10px">
      <mat-slide-toggle [formControl]="activoControl">Activo</mat-slide-toggle>
      <span class="hint-frecuencia">Se genera el día 1 de cada mes</span>
    </div>
  </form>

  <div mat-dialog-actions fxLayout="row" fxLayoutAlign="end center" fxLayoutGap="10px">
    <button mat-stroked-button (click)="onCancelar()">Cancelar</button>
    <button mat-raised-button color="primary" [disabled]="formGroup.invalid" (click)="onGuardar()">Guardar</button>
  </div>
</div>
```

`src/app/modules/rrhh/bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component.scss`:

```scss
.dialog-container {
  min-width: 460px;
  padding: 8px;
}

.hint-frecuencia {
  font-size: 12px;
  opacity: 0.7;
}
```

- [ ] **Step 2: Crear la lista**

`src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.ts`:

```typescript
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { PageInfo } from '../../../../app.component';
import { MainService } from '../../../../main.service';
import { BonoRecurrente } from '../bono-recurrente.model';
import { BonoRecurrenteService } from '../bono-recurrente.service';
import { EditBonoRecurrenteDialogComponent } from '../edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component';

@UntilDestroy({ checkProperties: true })
@Component({
  selector: 'app-list-bono-recurrente',
  templateUrl: './list-bono-recurrente.component.html',
  styleUrls: ['./list-bono-recurrente.component.scss']
})
export class ListBonoRecurrenteComponent implements OnInit {

  @ViewChild(MatPaginator) paginator: MatPaginator;

  displayedColumns = ['funcionario', 'tipo', 'monto', 'motivo', 'frecuencia', 'activo', 'acciones'];
  dataSource = new MatTableDataSource<BonoRecurrente>([]);

  funcionarioControl = new FormControl(null);
  activoControl = new FormControl(null);

  pageIndex = 0;
  pageSize = 25;
  selectedPageInfo: PageInfo<BonoRecurrente>;

  constructor(
    private bonoRecurrenteService: BonoRecurrenteService,
    public mainService: MainService,
    private dialog: MatDialog
  ) { }

  ngOnInit(): void {
    this.onFiltrar();
  }

  onFiltrar() {
    this.bonoRecurrenteService.onGetPage(
      this.pageIndex,
      this.pageSize,
      this.funcionarioControl.value,
      this.activoControl.value
    ).pipe(untilDestroyed(this)).subscribe(res => {
      if (res != null) {
        this.selectedPageInfo = res;
        this.dataSource.data = res.getContent || [];
      }
    });
  }

  onResetFiltro() {
    this.funcionarioControl.setValue(null);
    this.activoControl.setValue(null);
    this.pageIndex = 0;
    this.onFiltrar();
  }

  handlePageEvent(e: PageEvent) {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.onFiltrar();
  }

  onNuevo() {
    this.dialog.open(EditBonoRecurrenteDialogComponent, {
      data: { funcionarioId: this.funcionarioControl.value, bonoRecurrente: null },
      width: '520px', disableClose: true
    }).afterClosed().pipe(untilDestroyed(this)).subscribe(res => { if (res != null) this.onFiltrar(); });
  }

  onEditar(row: BonoRecurrente) {
    this.dialog.open(EditBonoRecurrenteDialogComponent, {
      data: { funcionarioId: null, bonoRecurrente: row },
      width: '520px', disableClose: true
    }).afterClosed().pipe(untilDestroyed(this)).subscribe(res => { if (res != null) this.onFiltrar(); });
  }

  onCambiarEstado(row: BonoRecurrente) {
    this.bonoRecurrenteService.onCambiarEstado(row.id, !row.activo)
      .pipe(untilDestroyed(this))
      .subscribe(res => { if (res != null) this.onFiltrar(); });
  }
}
```

`src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.html`:

```html
<app-generic-list titulo="Bonos recurrentes" style="height: 100%" [isAdicionar]="true" (adicionar)="onNuevo()"
  (filtrar)="onFiltrar()" (resetFiltro)="onResetFiltro()">

  <div filtros fxLayout="row wrap" fxLayoutGap="10px" style="width: 100%">
    <div fxFlex="320px">
      <app-select-funcionario
        [funcionarioId]="funcionarioControl.value"
        (idSelected)="funcionarioControl.setValue($event)">
      </app-select-funcionario>
    </div>

    <mat-form-field fxFlex="180px">
      <mat-label>Estado</mat-label>
      <mat-select [formControl]="activoControl" (selectionChange)="onFiltrar()">
        <mat-option [value]="null">Todos</mat-option>
        <mat-option [value]="true">Activos</mat-option>
        <mat-option [value]="false">Inactivos</mat-option>
      </mat-select>
    </mat-form-field>
  </div>

  <div table style="height: 100%" fxLayout="column" fxLayoutAlign="space-between start">
    <table mat-table [dataSource]="dataSource" class="mat-elevation-z2" style="width: 100%">
      <ng-container matColumnDef="funcionario">
        <th mat-header-cell *matHeaderCellDef>Funcionario</th>
        <td mat-cell *matCellDef="let row">{{ row.funcionario?.persona?.nombre }}</td>
      </ng-container>
      <ng-container matColumnDef="tipo">
        <th mat-header-cell *matHeaderCellDef>Tipo</th>
        <td mat-cell *matCellDef="let row">{{ row.tipo }}</td>
      </ng-container>
      <ng-container matColumnDef="monto">
        <th mat-header-cell *matHeaderCellDef>Monto</th>
        <td mat-cell *matCellDef="let row">{{ row.monto | number:'1.0-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="motivo">
        <th mat-header-cell *matHeaderCellDef>Motivo</th>
        <td mat-cell *matCellDef="let row" class="text-left">{{ row.motivo }}</td>
      </ng-container>
      <ng-container matColumnDef="frecuencia">
        <th mat-header-cell *matHeaderCellDef>Frecuencia</th>
        <td mat-cell *matCellDef="let row">{{ row.frecuencia }}</td>
      </ng-container>
      <ng-container matColumnDef="activo">
        <th mat-header-cell *matHeaderCellDef>Activo</th>
        <td mat-cell *matCellDef="let row">
          <mat-slide-toggle [checked]="row.activo" (change)="onCambiarEstado(row)"
            (click)="$event.stopPropagation()"></mat-slide-toggle>
        </td>
      </ng-container>
      <ng-container matColumnDef="acciones">
        <th mat-header-cell *matHeaderCellDef>Acciones</th>
        <td mat-cell *matCellDef="let row">
          <button mat-icon-button (click)="onEditar(row); $event.stopPropagation()">
            <mat-icon>edit</mat-icon>
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns; sticky: true"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>

    <mat-paginator itemsPerPageLabel="Ítems por página" [pageSizeOptions]="[15, 25, 50, 100]"
      (page)="handlePageEvent($event)" [length]="selectedPageInfo?.getTotalElements"
      style="width: 100%" showFirstLastButtons></mat-paginator>
  </div>
</app-generic-list>
```

`src/app/modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component.scss`: archivo vacio (el de `list-bono` tambien lo es).

- [ ] **Step 3: Declarar en el modulo**

En `src/app/modules/rrhh/rrhh.module.ts`, junto a los imports de bono (linea ~34):

```typescript
import { ListBonoRecurrenteComponent } from './bono-recurrente/list-bono-recurrente/list-bono-recurrente.component';
import { EditBonoRecurrenteDialogComponent } from './bono-recurrente/edit-bono-recurrente-dialog/edit-bono-recurrente-dialog.component';
```

Y en `declarations`, despues de `EditBonoDialogComponent`:

```typescript
    ListBonoRecurrenteComponent,
    EditBonoRecurrenteDialogComponent,
```

- [ ] **Step 4: Colgar del menu**

En `src/app/shared/components/side-mini-variant/side-mini-variant.component.ts`:

Import, junto al de `ListBonoComponent`:

```typescript
import { ListBonoRecurrenteComponent } from '../../../modules/rrhh/bono-recurrente/list-bono-recurrente/list-bono-recurrente.component';
```

Item de menu, inmediatamente despues del objeto `{ name: 'Bonos', ... }` (agregar una coma al cierre del anterior):

```typescript
            {
              name: 'Bonos recurrentes',
              icon: 'autorenew',
              action: 'list-bono-recurrente',
              visibilityRoles: [ROLES.RRHH_VER, ROLES.RRHH_GESTIONAR, ROLES.ADMIN]
            }
```

Case del switch, despues de `case "list-bono":`:

```typescript
      case "list-bono-recurrente":
        this.openTabIfAuthorized(ROLES.RRHH_VER, ListBonoRecurrenteComponent, "Bonos recurrentes");
        break;
```

- [ ] **Step 5: Verificar el build AOT**

Run: `npm run check`
Expected: sin errores de compilacion. Se puede cancelar apenas empiezan a aparecer los warnings de dependencias CommonJS (`canvg`, `luxon`, `leaflet`): esa fase ya paso la deteccion de errores.

- [ ] **Step 6: Commit**

```bash
git add src/app/modules/rrhh/bono-recurrente/ \
        src/app/modules/rrhh/rrhh.module.ts \
        src/app/shared/components/side-mini-variant/side-mini-variant.component.ts
git commit -m "feat(rrhh): pantalla de plantillas de bono recurrente"
```

---

### Task 8: Limpieza del bono manual (desktop)

Cierra el par de la Task 5. Hasta que esta task este hecha, guardar un bono manual desde el desktop falla, porque manda campos que el input del backend ya no declara.

**Files:**
- Modify: `src/app/modules/rrhh/bono/bono.model.ts` (sacar 2 claves de `toInput()`; los campos de la clase se quedan, la grilla los lee)
- Modify: `src/app/modules/rrhh/bono/edit-bono-dialog/edit-bono-dialog.component.ts`
- Modify: `src/app/modules/rrhh/bono/edit-bono-dialog/edit-bono-dialog.component.html:33-41`

**Interfaces:**
- Consumes: el `BonoInput` recortado de la Task 5.
- Produces: nada nuevo.

- [ ] **Step 1: Sacar los campos del `toInput()`**

En `src/app/modules/rrhh/bono/bono.model.ts`, dentro de `toInput()`, borrar estas dos claves (y la coma que quede colgando en `motivo`):

```typescript
      esRecurrente: this.esRecurrente,
      frecuencia: this.frecuencia
```

Las propiedades `esRecurrente` y `frecuencia` de la clase **se quedan**: la columna "Recurrente" de la grilla las lee y ahora las setea el generador.

- [ ] **Step 2: Sacar los controles del dialog (TS)**

En `edit-bono-dialog.component.ts`:
- Borrar `frecuenciaOptions`, `esRecurrenteControl` y `frecuenciaControl`.
- Sacar `esRecurrente` y `frecuencia` del `new FormGroup({...})`.
- En `onGuardar()`, borrar las dos lineas `b.esRecurrente = ...` y `b.frecuencia = ...`.
- Ajustar el import a `import { Bono, BonoTipo } from '../bono.model';` (ya no se usa `BonoFrecuencia`).

- [ ] **Step 3: Sacar los controles del dialog (HTML)**

En `edit-bono-dialog.component.html`, el bloque de la fecha queda solo con el datepicker:

```html
    <div fxLayout="row" fxLayoutGap="10px" fxLayoutAlign="start center">
      <mat-form-field fxFlex="50%">
        <mat-label>Fecha</mat-label>
        <input matInput [matDatepicker]="picker" [formControl]="fechaControl" required />
        <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-datepicker #picker></mat-datepicker>
      </mat-form-field>
    </div>
```

Y se borra entero el `<mat-form-field *ngIf="esRecurrenteControl.value">` de Frecuencia.

- [ ] **Step 4: Verificar el build AOT**

Run: `npm run check`
Expected: sin errores. Un `esRecurrenteControl` olvidado en el HTML aparece aca como error de template, no en `ng serve` — por eso el gate es `npm run check` y no el dev server.

- [ ] **Step 5: Commit**

```bash
git add src/app/modules/rrhh/bono/
git commit -m "refactor(rrhh): sacar el toggle de recurrencia del bono manual"
```

---

### Task 9: Verificacion integral y entrega

No hay codigo nuevo. Es el gate del proyecto y **no se saltea**.

**Files:** ninguno.

- [ ] **Step 1: Suite completa del backend**

```bash
cd backend/franco-system-backend-servidor
./mvnw -o -q -DskipFlyway=true test
```
Expected: PASS, incluidos `BonoRecurrenteServiceTest`, `BonoRecurrenteSchedulerTest`, `SchemaEnumsSincronizadosTest` y `SchemaSinCamposDuplicadosTest`.

- [ ] **Step 2: Build AOT del desktop**

```bash
cd frontend/frc-sistemas-integrados-angular
npm run check
```
Expected: sin errores de compilacion.

- [ ] **Step 3: Re-verificar el numero de la migracion contra `develop`**

```bash
cd backend/franco-system-backend-servidor
git fetch origin develop -q
git log --oneline HEAD..origin/develop     # tiene que salir vacio

# el mayor de develop AHORA
git ls-tree -r --name-only origin/develop src/main/resources/db/migration \
  | sed 's|.*/V||;s|__.*||' | sort -t. -k1,1n -k2,2n | tail -1

# el nuestro
git diff --name-only origin/develop..HEAD -- src/main/resources/db/migration
```
El entero de nuestra migracion tiene que ser **estrictamente mayor** que el de develop. Si no lo es, o si quedo duplicada, `git mv` al siguiente entero con sufijo `.1` y commitear el rename **antes de pushear**.

Renumerar antes de pushear es barato; despues de que se aplico en un entorno, no. Si el numero cambia, **avisarle al usuario**: es el numero que va a ver en el PR, y si ya probo en alpha con el numero viejo, esa base quedo con el anterior en su `flyway_schema_history`.

- [ ] **Step 4: Levantar el entorno**

```bash
# central — sin perfil dev, puerto 8081
cd backend/franco-system-backend-servidor
./mvnw -o spring-boot:run -DskipFlyway=true

# filial — con perfil dev, puerto 8082
cd backend/franco-system-backend-filial
./mvnw -o spring-boot:run -Dspring-boot.run.profiles=dev -DskipFlyway=true

# desktop
cd frontend/frc-sistemas-integrados-angular
npm start
```

Confirmar en el log del central `Started FrancoSystemsApplication`. Flyway **si** va a aplicar `V220.1` sobre la base del usuario: avisarselo explicitamente, no darlo por sentado.

- [ ] **Step 5: Guion de prueba para el usuario**

Pedirle que verifique, en RRHH → Bonos recurrentes:

1. Crear una plantilla para un funcionario activo, monto conocido, tipo PRODUCTIVIDAD.
2. Ir a RRHH → Bonos: **tiene que aparecer un bono de ese funcionario con fecha del dia 1 del mes corriente**, marcado con el icono `autorenew`.
3. Volver a guardar la misma plantilla (editar y guardar): en Bonos **no** debe aparecer un segundo bono del mismo mes.
4. Apagar el toggle "Activo" de la plantilla y volver a guardar: el bono ya generado se queda como esta.
5. En RRHH → Bonos, crear un bono manual: el dialog ya **no** debe mostrar el toggle "Recurrente" ni el select de Frecuencia, y debe guardar sin error.

Para forzar la generacion de un mes sin esperar al cron, se puede bajar el intervalo con `-Dspring-boot.run.arguments=--rrhh.bono.recurrente.cron="0 * * * * ?"` (cada minuto) al levantar el central.

- [ ] **Step 6: Esperar la aprobacion y preguntar por el push**

Esperar el mensaje del usuario diciendo que probo y que esta bien. **Confirmar que la prueba funciono no es autorizar el push.** Despues de su aprobacion, preguntar en una pregunta aparte, que trate solo del push, si se pushean las dos ramas y se abren los PRs draft.

- [ ] **Step 7: Bajar el entorno**

Cuando el usuario avise, terminar los procesos y verificar que no quedaron huerfanos:

```bash
ss -ltn | grep -E '4200|8081|8082'
```
Los procesos de `/opt/` (8083 central alpha, 8080 filial) **tienen que seguir vivos**: no son nuestros.
