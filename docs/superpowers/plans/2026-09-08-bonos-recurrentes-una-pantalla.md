# Bonos recurrentes en una sola pantalla — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la recurrencia vuelva a ser un atributo del bono — toggle y frecuencia en el dialogo de Bonos, que ahora tambien edita — y desaparezca la pantalla separada de plantillas.

**Architecture:** El motor no cambia: `rrhh.bono_recurrente`, `BonoRecurrenteService.generarUno` y `BonoRecurrenteScheduler` quedan intactos con sus 12 tests. Lo que cambia es la superficie: `saveBono` pasa a crear, actualizar o desactivar la plantilla segun el toggle, en una sola transaccion; se borra la API y la pantalla de plantillas.

**Tech Stack:** Spring Boot + JPA + graphql-java-kickstart (central); Angular 15 + Material + Apollo (desktop). Tests: JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-08-bonos-recurrentes-scheduler-design.md` (secciones "API GraphQL" y "Frontend", revision 2026-09-08 post-demo)

**Contexto:** esto continua la rama `feat/rrhh-bonos-recurrentes`, ya implementada y probada en la app. El usuario probo la version de dos pantallas y pidio esta forma. Nada esta pusheado.

## Global Constraints

- Repos: central `franco-system-backend-servidor`, desktop `frc-sistemas-integrados-angular`. Rama `feat/rrhh-bonos-recurrentes` en ambos, ya checkouteada. NO crear ni cambiar de rama.
- El desktop tiene cambios pre-existentes que NO son nuestros y nunca se commitean: `app/package-lock.json` modificado y `.claude/` sin trackear. Nunca `git add -A`, `git add .` ni `git commit -a`.
- Tipos de commit permitidos: `feat`, `fix`, `refactor`, `docs`, `chore`. Nunca `style`, `test`, `perf` ni `ci`.
- **No tocar** `BonoRecurrenteService.generarUno`, `plantillasActivas`, `BonoRecurrenteScheduler` ni sus tests. Son el motor y ya estan verificados.
- **No tocar** la migracion `V220.1` ni crear migraciones nuevas. El modelo de datos no cambia.
- Solo la frecuencia `MENSUAL` genera. El select del dialogo ofrece unicamente `MENSUAL`.
- Editar un bono con `liquidacionId` no nulo esta prohibido: `saveBono` lo rechaza.
- Maven siempre offline y sin el plugin Flyway: `./mvnw -o -q -DskipFlyway=true test`.
- El gate del desktop es `npm run check` (build AOT), nunca `ng serve`.
- No pushear ni abrir PR. El usuario prueba primero.

---

### Task R1: `saveBono` maneja la recurrencia

**Files:**
- Modify: `src/main/java/com/franco/dev/service/rrhh/BonoService.java`
- Modify: `src/main/java/com/franco/dev/graphql/rrhh/BonoGraphQL.java` (`saveBono`)
- Modify: `src/main/java/com/franco/dev/graphql/rrhh/input/BonoInput.java` (quitar el comentario de "deprecados")
- Modify: `src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls` (`type Bono`: agregar `bonoRecurrenteId`; `input BonoInput`: quitar el comentario de deprecados)
- Test: `src/test/java/com/franco/dev/service/rrhh/BonoServiceRecurrenciaTest.java`

**Interfaces:**
- Consumes: `BonoRecurrenteRepository` (de la rama), `Bono.getBonoRecurrenteId()/setBonoRecurrenteId()`, `Bono.getPeriodo()/setPeriodo()`.
- Produces: `BonoService.saveConRecurrencia(Bono entity, Boolean esRecurrente, BonoFrecuencia frecuencia)` -> `Bono`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/franco/dev/service/rrhh/BonoServiceRecurrenciaTest.java`:

