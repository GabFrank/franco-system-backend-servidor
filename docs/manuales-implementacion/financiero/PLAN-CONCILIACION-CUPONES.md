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

**El papel** (ajustado el 2026-09-16 con la impresora delante):

```
   NO ENTREGAR AL CLIENTE
        [ QR ]
   Venta 35518 / Cobro 36
    32.000 Gs.  16-09 16:17
```

La primera versión llevaba encabezado de tres líneas, tres separadores de 32 guiones, sucursal,
local, caja, cajero, terminal, fecha en renglón propio y un pie de dos líneas: **salía el triple de
largo que el ticket de la venta que acompaña**, y es un papel que se grapa a otro papel. Quedaron
tres renglones y el QR. Lo que sobrevive es lo mínimo para conciliar a mano si el QR no se lee:
**Cobro** —el id de `venta_tarjeta`, único dato que desempata dos cobros de la misma venta—, la
venta, el monto y la hora. Caja, cajero y terminal salieron: la caja y el monto ya viajan dentro del
QR, y la terminal la dice el cupón que se está grapando.

⚠️ **El `feed(1)` inicial no es decoración, es funcional.** Al sacarlo, la impresora se comió los
primeros bytes del trabajo: desapareció el encabezado y el `GS` que abre el comando del QR, con lo
cual el resto del comando salió **impreso como texto** (`(k1E1Venta 35518 / Cobro 36`) y no hubo QR.
Con una línea de sacrificio adelante sale todo. Medido con papel, no deducido — y explica por qué el
diseño viejo, que arrancaba con `feed(2)`, nunca mostró el problema. Vale para cualquier diseño
nuevo sobre esta impresora (Xprinter 58 mm).

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

- ✅ **§3.2 probado contra la impresora real (2026-09-16).** Se confirmó que **no hace falta
  Electron**: la mutation se disparó desde el navegador y el papel salió por la impresora del host
  del filial (`javax.print`). Quedó pendiente sólo el escaneo de §3.3 end-to-end.

  ⚠️ **Trampa encontrada en la prueba: `printerName` puede llegar `undefined` sin que nada avise.**
  `ConfiguracionService.getConfig()` lee **localStorage**, no `configuracion-local.json` — ese
  archivo no lo consulta nadie en este camino. El perfil de prueba tenía la config guardada **sin la
  clave `printers`**, así que `getConfig()?.printers?.ticket` daba `undefined`, `printSenaCupon`
  devolvía `false` y no salía papel. La clave legacy `printerTicket` estaba bien puesta pero no
  ayuda: sólo se migra cuando **no** hay config guardada. Cualquier caja que haya guardado su
  configuración antes de que existiera el bloque `printers` tiene el mismo agujero.
- **Reimprimir la seña** desde la tabla de conciliación, para cuando el papel se perdió o no salió.
  Hay precedente (`add-factura-legal-dialog`, `reimprimirRetiro`) y esta pantalla tiene dónde
  ponerlo. No es bloqueante: los números quedan visibles en la fila.
- **El ancho del papel** está asumido en 32 caracteres (58 mm), igual que `printRetiro`. Sin
  verificar contra una impresora de 80 mm. Con el diseño recortado el riesgo bajó: el renglón más
  largo es `Venta 35518 / Cobro 36` (22 caracteres) y ya no hay nada que dependa de rellenar los 32.
- **El QR está en tamaño 6** y a 58 mm entra con margen (78 caracteres de payload). Si se necesita
  acortar más el papel, bajarlo a 5 es lo que más altura ahorra — pero hay que volver a probar el
  lector, que es lo único que valida ese cambio.

---

## 8 · El cierre de caja: salida con motivo, no puerta trabada (2026-09-17)

### El péndulo

Este gate ya se movió dos veces, y las dos por buenas razones:

1. **Advertencia con «Cerrar igualmente»** a mano del cajero. El escape convertía cada venta sin
   registrar en un `NO_COMPLETADO` silencioso: plata cobrada con tarjeta que después no se puede
   conciliar contra la liquidación del proveedor.
2. **Bloqueo total para el cajero**, sólo ADMIN puede forzar. Cerró ese agujero y abrió el opuesto:
   un cupón que no se imprimió o un POS que falló dejaban la caja trabada de noche, esperando a un
   supervisor que no estaba.

### Lo que cambia ahora

**No cambia quién puede: cambia qué queda.** El cajero vuelve a poder cerrar, pero tiene que decir
**por qué**, y eso se guarda con su usuario y la hora en cada fila.

| dónde | qué |
|---|---|
| `filial` | `V102.5` — `no_completado_motivo`, `no_completado_observacion`, `no_completado_por_id`, `no_completado_en`. Con CHECK y FK: es el lado que escribe |
| `central` | `V228.5` — las mismas cuatro columnas, **sin CHECK ni FK**: es el subscriber |
| `filial` | `marcarNoCompletada(id, …)` para un cobro, y `marcarNoCompletadas(caja, …)` para el cierre; las dos exigen motivo |
| `desktop` | `motivo-no-conciliar-dialog` — lista fija + texto libre, obligatorio cuando el motivo es OTRO |
| `desktop` | Acción **«Dejar sin conciliar»** por fila en el diálogo de conciliación |
| `desktop` | El cierre de caja pide el motivo en vez de pedir un supervisor |

Los motivos son cerrados —`CUPON_NO_IMPRESO`, `POS_FALLADO`, `CUPON_PERDIDO`, `OTRO`— porque con
texto libre solo no se puede responder «cuántas veces falló el POS este mes»: cada cajero escribe
distinto. El texto libre queda para lo que la lista no cubre, y ahí es obligatorio.

⚠️ **Por fila y no sólo por caja.** De tres pendientes, dos tienen su cupón y el tercero se perdió;
marcar los tres con el mismo motivo sería escribir dos mentiras para registrar una verdad. El
marcado en bloque sigue existiendo para el cierre, donde el cajero ya decidió por todos.

