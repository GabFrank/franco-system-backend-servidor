# Bonos recurrentes: scheduler generador

Fecha: 2026-09-08
Repos: `franco-system-backend-servidor` (central), `frc-sistemas-integrados-angular` (desktop)

## Problema

Hoy `rrhh.bono` tiene los campos `es_recurrente` y `frecuencia`, y el desktop
expone un toggle "Recurrente" con un select de frecuencia. Nada los lee.

Verificado sobre el codigo:

- `BonoGraphQL:67-68` los persiste desde el input; `BonoService:54` solo
  defaultea `esRecurrente = false`. No hay otra lectura logica en `src/main`.
- No existe scheduler de bonos. Los `@Scheduled` de RRHH son
  `PenalizacionScheduler`, `PrestamoCuotaScheduler`, `VacacionPrescripcionScheduler`
  y `RrhhNotificacionScheduler`.
- `LiquidacionSueldoService:281-288` toma bonos por `fecha` dentro del periodo y
  sin `liquidacionId`. Una vez liquidado el bono queda marcado
  (`LiquidacionSueldoService:673-675`) y no vuelve a aparecer. No hay rama
  alternativa para recurrentes.

Consecuencia: un bono marcado MENSUAL se paga **una sola vez**, en el mes de su
`fecha`. RRHH tiene que cargarlo a mano, funcionario por funcionario, todos los
meses. El toggle y el icono `autorenew` de la grilla sugieren lo contrario.

La tabla `rrhh.bono` esta vacia en la base del usuario (0 filas), asi que no hay
datos legacy que migrar.

## Objetivo

Un job que genere automaticamente el bono de cada periodo a partir de una
plantilla de recurrencia, sin tocar `LiquidacionSueldoService`.

## Decisiones tomadas

| Decision | Elegido | Descartado |
|---|---|---|
| Modelo | Tabla plantilla separada `rrhh.bono_recurrente` | Auto-referencia dentro de `rrhh.bono` |
| Momento | Genera al inicio del periodo (fecha = dia 1) | Al cierre del periodo; dia configurable |
| Cortes | Funcionario inactivo/egresado + idempotencia | Tope de ocurrencias |
| Apagado | Toggle `activo` en la plantilla | `vigencia_hasta`; borrado |
| Frecuencias | Solo MENSUAL | Las cinco del enum |
| Alta a mitad de mes | Genera el mes corriente al guardar la plantilla | Arranca el mes siguiente |
| Estrategia de cron | Diario, genera el periodo corriente (auto-reparable) | Solo el dia 1; catch-up de N meses |

El cron diario se eligio porque si el backend esta caido o desplegando el dia 1,
el job del dia 2 genera igual, con fecha del dia 1, y entra a la liquidacion del
mes correcto. Un cron que corre solo el dia 1 pierde el mes en silencio. El
catch-up de varios meses se descarto porque generaria bonos con fecha en
periodos ya liquidados.

## Modelo de datos

Migracion Flyway: siguiente entero libre despues de `V216.5`. Confirmar el
numero con el skill `flyway-migraciones-frc` al momento de crear el archivo, y
re-verificarlo despues de rebasar sobre `origin/develop`.

```sql
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

ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS bono_recurrente_id BIGINT
    REFERENCES rrhh.bono_recurrente(id);
ALTER TABLE rrhh.bono ADD COLUMN IF NOT EXISTS periodo VARCHAR(7);  -- 'YYYY-MM'

CREATE UNIQUE INDEX IF NOT EXISTS uq_bono_recurrente_periodo
    ON rrhh.bono(bono_recurrente_id, periodo)
    WHERE bono_recurrente_id IS NOT NULL;
```

Solo `ADD COLUMN`, sin `DROP` ni `RENAME` ni cambio de tipo, segun la tabla
permitido/prohibido del CLAUDE.md del backend.

Tres puntos que no son obvios:

**El indice unico parcial es la idempotencia real**, no un chequeo en Java. El
`WHERE bono_recurrente_id IS NOT NULL` deja fuera a los bonos manuales, que
siguen sin restriccion. El service igual consulta antes de insertar para no
ensuciar el log, pero si el cron y un `saveBonoRecurrente` corren a la vez, es
la base la que garantiza que no haya duplicado.

**`periodo` es `VARCHAR(7)` y no una fecha.** Existe solo como clave de
idempotencia. El bono generado es editable: si RRHH cambia la `fecha` de un mes
puntual, `periodo` no se mueve y el job sigue sabiendo que ese mes ya se genero.
Atar la idempotencia a `fecha` reabriria la puerta al duplicado.

**No hay `vigencia_desde`.** Al crear la plantilla se genera el mes corriente,
asi que la fecha de alta es implicitamente el inicio; `creado_en` alcanza para
auditar.

### `es_recurrente` y `frecuencia` en `rrhh.bono`

Quedan en la tabla (la regla del repo prohibe `DROP`) pero cambian de
significado: pasan a ser **marcadores de salida**. El generador los setea
(`true` / `MENSUAL`) en los bonos que crea, con lo cual el icono `autorenew` de
`list-bono.component.html:59` por fin dice la verdad. Se quitan de `BonoInput` y
del dialog manual, donde eran una promesa falsa.

