# Plan — que el cajero pueda conciliar sin adivinar

**2026-09-16.** Nace de mirar el diálogo «Ventas con tarjeta — caja N» durante el testeo: dos cobros
del mismo monto a horas parecidas son **indistinguibles**, y la única columna identificadora que la
tabla muestra es la menos útil de todas.

---

## 1 · El problema

El cajero termina una venta con tarjeta sin poder registrar el cupón (posterga, el OCR falla, el QR
no se lee, el cupón era duplicado). Queda una `venta_tarjeta` en **PENDIENTE**. Más tarde abre este
diálogo con un puñado de cupones de papel en la mano y tiene que decir cuál va con cuál.

Hoy la tabla muestra: `ID` (de `venta_tarjeta`), Fecha, Terminal, Cobrado, Cupón, Estado.

**Lo que falla:**

- **El `ID` es el de `venta_tarjeta`**, un número que no está impreso en ninguna parte. El número
  que el cajero tiene delante es el de la **venta**.
- **No se ve de quién es.** La consulta filtra por caja y sucursal del lado del servidor
  (`filtrarVentasTarjetaPorCaja(cajaId, sucId, …)`), así que no se puede tocar otra caja — pero
  **no filtra por usuario**, y las cajas cruzan turnos: la 654 de prueba está abierta desde el
  2026-08-27.
- **Dos cobros iguales son iguales.** Mismo monto, misma terminal, minutos de diferencia: no hay
  nada en pantalla que los separe.

Conciliar mal no avisa: los montos coinciden, el estado pasa a COMPLETADO y el cupón queda pegado a
la venta equivocada.

## 2 · Lo que ya existe y no se usa

| dato | ¿está? | ¿se muestra? |
|---|---|---|
| `ventaId` | **sí, ya viaja en `filtrarVentasTarjetaPorCajaQuery`** | no |
| `usuario` | el filial lo expone (`usuario: Usuario` en el type) | no se pide |
| `construirQrPayloadVentaTarjeta()` | **sí, existe y está testeado** | sólo para el QR en pantalla |

El payload ya lleva `idOrigen = venta.id` y `data = cajaId|monto|ventaTarjetaId`, y es el contrato
que **el mobile ya consume** para abrir `RegistroVentaTarjetaComponent`.

## 3 · Los tres pasos

> **Auditado el 2026-09-16 por tres agentes en sectores separados.** Lo que sigue ya incorpora sus
> hallazgos; los que cambiaron el plan están marcados **[auditoría]**.

> ⚠️ **§3.2 y §3.3 NO son un entregable nuevo.** `PLAN-IMPLEMENTACION-FASE-2.md` (tabla de Etapa 5)
> ya lista **«§3.1 · Ticket con seña con QR»**, espejo de `FASE-2-TICKET-FISICO.md` §3.1. Lo de acá
> **corrige y amplía ese ítem**, no lo duplica: el disparador deja de ser el botón «Registrar más
> tarde» y pasa a ser cualquier línea de tarjeta que quede sin cupón. **[auditoría]** — escribir un
> plan paralelo habría producido dos implementaciones con contratos distintos.

### 3.1 · Columnas `Venta` y `Cajero`, y filtro por cajero — ✅ HECHO (2026-09-16)

- Columna **Venta** con `ventaId` — render puro, el dato ya viene (`graphql-query.ts:206`).
- Columna **Cajero** — agregar `usuario { id nickname }` a la query. El campo existe en el type del
  filial y se puebla siempre en el único punto de creación (`venta-touch.component.ts:1133`);
  verificado: las cuatro filas PENDIENTE de la base de prueba lo tienen.
- **Filtro por cajero**, con el usuario actual preseleccionado, **del lado del servidor**.

⚠️ **[auditoría] El filtro NO puede ser en memoria.** La pantalla pagina del lado del servidor
(`[length]="selectedPageInfo?.getTotalElements"`) y sus otros cinco filtros son parámetros de
servidor. Filtrar sobre `dataSource.data` sólo tocaría la página cargada y el total del paginador
quedaría mintiendo — la regla que este módulo ya tiene prohibida. Se agrega `usuarioId` a
`filtrarVentasTarjetaPorCaja` (filial: graphqls + resolver + service + repository).

**No se filtra por usuario por defecto del lado del servidor**: un supervisor que cierra la caja
necesita poder ver todas. El filtro es una opción, no un encierro.

⚠️ **[auditoría] Rebalancear los anchos.** Hoy siete columnas suman 100%. Agregar dos sin tocar el
resto trunca «Terminal» (22%, texto largo).

⚠️ **[auditoría] La columna `Venta` es necesaria pero NO alcanza.** Una venta con dos líneas de
tarjeta produce dos filas con el mismo número de venta — medido: las filas 24 y 25 de la base de
prueba son las dos de la venta 35512. Lo que las separa sin ambigüedad es el `ventaTarjetaId`, que
es justamente lo que lleva el QR de §3.2. Los dos pasos se necesitan.