```java
package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El toggle "Recurrente" del dialogo de bonos vuelve a hacer algo: saveBono crea,
 * actualiza o desactiva la plantilla. Como edita plata y una regla que se repite
 * todos los meses, cada transicion tiene su test.
 */
class BonoServiceRecurrenciaTest {

    private BonoRepository repository;
    private BonoRecurrenteRepository plantillaRepository;
    private BonoService service;

    @BeforeEach
    void setUp() {
        repository = mock(BonoRepository.class);
        plantillaRepository = mock(BonoRecurrenteRepository.class);
        service = new BonoService(repository, plantillaRepository);
        when(repository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(plantillaRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> {
            BonoRecurrente p = i.getArgument(0);
            if (p.getId() == null) p.setId(77L);
            return p;
        });
    }

    private Bono bono(Long id, Long plantillaId) {
        Funcionario f = new Funcionario();
        f.setId(7L);
        Bono b = new Bono();
        b.setId(id);
        b.setFuncionario(f);
        b.setTipo(BonoTipo.PRODUCTIVIDAD);
        b.setMonto(new BigDecimal("150000"));
        b.setFecha(LocalDate.of(2026, 9, 8));
        b.setMotivo("TRANSPORTE");
        b.setBonoRecurrenteId(plantillaId);
        return b;
    }

    @Test
    void toggleEncendidoSinPlantillaPreviaCreaLaPlantillaYEnlazaElBono() {
        Bono guardado = service.saveConRecurrencia(bono(null, null), true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        BonoRecurrente p = cap.getValue();
        assertEquals(7L, p.getFuncionario().getId());
        assertEquals(BonoTipo.PRODUCTIVIDAD, p.getTipo());
        assertEquals(new BigDecimal("150000"), p.getMonto());
        assertEquals(BonoFrecuencia.MENSUAL, p.getFrecuencia());
        assertEquals(Boolean.TRUE, p.getActivo());

        assertEquals(77L, guardado.getBonoRecurrenteId());
        assertEquals("2026-09", guardado.getPeriodo());
        assertEquals(Boolean.TRUE, guardado.getEsRecurrente());
        assertEquals(BonoFrecuencia.MENSUAL, guardado.getFrecuencia());
    }

    @Test
    void editarUnBonoRecurrenteActualizaLaPlantillaConElMontoNuevo() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setMonto(new BigDecimal("150000"));
        existente.setActivo(true);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono editado = bono(5L, 77L);
        editado.setMonto(new BigDecimal("200000"));
        service.saveConRecurrencia(editado, true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(77L, cap.getValue().getId());
        assertEquals(new BigDecimal("200000"), cap.getValue().getMonto());
        // No se crea una plantilla nueva: se reusa la enlazada.
        verify(plantillaRepository, times(1)).save(any(BonoRecurrente.class));
    }

    @Test
    void apagarElToggleDesactivaLaPlantillaPeroConservaElBono() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setActivo(true);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono guardado = service.saveConRecurrencia(bono(5L, 77L), false, null);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(Boolean.FALSE, cap.getValue().getActivo());
        assertEquals(Boolean.FALSE, guardado.getEsRecurrente());
        verify(repository).save(any(Bono.class));
    }

    @Test
    void volverAPrenderElToggleReactivaLaMismaPlantillaYNoCreaOtra() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setActivo(false);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono guardado = service.saveConRecurrencia(bono(5L, 77L), true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(77L, cap.getValue().getId());
        assertEquals(Boolean.TRUE, cap.getValue().getActivo());
        assertEquals(77L, guardado.getBonoRecurrenteId());
    }

    @Test
    void unBonoManualNoCreaNiTocaPlantillas() {
        service.saveConRecurrencia(bono(null, null), false, null);

        verify(plantillaRepository, never()).save(any(BonoRecurrente.class));
        verify(repository).save(any(Bono.class));
    }

    @Test
    void noSePuedeEditarUnBonoYaLiquidado() {
        Bono previo = bono(5L, null);
        previo.setLiquidacionId(99L);
        when(repository.findById(5L)).thenReturn(Optional.of(previo));

        assertThrows(GraphQLException.class,
                () -> service.saveConRecurrencia(bono(5L, null), false, null));
        verify(repository, never()).save(any(Bono.class));
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o -q -DskipFlyway=true test -Dtest=BonoServiceRecurrenciaTest`
Expected: FALLA en compilacion — `BonoService` no tiene el constructor de dos argumentos ni `saveConRecurrencia`.