## Generacion

### `BonoRecurrenteService`

```java
/** Ids de plantillas candidatas. Sin transaccion. */
public List<Long> plantillasActivas()

/** Genera (o no) el bono de UNA plantilla para UN periodo. Transaccional. */
@Transactional
public Optional<Bono> generarUno(Long plantillaId, YearMonth periodo)
```

Reglas de `generarUno`, en orden:

1. La plantilla existe y tiene `activo = true`.
2. `frecuencia == MENSUAL`. Cualquier otra se ignora en silencio.
3. El funcionario tiene `activo = true` **y** (`fechaEgreso` es null o es
   posterior al dia 1 del periodo).
4. No existe ya un `Bono` con ese `(bono_recurrente_id, periodo)`.
5. Crea el `Bono`: `fecha = periodo.atDay(1)`, `periodo = "YYYY-MM"`,
   `monto`/`tipo`/`motivo` copiados de la plantilla, `esRecurrente = true`,
   `frecuencia = MENSUAL`, `anulado = false`, `liquidacionId = null`,
   `usuario` y `autorizadoPor` heredados de la plantilla.

### `BonoRecurrenteScheduler`

```java
@Scheduled(cron = "${rrhh.bono.recurrente.cron:0 30 6 * * ?}")
public void generarBonosDelMes() {
    YearMonth periodo = YearMonth.now();
    int generados = 0;
    for (Long id : service.plantillasActivas()) {
        try {
            if (service.generarUno(id, periodo).isPresent()) generados++;
        } catch (Exception e) {
            LOGGER.error("BonoRecurrenteScheduler: error en plantilla {} periodo {}", id, periodo, e);
        }
    }
    if (generados > 0) LOGGER.info("BonoRecurrenteScheduler: {} bonos generados para {}", generados, periodo);
}
```

**El bucle vive en el scheduler, no dentro del service, y eso es deliberado.** Si
`generarUno` se llamara desde otro metodo del mismo bean, Spring lo invoca
directo y se saltea el proxy: el `@Transactional` no aplica y una plantilla que
falla arrastra a todas las demas en la misma transaccion. Iterando desde el
scheduler cada llamada cruza el proxy, asi que cada plantilla tiene su propia
transaccion y su propio `try/catch`.

06:30 y no 06:00 porque `PrestamoCuotaScheduler` ya ocupa las 06:00 y
`PenalizacionScheduler` las 05:00; media hora de separacion evita que tres jobs
peleen por el pool de conexiones el dia 1. El cron es property, se ajusta sin
recompilar.

### Por que se chequean `activo` y `fechaEgreso`

En la practica alcanza con `activo`: las dos rutas de egreso lo apagan junto con
`fechaEgreso` en la misma transaccion (`FuncionarioRrhhService.egresar:125-127`
y `LiquidacionFinalService:614-617`). El chequeo de `fechaEgreso` es defensivo,
para el caso de una fila que llegue con egreso cargado y `activo` sin actualizar
—por replicacion o carga manual en la base—. Cuesta una comparacion y evita
seguir generando bonos para alguien que ya no trabaja.

### Egreso a mitad de mes

Consecuencia conocida y aceptada del corte por funcionario:

- Si alguien egresa el 10 de marzo, `activo` pasa a `false` y el bono de marzo
  **no se genera**. RRHH carga el proporcional a mano si corresponde.
- Si el job ya genero el bono el 1 de marzo y el egreso ocurre el 10, el bono
  **queda** y se paga completo salvo que alguien lo anule. Es visible en la
  grilla y anulable con un clic, pero no se limpia solo.

Se prefiere esto a que el sistema borre un pago que quizas si corresponde.

### Alta a mitad de mes

`saveBonoRecurrente` termina llamando a `generarUno(id, YearMonth.now())` — la
misma ruta que el job, no una copia. Una plantilla creada el 15/03 genera su
bono de marzo con fecha 01/03 al instante, y el paso 4 evita el duplicado si el
job ya lo habia hecho.

## API GraphQL

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
```

| Operacion | Permiso |
|---|---|
| `bonoRecurrente(id)` | `requireVer()` |
| `bonosRecurrentesPage(page, size, funcionarioId, activo)` | `requireVer()` |
| `saveBonoRecurrente(input)` | `requireAnyRole(GESTIONAR)` |
| `cambiarEstadoBonoRecurrente(id, activo)` | `requireAnyRole(GESTIONAR)` |

`BonoRecurrentePage` sigue el formato de paginacion estandar del repo
(`getTotalPages`, `getContent`, `getPageable`, ...) y el filtrado va en el
backend, como `bonosPage`.

No hay mutation de borrado: el toggle `activo` es la salida elegida, y borrar la
plantilla dejaria bonos generados apuntando a una FK muerta.

## Frontend

Pantalla nueva, hermana de la de bonos:

```
src/app/modules/rrhh/bono-recurrente/
    bono-recurrente.model.ts
    bono-recurrente.service.ts
    graphql/graphql-query.ts
    graphql/BonosRecurrentesPage.ts
    graphql/SaveBonoRecurrente.ts
    graphql/CambiarEstadoBonoRecurrente.ts
    list-bono-recurrente/
    edit-bono-recurrente-dialog/
