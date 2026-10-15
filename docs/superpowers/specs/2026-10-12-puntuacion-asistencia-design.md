# Puntuacion de asistencia del legajo

Fecha: 2026-10-12
Repos: `franco-system-backend-servidor` (central), `frc-sistemas-integrados-angular` (desktop)
Origen: TODO-6 de `docs/manuales-implementacion/rrhh/PLAN-TESTEO-MANUAL-RRHH.md`

## Problema

El legajo del funcionario muestra una card "Puntuacion asistencia", un dialogo
de detalle y una pestana "Asistencia". Los tres son maqueta:

- `legajo-funcionario.component.ts:49` tiene `puntuacionAsistencia = 8.4` fijo.
- `legajo-metrica-dialog.component.ts` arma las filas (`22 / 24`, `2`, `1`, `0`,
  `91%`) a mano y muestra el aviso "Modulo en desarrollo".
- La pestana "Asistencia" solo dice "Metricas de asistencia en desarrollo."
- En el central no hay query, servicio ni tabla de metricas de asistencia.

La marcacion de horario ya esta operativa, asi que los datos para calcularlo
existen: `Jornada` (con `minutosLlegadaTardia` y `minutosLlegadaTardiaAlmuerzo`),
`Horario.dias`, `rrhh.feriado` y `rrhh.justificativo` con su catalogo de tipos.

## Objetivo

Que la card, el dialogo y la pestana muestren la asistencia real de cada
funcionario, calculada en el central a partir de las marcaciones.

Decisiones tomadas con Diego (2026-10-12):

1. **Solo informativa.** No descuenta salario ni genera penalizaciones.
2. **Periodo: mes calendario.** La card muestra el mes en curso; el dialogo y la
   pestana tienen selector de mes.
3. **Puntaje por descuento de eventos**, con pesos configurables.
4. **La tardanza del regreso de almuerzo tambien resta.**

Fuera de alcance: puntuacion 360 (TODO-5), metas (TODO-7), detectar y persistir
ausencias, historial de horarios, cambios en la filial.

## Reglas de calculo

Entrada: un funcionario, un anho y un mes.

**Rango evaluado.** Desde `max(primer dia del mes, fecha de ingreso)` hasta
`min(ultimo dia del mes, hoy, fecha del egreso vigente)`. El dia de hoy entra
solo si ya tiene jornada con marcacion de entrada (el dia todavia no termino:
sin marcar no es falta). Un mes futuro o anterior al ingreso da cero dias
esperados. El egreso vigente (no revertido) corta el rango para que un
ex-funcionario no acumule faltas despues de irse.

**Usuario del funcionario.** Las jornadas son por usuario y los justificativos
por funcionario. Se resuelve por la persona compartida (el usuario cuya persona
es la del funcionario), igual que `PenalizacionService.generarPenalizacionesAuto`.
No usar `funcionario.usuario_id`, que es auditoria ("creado por").

**Clasificacion de cada dia del rango**, en este orden:

| Estado | Condicion |
|---|---|
| no laborable (no se devuelve) | El horario no cubre ese dia de la semana, o es feriado activo, o hay un justificativo cuyo tipo es `generadoPorSistema` (vacacion, feriado). Si igual hay jornada ese dia, se ignora para el puntaje. |
| `TARDANZA` | Dia laborable con jornada con entrada, y al menos una llegada tardia (ver abajo). |
| `TRABAJADO` | Dia laborable con jornada con entrada y sin llegadas tardias. |
| `AUSENCIA_JUSTIFICADA` | Dia laborable sin jornada con entrada, con un justificativo cuyo tipo tiene `evitaPenalizacion = true`. |
| `AUSENCIA_INJUSTIFICADA` | Dia laborable sin jornada con entrada y sin ese justificativo. |

El horario que rige cada dia se resuelve con el mismo criterio que
`HorarioResolver`: el horario del funcionario si cubre el dia; si no, un horario
alternativo del usuario para ese dia; un horario sin dias cargados o con
`Dia.TODOS` rige todos los dias. Sin ningun horario, el dia es `NO_LABORABLE`.

**Franco semanal.** Se trabaja seis dias y uno es libre, sin dia fijo (Diego,
2026-10-12). Si el horario del funcionario rige los siete dias de la semana, en
cada semana de lunes a domingo el primer dia sin jornada y sin justificar es el
franco: deja de ser dia esperado y no aparece en el detalle. El segundo ya es
ausencia injustificada. Una ausencia justificada no gasta el franco, y quien
trabaja los siete dias tiene siete dias esperados. La semana se evalua completa
desde el lunes, aunque caiga en el mes anterior. Con un horario de seis dias o
menos no se descuenta nada: el dia libre ya esta fuera del horario.

Si un dia tiene mas de una jornada con entrada, cuenta la primera (menor id):
la tardanza de las siguientes se mide contra la misma hora de entrada y no dice
nada de la puntualidad.

**Llegadas tardias.** Por cada jornada de un dia laborable se cuentan hasta dos
eventos independientes:

- entrada: `minutosLlegadaTardia > TOLERANCIA_TARDANZA_MIN`
- almuerzo: `minutosLlegadaTardiaAlmuerzo > TOLERANCIA_TARDANZA_MIN`

Si la jornada tiene un justificativo con `evitaPenalizacion = true`
(`JustificativoService.findByJornada`), no se cuenta ninguno de los dos.

Nota: la penalizacion automatica solo mira la tardanza de entrada. La cantidad
de llegadas tardias de esta pantalla puede ser mayor que la cantidad de
penalizaciones del mes. Es intencional.

**Totales.**