- [ ] **Step 3: Implementar en `BonoService`**

`BonoService` hoy usa `@AllArgsConstructor` de Lombok con un solo campo `repository`. Agregar el segundo repositorio y un constructor explicito (el test lo construye con `new`):

```java
    private final BonoRepository repository;
    private final BonoRecurrenteRepository plantillaRepository;

    public BonoService(BonoRepository repository, BonoRecurrenteRepository plantillaRepository) {
        this.repository = repository;
        this.plantillaRepository = plantillaRepository;
    }
```

Quitar `@AllArgsConstructor` de la clase si esta presente, para que no genere un constructor en conflicto.

Y agregar el metodo:

```java
    /**
     * Guarda el bono y sincroniza su plantilla recurrente segun el toggle del dialogo.
     *
     * Un formulario, una transaccion: el bono y la regla que lo repite se escriben
     * juntos o no se escribe ninguno. Editar el monto cambia ambos, por lo que vale
     * para este mes y los siguientes; los meses anteriores son filas propias que el
     * job nunca revisita.
     */
    @Transactional
    public Bono saveConRecurrencia(Bono entity, Boolean esRecurrente, BonoFrecuencia frecuencia) {
        if (entity.getId() != null) {
            Bono previo = repository.findById(entity.getId()).orElse(null);
            if (previo != null && previo.getLiquidacionId() != null) {
                throw new GraphQLException("No se puede editar un bono ya liquidado");
            }
        }

        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());

        boolean recurrente = Boolean.TRUE.equals(esRecurrente);
        BonoFrecuencia freq = frecuencia != null ? frecuencia : BonoFrecuencia.MENSUAL;
        entity.setEsRecurrente(recurrente);
        entity.setFrecuencia(recurrente ? freq : null);

        if (recurrente) {
            BonoRecurrente p = entity.getBonoRecurrenteId() != null
                    ? plantillaRepository.findById(entity.getBonoRecurrenteId()).orElse(null)
                    : null;
            if (p == null) p = new BonoRecurrente();
            p.setFuncionario(entity.getFuncionario());
            p.setTipo(entity.getTipo());
            p.setMonto(entity.getMonto());
            p.setMotivo(entity.getMotivo());
            p.setFrecuencia(freq);
            p.setActivo(true);
            p.setUsuario(entity.getUsuario());
            p.setAutorizadoPor(entity.getAutorizadoPor());
            p = plantillaRepository.save(p);

            entity.setBonoRecurrenteId(p.getId());
            // El periodo ancla la idempotencia del job: con el seteado, la corrida
            // del dia 1 no vuelve a generar el mes que este bono ya cubre.
            if (entity.getPeriodo() == null && entity.getFecha() != null) {
                entity.setPeriodo(YearMonth.from(entity.getFecha()).toString());
            }
        } else if (entity.getBonoRecurrenteId() != null) {
            // Se apaga la regla, pero el bono del mes se queda: es plata ya devengada.
            plantillaRepository.findById(entity.getBonoRecurrenteId()).ifPresent(p -> {
                p.setActivo(false);
                plantillaRepository.save(p);
            });
        }

        return save(entity);
    }
```

Imports nuevos: `BonoRecurrente`, `BonoFrecuencia`, `BonoRecurrenteRepository`, `java.time.YearMonth`. `GraphQLException` y `@Transactional` ya estan.

- [ ] **Step 4: Conectar el resolver**

En `BonoGraphQL.saveBono`, reemplazar la ultima linea `return service.save(e);` por:

```java
        return service.saveConRecurrencia(e, input.getEsRecurrente(), input.getFrecuencia());
```

No re-agregar las lineas viejas `e.setEsRecurrente(...)` / `e.setFrecuencia(...)`: de eso se encarga ahora el service.