⚠️ **Orden de despliegue, igual que con `origen`:** `financiero.venta_tarjeta` es BRANCH_TO_MAIN y
su fila en `pg_publication_rel` no tiene column list, así que PostgreSQL publica las columnas nuevas
solo. **Central primero, desplegado y confirmado; recién después el filial.** Al revés, el apply
worker de central se detiene con *missing replicated column* y entra en crash-loop con el slot
reteniendo WAL — el corte del 2026-08-20.

### Lo que sigue abierto

- **El aviso al supervisor.** Los `NO_COMPLETADO` quedan visibles en el diálogo de la caja con su
  motivo y su autor, pero nadie los persigue: no hay pantalla que junte los de todas las cajas ni
  aviso a nadie. Las columnas ya están en central, así que es sólo la vista.
- **Reimprimir la seña** — ✅ hecho el 2026-09-17: acción «print» en cada fila PENDIENTE.

## 9 · Reimprimir la seña (2026-09-17)

Acción por fila en el diálogo de conciliación, sólo sobre PENDIENTE. Reimprime con los datos de la
fila, así que el QR sale idéntico al original salvo el sello de tiempo, que no se valida.

Para cuando el papel no está: no salió, se mojó, se traspapeló. Sin esto la única salida era buscar
la fila a ojo entre cobros del mismo monto, que es justamente lo que la seña existe para evitar.

⚠️ **Y se arregló el modo de falla silencioso que encontró la prueba del 2026-09-16:** si la
configuración guardada de la caja no tiene el bloque `printers` —porque se guardó antes de que
existiera—, `printerName` llegaba `undefined`, `printSenaCupon` devolvía `false` y el cajero leía
«no se pudo imprimir» sin ninguna pista de que el problema era la configuración de su propia caja.
Ahora `onImprimirSena` no llama al filial sin impresora y el aviso dice dónde configurarla.

---

## 10 · Pulidos pendientes del PR (2026-09-17)

Cosas chicas, encontradas corriendo la prueba manual. Ninguna bloquea, pero todas le cuestan
minutos a alguien que no sabe lo que nosotros sabemos.

### 10.1 · El rechazo del cupón manda al lugar equivocado cuando lo que se pegó es una seña

Hay **dos campos de escaneo a un clic uno del otro**, y esperan vocabularios distintos:

| campo | dónde | acepta | rechaza con |
|---|---|---|---|
| «Escaneá la seña del cobro» | arriba de la lista de conciliación | `frc-…` | «Ese código no es la seña de un cobro con tarjeta» |
| «Escaneá el QR del cupón» | dentro del diálogo de completar | `FRCP1*…` | «El código leído no corresponde a ningún formato conocido. Registralo desde el celular» |

El de la izquierda dice **qué es lo que esperaba**. El de la derecha no: dice que no reconoce el
código y propone el celular, que es la salida correcta para un cupón ilegible y la equivocada para
una seña pegada en el campo de al lado. Pasó en la prueba del 2026-09-17: la cadena era correcta y
el campo era el otro.

**Implementado el 2026-09-21** (desktop `47a7b13d`, `qr-pos-parser.ts:84`): antes de devolver el error genérico se mira si la cadena empieza con `frc-`, y en ese caso el rechazo dice *«Eso es la seña del cobro, no el cupón de la terminal. La seña va en el campo de arriba de la lista.»* Dos specs lo cubren (`qr-pos-parser.spec.ts`), **escritos y no ejecutados**: Karma está roto en todo el repo (`require.context`) y el CI del desktop no corre specs. El arreglo, tal como se había propuesto: antes de devolver el error genérico, mirar si la cadena
empieza con `frc-`; si empieza, decir «Eso es la seña del cobro, no el cupón de la terminal. La seña
va en el campo de arriba de la lista». Es una rama de tres líneas y no toca el contrato de parseo.

### 10.2 · Reabrir un cobro marcado NO_COMPLETADO — APROBADO para este PR

Pedido de Gabriel el 2026-09-17: hoy `NO_COMPLETADO` es terminal, y los dos casos en que eso está
mal son reales y frecuentes.

- **Se marcó por error.** El cajero eligió la fila equivocada de tres del mismo monto.
- **Apareció el cupón.** `CUPON_PERDIDO` es literalmente «todavía no lo encontré». Que el papel
  aparezca al día siguiente es el caso normal, no el raro.

Hoy, en los dos, la plata queda sin conciliar para siempre por una decisión tomada con información
incompleta, que es exactamente lo que §8 quería evitar.

**Lo que hay que resolver antes de escribirlo:**

1. **Qué pasa con el rastro.** Limpiar `no_completado_*` borra justamente lo que §8 existe para
   guardar. Lo mínimo honesto es conservarlas y agregar `reabierto_por_id` + `reabierto_en`: un
   nivel de historia, sin tabla nueva. Una tabla de historial completo sería lo correcto en abstracto
   y abre superficie de replicación nueva en una tabla BRANCH_TO_MAIN — no en esta entrega.
2. **Caja cerrada.** El caso realista ocurre *después* del cierre, y el diálogo de conciliación está
   atado a una caja. La pantalla que sirve es `ListVentaTarjetaComponent`, que ya filtra por estado
   y tiene columna de acciones.
3. **Quién.** Deshacer el propio error dentro de la propia caja abierta es del cajero. Reabrir el
   cobro de una caja ya cerrada es de supervisor: es tocar un turno que alguien dio por cerrado.

⚠️ **El costo no es simétrico en el tiempo.** Las columnas `reabierto_*` van sobre
`financiero.venta_tarjeta`, que es BRANCH_TO_MAIN: agregarlas después es **otro** par de migraciones
con la misma secuencia obligatoria central-primero-filial-después. Agregarlas ahora, dentro de
`V102.5` / `V228.5`, es gratis.

**Decidido el 2026-09-17 (Gabriel): entra completo en este PR** —columnas, mutation y UI—, pero
**se escribe recién cuando termine la prueba manual en curso**. Primero se cierra lo que ya está
implementado; después se agrega esto y se prueba **sólo el camino nuevo**, no la guía entera. El
orden importa: meterlo ahora obligaría a reabrir bloques de la prueba que ya pasaron.

Alcance acordado:

