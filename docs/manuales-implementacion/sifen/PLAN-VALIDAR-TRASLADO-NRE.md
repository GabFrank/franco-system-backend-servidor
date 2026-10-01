# Plan — Validar los datos de traslado de la nota de remisión antes de numerarla (central)

Rama: `fix/sifen-validar-traslado-nota-remision` (central, desde `develop` 4aa2e855). Solo central.

## 1. Qué pasa hoy (verificado en bodega producción, 2026-09-30 / 10-01)

SIFEN rechazó tres NRE de la sucursal 13 por datos de traslado que el sistema aceptó sin chistar. Cada
rechazo **quema un número** de la serie (se asigna en `NotaRemisionService.crear`) y obliga a emitir
otra nota.

| NRE | Código | Mensaje SIFEN | Dato guardado |
|---|---|---|---|
| 31 (id 85) | 2208 | Descripción de la ciudad del local de entrega no corresponde al código | entrega `KATUETE` con código `5626` (es FRANCISCO CABALLERO ALVAREZ, sucursal 11) |
| 41 (id 95) | 2108 | Fecha estimada de inicio de traslado es antigua | inicio `2026-10-01`, **fin `2026-09-01`** |
| 42 (id 96) | 2108 | ídem | inicio `2026-10-01`, **fin `2026-09-01`** |

- El 2108 dice «inicio antigua», pero el builder mapea bien (`SifenNotaRemisionBuilder:270-274`,
  `dIniTras` ← inicio, `dFinTras` ← fin) y lo único que separa a la 41/42 de la 39/40/43 aprobadas
  (mismo día, mismo depósito, inicio 2026-10-01) es **fin anterior a inicio**. Inferencia: SIFEN
  reporta ese caso con el 2108.
- El 2208: el diálogo del desktop actualiza ciudad y código juntos solo al elegir el local con el
  buscador (`add-nota-remision-dialog.component.ts:201-205`); la ciudad es texto editable aparte.
- No hay tabla de ciudades SIFEN en el repo ni en jsifenlib (solo `TDepartamento`;
  `CodigosGeograficos` tiene unos pocos distritos con otros códigos). Los pares ciudad/código que SÍ
  se conocen buenos son los de `financiero.timbrado_detalle`: con ellos se emiten las facturas
  aprobadas de cada sucursal.

## 2. Decisiones (revisadas tras la auditoría, §6)

- **D1 — Validar en `NotaRemisionService.validar`**, que corre **antes** de `lockById` y de asignar el
  número (`crear`, :102 vs :106-115): un dato inválido no se numera ni llega a SIFEN. Único camino de
  creación: `NotaRemisionGraphQL.saveNotaRemision` → `crear` (la PWA no emite NRE). Mensaje claro en
  `GraphQLException`; el desktop lo muestra tal cual.
- **D2 — Fechas.** Inicio efectivo = `fechaInicioTraslado`, o si falta la fecha de la nota, o hoy (es
  lo que manda el builder, `SifenNotaRemisionBuilder:270-274`). `fechaFinTraslado`, si viene, no puede
  ser anterior al inicio efectivo. Mensaje: «La fecha de fin del traslado (01/09/2026) es anterior a
  la de inicio (01/10/2026): corregí la fecha de fin». **No** se agrega regla de inicio contra fecha
  de emisión (sin evidencia de que SIFEN la exija).
  Las NRE 41 y 42 no tenían hoja de ruta: las fechas se cargaron a mano. Igual, el prellenado
  (`NotaRemisionPrellenadoService:161-166`) **no** copia la llegada de la hoja de ruta si es anterior a
  la salida, para no precargar un dato que después el validador rechaza.