`bonoRecurrenteId` y `periodo` no viajan en el input y no hace falta que lo hagan: en una edicion, `saveBono` carga el `Bono` por id y ya vienen con el; en un alta quedan en null, que es lo correcto.

- [ ] **Step 5: Exponer `bonoRecurrenteId` y limpiar los comentarios de deprecacion**

En `src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls`, dentro de `type Bono`, despues de `liquidacionId: Int`:

```graphql
    bonoRecurrenteId: Int
```

Y en `input BonoInput`, borrar el comentario que dice que `esRecurrente`/`frecuencia` son deprecados e ignorados — vuelven a usarse. Lo mismo en `BonoInput.java`.

- [ ] **Step 6: Correr la suite completa**

Run: `./mvnw -o -q -DskipFlyway=true test`
Expected: PASS. Los 12 tests del motor (`BonoRecurrenteServiceTest`, `BonoRecurrenteSchedulerTest`) tienen que seguir verdes sin haberlos tocado.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/franco/dev/service/rrhh/BonoService.java \
        src/main/java/com/franco/dev/graphql/rrhh/BonoGraphQL.java \
        src/main/java/com/franco/dev/graphql/rrhh/input/BonoInput.java \
        src/main/resources/graphql/rrhh/vacaciones-aguinaldo-bonos.graphqls \
        src/test/java/com/franco/dev/service/rrhh/BonoServiceRecurrenciaTest.java
git commit -m "feat(rrhh): el toggle de recurrencia del bono crea y apaga su plantilla"
```

---

### Task R2: Borrar la API de la pantalla de plantillas

Nunca se publico y se queda sin consumidores. El motor no se toca.

**Files:**
- Delete: `src/main/java/com/franco/dev/graphql/rrhh/BonoRecurrenteGraphQL.java`
- Delete: `src/main/java/com/franco/dev/graphql/rrhh/input/BonoRecurrenteInput.java`
- Delete: `src/main/resources/graphql/rrhh/bono-recurrente.graphqls`

**Interfaces:**
- Consumes: nada.
- Produces: nada. Desaparecen `bonoRecurrente`, `bonosRecurrentesPage`, `saveBonoRecurrente`, `cambiarEstadoBonoRecurrente` y el tipo `BonoRecurrentePage`.

- [ ] **Step 1: Verificar que nada mas los usa**

Run:
```bash
grep -rn "bonosRecurrentesPage\|saveBonoRecurrente\|cambiarEstadoBonoRecurrente\|BonoRecurrentePage\|BonoRecurrenteInput" src/ ../../frontend/frc-sistemas-integrados-angular/src ../../frontend/frc-mobile-pwa/src 2>/dev/null
```
Expected: solo los tres archivos a borrar, y los del frontend que la Task R4 elimina. Si aparece algo mas, **frenar** y avisar.

- [ ] **Step 2: Borrar los tres archivos**

```bash
git rm src/main/java/com/franco/dev/graphql/rrhh/BonoRecurrenteGraphQL.java \
       src/main/java/com/franco/dev/graphql/rrhh/input/BonoRecurrenteInput.java \
       src/main/resources/graphql/rrhh/bono-recurrente.graphqls
```

**No borrar** `BonoRecurrenteService`, `BonoRecurrenteScheduler`, `BonoRecurrente`, `BonoRecurrenteRepository` ni sus tests: son el motor de generacion y siguen en uso (`BonoService` usa el repositorio, el scheduler usa el service).

- [ ] **Step 3: Correr la suite completa**

Run: `./mvnw -o -q -DskipFlyway=true test`
Expected: PASS. `SchemaEnumsSincronizadosTest` y `SchemaSinCamposDuplicadosTest` confirman que el schema sigue coherente sin ese archivo.

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(rrhh): borrar la api graphql de la pantalla de plantillas"
```

---

### Task R3: Dialogo de bono con recurrencia y modo edicion (desktop)