| dónde | qué |
|---|---|
| `filial` `V102.5` | `reabierto_por_id` (FK a `personas.usuario`) + `reabierto_en`, en la **misma** migración que las cuatro de §8 |
| `central` `V228.5` | las mismas dos columnas, sin FK — es el subscriber |
| `filial` | `reabrir(id, sucursalId, usuario)`: sólo `NO_COMPLETADO` → `PENDIENTE`; conserva `no_completado_*` y sella quién reabrió y cuándo |
| `desktop` | acción en `ListVentaTarjetaComponent` (la lista general, no el diálogo de una caja: el caso real ocurre con la caja ya cerrada), con gate de rol |

#### Alcance corregido el 2026-09-17 — dónde vive la acción

⚠️ **La fila de `desktop` de arriba estaba mal, y la corrige lo que sigue.** Salió al mirar §10.4:
`ListVentaTarjetaComponent` **lee del central** (`onFiltrar` → `servidor = true`) pero todas las
acciones de este módulo escriben en el **filial**. Poner ahí `reabrir` repetía esa costura en la
pantalla donde más duele, porque el supervisor es justamente quien más probablemente entre por la
web contra central, donde **no existe link al filial**: con `isLocal: false`,
`graphql-connection.service.ts:280` no crea el link local y todo va al central, que no tiene
`completarVentaTarjeta`, ni `marcarVentaTarjetaNoCompletada`, ni `crearCapturaCupon`.

**Lo acordado (Gabriel, 2026-09-17): la acción vive en el diálogo del filial, y la lista es sólo la
puerta.**

| dónde | qué |
|---|---|
| `ventas-tarjeta-caja-dialog` | Acá va **«Reabrir»**, al lado de completar y de dejar sin conciliar: las tres contra el filial, con los mismos gates por fila. Con gate de rol |
| `ListVentaTarjetaComponent` | Gana una acción que **abre ese mismo diálogo** para la caja de la fila. La lista sigue siendo el índice; el diálogo es el taller |
| los dos | Cuando `isLocal` es `false` (cliente contra central), el diálogo abre en **modo lectura**: se ven las filas y, de los `NO_COMPLETADO`, el motivo, la observación, quién y cuándo. Las acciones no aparecen y una línea dice que necesitan el servidor de la sucursal |

✅ **El diálogo ya sirve para una caja cerrada, verificado:** recibe `cajaId` como dato, filtra por
él y **no chequea el estado de la caja en ningún lado**. Su comentario de la línea 56 ya dice que
«una caja puede quedar abierta varios días». Dice «de la caja abierta» por dónde se abre hoy (PDV →
Utilitarios), no por una restricción. No hay que adaptarlo.

⚠️ **Y esto arregla un defecto que ya está vivo.** `puedeCompletarDesdeAqui` acota por sucursal
—su javadoc ya razona que «el filial local no la va a tener»— pero **no mira `isLocal`**. Desde la
web contra central, `sucursalActual` existe igual y coincide, así que el botón de completar **se
muestra y después falla** con un error de GraphQL que no menciona la configuración. Se le agrega la
condición en este PR: es una línea, y el hallazgo salió de esta misma discusión.

**Lo que esto NO hace:** no resuelve la conciliación desde central. La hace **explícita y acotada**,
que es lo decidido — el diseño de ese flujo se discute después de este PR. Tampoco cubre el
pendiente de §8 («no hay pantalla que junte los `NO_COMPLETADO` de todas las cajas»): el diálogo es
de una caja por vez.

#### Implementado el 2026-09-21 — qué quedó dónde

| repo | archivo | qué |
|---|---|---|
| `filial` | `V102.5__venta_tarjeta_no_completado.sql` | `reabierto_por_id` + `reabierto_en` en el **mismo** `ALTER TABLE` que las cuatro de §8, con FK `fk_vt_reabierto_por` en dos pasos (`NOT VALID` → `VALIDATE`) |
| `central` | `V228.5__venta_tarjeta_no_completado.sql` | las mismas dos columnas, **sin FK** — es el subscriber |
| `filial` | `VentaTarjeta.java`, `VentaTarjetaService.reabrir()`, `VentaTarjetaGraphQL`, `venta-tarjeta.graphqls` | `reabrirVentaTarjeta(id, sucId, usuarioId)`: sólo `NO_COMPLETADO` → `PENDIENTE`, sella quién y cuándo, **conserva las `no_completado_*`** |
| `central` | `VentaTarjeta.java`, `venta-tarjeta.graphqls` | las dos columnas con `insertable=false, updatable=false` y `ConstraintMode.NO_CONSTRAINT`, igual que `noCompletadoPor`, y expuestas en el schema |
| `desktop` | `ventas-tarjeta-caja-dialog` | acción «Reabrir», modo lectura, y el rastro de reabierto bajo el chip de estado |
| `desktop` | `list-venta-tarjeta` | la puerta al diálogo, el guard de `isLocal` que le faltaba a completar, y el rastro de §8 y de reabrir en la columna de estado |

**El gate de «Reabrir» son DOS, no uno.** El punto 3 de arriba distingue dos actos que no son el
mismo —el cajero que se corrige dentro de su turno, y quien toca un turno que alguien dio por
cerrado— y darles un solo permiso obligaba a elegir entre dejar al cajero llamando a un supervisor
para deshacer su propio error, o dejar que cualquier cajero con el rol tocara el turno cerrado de
otro. El diálogo **nunca miraba el estado de la caja** (recibe `cajaId` y filtra), así que la
distinción no existía; ahora la consulta con `CajaService.onGetByIdSimp` contra el filial:

| caja | rol | por qué ése |
|---|---|---|
| abierta (`EN_PROCESO`) | `VENTA TARJETA COMPLETAR` o `ADMIN` | el mismo con el que completa: es su propio error, dentro de su turno |
| cerrada, o estado desconocido | `ANALISIS DE CAJA` o `ADMIN` | es el rol que **ya** gobierna las cajas cerradas en esta app —lista de cajas, retiros, gastos, observaciones de caja cuelgan todas de él—, así que el permiso no se inventó para esto |

`cajaAbierta` arranca en `false`, así que mientras la consulta no volvió —o si falló— rige el gate
estricto. Fallar hacia el lado que pide más permiso es lo único honesto: la alternativa es abrir la
acción por no haber podido averiguar si correspondía. Y la consulta **no bloquea la carga**: la
tabla ya se ve, lo único que espera es una acción.