- **D3 — Ciudad/código contra los pares conocidos de `timbrado_detalle`**, para salida y entrega:
  - consulta **nativa con columnas sueltas** (`codigo_ciudad`, `ciudad`) de las filas con
    `activo IS NOT FALSE` y código no nulo: la entidad mapea solo `id` y con ids compartidos entre
    sucursales Hibernate mezcla filas (comentario del propio repo, :43-50);
  - el código del timbrado es `VARCHAR` y el de la nota `Integer`: se parsea con `trim` +
    `Integer.parseInt` (no numérico → se ignora esa fila), y se compara como número;
  - la descripción tiene que coincidir con **alguna** de las filas de ese código (no la primera);
    normalización: mayúsculas (`Locale.ROOT`), sin acentos, espacios colapsados. Los paréntesis **no**
    se quitan: «CURUGUATY (MUNICIPIO)» es el texto que SIFEN aprobó, y aflojarlo podría dejar pasar
    uno que SIFEN rechaza;
  - código de la nota nulo o que ningún timbrado tiene → no se valida.
  Mensaje: «La ciudad de entrega "KATUETE" no corresponde al código 5626 (SIFEN lo tiene como
  FRANCISCO CABALLERO ALVAREZ): elegí el local con el buscador o corregí la ciudad».
  En bodega hoy cada código tiene una sola descripción (6 códigos, 13 filas activas sin código).
- **D4 — Fuera de alcance:** la tabla completa de ciudades SIFEN (destinos que no son sucursales
  siguen sin validarse y pueden quemar número); trabar el campo ciudad en el desktop.

## 3. Fase única (commit `fix(sifen): …`)

- `repository/financiero/TimbradoDetalleRepository.java`: `findCiudadesConCodigo()` nativa →
  `List<Object[]>` {codigo_ciudad, ciudad}.
- `service/financiero/NotaRemisionService.java`: D2 y D3 en `validar`.
- `service/financiero/NotaRemisionPrellenadoService.java`: guarda de D2 sobre la hoja de ruta.
- Tests (`NotaRemisionValidarTrasladoTest`, Mockito sobre el service; mismo constructor):
  1. fin anterior a inicio → error, y ni `lockById` ni `save` se llaman (no se quema número);
  2. inicio nulo y fin anterior a la fecha de la nota → error;
  3. fin igual a inicio, y fin nulo → pasa;
  4. entrega 5626 con «KATUETE» → error;
  5. misma ciudad con acentos/minúsculas/espacios distintos → pasa;
  6. código que ningún timbrado tiene, y código nulo → pasa;
  7. código del timbrado con espacios («  5626 ») → igual valida;
  8. dos filas con el mismo código y distinta ciudad → pasa con cualquiera de las dos;
  9. salida con par inconsistente → error.
  Prellenado: hoja de ruta con llegada anterior a la salida → fin queda nulo.
  Revertido el fix, 1, 2, 4, 7 y 9 fallan.

## 4. Datos nuevos

Ninguno. Sin migraciones, sin GraphQL, sin desktop.

## 5. Sin verificar

- Que el 2108 sea efectivamente por fin < inicio (inferido de 5 notas del mismo día; ver §1).
- Ciudades de destinos fuera de las sucursales (NRE por factura a un cliente): sin tabla, no se
  validan.

## 6. Auditoría del plan (paso 5)

| Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| B | Inicio nulo + fin viejo pasaba la validación; el prellenado copia la llegada de la hoja de ruta sin chequear | media | D2: inicio efectivo + guarda en el prellenado. Verificado: la 41/42 no tenían hoja de ruta (carga manual) |
| A/B | `TimbradoDetalle` mapea solo `id`: una derivada mezcla filas con id compartido | media | D3: consulta nativa con columnas sueltas |
| A/B | Código `Integer` en la nota vs `VARCHAR` en el timbrado: ceros/espacios → falla silenciosa | media | D3: parseo numérico; test 7 |
| A | Mismo código con descripciones distintas: comparar contra todas las filas | alta | D3: «alguna de las filas»; test 8 |
| A/B | `activo` nulo excluido por `ActivoTrue` | media | D3: `activo IS NOT FALSE`, mismo criterio que `crear` |
| B | Normalizer no cubre paréntesis/puntuación | baja | decidido no aflojar: el texto con paréntesis es el que SIFEN aprobó |
| A | Destinos fuera de las sucursales siguen sin validarse y pueden quemar número | media | anotado en D4 / §5 |
| B | Tests 3/5/6 no prueban el fix (no regresión) | baja | se agregan 1-2, 7, 9 y la verificación de que no se toma el lock |