### 3.2 · Seña impresa al finalizar la venta — ✅ HECHO (2026-09-16)

Por **cada línea de tarjeta que quede sin cupón**, imprimir un ticket chico con el QR, y en texto:
venta, caja, terminal, monto, moneda, hora, con encabezado **COMPROBANTE INTERNO — NO ENTREGAR AL
CLIENTE**.

⚠️ **[auditoría] BLOQUEANTE: no se puede usar `construirQrPayloadVentaTarjeta`.** Esa función lee
`item.venta?.id` y `item.caja?.id` —objetos— y ninguna fuente de este flujo los tiene: el filial
devuelve los escalares `ventaId`/`cajaId` (documentado en `venta-tarjeta.model.ts:5-9`) y la mutation
`saveVentaTarjeta` sólo devuelve `id, sucursalId, estado, monto, creadoEn`. Usarla produciría un QR
con `idOrigen: undefined` **sin lanzar ningún error**. El payload se arma a mano desde los locales,
como ya hace `ventas-tarjeta-caja-dialog.onCompletar` — que llegó a esa solución sin dejarla escrita.

⚠️ **[auditoría] Son TRES puntos de enganche, no uno.** La `venta_tarjeta` la crea el **desktop**, no
el servidor, y el `id` sólo existe dentro de `registrarPagosConTarjeta`
(`venta-touch.component.ts:1128-1195`). Se llega a PENDIENTE por:

| camino | dónde | ¿hay qué imprimir? |
|---|---|---|
| pospuesto | `if (!datos) return` | sí |
| falla `onCompletar` | su callback `error`, asíncrono y aparte | sí |
| falla el `forkJoin` | su `error` externo | **no** — no se creó ninguna fila |

El tercero no puede imprimir nada y necesita otra salida (avisar, no un papel mudo).

**Impresión — RESUELTO: se arma en el BACKEND, en el filial.** El «A definir» de este plan estaba
mal planteado y me llevó a proponer dos veces infraestructura que ya existía. El PDV **no** usa el
`ImpresionService` del desktop para el ticket de venta: lo imprime el **filial**, porque ahí está la
impresora. `venta.service.ts:144` le pasa a la mutation `saveVenta` el `ticket`, el `printerName`
(de `configuracion-local.json` → `printers.ticket`) y el `local`; del otro lado,
`filial/service/impresion/ImpresionService.java` tiene los diseños (`printBalance`, `printGasto`,
`printRetiro`) y `VentaGraphQL:924` ya escribe un QR con la clase `QRCode` del ESC/POS.

Así que la seña **no era un servicio nuevo: era un diseño de ticket más**, modelado sobre
`printRetiro`. Lo único que se arma en el frontend es la **cadena del QR**, porque el contrato de
`codificarQr()` vive ahí y lo comparte el mobile — reimplementarlo en Java sería un segundo lugar
donde desincronizarse en silencio.

⚠️ **El separador.** `codificarQr` une con `-` y `descodificarQr` hace `split('-')` por posición; por
eso `data` usa `|` adentro. Ningún campo puede contener `-`. **[auditoría]** no hay test que cubra un
monto con decimales o negativo.

⚠️ ~~**Es Electron-only.** La impresión térmica no existe en el build web.~~ **FALSO, corregido el
2026-09-16.** Eso valía mientras se asumía que el ESC/POS lo armaba el frontend por IPC. Lo arma el
**filial**: `PrintingService.getPrintService()` → `PrinterOutputStream.getPrintServiceByName()`, o
sea `javax.print` **en la máquina del filial**. La impresora cuelga del servidor, no del cliente. Y
`ConfiguracionService.getConfig()` lee de `localStorage` (línea 698), sin Electron, así que
`printerName` y `local` también están en el build web. **Se puede probar desde el navegador.**

### 3.3 · Escaneo en el diálogo — ✅ HECHO (2026-09-16)

Un input que espera el disparo del lector —**no una cámara**— que decodifica y salta a la fila.

⚠️ **[auditoría] `descodificarQr` no lo consume ningún componente del desktop**: sólo existe en el
espejo de tests del mobile. La mecánica del input se reusa de `scan-terminal-pos-dialog` (debounce
350 ms, Enter del lector, guard de `buscando`), pero **no hay de dónde copiar** la validación.

**[auditoría] Guardas que faltaban en el plan:**

1. **Fila ya COMPLETADO o CANCELADO** — el botón manual está gateado con
   `*ngIf="item.estado === 'PENDIENTE'"`; el escaneo tiene que replicarlo o reabriría el registro
   de un cupón ya conciliado.