⚠️ **Corrección medida el 2026-09-21: en modo lectura la pantalla que sirve es la LISTA, no el
diálogo.** El alcance de arriba decía que con `isLocal: false` el diálogo abriría «en modo lectura».
No puede: `filtrarVentasTarjetaPorCaja` —la query con la que el diálogo carga— **existe sólo en el
filial**; central no la tiene (verificado contra su `venta-tarjeta.graphqls` y sus resolvers). Contra
central el diálogo abriría con la tabla vacía, que es peor que no ofrecerlo.

Lo que se hizo en su lugar, que da lo mismo que se quería sin superficie nueva en central:

- la **puerta** al diálogo sólo aparece con `isLocal` (y en filas de la sucursal actual);
- el **rastro** —motivo, observación en el tooltip, quién, cuándo, y si fue reabierto y por quién—
  se muestra en la **lista**, que lee de central y donde esos campos ya estaban replicados y
  expuestos: sólo faltaba pedirlos. Antes la lista mostraba `NO_COMPLETADO` a secas, sin decir por
  qué ni a quién preguntarle, en la única pantalla que ve los cobros sin conciliar de **todas** las
  sucursales;
- el diálogo conserva igual sus guardas de modo lectura, como defensa en profundidad: oculta las
  cuatro acciones, apaga el lector de QR (que termina en `onCompletar` sin pasar por ningún `*ngIf`)
  y dice con el motivo concreto que hace falta el servidor de la sucursal.

Si alguna vez se quiere el diálogo contra central, lo que falta es exactamente una query de lectura
—`filtrarVentasTarjetaPorCaja`— en central. Es la misma discusión del flujo de conciliación en el
servidor central, que queda para después de este PR.

**Y se arregló el defecto que ya estaba vivo:** `puedeCompletarDesdeAqui` ahora exige `isLocal`. Su
javadoc razonaba sobre «el filial local» dando por sentado que existe; desde la web contra central
no existe, `sucursalActual` coincidía igual, y el botón se mostraba para fallar al apretarlo con un
error de GraphQL que no mencionaba la configuración.

### 10.3 · «Escanear otro» deja el código de la terminal en el campo del próximo escaneo

Encontrado por Gabriel el 2026-09-17 y reproducido con un grabador de DOM en la app en vivo.

**Qué pasa.** En el PDV, al registrar un cobro con tarjeta cuyo cupón no coincide con el monto, el
diálogo ofrece «Registrar igual» / «Escanear otro». Al elegir «Escanear otro»,
`PagoTouchComponent.confirmarDiferenciaCupon()` llama a `escanearTarjeta(item)`, que reabre
`ScanTerminalPosDialogComponent` pasándole `terminalPos: item.terminalPos`. El constructor de ese
diálogo hace:

```ts
if (data?.terminalPos != null) {
  this.selectedTerminalPos = data.terminalPos;
  this.codigoControl.setValue(data.terminalPos.codigo);   // ← queda "VP-CAJA1" en el campo
}
```

Medido en la página, justo después de tocar «Escanear otro»:

```json
{ "value": "VP-CAJA1", "focused": true, "selStart": 8, "selEnd": 8 }
```

El campo tiene el foco, con el texto **puesto y sin seleccionar**, y el cursor al final.

**Por qué importa.** Ese mismo campo —«Código de la terminal o QR del cupón»— es donde el cajero
tiene que pasar el cupón siguiente, y el lector es keyboard-wedge: escribe donde está el cursor. El
resultado es `VP-CAJA1FRCP1*J1K2L3*...`, que **no matchea ningún patrón** (están anclados con `^`) y
tampoco encuentra ninguna terminal por código. O sea: el cajero escanea un cupón perfectamente bueno
y recibe un error que no tiene nada que ver. La única salida es borrar el campo a mano, y nada en la
pantalla se lo dice.

El prefill no está de más en el caso normal —reabrir el diálogo desde el ícono de QR para ver o
cambiar la terminal—, pero en el camino de «Escanear otro» lo que viene es un **cupón**, no un
código de terminal.

**El arreglo — y por qué no alcanzaba seleccionar el texto.** El primer intento fue dejar el prefill
y agregar `input.select()`, para que el escaneo lo pisara. Gabriel lo rechazó con la pregunta
correcta: *¿qué utilidad tiene que el texto se mantenga ahí?*

La respuesta, mirando el template: **el diálogo no muestra la terminal elegida en ningún lado**. No
hay chip, ni nombre, ni nada — `selectedTerminalPos` no aparece en el HTML. El input precargado era
el único indicio, y encima mostraba `codigo` (`VP-CAJA1`, la etiqueta interna) en vez de
`descripcion` (`VALIDAPIX CAJA 1`, que es como el cajero la conoce). O sea: un cartel informativo
puesto adentro del campo donde entra el próximo escaneo.

Lo implementado:

| archivo | cambio |
|---|---|
| `scan-terminal-pos-dialog.component.ts` | El constructor ya **no** precarga `codigoControl`. Sigue recordando `selectedTerminalPos` |
| `scan-terminal-pos-dialog.component.html` | Línea nueva que muestra la terminal de la línea por **nombre** y dice qué hace cada salida |
| `scan-terminal-pos-dialog.component.ts` | `enfocarInput()` agrega `select()` como defensa: si algo vuelve a dejar texto, el lector lo reemplaza |

No se pierde ninguna capacidad: conservar la terminal sin volver a escanear es **Cancelar**, que en
`pago-touch` deja la línea como estaba (`if (!result?.terminalPos) return;`). El botón Confirmar ya
estaba atado a `codigoControl.invalid`, así que con el campo vacío queda deshabilitado — que es lo
correcto, porque sin escanear nada no hay nada que confirmar.

Va en este PR: está en el camino principal del cajero, no en un borde.

### 10.4 · El QR de la app móvil sale del diálogo de completar (2026-09-17)

