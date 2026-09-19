# Plan — recibos RRHH: concepto visible y número del vale

Rama: `fix/rrhh-recibos-concepto-numero` (central, desde `develop` 4b4cf106). Pieza única: **central**.

## Pedido

1. «Concepto no se muestra en vales (impresos)».
2. Numeración en los vales: que el recibo muestre el id del vale.
3. Revisar si hay más impresiones de RRHH que no muestren los conceptos.

## Análisis (paso 3) — lo que se midió

Se rellenaron (`fillReport`) todas las plantillas RRHH con datos y se listaron los `JRPrintText`
que salen vacíos. Test descartable `ProbeRrhhTextosVaciosTest`, no se commitea.

| Plantilla | `develop` | `master` (producción) |
|---|---|---|
| `recibo-rrhh.jrxml` (A4) | ok | ok |
| `recibo-ticket-58.jrxml` | ok | ok |
| `recibo-ticket-80.jrxml` | ok | **encabezado `Concepto` / `Monto` vacío** (9 pt en height 12) |
| `recibo-liquidacion`, `recibo-finiquito`, `acta-advertencia`, `nomina-mes`, `resumen-ips`, `reporte-rrhh-generico` | ok | ok |

- El caso de 80 mm ya está corregido en `develop` (`c168fa03`), pero **no está en `master`**. Llega
  a producción con el próximo release: no requiere código.
- **Ticket térmico ESC/POS** (`ReciboTicketEscPos.build`, lo que realmente sale por la impresora
  térmica): **nunca tuvo encabezado `Concepto | Monto`**, ni en `master` ni en `develop`. Lo usan
  los 7 recibos (vale, penalización, aguinaldo, préstamo, bono, finiquito y liquidación mensual).
- **Contenido del concepto del vale**: `reciboValeBase64` imprime solo `motivo.nombre (fecha)`. La
  **observación** que se escribe al cargar el vale (RRHH y tesorería la llaman «Observación») no
  sale en ninguna impresión. Préstamo (descripción), bono (motivo) y penalización (descripción) sí
  imprimen su texto libre: el vale es el único que no lo hace.
- Número: ningún recibo firmable muestra el id. Solo el acta de amonestación tiene número.

## Alcance aprobado (2026-09-19)

El usuario aprobó el plan y lo amplió: **número en todos los recibos RRHH** y **observación en
todos los recibos cuya entidad la tiene**. Aclaró que lo que no aparecía era la observación.

| Recibo | Número | Observación |
|---|---|---|
| Vale | `vale.id` | `vale.observacion` |
| Penalización | `penalizacion.id` | — (su `descripcion` ya va en el concepto) |
| Aguinaldo | `aguinaldo.id` | — (la entidad no tiene) |
| Préstamo | `prestamo.id` | `prestamo.observacion` |
| Bono | `bono.id` | — (su `motivo` ya va en el concepto) |
| Finiquito | `liquidacion_final.id` | `liquidacion_final.observacion` |
| Liquidación de sueldo | `liquidacion_sueldo.id` | `liquidacion_sueldo.observacion` |

Cambio de diseño respecto de la versión auditada: la observación **no** va dentro del concepto del
vale sino en una **línea propia `Obs.: …` debajo del total**, igual en todos los recibos. En
finiquito y sueldo la observación es del documento, no de una fila, y sus tickets comparten
plantilla con el vale: una sola forma para todos.

## Fase 1 — un commit (`fix(rrhh): ...`)

Implementación final (reemplaza los puntos 2 a 4 de abajo donde contradicen):

- Número: en las plantillas genéricas y en ESC/POS se agrega al título en Java
  (`… Nro. <id>`). En `recibo-finiquito.jrxml` y `recibo-liquidacion.jrxml` el título es fijo:
  nuevo parámetro `numero`.
- Observación: parámetro `observacion` en las 5 plantillas y en `ReciboTicketEscPos.build`.
  Normalizada (`\s+` → espacio, `trim`); vacía → la línea no se imprime (`printWhenExpression`
  + `isRemoveLineWhenBlank`). En el A4 de sueldo va en el hueco libre a la izquierda de los
  totales, en las dos copias, con `textAdjust="ScaleFont"` porque ese layout es fijo.

1. `ReciboTicketEscPos.build`: fila de encabezado `Concepto` … `Monto` (bold, `dosColumnas`) entre
   la línea separadora y las filas. Alcanza a los 7 tickets ESC/POS.
2. `ReporteRrhhService.reciboValeBase64`:
   - concepto = `motivo.nombre` (o `ADELANTO DE SALARIO` / `VALE` si no tiene motivo)
     + ` - OBSERVACION` si la tiene + ` (fecha)`. Mismo formato que préstamo y bono.
   - título = `RECIBO DE VALE|ADELANTO Nro. <id>`, armado en Java. En los tickets PDF el título
     estira (`isStretchWithOverflow`); en A4 tiene 400 px a 13 pt. Se usa `Nro.` por consistencia
     con el texto plano del ticket (el `°` existe en cp437, pero no en todas las térmicas).
   - la observación se normaliza (`\s+` → un espacio, `trim`) y solo se agrega si queda no vacía.
3. `ReciboTicketEscPos`: el título pasa por `wrap(…, cols)`; `wrapAncho` corta también la palabra
   que no entra en la **primera** línea (`maxPrimera`), no solo la que supera `cols`.
4. `recibo-rrhh.jrxml` (A4): `isStretchWithOverflow="true"` en concepto y monto del detail. Hoy
   un concepto largo se corta en silencio a una línea (también préstamo y bono con descripción larga).

### Tests (los dos primeros tienen que fallar con el código viejo)

- `ReciboTicketEscPosTest` (nuevo): decodifica el payload y verifica la línea `Concepto … Monto`
  en 58 y 80 mm.
