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