Encontrado por Gabriel corriendo la venta 3 de INFONET del bloque E: escaneó el QR que ofrecía el
diálogo y no lo llevó a la página de carga. Su pregunta fue la correcta —*¿en qué momento metimos a
la app en esta ecuación?*— y la respuesta es que no la metimos nosotros, pero tampoco la sacamos.

**Lo que había.** El diálogo mostraba **dos QR que se alternaban en el mismo lugar**, y de los dos,
el que salía primero era el de la app:

| QR | valor | cuándo |
|---|---|---|
| app móvil | payload `frc-…` que abre `RegistroVentaTarjetaComponent` de `frc-mobile` | **por defecto** |
| página de captura | la URL que sirve el filial por HTTP en la LAN | sólo tras tocar «Sacar foto sin la app» |

**Por qué importaba.** Ese payload lleva a la pantalla de `frc-mobile`, que escribe con
`updateVentaTarjeta` del **central** (`VentaTarjetaGraphQL.java:121`), un setter pelado:

```java
if (input.getEstado() != null) entity.setEstado(input.getEstado());
```

Sin `completar()` del filial, o sea: sin el chequeo de cupón repetido (`motivoCuponNoUsable`), sin
control de monto, y sin mirar si el cobro ya estaba conciliado. Y en una terminal `tipo = MAQUINA`
—las que **no** imprimen QR en el ticket, cuyo único camino real es la foto— ese era justamente el
QR que se mostraba primero. El camino por defecto era el único que esquivaba todas las guardas que
esta entrega construyó.

**Ya estaba anotado, y subestimado.** `PLAN-IMPLEMENTACION-FASE-2.md:1545` dice que la pantalla de
`mobile` es un camino heredado y **no** el de la fase 2. El hallazgo **B1** de la auditoría se bajó
de alto a bajo con el argumento de que «los dos caminos vivos —lector del PDV y foto— sí pasan por
`completar()`». Ese argumento no se sostenía mientras esta pantalla ofreciera el tercero, y de
primera.

**Lo implementado** (decidido por Gabriel el 2026-09-17: sacarlo del diálogo):

| archivo | cambio |
|---|---|
| `registrar-venta-tarjeta-dialog.component.html` | Se va el `<ngx-qrcode [value]="valorQr">`. Queda el botón, ahora **«Sacar foto con el celular»**, y una línea que dice qué hace: abre un código para cualquier teléfono, servido por este servidor en la red del local |
| `registrar-venta-tarjeta-dialog.component.ts` | Se van `valorQr` y `qrPayload` de `RegistrarVentaTarjetaData`. `onVolverAlQrApp()` pasa a `onCancelarFoto()` |
| `list-venta-tarjeta` / `ventas-tarjeta-caja-dialog` | Dejan de armar el `qrPayload` que ya nadie consume |

⚠️ **El QR aparece recién al pedir la captura, y es a propósito.** Antes de eso no hay URL que
mostrar: un código permanente en pantalla que no lleva a ninguna parte es peor que ninguno — es
exactamente lo que produjo este hallazgo.

⚠️ **Esto cierra la puerta, no el agujero.** `updateVentaTarjeta` del central **sigue aceptando**
que le pongan `COMPLETADO` sin validar nada, y `frc-mobile` sigue instalada y con su botón de
escaneo en el home. Lo que se quitó es que el desktop lo ofreciera. Tocar ese resolver rompería una
app que sólo se actualiza por release de Play Store, así que **no entra en este PR**: queda como
issue aparte, y **B1 vuelve a su severidad real**.

**Probado en el navegador el 2026-09-17**, sobre el cobro 50 (venta 35525, BANCARD - POS-001,
3.500 Gs.): el diálogo abre con **cero** QR; al tocar el botón aparece **uno solo**, con
`http://192.168.0.106:8082/public/captura/<token>` —verificado con `curl`: HTTP 200, `<title>Foto
del cupón</title>`—; y «Cancelar la foto» vuelve al estado inicial.

### 10.5 · El QR de la foto se pide al abrir, y un campo ilegible ya no tira la lectura entera (2026-09-17)

Dos hallazgos de la misma prueba, encadenados: el primero tapaba al segundo.

#### 10.5.1 · El QR detrás de un botón

En una terminal `tipo = MAQUINA` la foto **no es la alternativa: es el único camino**, porque su
ticket no trae QR y no hay nada que pasar por el lector. Tenerla detrás de «Sacar foto» obligaba a
pedir a mano lo que siempre se iba a pedir, y la pantalla abría sin nada que escanear justo cuando
el teléfono ya estaba en la mano.

Ahora `ngOnInit` pide la captura solo, **y sólo cuando `ofreceCamara`**: en `tipo = WEB` el camino
es el lector, y abrir una captura de prepo quemaría un token por cada pendiente que alguien mire.
Si el filial no puede abrirla, en lugar del QR sale el error con **«Sacar otra foto»** — sin eso la
pantalla quedaba muda.

⚠️ **Consecuencia deliberada: en `tipo = MAQUINA` el diálogo dejó de cerrarse solo.** No es una
regla nueva; es la que ya existía —pedir una captura frena el countdown, porque 120 s no alcanzan
para desbloquear un teléfono, escanear, encuadrar y esperar el OCR— aplicada desde que el diálogo
abre. Incluye el que salta en el PDV después de cobrar: el cajero tiene que tocar «Registrar más
tarde» para sacarlo.

✅ **Decidido el 2026-09-21 (Gabriel): queda así.** La alternativa era que el reloj siguiera
corriendo y se frenara recién con la foto, pero eso reintroduce exactamente el riesgo que el
comentario original describe: la pantalla se cierra con la foto en camino y el cajero cree que se
perdió. En una maquinita la foto es el único camino, así que cerrar solo casi siempre iba a
interrumpir algo.

#### 10.5.2 · «Detectó el código y de ahí no pasó»

Lo reportó Gabriel sacando la foto del cupón de 3.500 de INFONET. El OCR corrió, el texto llegó a
la pantalla, y no pasó nada más.

**La causa, medida.** El patrón de INFONET falla **sólo en el tramo del monto**
(`[\s\S]*G\.\s*(?<monto>[0-9][0-9.]*)`): sacándolo, matchean `cn`, `fecha`, `hora`, `boleta` y
`auth`. Al OCR se le escapó ese renglón. Y como el patrón es **una sola expresión todo-o-nada**,
`campos` quedó vacío y se perdieron los cuatro campos que sí se habían leído bien.