- `ReporteRrhhServiceReciboValeTest` (nuevo, mocks): vale con motivo, observación e id → el ticket
  ESC/POS contiene `Nro. <id>`, el nombre del motivo y la observación. Vale sin motivo ni
  observación → `VALE (fecha)`. Y el PDF A4 rellenado contiene `Nro. <id>` y la observación.
- `ReciboRrhhJrxmlTest`: además de los encabezados, verificar que el **valor** de la fila
  sobrevive al fill **completo**, con un concepto de >120 caracteres que termina en `(fecha)`:
  `getFullText()` devuelve el texto ya recortado, así que una observación corta no detecta el corte.
  Falla hoy en A4.
- `ReciboTicketEscPosTest` además: ninguna línea supera 32/48 columnas con una palabra larga al
  principio del concepto y con título largo; el payload de liquidación también lleva el encabezado.

## Auditoría del plan (paso 5) — hallazgos y qué se hizo

| Eje | Hallazgo | Sev. | Acción |
|---|---|---|---|
| A | El encabezado ESC/POS también cambia el ticket de sueldo y el de finiquito | baja | aceptado; test lo cubre y el PR lo dice |
| A | Título ESC/POS sin `wrap`: en 58 mm un id largo se corta a mitad | baja | aplicado (punto 3) |
| A/B | `\n` en la observación descuadra el ticket; observación solo con espacios | media | aplicado (normalización) |
| B | A4: concepto sin stretch, se recorta en silencio (perdía también la fecha) | alta | verificado en `recibo-rrhh.jrxml:81`; aplicado (punto 4) |
| B | El test propuesto no detectaba el recorte (`getFullText` ya recortado) | alta | aplicado: concepto largo + `(fecha)` final |
| B | `wrapAncho` no corta la 1ra palabra entre `maxPrimera` y `cols` → línea de 35 en 58 mm | baja | verificado en código; aplicado (punto 3) |
| B | Rollback, migración, datos, cliente viejo | — | sin riesgo: solo Java + jrxml, contrato GraphQL idéntico |

## Tabla de datos nuevos

No nace ninguna columna, campo GraphQL ni enum; sin migración. Nacen **parámetros de plantilla**
y un argumento de `ReciboTicketEscPos.build`, cada uno con escritor y lector:

| Dato | Escritor | Lector |
|---|---|---|
| título con `Nro. <id>` (7 recibos) | `ReporteRrhhService.reciboRrhh` / `tituloConNumero`; `ReciboLiquidacionService.tituloTicket` | `recibo-rrhh`, `recibo-ticket-58/80` (`titulo`), ESC/POS |
| `numero` | `ReporteRrhhService.finiquitoBase64` (A4), `ReciboLiquidacionService.generarBase64` (A4) | `recibo-finiquito.jrxml`, `recibo-liquidacion.jrxml` |
| `observacion` (vale, préstamo, finiquito, sueldo) | `reciboRrhh`, `finiquitoBase64`, `ReciboLiquidacionService` (A4 recortada a `MAX_OBSERVACION_A4`=300, ticket y ESC/POS enteras) | las 5 plantillas y `ReciboTicketEscPos.build` |

Plantillas tocadas: las 5 (`recibo-rrhh`, `recibo-ticket-58`, `recibo-ticket-80`,
`recibo-finiquito`, `recibo-liquidacion`).

## Fuera de alcance / decisiones para el usuario

- Número en los demás recibos (penalización, aguinaldo, préstamo, bono, finiquito): no se pidió.
  Se agrega igual que el del vale si el usuario lo aprueba.
- Encabezado de 80 mm en producción: sale con el próximo release de `develop` → `master`.

## Auditoría del diff (paso 8)

| Eje | Hallazgo | Sev. | Acción |
|---|---|---|---|
| Fijo 1 | Los 7 `imprimirRecibo*` no llaman a `seg.*` ni validan dueño: cualquier usuario autenticado que recorra ids baja el recibo de otro (verificado: `ReporteRrhhGraphQL` solo gatea el acta; `LiquidacionSueldoGraphQL.imprimirReciboLiquidacion` sin control). **Preexistente**; este diff suma la observación al contenido | alta (previa) | fuera de esta fase: fix aparte propuesto al usuario (tocar el autoservicio de la PWA) |
| Fijo 1 | `SecurityConfig` `antMatchers("**/graphql/**")` sin `/` inicial podría no cubrir `/graphql` | sin verificar | anotado para el mismo fix aparte |
| Fijo 2 | Los 28 caminos (7 recibos × formatos) tienen escritor y lector; layout sin superposición | — | verificado |
| Fijo 2 | A4 de sueldo con `ScaleFont`: observación muy larga baja de 6 pt sin error | baja-media | verificado (700 caracteres → 4 pt); aplicado: tope 300 con `...` solo en A4 + tests |
| Fijo 2 | Tabla de datos nuevos del plan desactualizada | baja | aplicado |
| Fijo 3 | Contrato GraphQL idéntico; desktop pasa el payload sin leerlo; filial y móviles no usan esto | — | verificado |
| Fijo 3 | Concepto con texto libre (`\n`, dobles espacios) en ESC/POS | baja | aplicado: `filaConcepto` normaliza con `textoEnUnaLinea` + test |
| Fijo 3 | Caracteres fuera de cp437 (emoji) en observación | baja | no aplicado: la librería codifica cp437 y reemplaza por `?` sin error |

## Qué queda sin verificar

- La impresión física en una térmica real (ESC/POS): se verifica el payload decodificado, no el
  papel. Probar con una térmica de 80 mm y otra de 58 mm antes de aprobar el PR.
- El PDF se verifica por el texto del fill, no a ojo.