- `diasEsperados` = dias laborables del rango.
- `diasTrabajados` = dias `TRABAJADO` + `TARDANZA`.
- `llegadasTardias` = suma de eventos de tardanza (entrada + almuerzo).
- `ausenciasJustificadas`, `ausenciasInjustificadas` = conteo por estado.
- `puntualidad` = `TRABAJADO / diasTrabajados × 100`, redondeado a entero.
  `null` si `diasTrabajados = 0`.
- `puntaje` = `10 × (1 − (pesoFalta × ausenciasInjustificadas + pesoTardanza × llegadasTardias) / diasEsperados)`,
  acotado a `[0, 10]`, un decimal. `null` si `diasEsperados = 0`.

Pesos, en `rrhh.configuracion_rrhh`:

| Clave | Default | Descripcion |
|---|---|---|
| `ASISTENCIA_PESO_FALTA` | 3 | Peso de cada ausencia injustificada |
| `ASISTENCIA_PESO_TARDANZA` | 1 | Peso de cada llegada tardia |

Ejemplos con 24 dias esperados: 2 tardanzas → 9,2; 1 falta → 8,8 (8,75);
3 faltas → 6,3 (6,25); 8 faltas → 0.

## Central

Sin tablas nuevas: no se persiste nada, se calcula al consultar.

- `service/rrhh/builder/AsistenciaCalculator` — clase pura, sin Spring ni
  repositorios. Recibe la lista de dias ya armada (fecha, laborable, minutos de
  tardanza de entrada y almuerzo, tiene jornada, justificativo que evita, tolerancia)
  y los pesos; devuelve `AsistenciaResumen`. Aca vive toda la aritmetica.
- `service/rrhh/AsistenciaService` — junta los datos del mes: funcionario y su
  usuario, horarios, feriados activos del rango, jornadas
  (`JornadaService.findByUsuarioIdAndFechaRange`), justificativos
  (`findByFuncionarioIdAndFechaBetween` o equivalente) y la configuracion. Arma
  los dias y delega en el calculator. Una consulta por tipo de dato para todo el
  mes, no una por dia.
- DTOs `AsistenciaResumenDto`, `AsistenciaDiaDto` y el enum `EstadoAsistenciaDia`
  en `service/rrhh/dto`, junto a los demas DTO de RRHH.
- `graphql/rrhh/AsistenciaGraphQL` + `resources/graphql/rrhh/asistencia.graphqls`:

```graphql
enum EstadoAsistenciaDia { TRABAJADO TARDANZA AUSENCIA_JUSTIFICADA AUSENCIA_INJUSTIFICADA }

type AsistenciaDia {
    fecha: String
    estado: EstadoAsistenciaDia
    minutosLlegadaTardia: Int
    minutosLlegadaTardiaAlmuerzo: Int
    justificativo: String   # nombre del tipo, si hay
}

type AsistenciaResumen {
    anho: Int
    mes: Int
    tieneHorario: Boolean
    diasEsperados: Int
    diasTrabajados: Int
    llegadasTardias: Int
    ausenciasJustificadas: Int
    ausenciasInjustificadas: Int
    puntualidad: Int        # null sin dias trabajados
    puntaje: Float          # null sin dias esperados
    dias: [AsistenciaDia]   # solo los dias laborables del rango
}

extend type Query {
    asistenciaFuncionario(funcionarioId: ID!, anho: Int!, mes: Int!): AsistenciaResumen
}
```

  Misma seguridad que el resto de las queries del legajo.
- Migracion `V244.3__rrhh_config_asistencia.sql` que inserta las dos claves si
  no existen (243 era el mayor en todas las ramas remotas al 2026-10-12).

Errores: funcionario inexistente → error GraphQL como el resto del legajo.
Funcionario sin usuario o sin horario → resumen con `tieneHorario = false`,
ceros y `puntaje = null`; no es un error.

## Desktop

- `legajo/graphql/AsistenciaFuncionario.ts` + metodo en `legajo.service.ts` +
  tipos en `legajo.model.ts`.
- **Card** (`legajo-funcionario`): al cargar el funcionario pide el mes en curso
  y muestra `puntaje / 10`; si `puntaje` es null muestra "Sin datos".
- **Dialogo** (`legajo-metrica-dialog`, solo `tipo = 'asistencia'`): recibe el
  `funcionarioId`, muestra selector de mes y las cinco filas actuales con datos
  reales, precedidas por una fila con el puntaje del mes
  (`diasTrabajados / diasEsperados`, llegadas tardias, ausencias
  justificadas, ausencias injustificadas, puntualidad con barra). Sin el aviso
  de "Modulo en desarrollo". Los tipos `puntuacion` y `metas` no cambian.
- **Pestana Asistencia**: selector de mes y tabla dia por dia (fecha, estado,
  minutos de tardanza de entrada y de almuerzo, justificativo). Estado vacio
  cuando no hay dias esperados o no hay horario asignado.
- El desktop no calcula nada: solo presenta lo que devuelve el central.

## Limitaciones conocidas

- El horario no tiene historial: un mes pasado se evalua con el horario actual.
- Quien trabaja sin marcar figura con ausencia injustificada hasta que RRHH
  cargue el justificativo.
- Un dia trabajado en feriado o fuera del horario no suma ni resta.

## Verificacion

- `AsistenciaCalculatorTest` (JUnit, junto a `AguinaldoCalculatorTest`): mes
  perfecto, tardanza de entrada, tardanza de almuerzo, ambas el mismo dia,
  tolerancia en el limite, tardanza justificada, ausencia justificada e
  injustificada, piso en 0, sin dias esperados, sin dias trabajados.
- Compilacion del central y `npm run check` en el desktop (Karma esta roto).
- Prueba en pantalla a cargo de Diego con el backend levantado: un funcionario
  con horario y marcaciones del mes, comparando contra el historial de
  marcaciones.