No es un borde. De las seis capturas con texto de esa jornada, **tres terminaron así** — y la 23 es
la misma boleta que la 29, o sea que ese cupón ya había fallado igual antes:

| captura | ¿el OCR leyó el monto? | ¿produjo campos? |
|---|---|---|
| 29, 23, 22 | no | **no** |
| 24, 21, 20 | sí | sí |

**Y encima, mudo.** `confirmarLectura` tenía dos `return` silenciosos. El filial devolvía `LISTO`
con `campos` vacío, el método salía sin abrir nada y sin decir nada. Peor: `onEsperar` termina con
`takeWhile(c => c.estado !== 'LISTO', true)`, así que **el desktop ya no escuchaba** — sacar otra
foto desde el teléfono no hacía nada, mientras el QR seguía en pantalla invitando a hacer
exactamente eso.

**Lo implementado:**

| dónde | qué |
|---|---|
| `filial` `ExtractorCupon` | Si el patrón entero falla, rescata **tramo por tramo**. Corta por cada `[\s\S]*` que esté **fuera de paréntesis**, compila cada tramo y lo corre solo. El resultado viaja marcado `parcial` |
| `filial` `CapturaCuponService` | `parcial: true` viaja dentro de `campos` |
| `desktop` | Sin ningún campo: saca el QR muerto, lo dice, y ofrece **«Sacar otra foto»** (captura nueva) |
| `desktop` | El texto del OCR sale **fuera** del bloque del QR: se necesita justo cuando ese bloque ya no está |
| `desktop` | Con lectura parcial, la carga a mano se titula **«Completá los datos del cupón»** y dice que lo cargado sí se leyó |

⚠️ **Por qué el corte respeta paréntesis.** El tramo del monto arrastra
`(?:[\s\S]*Lote:\s*(?<lote>[0-9]+))?`. Cortando por cada `[\s\S]*` sin mirar profundidad, ese grupo
quedaba partido al medio, los dos pedazos eran expresiones inválidas y se perdía **justo** el campo
que motivó todo. Hay un test dedicado a eso.

⚠️ **Los offsets siguen siendo absolutos**: cada tramo corre sobre el texto completo, no sobre un
pedazo, así que el semáforo por campo sigue valiendo.

⚠️ **El rescate no convierte un fallo legítimo en un éxito a medias:** si no se reconoce **ningún**
tramo, la respuesta sigue siendo que el formato no reconoció el cupón.

**Tests: 30/30 en `ExtractorCuponTest`**, con 5 casos nuevos. Uno usa el texto OCR de la captura 29
copiado de la base y fija que ahora salgan `numeroBoleta`, `codigoAutorizacion` y `terminal` con
`monto` vacío.

### 10.6 · La página de captura sólo dejaba sacar la foto, nunca elegirla (2026-09-17)

Encontrado por Gabriel al intentar reusar la foto que había fallado, para probar el rescate parcial
de §10.5.2: **no había opción de cargar una imagen**.

**La causa, en una línea del HTML** (`filial/src/main/resources/captura/captura.html`):

```html
<input id="f" type="file" accept="image/*" capture="environment">
```

`capture="environment"` no es una sugerencia: le dice al navegador del teléfono **«abrí la cámara»**,
y al hacerlo **le saca la opción de la galería**. Con un solo input así, un cupón ya fotografiado
—o una imagen que está en la PC— no había forma de mandarlo: había que volver a sacarle la foto al
papel, con el papel delante.

**El arreglo: dos inputs, no un toggle.** El atributo lo lee el navegador del input al que apunta el
`<label>`, así que no se puede prender y apagar por botón. Quedó **📷 Sacar foto** (con `capture`,
el camino de siempre, en color de acento) y **🖼️ Elegir una imagen** (sin `capture`: galería en el
teléfono, explorador en la PC, como acción secundaria). Los dos escriben en el **mismo handler** —
lo único que cambia entre ellos es qué pantalla abre el navegador; lo que llega después es un `File`
igual en los dos casos. Los dos se deshabilitan durante la subida: tocar el otro mientras sube
arrancaría una segunda subida sobre el mismo token.

**Probado el 2026-09-17** subiendo `cupones/2026/09/29.jpg` —la foto que había fallado— por el botón
nuevo: captura 33, `LISTO`, `parcial: true`, cuatro campos rescatados. La misma foto que el día
anterior dejaba `campos` vacío.

⚠️ **Además quedó comprobado que un token se consume con la subida**: reusar la URL contesta
«Este código ya no sirve — pedí uno nuevo desde la caja y volvé a escanear», que es el
comportamiento correcto.


### 10.7 · Se borró `venta-tarjeta-qr-payload.ts` (2026-09-21)

`construirQrPayloadVentaTarjeta()` existía para alimentar el QR de la app móvil que §10.4 sacó del
diálogo. Sin ese QR no lo consumía nadie en producción: sólo su propio `.spec.ts`. Se borran los
dos.

⚠️ **Lo que ese helper enseñaba no se pierde**, porque el motivo por el que NO servía para la seña
es una trampa real y sigue valiendo: leía `item.venta?.id` e `item.caja?.id` —objetos— y en ese
flujo el filial devuelve **escalares**, así que producía un QR con `idOrigen: undefined` **sin
lanzar ningún error**. La advertencia quedó en el javadoc de
`VentaTarjetaService.onImprimirSena()`, que es donde alguien la va a necesitar: si algún día se
escribe un armador compartido, tiene que partir de escalares.

### 10.8 · El semáforo por campo está siempre en verde (2026-09-21)

Salió preparando el paso **E6** de la guía, que pide «al menos un campo en ámbar pidiendo revisión».
No se pudo producir uno, y averiguar por qué dio algo más grande que el paso.

**Lo que se probó.** Se degradó una foto que leía los cinco campos, de tres maneras, subiéndola por
el botón nuevo del desktop y leyendo `confianzas` de `captura_cupon`:

| degradado | resultado | confianza más baja |
|---|---|---|
| resolución al 30% | lee los 5 campos | `lote` **0,9049** |
| resolución al 22% | **deja de leer** 4 campos | 0,976 en los que lee |
| calidad JPEG mínima | deja de leer 2 campos | `lote` **0,9292** |

**El OCR no duda: o lee bien, o no lee.** Degradar la imagen mata caracteres en vez de volverlos
ambiguos, así que el camino «leído pero incierto» casi no existe.

**Y el historial lo confirma:**

> **83 mediciones de confianza en toda la base. CERO por debajo de 0,9.**

Con `CONFIANZA_MINIMA = 0.9` en `carga-manual-cupon-dialog`, el ámbar **nunca se disparó**. El
mínimo histórico es 0,9049, producido a propósito para esta prueba.

⚠️ **Se cruza con algo que el código ya sabía.** El javadoc de esa misma clase anota que el
2026-09-14 se midió *«confianza 0,9657 sobre un valor equivocado»*. Juntando las dos cosas: el
semáforo **no avisa cuando debería** (nada baja del umbral) y **no distingue cuando acierta** (el
verde no garantiza el dato). Hoy es decoración, y el texto que le pide al cajero «revisá los campos
en ámbar» le habla de algo que no va a ver nunca.

**Decidido el 2026-09-21 (Gabriel): medir antes de decidir.** No se toca el umbral en este PR. Un
número calibrado sobre 83 mediciones de **una sola terminal y un solo formato** puede llenar de
ámbar lo que está bien. Queda como tarea aparte: juntar la distribución de confianzas por campo y
por formato con lo que haya en farmacia y bodega, y recién con eso elegir el valor.

**E6 queda como NO REPRODUCIBLE**, con esta evidencia como motivo. No es una falla del cambio de
esta entrega: el semáforo se comporta igual que antes.

### 10.9 · Auditoría previa al PR — 2026-09-21

Cinco auditores en paralelo sobre la rama entera (tres repos, 27 + 39 + 68 commits, 20 migraciones),
cada uno con un criterio: **regresión frontend**, **regresión backend**, **migraciones y
replicación**, **seguridad**, **consistencia y brechas de documentación**. Pregunta principal, pedida
explícitamente: *¿esto rompe algo que hoy funciona, en este módulo o en otro?*

**Resultado en una línea: nada rompe lo existente.** Todos los cambios de schema GraphQL son
aditivos, ninguna entidad agrega `NOT NULL` sin default, los archivos borrados no tienen referencias
vivas, y los callers con firma cambiada viven todos dentro del diff. Lo que sí apareció, y se
arregló antes del PR:

| sev. | dónde | qué | cómo se cerró |
|---|---|---|---|
| **ALTA** | central `CapturaMuestraImagenController` | `GET /api/captura-muestra/imagen/{id}` sin rol. Nació junto con el fix del filtro JWT que hizo que `/api/**` recién autentique: pasó de inalcanzable a alcanzable por cualquier usuario logueado, enumerando ids | `seg.requireVer()`, igual que el resto del ABM de muestras |
| MEDIA | filial `VentaTarjetaGraphQL` | `marcar*NoCompletada(s)` y `reabrir` tomaban `usuarioId` del argumento: cualquier cliente podía marcar o reabrir **a nombre de otro**. Regresión propia de la rama —las columnas de rastro son nuevas— | manda el nickname del JWT (`JwtUserDetails` en el contexto); el argumento queda como respaldo y se loguea si no coincide |
| MEDIA | `ocr/Imagen.java` (ambos) | `ImageIO.read` reservaba el buffer con las dimensiones que declara el archivo: bomba de descompresión con pocos KB. El tope de 8 MB del cuerpo limita bytes, no píxeles | se lee ancho y alto del encabezado y se rechaza > 6000 de lado antes de decodificar |
| MEDIA | `ocr/ExtractorCupon.java` (ambos) | el patrón del formato corría sin plazo sobre hasta 4.000 caracteres, bajo lock de `captura_cupon`. Un patrón con backtracking catastrófico pasa el guardado (matchea su ejemplo corto) y cuelga una captura real | `CharSequence` con plazo de 500 ms en `charAt` —no un `Future`: `java.util.regex` no mira la interrupción—. Mismo plazo en `validar()` del formato. Test con `(a+)+b` sobre 4.000 `a` |
| BAJA | páginas de captura (ambos) | el token va en el path y la página quedaba en caché e historial del teléfono | `Cache-Control: no-store` |
| consist. | `ocr/ExtractorCupon.java` | **había divergido**: el rescate parcial de §10.5 estaba sólo en filial. El botón «Probar» de central decía NO PASA sobre cupones que el PDV sacaba con 4 de 5 campos | mismo archivo en los dos repos, con los mismos 32 tests |
| consist. | `FormatoTerminalPos.esMaquina()` | central estricto, filial `null = MAQUINA` (el lado seguro). El mismo formato tenía caminos distintos según el repo | central alineado al filial |
| doc | §10.1 | el plan decía «propuesto» y estaba implementado desde `47a7b13d` | corregido arriba |
| doc | `VENTA-TARJETA-CIRCUITO-COMPLETO.md` §9 | `diasRetencionImagenes` figuraba «sin lector»; la purga ya existe | corregido |

Verificación: 71/71 tests OCR verdes en los dos repos, los tres repos compilan, y **se mergeó
`origin/develop` en las tres ramas sin conflictos** (central estaba 103 commits atrás; filial 9;
desktop 128).

#### El orden de despliegue, tabla completa — reemplaza a «central primero»

⚠️ **La instrucción única «central primero, filial después» sólo cubre 3 de los 9 pares con
replicación.** Vale para `venta_tarjeta` (BRANCH_TO_MAIN: el filial publica). Para `terminal_pos` y
`configuracion_venta_tarjeta`, que son **MAIN_TO_ALL y ya replican fila completa**, es exactamente
al revés: el subscriber (filial) tiene que tener la columna **antes** de que central la escriba. Cada
migración lo dice en su encabezado (`V221.5:15`, `V222.5:31`, `V224.5:34`, `V226.5:34`); nadie que
siga el runbook genérico lo lee. Verificado contra `pg_publication_tables` y `pg_publication_rel`
(`prattrs` NULL = sin lista de columnas) en las dos bases locales.

