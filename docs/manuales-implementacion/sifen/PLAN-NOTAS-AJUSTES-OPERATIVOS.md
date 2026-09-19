# Plan · Ajustes operativos de las notas electrónicas

Segunda tanda sobre el módulo ya mergeado (central #310, desktop #323, filial #130). Sale de
probarlo con datos reales.

Ramas: `feature/sifen-notas-ajustes-operativos` en **central** y **desktop**.
**El filial no se toca.**

> Este plan fue **reescrito después de la auditoría del paso 5**. La primera versión proponía que
> la sucursal 13 emitiera con el `timbrado_detalle` de la central resolviéndolo en código: es
> **imposible**, la base lo rechaza (ver Fase 1). La auditoría encontró además dos huecos de
> concurrencia que la Fase 3 volvería explotables.

---

## 1 · Qué se pide y qué dice el código

| # | Pedido | Hallazgo |
|---|---|---|
| 1 | La sucursal 13 no tiene timbrado; que use el de la central | Sus dos `timbrado_detalle` apuntan a timbrados **inactivos y no electrónicos** |
| 2 | Generar la nota al guardar; que el menú pase a «Imprimir nota de remisión» | Hoy pregunta «¿Enviarla ahora?». **Franco: enviar siempre, sin preguntar** |
| 3 | Elegir sucursal de salida y autocompletar dirección/ciudad/código/departamento | `general.ciudad.codigo` son abreviaturas internas (`SDG`), **pero `financiero.timbrado_detalle` ya tiene `ciudad`, `codigo_ciudad` y `departamento`** de las 18 sucursales, **todas CANINDEYU** |
| 4 | La transferencia 51339 abre el diálogo sin productos | **Es el timbrado**, como sospechabas: sale de la sucursal 13, `prellenar` lanza y el desktop **se traga el error**. Los ítems están bien |
| 5 | Botón «Adicionar» en notas de crédito con buscador de factura | Hoy la NC solo sale del menú de una factura |
| 6 | Lista de remisiones: sucursal «todos» y fechas ayer→hoy | Hoy arranca en la sucursal actual y sin rango |

---

## 2 · Fases

### Fase 1 · central — el timbrado de la sucursal 13 es un dato, no código

> ⛔ **Esta fase está equivocada y no se debe repetir.** Compartir el id 105 entre sucursales es
> válido en el central, pero en las **filiales la PK de `timbrado_detalle` es solo `(id)`**: la fila
> `(105, 13)` trabó la replicación `central_pub` de las 18 filiales el 2026-09-19. Los ids «ya
> compartidos» (89, 93) que se citan abajo no se verificaron contra las filiales. Qué pasó y cómo
> quedó, en §5.

**Por qué la idea original no sirve.** `timbrado_detalle` tiene PK **compuesta `(id, sucursal_id)`**
y `nota_remision` tiene FK a **`(timbrado_detalle_id, sucursal_id)`**. Una nota con
`sucursal_id = 13` **no puede** referenciar el timbrado de la sucursal 1: lo rechaza la base al
insertar. Resolverlo en código era imposible; además `NotaRemisionGraphQL.crearDocumento:210` y
`imprimirNotaRemision:107` vuelven a buscar el timbrado con `findByIdAndSucursalId(id, sucursalId)`,
así que el envío habría fallado para siempre y sin ruta de reintento.

**Lo que sí funciona, y es una sola fila.** La PK compuesta permite que **el mismo `id` exista para
dos sucursales** — y el sistema ya lo hace: los ids `89` (sucursales 20 y 22) y `93` (18 y 24) están
compartidos hoy. Y la unicidad de la serie es
`uk_nota_remision_numero UNIQUE (timbrado_detalle_id, numero_nota_remision)`, **sin sucursal**, y la
numeración sale de `findMaxNumeroByTimbradoDetalleId(id)`, **también sin sucursal**.

Entonces: una migración que inserte `timbrado_detalle (id = 105, sucursal_id = 13, …)` —el mismo id
que el de la central, timbrado 18270044, punto 001— da exactamente lo pedido:

- la FK de la nota se satisface;
- `timbradoElectronicoDe(13)` lo encuentra sin tocar una línea de código;
- `crearDocumento` e `imprimir` lo encuentran con su `findByIdAndSucursalId(105, 13)`;
- **la serie queda compartida con la central** por el UNIQUE y por el `MAX(numero)`, que es lo que
  se pidió;
- el establecimiento del CDC sale de la sucursal 13 (sin código propio → `001`), que coincide.

Es `INSERT INTO` (permitido), **no DDL**: se replica como dato, sin espejo en filial.

Ciudad, código de ciudad y departamento de la fila nueva salen de los del **depósito**
(Salto del Guairá, 4738, CANINDEYU); la dirección, de `empresarial.sucursal`.

⚠️ **Contrapartidas a asumir**, las dos consecuencia directa de compartir serie:
- las notas de la central y del depósito salen **intercaladas** en la misma numeración;
- el número se asigna al **guardar**, no al aprobar, así que una nota abandonada o rechazada
  **deja un hueco** — y ahora el hueco lo puede generar cualquiera de las dos sucursales.

**Además**: el mensaje de «no tiene timbrado electrónico activo» pasa a decir qué hacer.

Tests: la migración se prueba con dry-run; `NotaRemisionPrellenadoTimbradoTest` cubre sucursal con
timbrado, sin timbrado (mensaje accionable) y que el id compartido no rompa la numeración.

### Fase 2 · central + desktop — cerrar los dos huecos ANTES de sacar la confirmación

La auditoría (eje B) encontró que hoy la pausa del «¿Enviarla ahora?» **tapa por accidente** dos
bugs de concurrencia. Sacarla sin arreglarlos primero los vuelve alcanzables con un doble click.

1. **No hay guard de duplicados para origen FACTURA.** `NotaRemisionService.validar` sí lo tiene
   para `TRANSFERENCIA` (línea 169) y `NotaCreditoService` lo tiene para la factura (línea 122),
   pero una nota de remisión **originada en factura** no valida nada: dos clicks = dos notas
   activas y dos números. Se agrega el guard simétrico.
2. **`guardando` se libera antes de terminar.** En el diálogo, `guardando = false` corre apenas
   vuelve el guardado (línea 245), antes de decidir el envío. Pasa a cubrir **todo** el ciclo
   guardar + enviar, así el botón no se reactiva mientras SIFEN responde.

Estos dos no son decisiones de diseño: son defectos que ya existen.

### Fase 3 · desktop — el diálogo de remisión

1. **Enviar siempre al guardar**, sin preguntar (decisión de Franco). Con un **aviso fijo, no
   bloqueante**, en el diálogo: *«Al guardar se emite ante SIFEN y no siempre se puede
   cancelar»* — coherente con lo aprendido en §13.16 del plan anterior, donde una nota aprobada
   quedó incancelable el mismo día.
2. **Selector de sucursal en «Dirección de salida»**: la lupa abre el buscador; al elegir completa
   **dirección** (la de la sucursal, no el nombre), **ciudad**, **código de ciudad** y
   **departamento**, del `timbrado_detalle` de esa sucursal.
3. **CANINDEYU por defecto** en salida y entrega al abrir, si el prellenado no trajo otro.
4. **El error del prellenado se ve**: hoy `onPrellenar` no tiene rama de error y el diálogo queda
   vacío sin explicación — que es exactamente lo que pasó con la transferencia 51339.
5. En el menú de la transferencia, si ya tiene nota, el ítem pasa a **«Imprimir nota de remisión»**.

### Fase 4 · desktop — la lista de remisiones

Sucursal **«Todos»** y rango **ayer → hoy** por defecto.

### Fase 5 · central + desktop — «Adicionar» nota de crédito

- **Central**: query `facturasElectronicasParaNotaCredito(sucursalId, numero, page, size)` que
  devuelva solo candidatas reales. Son **cinco** requisitos, no cuatro: electrónica, aprobada,
  activa, sin NC activa **y con ítems** (`NotaCreditoService:128` también lo exige; sin ese filtro
  el buscador ofrecería facturas que fallan al emitir).
- **Desktop**: botón «+ Adicionar». Como es un punto de entrada **con menos contexto** que el menú
  de una factura concreta, el diálogo muestra **cliente, RUC, fecha y total** de la factura elegida
  y pide confirmación antes de emitir. Una NC equivocada es tan irreversible como cualquier otra.

---

## 3 · Tabla de datos nuevos

Ninguna columna ni clave nueva. La fila de `timbrado_detalle` de la Fase 1 la **escribe** la
migración y la **leen** `NotaRemisionPrellenadoService.timbradoElectronicoDe`,
`NotaRemisionGraphQL.crearDocumento` e `imprimirNotaRemision`. Los campos que completa la Fase 3 ya
existen y ya tienen escritor y lector; cambia de dónde se prellenan. La query de la Fase 5 es de
solo lectura.

---

## 4 · Qué queda sin verificar

- **La sucursal 13 emitiendo de verdad ante SIFEN.** Solo se comprueba emitiendo, y cada aprobación
  consume un número irreversible (§13.16). Se verifica con test y revisando el XML generado, sin
  enviarlo.
- **Ciudades fuera de Canindeyú.** Hoy las 18 sucursales son CANINDEYU.
- **Riesgo propio de la Fase 1**: `enviarLote` reenvía el XML guardado sin reconstruirlo, así que si
  una nota de la sucursal 13 saliera con el timbrado mal armado, «Reenviar» **no** tomaría el
  arreglo: habría que emitir una nota nueva y quedaría otro hueco.

---

## 5 · Cómo terminó (actualizado 2026-09-19)

Las cinco fases están implementadas, probadas en el navegador con datos reales y pusheadas.
Lo que cambió respecto del plan, y lo que se sumó al probar:

### Fase 1: incidente de replicación y estado abierto

La migración `V230.1` (fila `timbrado_detalle (105, 13)`) **se borró de la rama** y la fila se
cargó a mano en producción: primero como `id 117` (id propio y `punto_de_venta_id = 12`) y después,
con un script que reemplazó la 117 por la `(105, 13)`.

**Ese script cortó la replicación.** En el central la PK es `(id, sucursal_id)` y el INSERT es
válido; en las 18 filiales la PK es `(id)` y el INSERT falla por clave duplicada. Desde las 08:42
del 2026-09-19 ninguna filial recibió nada de las 60 tablas de `central_pub`. Se resolvió así:

1. En el central, la fila de la sucursal 13 pasó al **id 118**, sin replicar ese cambio.
2. En las 18 filiales, `ALTER SUBSCRIPTION … SKIP` de la transacción trabada, y se reaplicó a mano
   el resto: DELETE 117, dirección de la sucursal 13, INSERT 118.
3. La `(105, 1)` de la central no se tocó en ningún lado.

**Estado hoy: la 118 choca con la 105.** Mismo timbrado 18270044, sucursal 13 sin
`codigo_establecimiento_factura` (cae al `001`) y mismo punto `001`: las dos filas declaran la serie
`001-001` con contadores separados. `SerieDeNumeracionValidator` **frena las dos sucursales**, la
central incluida (test `conLaFilaDelDepositoEnSuPropioIdTambienSeFrenaLaCentral`). Sin el
validador, emitirían números duplicados. **Hay que resolverlo antes de desplegar**; ver
«Pendiente para el deploy».

La CAJA 1 del depósito (PDV 12) queda solo con la fila 54 (timbrado inactivo, no electrónico),
como antes.

El commit `ccd46d98` agregó la migración y `ecf0dd19` la quitó: el neto de la rama **no tiene
migraciones**. Lo que queda de `ccd46d98` es el guard de duplicados del origen FACTURA (Fase 2).

### Agregado al probar

| Qué | Pieza | Por qué |
|---|---|---|
| `SerieDeNumeracionValidator` | central | La fila 117 mostró que dos filas activas con distinto id y la misma serie (establecimiento + punto) numeran con contadores separados: números duplicados ante la SET. La nota de remisión y la de crédito se niegan a emitir en ese caso. El mismo id en dos sucursales no es colisión de numeración, pero **no se debe usar**: traba la réplica de las filiales. |
| Entrega desde el timbrado del destino | central | El prellenado leía el código de entrega de `general.ciudad.codigo` (`SDG`), que no parsea: la entrega salía siempre sin código. Ahora sale del `timbrado_detalle` de la sucursal destino, igual que la salida. |
| Lupa de entrega | desktop | Misma lupa y misma query que la de salida. |
| Paginador de ítems | desktop | De a 10 (25/50). Quitar por referencia, agregar salta a la última página, la validación lleva el paginador al ítem incompleto. |
| Cabecera «Mercadería» en el KuDE | central | Los ítems salían sin título ni nombres de columna. Va en el `columnHeader` (se repite por página); entran ~13 ítems por página en vez de ~15. |
| `notasRemisionPorTransferencias` | central + desktop | El menú de la transferencia decía «Nota de remisión» aunque ya existiera; se enteraba al hacer clic. Ahora la lista consulta las notas de la página al cargar. Tope de 200 ids. |
| Buscador de facturas por número exacto | central + desktop | Era `LIKE %n%`: «1000» traía 10000, 10008… Además se quitó el `fallbackToLocal` de las queries que solo existen en el central. |

### Auditoría del diff (paso 8)

Tres ejes (autorización, esquema, contrato con clientes). Ningún condicional aplicaba: sin
migraciones, workflows ni replicación en el diff. Sin cambios breaking ni fugas nuevas explotables.
Lo que se corrigió a partir de ella:

| Hallazgo | Corrección |
|---|---|
| `notasRemisionPorTransferencias` no filtraba por sucursal, contra la regla que el repo documenta | Solo devuelve notas de la sucursal de **origen** de cada transferencia (join a `Transferencia`), igual que la consulta individual. El javadoc despegado volvió a su query. |
| `SerieDeNumeracionValidator` ignoraba el timbrado con el que se emite si estaba inactivo o con `activo` NULL (la NC emite con el de la factura) | El timbrado de la emisión cuenta siempre; se comparan contra él las otras filas activas. |
| El validador calculaba la serie distinto que el XML | Punto normalizado con `%03d` como los builders; establecimiento de la sucursal que **emite**, no de la fila. Lee las filas con una query nativa: con ids compartidos Hibernate devolvía una sola instancia para las dos filas. |
| El buscador vacío manda `'%'` y `localesDeSalida` / `facturasParaNotaCredito` lo tomaban como texto: 0 resultados | `'%'` es «sin filtro». |
| La lupa devolvía la dirección del timbrado y el prellenado la de la sucursal | Las dos usan la de la sucursal si está cargada, si no la del timbrado. |

Quedaron anotados sin cambio: `facturasParaNotaCredito` pagina en SQL y filtra en memoria (hoy no
pega: el desktop busca por número exacto y no pagina), y el desktop muestra un toast de error si
llega antes que su central (se evita con el orden de deploy).

Los tests de la Fase 1 que menciona §2 terminaron en `NotaRemisionPrellenadoServiceTest` y
`SerieDeNumeracionValidatorTest`; `NotaRemisionPrellenadoTimbradoTest` no existe.

### Pendiente para el deploy

0. **⛔ Bloqueante: resolver el choque 105 / 118.** Mientras las dos filas estén activas con la
   serie `001-001`, desplegar esta versión deja a la central sin poder emitir notas de remisión ni
   de crédito. La consulta del punto 3 lo muestra (`timbrado 18270044 · 001-001 · {105,118}`).

   **Salida elegida: punto de expedición propio.** El depósito **no vende**: solo emite notas de
   remisión para trasladar mercadería entre sucursales. Lo único que se buscaba era que tenga un
   timbrado electrónico activo, no compartir la numeración de la central. La 118 pasa al punto
   **002** del establecimiento 001 → serie propia **001-002**, con su contador. El 002 de la central
   existe en la fila 106 (inactiva, nunca numeró). Es un UPDATE sobre una fila de id único: replica
   sin problema. La 118 no tiene punto de venta, así que ninguna caja factura con ella.
   Script transaccional fuera del repo (`sucursal-13-punto-propio.sql`), probado en la copia
   local: después la consulta de colisiones sale vacía. **Antes de correrlo en producción,
   confirmar en Marangatu que el punto 002 del establecimiento 001 está habilitado.**
1. **Orden: central antes que desktop.** El desktop nuevo usa `localesDeSalida`,
   `facturasParaNotaCredito` y `notasRemisionPorTransferencias`.
2. **Numeración desde 10 en el timbrado 105.** La nota `001-001-0000009` quedó **aprobada en la
   SET** durante las pruebas. En producción las tablas de notas nacen vacías con el deploy: antes
   de la primera emisión hay que dejar la numeración de la central (fila 105) arrancando en 10, o
   SIFEN rechaza el 9 por duplicado. La serie del depósito (001-002) arranca en 1.
3. **Series sin colisión.** Correr en la base de cada red la consulta de solo lectura que busca
   timbrados electrónicos activos con dos ids en la misma serie; tiene que salir vacía, o el
   validador frena las notas de esas sucursales. Hoy devuelve el choque 105 / 118 del punto 0. Los
   otros choques que aparecen sin el filtro de electrónicos son de los timbrados 16644115 y
   16707780, inactivos y no electrónicos: no emiten notas.
4. **Dirección del depósito**: se cargó «RESIDENCIAL ACUARIO». Si la dirección fiscal declarada
   ante la DNIT es otra, corregir `timbrado_detalle (118, 13)` y `empresarial.sucursal (13)`.
5. **Nunca repetir un id de `timbrado_detalle` entre sucursales** mientras la PK de las filiales
   sea `(id)`. La solución de fondo es una migración en filial que la alinee a `(id, sucursal_id)`.