```

Un `graphql-query.ts` por modelo y una clase service Apollo por operacion, que
es la convencion del repo. Se declara en `rrhh.module.ts` y se cuelga del menu
en `side-mini-variant.component.ts` junto al item existente, con
`openTabIfAuthorized(ROLES.RRHH_VER, ListBonoRecurrenteComponent, "Bonos recurrentes")`.

Grilla: funcionario, tipo, monto, frecuencia, slide-toggle de `activo`, fecha de
alta. Dialog: el mismo formulario que el de bono manual menos la fecha (la
define el periodo) y con `frecuencia` fija en MENSUAL — el enum tiene cinco
valores pero solo uno genera, y ofrecer los otros repetiria la mentira que se
esta corrigiendo.

Quitar `esRecurrente` y `frecuencia` de `BonoInput` no rompe otros clientes:
`frc-mobile-pwa` y `frc-mobile` no referencian `BonoInput`, `saveBono` ni
`esRecurrente` (verificado por grep sobre ambos `src/`). El unico consumidor es
el desktop.

Cambio en la pantalla existente: se quitan el toggle "Recurrente" y el select de
frecuencia de `edit-bono-dialog.component.html:33-36`, y los campos
correspondientes de `BonoInput`. Un bono generado sigue siendo editable y
anulable como cualquier otro; si el monto de un mes es distinto, RRHH lo toca
ahi y la plantilla no se entera.

## Testing

JUnit 5 + Mockito con repositorios mockeados, sin contexto de Spring
(`ValeServiceSincronizacionTest` es el molde).

### `BonoRecurrenteServiceTest`

| Caso | Esperado |
|---|---|
| Plantilla activa, funcionario activo, sin bono previo | Genera `Bono` con `fecha = 01/MM`, `periodo = "YYYY-MM"`, `esRecurrente = true`, `bonoRecurrenteId` seteado |
| Ya existe bono de `(plantilla, periodo)` | No genera; `repository.save` nunca se llama |
| Llamado dos veces para el mismo periodo | Genera una sola vez |
| Plantilla con `activo = false` | No genera |
| Funcionario con `activo = false` | No genera |
| Funcionario `activo = true` pero con `fechaEgreso` anterior al periodo | No genera |
| Plantilla con `frecuencia = ANUAL` | No genera |
| Diciembre | `periodo = "YYYY-12"`, `fecha = 01/12` — fija el off-by-one de mes |

### `BonoRecurrenteSchedulerTest`

Tres plantillas donde la del medio tira excepcion: las otras dos se generan
igual y el contador da 2. Es la garantia de que el `try/catch` por plantilla
funciona; si alguien mueve el bucle adentro del service, este test se cae.

### Ya existente

`SchemaEnumsSincronizadosTest` corre solo y falla el build si el `.graphqls` y
el enum de Java se desalinean. No hay que escribir nada, pero va a atrapar un
olvido en el schema nuevo.

## Manejo de errores

1. Excepcion en una plantilla -> `LOGGER.error` con id de plantilla y periodo, el
   bucle sigue. Al dia siguiente el job reintenta esa plantilla, porque
   `generarUno` es idempotente y el bono faltante todavia no existe. Se
   auto-repara sin intervencion.
2. Violacion del indice unico (dos procesos generando a la vez) -> la excepcion
   de la base cae en el mismo `catch`, se loguea, y no hay duplicado. El bono ya
   existe, que es el resultado correcto.
3. El job entero falla -> los bonos no se generan ese dia y el del dia siguiente
   los crea con la fecha correcta del dia 1.

**Fuera de alcance:** no hay alertas ni notificaciones si el job falla varios
dias seguidos; queda visible solo en el log del backend. Se puede colgar de
`RrhhNotificacionScheduler` (que ya existe) en una version posterior.

## Fuera de alcance de esta version

- Frecuencias SEMANAL, TRIMESTRAL, SEMESTRAL y ANUAL.
- Tope de ocurrencias (`cantidad_ocurrencias`).
- `vigencia_hasta` programable.
- Boton manual de "generar periodo X" / regeneracion retroactiva.
- Notificacion a RRHH ante fallos repetidos del job.
- Limpieza automatica de bonos ya generados cuando el funcionario egresa a
  mitad de mes.

## Verificacion pre-push

- Central: `./mvnw test`.
- Desktop: `npm run check` (build AOT).
- Numero de la migracion Flyway re-verificado despues de rebasar sobre
  `origin/develop` (`git log --oneline HEAD..origin/develop` vacio).
- Levantar central 8081, filial 8082 y `npm start`, y esperar la aprobacion
  explicita del usuario antes de pushear o abrir PR.