| columna(s) | tabla | dirección | quién primero | si se invierte |
|---|---|---|---|---|
| `datos_extra` | `venta_tarjeta` | BRANCH_TO_MAIN | **central** (V220.5) → filial (V93.5) | el filial publica una columna que central no tiene → apply worker de central en crash-loop, WAL retenido |
| tabla `formato_terminal_pos` | — | MAIN_TO_ALL | **filial** (V95.5) → central (V221.5) | tabla nueva: sólo falla el `REFRESH PUBLICATION`, no corta la suscripción (medido) |
| `formato_terminal_pos_id` | `terminal_pos` (**ya viva**) | MAIN_TO_ALL | **filial** (V95.5) → central (V221.5) | la próxima escritura de central sobre `terminal_pos` manda una columna que la filial no tiene → **esa filial** en crash-loop |
| `registro_obligatorio`, `tolerancia_diferencia_monto_pct`, `minutos_validez_captura`, `segundos_dialogo_registro`, `horas_ventana_duplicado`, `dias_retencion_imagenes`, `mb_libres_minimos` | `configuracion_venta_tarjeta` (**ya viva**) | MAIN_TO_ALL | **filial** (V96.5) → central (V222.5) | corte apenas central haga el próximo `UPDATE` de la configuración desde el ABM |
| `origen` | `venta_tarjeta` | BRANCH_TO_MAIN | **central** (V223.5) desplegado y confirmado → recién ahí filial (V97.5) | una venta con tarjeta → el stream lleva `origen` → central en crash-loop (es el 2026-08-20) |
| `sucursal_id`, `serie` | `terminal_pos` (**ya viva**) | MAIN_TO_ALL | **filial** (V98.5) → central (V224.5) | igual que `formato_terminal_pos_id` |
| tabla `formato_terminal_pos_region` | — | MAIN_TO_ALL | **filial** (V99.5) → central (V225.5) | sólo `REFRESH PUBLICATION` falla |
| `carga_manual_permitida`, `campos_obligatorios` | `terminal_pos` (**ya viva**) | MAIN_TO_ALL | **filial** (V100.5) → central (V226.5) | igual que `formato_terminal_pos_id` |
| tabla `captura_muestra` | — | no replica (central) | sin orden | — |
| `no_completado_motivo`, `no_completado_observacion`, `no_completado_por_id`, `no_completado_en`, `reabierto_por_id`, `reabierto_en` | `venta_tarjeta` | BRANCH_TO_MAIN | **central** (V228.5) desplegado y confirmado → recién ahí filial (V102.5) | igual que `origen` |
| tabla `captura_cupon` | — | no replica (filial) | sin orden | — |
| índice `idx_venta_tarjeta_codigo_autorizacion` (V96.7) | — | local | sin orden | — |

**Cómo se cumplen las dos direcciones a la vez sin desplegar dos veces:** central se despliega
primero (sus migraciones son idempotentes y aditivas, y crear la columna en el publisher no rompe
nada mientras **no se escriba**), y **no se toca `terminal_pos` ni `configuracion_venta_tarjeta`
—ni por ABM ni por SQL— hasta confirmar que las filiales del canal ya corren la versión con
V95.5–V100.5**. Es lo que ya decía el PR de central; esta tabla dice *por qué* y *cuáles* columnas.
Después, filial. Y el desktop al final, porque sus queries piden campos que sólo el backend nuevo
declara y GraphQL valida el documento entero.

#### Para quien apruebe el release — cambios de comportamiento deliberados, confirmarlos

- **El cajero puede cerrar la caja con cobros sin conciliar**, con motivo (§8). Antes sólo un ADMIN
  forzaba. Es la decisión central de esta tanda y afloja un control que existía.
- **El diálogo de conteo de billetes sólo se abre para EFECTIVO** (`pago-touch.setMoneda`). Antes se
  abría para cualquier forma de pago al elegir moneda.
- **Cuatro mutations que ya existían ahora exigen rol de tesorería** (`saveTerminalPos`,
  `deleteTerminalPos`, `configuracionVentaTarjeta`, `saveConfiguracionVentaTarjeta`). ADMIN pasa
  siempre. Antes de desplegar: `select u.nickname from personas.usuario u where ... ` —confirmar que
  ningún no-admin que hoy edite terminales o la configuración quede afuera.
- **Índices únicos sobre `terminal_pos.codigo` y `serie`** (V224.5). El dry-run corrió sobre una base
  con cero terminales. Inmediatamente antes de desplegar central en cada instancia:
  `select codigo, count(*) from financiero.terminal_pos group by codigo having count(*) > 1` y lo
  mismo con `serie`. Un duplicado hace fallar la migración ahí, no acá.

#### Lo que la auditoría dejó documentado y fuera de alcance

- **`updateVentaTarjeta` en central sigue siendo un setter sin validaciones** (B1). Esta rama no lo
  toca ni lo agrava, pero amplía la brecha relativa: ahora hay un camino con guardas (filial) y uno sin
  ninguna (el que usa la app móvil), que además puede poner `NO_COMPLETADO` sin las columnas de
  quién/cuándo/por qué. Acotarlo a lo que la app móvil realmente necesita es un cambio aparte.
- La FK de `no_completado_por_id`/`reabierto_por_id` a `personas.usuario` vive en el filial. Hoy es
  inofensiva (sólo marca quien está logueado ahí, y el login exige la fila). Si alguna vez se marca
  desde un flujo sin sesión local, revisarla.
- Los specs del desktop **no corren en CI** (`ci.yml` sólo hace `build:prod` + `electron:serve-tsc`)
  y Karma está roto en todo el repo. Los de esta rama están escritos y no ejecutados.
- Constantes cerradas copiadas a mano en tres lugares sin test de sincronía: tipos `MAQUINA|WEB|API`
  (`FormatoTerminalPos` en los dos repos + `formato-terminal-pos.model.ts`), orígenes y motivos
  (`VentaTarjeta` del filial + `venta-tarjeta.model.ts`; central sólo tiene las columnas).
