# Plan — fix: una SALIDA sin fecha guarda su hora en `fecha_salida`

Rama: `fix/marcacion-fecha-salida` (desde `develop` @ `389da334`). Pieza: **central**, sola.
Complementa `GabFrank/frc-mobile-pwa#60`, que ya corrige la lectura en la PWA.

## El bug

`MarcacionService.prepararMarcacion()` (`:98-109`), cuando una marcación nueva llega **sin ninguna
fecha**, completa `fechaEntrada = now()` **sin mirar el tipo**. La única que manda así es la PWA
(marcación personal y kiosco): desktop (`marcacion.service.ts:140,161`, hora sincronizada con el
servidor) y frc-mobile mandan siempre la fecha del campo que corresponde. Resultado: toda SALIDA de la
PWA queda con la hora en `fecha_entrada` y `fecha_salida` nula. En alpha: 12 SALIDA así, todas de la
PWA; 0 filas con las dos fechas.

Quién lo sufre hoy:

| Lector | Qué hace con una SALIDA de la PWA |
|---|---|
| desktop `marcar-horario.component.ts:373` | `m.fechaEntrada && !m.fechaSalida` → la toma como **entrada** |
| desktop `resumen-marcaciones.component.html:40` | «En Curso» |
| frc-mobile `tipo-marcacion.component.ts:134` | salida vacía |

(`JornadaMarcacionResolver.shouldCreateNewJornada()` mira `getFechaEntrada() != null`, pero **no se
alcanza con una SALIDA**: `resolver()` (`:27-32`) despacha por tipo antes. No es afectado.)

Los calculadores del central (`HorasTrabajadasCalculator:149`, `TardanzaCalculator:57`), el reporte
(`ImpresionService:1210,1223`), los filtros por rango (`MarcacionRepository:26-34`, OR sobre las dos) y
el orden de jornadas (`JornadaRepository:42,48`, COALESCE) ya toleran cualquiera de los dos campos.

## El cambio

En `prepararMarcacion()`, al completar la fecha de una marcación nueva sin fechas: **SALIDA →
`fechaSalida = now()`; cualquier otro caso (ENTRADA o tipo nulo) → `fechaEntrada = now()`**, como hoy.
La hora sigue siendo la del **servidor**; el cliente no manda nada nuevo.

- No cambia firma GraphQL, input, enum, entidad ni esquema: **sin migración**.
- No toca a quien manda la fecha (desktop, frc-mobile): la rama solo corre con las dos nulas.
- `MarcacionGraphQL` ya completa `sucursalSalida` para SALIDA (`:102-106`): no hace falta tocarlo.
- Regla 5 de la PWA / paso 7 («método paralelo `Mobile`»): **N/A** porque el desktop nunca entra por
  esta rama [ev: `marcacion.service.ts:140,161` del desktop siempre setea la fecha].

## Fase única (un commit, un push)

1. `MarcacionService.prepararMarcacion()`: la bifurcación por tipo, con el porqué en el comentario.
2. Test nuevo `MarcacionServiceFechaPorTipoTest` (mismo armado que `MarcacionServiceHorarioSyncTest`,
   por `save()` con mocks, sin usuario para que no procese jornada):
   - SALIDA sin fechas → `fechaSalida` cerca de `now()`, `fechaEntrada` nula;
   - ENTRADA sin fechas → `fechaEntrada`, `fechaSalida` nula;
   - tipo nulo sin fechas → `fechaEntrada` y tipo `ENTRADA` (comportamiento de hoy);
   - SALIDA **con** `fechaSalida` del cliente → no se pisa; ENTRADA con `fechaEntrada` → idem.
   - **Revertir el fix y comprobar que falla el caso SALIDA.**
3. Docs: nota en este directorio (se borra al cierre) y gotcha en el `CLAUDE.md` si corresponde.

Batería: `./mvnw clean verify -B -DskipFlyway=true`. Veredicto: `gh pr checks`.

## Datos nuevos

`N/A para central porque [ev: no nace campo, columna ni clave; cambia qué columna existente se
completa]`. Migraciones: N/A.

## Qué queda sin verificar

- **Las 12 filas ya escritas** en alpha (y las que haya en farmacia/bodega desde la PWA) quedan como
  están: los lectores con fallback las muestran bien; el desktop no. Corregirlas sería un `UPDATE` sobre
  una tabla que **replica en las dos direcciones** — fuera de este PR; se decide aparte.
- Prueba de runtime: central local con perfil `dev` + una marcación de salida sin fecha, o, después
  del deploy de alpha, una salida desde la PWA y ver `fecha_salida` en la base.

## Premisas y supuestos

- **Hibernate manda `NULL` explícito** en `fecha_entrada`: `Marcacion` es `@Entity` sin
  `@DynamicInsert`, sin `insertable=false` ni `@ColumnDefault`, así que el `DEFAULT now()` de la
  columna no se dispara. Si algún día se agrega `@DynamicInsert`, una SALIDA volvería a tener
  `fecha_entrada`.
- **El tipo llega siempre.** Con `tipo` nulo la rama cae a ENTRADA, como hoy. En alpha las 12 SALIDA
  de la PWA traen `tipo_marcacion = 'SALIDA'`.
- **El filial tiene la misma rama** (`filial/MarcacionService.java:94-96`), dormida: la PWA solo habla
  con el central (`api-por-host.ts`) y el desktop, que sí escribe en el filial, siempre manda fecha.
  No se toca en este PR; queda anotado por si cambia el enrutamiento de clientes.

## Auditoría (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Filial con la misma rama, hoy inalcanzable | Verificado; anotado en «Premisas», fuera del PR |
| A | El fix depende de que Hibernate no deje actuar `DEFAULT now()` | Verificado (`Marcacion.java`: sin `@DynamicInsert`); anotado como premisa |
| A | Replicación bidireccional: sin NOT NULL, CHECK ni trigger sobre las fechas; publicación por `sucursal_entrada_id` | Sin riesgo |
| A | PWA vieja contra central nuevo: `fechaSalida` pasa a estar cargado, el «—» se resuelve solo para filas nuevas | Sin riesgo; cualquier orden de deploy sirve |
| B | La tabla atribuía a `shouldCreateNewJornada` un efecto sobre SALIDA: falso, `resolver()` despacha por tipo | Corregido |
| B | Rollback: el código viejo procesa bien una SALIDA con solo `fecha_salida` (`obtenerFechaReferencia` y calculadores ya tienen fallback) | Sin riesgo |
| B | Zona horaria: `fecha_salida` es `timestamp` sin zona, el desktop ya escribe ahí con horas correctas | Sin riesgo |
| B | Dependencia de `tipo` no nulo | Anotado como supuesto |