2. **QR de otra caja o sucursal** — decir que no es de esta caja, no ignorarlo en silencio.
3. **QR viejo** — una seña de otro día apunta a una caja ya cerrada.
4. **La fila no está en la página cargada** — la tabla trae de a 15 del servidor. «Saltar a la fila»
   tiene que poder traerla, no sólo buscarla en memoria.

## 4 · Por qué los tres y no sólo el QR

El QR es el camino rápido; las columnas son el piso. Los pendientes que **ya existen** no tienen
papel, y toda seña que se pierda, se moje o no se imprima cae en el mismo caso. Sin `Venta` y
`Cajero` en la tabla, esos quedan sin salida.

## 5 · Lo que este plan NO hace

- No filtra por usuario en el backend (ver 3.1).
- No cambia cómo se decide que una `venta_tarjeta` queda PENDIENTE.
- No toca la conciliación en sí (`completar`), sólo cómo se elige la fila.

## 6 · Lo que quedó implementado (2026-09-16)

### §3.2 · La seña

| dónde | qué |
|---|---|
| `filial` | `SenaCuponDto` + `ImpresionService.printSenaCupon()` — el diseño, modelado sobre `printRetiro` |
| `filial` | `SenaCuponInput` + `imprimirSenaCupon(input, printerName, local)` en `venta-tarjeta.graphqls` / `VentaTarjetaGraphQL` |
| `desktop` | `VentaTarjetaService.onImprimirSena()` — arma el QR con `codificarQr()` **desde valores locales** |
| `desktop` | `TarjetaPago` lleva `monedaSimbolo` / `monedaDecimales`, resueltos en `pago-touch` donde está el objeto `Moneda` |
| `desktop` | `venta-touch.registrarPagosConTarjeta` llama en los **dos** puntos que dejan un PENDIENTE |

**El papel**: encabezado `COMPROBANTE INTERNO / NO ENTREGAR AL CLIENTE`, sucursal, local, venta,
cobro (`ventaTarjetaId`), caja, cajero, terminal, monto con su moneda, fecha, el QR, y el pie
«Grapá este comprobante al cupón de la terminal».

**El tercer camino** (falla el `forkJoin`) no imprime nada, como decía el plan: no hay
`ventaTarjetaId` y un papel sin ese número no concilia nada. Sale un aviso `danger` de 12 s, y es el
único caso donde el cobro queda **sin registro de ningún tipo** — ni siquiera PENDIENTE — así que el
cierre de caja tampoco lo va a reclamar.

`printSenaCupon` devuelve `Boolean` y **nunca lanza**: si el papel no sale, el cajero ve los números
en pantalla para anotarlos en el cupón. La venta ya se guardó; reventar ahí no arregla nada.

### §3.3 · El escaneo

Banda propia arriba de los filtros en `ventas-tarjeta-caja-dialog` — es una acción, no un filtro.
Input de lector (debounce 350 ms + `keyup.enter`), y las cuatro guardas de la auditoría:

1. **No es una seña** — prefijo `frc-` y `tipoEntidad === VT`.
2. **Otra sucursal / otra caja** — se dice con el número puesto, no se ignora en silencio. La caja
   cubre también el «QR viejo»: una seña de otro día apunta a una caja ya cerrada.
3. **La fila no está en la página cargada** — nueva query `ventaTarjetaCompletaPorIdQuery` contra el
   filial. Separada de `ventaTarjetaPorIdQuery`, que trae sólo `id`+`estado` porque la usa un poller
   y engordarla le haría arrastrar la terminal y su formato en cada vuelta.
4. **Fila ya COMPLETADO/CANCELADO** — mismo gate que el `*ngIf` del botón manual de la fila.

⚠️ **Sin `distinctUntilChanged`**, a diferencia de `scan-terminal-pos-dialog`. Allá el diálogo se
cierra al acertar; acá queda abierto, así que escanear una seña, cancelar y volver a escanear **la
misma** habría sido descartado por repetido —el reset va con `emitEvent: false`— y el lector quedaba
muerto sin aviso. La guarda contra consultas dobles es `buscandoQr`.

## 7 · Lo que sigue pendiente

- **Prueba manual de §3.2 y §3.3.** Nada de esto se probó todavía contra una impresora real. **No
  hace falta Electron** (ver la corrección en §3.2): lo único que se necesita es que la impresora
  esté en la máquina donde corre el **filial** y que `printerName` coincida con un nombre que
  `javax.print` vea ahí. Si no encuentra la impresora, `printSenaCupon` devuelve `false` y el PDV
  avisa — no rompe la venta.
- **Reimprimir la seña** desde la tabla de conciliación, para cuando el papel se perdió o no salió.
  Hay precedente (`add-factura-legal-dialog`, `reimprimirRetiro`) y esta pantalla tiene dónde
  ponerlo. No es bloqueante: los números quedan visibles en la fila.
- **El ancho del papel** está asumido en 32 caracteres (58 mm), igual que `printRetiro`. Sin
  verificar contra una impresora de 80 mm.