**Files:**
- Modify: `src/app/modules/rrhh/bono/bono.model.ts`
- Modify: `src/app/modules/rrhh/bono/graphql/graphql-query.ts`
- Modify: `src/app/modules/rrhh/bono/edit-bono-dialog/edit-bono-dialog.component.ts`
- Modify: `src/app/modules/rrhh/bono/edit-bono-dialog/edit-bono-dialog.component.html`

**Interfaces:**
- Consumes: `saveBono` con `esRecurrente`/`frecuencia` (Task R1), `bonoRecurrenteId` en `type Bono` (Task R1).
- Produces: `BonoDialogData` pasa a `{ funcionarioId: number; bono: Bono }`. El dialogo crea y edita.

- [ ] **Step 1: Modelo — restaurar los campos en `toInput()` y sumar la trazabilidad**

En `bono.model.ts`: la clase ya declara `esRecurrente` y `frecuencia`. Agregar:

```typescript
  bonoRecurrenteId: number;
  liquidacionId: number;
```

Y en `toInput()`, volver a incluir los dos campos de recurrencia (ya no se mandan `bonoRecurrenteId` ni `liquidacionId`, que son de solo lectura):

```typescript
      motivo: this.motivo,
      esRecurrente: this.esRecurrente,
      frecuencia: this.frecuencia
```

- [ ] **Step 2: Query — pedir los campos nuevos**

En `graphql/graphql-query.ts`, la constante `FIELDS` pasa a:

```typescript
const FIELDS = `id funcionario { id persona { id nombre } } tipo monto fecha motivo esRecurrente frecuencia anulado liquidacionId bonoRecurrenteId`;
```

- [ ] **Step 3: Dialogo (TS) — toggle, frecuencia y modo edicion**

En `edit-bono-dialog.component.ts`:

```typescript
export interface BonoDialogData {
  funcionarioId: number;
  bono: Bono;
}
```

Controles: restaurar `esRecurrenteControl` y `frecuenciaControl`, y agregar el estado de edicion.

```typescript
  // Solo MENSUAL: es la unica frecuencia que el generador implementa. Ofrecer las
  // otras cuatro del enum repetiria la promesa vacia que esta pantalla vino a sacar.
  frecuenciaOptions: BonoFrecuencia[] = ['MENSUAL'];

  funcionarioControl = new FormControl(null, [Validators.required]);
  tipoControl = new FormControl('OTRO', [Validators.required]);
  montoControl = new FormControl(0, [Validators.required, Validators.min(1)]);
  fechaControl = new FormControl(new Date(), [Validators.required]);
  motivoControl = new FormControl(null);
  esRecurrenteControl = new FormControl(false);
  frecuenciaControl = new FormControl('MENSUAL');

  editandoId: number = null;
  soloLectura = false;
```

En `ngOnInit`, despues de armar el `FormGroup` con las claves `esRecurrente` y `frecuencia`:

```typescript
    const edit = this.data?.bono;
    if (edit != null) {
      this.editandoId = edit.id;
      this.funcionarioControl.setValue(edit.funcionario?.id);
      this.tipoControl.setValue(edit.tipo);
      this.montoControl.setValue(edit.monto);
      this.fechaControl.setValue(edit.fecha ? new Date(edit.fecha) : new Date());
      this.motivoControl.setValue(edit.motivo);
      this.esRecurrenteControl.setValue(edit.esRecurrente ?? false);
      this.frecuenciaControl.setValue(edit.frecuencia ?? 'MENSUAL');
      // Un bono ya liquidado es un pago hecho: se puede mirar, no editar.
      if (edit.liquidacionId != null) {
        this.soloLectura = true;
        this.formGroup.disable();
      }
    } else if (this.data?.funcionarioId != null) {
      this.funcionarioControl.setValue(this.data.funcionarioId);
    }
```

En `onGuardar()`, arrastrar id y trazabilidad y mandar la recurrencia:

```typescript
  onGuardar() {
    if (this.formGroup.invalid || this.soloLectura) { return; }
    const b = new Bono();
    b.id = this.editandoId;
    const func = new Funcionario();
    func.id = this.funcionarioControl.value;
    b.funcionario = func;
    b.tipo = this.tipoControl.value as BonoTipo;
    b.monto = this.montoControl.value;
    b.fecha = dateToString(this.fechaControl.value);
    b.motivo = this.motivoControl.value ? this.motivoControl.value.toUpperCase() : null;
    b.esRecurrente = this.esRecurrenteControl.value ?? false;
    b.frecuencia = this.esRecurrenteControl.value
      ? (this.frecuenciaControl.value as BonoFrecuencia) : null;

    this.bonoService.onSave(b.toInput())
      .pipe(untilDestroyed(this))
      .subscribe(res => { if (res != null) this.dialogRef.close(res); });
  }
```

Reponer el import de `BonoFrecuencia`: `import { Bono, BonoTipo, BonoFrecuencia } from '../bono.model';`

- [ ] **Step 4: Dialogo (HTML) — toggle, select y aviso de solo lectura**

Titulo dinamico:

```html
  <h2 mat-dialog-title>{{ soloLectura ? 'Bono liquidado' : (editandoId ? 'Editar bono' : 'Nuevo bono') }}</h2>
```

Debajo del titulo, el aviso:

```html
  <div class="aviso-liquidado" *ngIf="soloLectura">
    Este bono ya fue liquidado, por eso no se puede modificar.
  </div>
```

El bloque de fecha recupera el toggle al lado:

```html
    <div fxLayout="row" fxLayoutGap="10px" fxLayoutAlign="start center">
      <mat-form-field fxFlex="50%">
        <mat-label>Fecha</mat-label>
        <input matInput [matDatepicker]="picker" [formControl]="fechaControl" required />
        <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-datepicker #picker></mat-datepicker>
      </mat-form-field>
      <mat-slide-toggle [formControl]="esRecurrenteControl">Recurrente</mat-slide-toggle>
    </div>

    <mat-form-field style="width: 100%" *ngIf="esRecurrenteControl.value">
      <mat-label>Frecuencia</mat-label>
      <mat-select [formControl]="frecuenciaControl">
        <mat-option *ngFor="let fr of frecuenciaOptions" [value]="fr">{{ fr }}</mat-option>
      </mat-select>
    </mat-form-field>
```

Y el boton Guardar se deshabilita en solo lectura:

```html
    <button mat-raised-button color="primary" [disabled]="formGroup.invalid || soloLectura" (click)="onGuardar()">Guardar</button>
```

En el `.scss`, agregar:

```scss
.aviso-liquidado {
  font-size: 12px;
  opacity: 0.8;
  padding: 0 8px 4px;
}
```

- [ ] **Step 5: Verificar el build AOT**

Run: `npm run check`
Expected: sin errores de compilacion. Se puede cortar cuando empiezan los warnings de CommonJS (`canvg`, `luxon`, `leaflet`): esa fase ya paso la deteccion de errores.

- [ ] **Step 6: Commit**

```bash
git add src/app/modules/rrhh/bono/
git commit -m "feat(rrhh): el dialogo de bono maneja la recurrencia y permite editar"
```

---

### Task R4: Accion Editar en la grilla y borrar la pantalla de plantillas (desktop)

**Files:**
- Modify: `src/app/modules/rrhh/bono/list-bono/list-bono.component.ts`
- Modify: `src/app/modules/rrhh/bono/list-bono/list-bono.component.html`
- Modify: `src/app/modules/rrhh/rrhh.module.ts`
- Modify: `src/app/shared/components/side-mini-variant/side-mini-variant.component.ts`
- Delete: `src/app/modules/rrhh/bono-recurrente/` (el directorio completo)

**Interfaces:**
- Consumes: `EditBonoDialogComponent` con `BonoDialogData { funcionarioId, bono }` (Task R3).
- Produces: nada.

- [ ] **Step 1: Grilla (TS) — accion Editar y el `data` nuevo**

En `list-bono.component.ts`, `onNuevo()` pasa a mandar `bono: null`:

```typescript
  onNuevo() {
    this.dialog.open(EditBonoDialogComponent, {
      data: { funcionarioId: this.funcionarioControl.value, bono: null },
      width: '520px', disableClose: true
    }).afterClosed().pipe(untilDestroyed(this)).subscribe(res => { if (res != null) this.onFiltrar(); });
  }

  onEditar(row: Bono) {
    this.dialog.open(EditBonoDialogComponent, {
      data: { funcionarioId: null, bono: row },
      width: '520px', disableClose: true
    }).afterClosed().pipe(untilDestroyed(this)).subscribe(res => { if (res != null) this.onFiltrar(); });
  }
```

- [ ] **Step 2: Grilla (HTML) — Editar como primera opcion del menu**

En `list-bono.component.html`, dentro del `<mat-menu #menu="matMenu">`, antes de Anular:

```html
            <button mat-menu-item (click)="onEditar(row)">
              <mat-icon>edit</mat-icon><span>Editar</span>
            </button>
```

Anular e Imprimir recibo quedan como estan.

- [ ] **Step 3: Borrar el modulo de plantillas**

```bash
git rm -r src/app/modules/rrhh/bono-recurrente
```

- [ ] **Step 4: Sacarlo del modulo y del menu**

En `src/app/modules/rrhh/rrhh.module.ts`: borrar los dos imports de `ListBonoRecurrenteComponent` / `EditBonoRecurrenteDialogComponent` y sus dos entradas en `declarations`.

En `src/app/shared/components/side-mini-variant/side-mini-variant.component.ts`: borrar el import de `ListBonoRecurrenteComponent`, el objeto de menu `{ name: 'Bonos recurrentes', ... }` (cuidando la coma del objeto anterior) y el `case "list-bono-recurrente":` con su `break`.

- [ ] **Step 5: Verificar que no quedan referencias**

Run:
```bash
grep -rn "bono-recurrente\|BonoRecurrente" src/app | grep -v "src/app/modules/rrhh/bono/"
```
Expected: sin salida. Cualquier referencia sobreviviente rompe el build AOT.

- [ ] **Step 6: Verificar el build AOT**

Run: `npm run check`
Expected: sin errores de compilacion.

- [ ] **Step 7: Commit**

```bash
git add src/app/modules/rrhh/ src/app/shared/components/side-mini-variant/side-mini-variant.component.ts
git commit -m "feat(rrhh): editar bonos desde la grilla y quitar la pantalla de plantillas"
```

---

### Task R5: Verificacion integral

Sin codigo nuevo. Lo ejecuta el controller, no un subagente: termina en el gate del usuario.

- [ ] **Step 1: Suite del central**

`./mvnw -o -q -DskipFlyway=true test` — PASS, con los 12 tests del motor intactos y los 6 nuevos de recurrencia.

- [ ] **Step 2: Build del desktop**

`npm run check` — sin errores.

- [ ] **Step 3: Base contra develop**

`git log --oneline HEAD..origin/develop` vacio en ambos repos; `V220.1` sigue siendo mayor que la ultima de develop.

- [ ] **Step 4: Levantar y guion de prueba**

Central en 8081 (sin perfil dev), desktop con `npm start`. Guion:

1. RRHH -> Bonos: el menu ya **no** tiene "Bonos recurrentes".
2. Nuevo bono con toggle **Recurrente** encendido -> se guarda y aparece con el icono `autorenew`.
3. Tres puntos -> **Editar** -> cambiar el monto -> guardar. El bono queda con el monto nuevo.
4. Verificar en la base que `rrhh.bono_recurrente` tomo el monto nuevo (es lo que van a cobrar los meses siguientes).
5. Editar de nuevo y **apagar** el toggle -> el bono se queda, la plantilla queda `activo=false`.
6. Datos de la demo anterior: hay 1 plantilla y 2 bonos de prueba en la base del usuario. Preguntarle si los limpia antes de probar.

- [ ] **Step 5: Esperar aprobacion y preguntar por el push, por separado**

Confirmar que la prueba funciono NO es autorizar el push. Preguntarlo aparte, despues.
