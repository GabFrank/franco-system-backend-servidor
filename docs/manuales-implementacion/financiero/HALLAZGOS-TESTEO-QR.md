# Hallazgos — testeo manual venta con tarjeta por QR

Sesión del **2026-09-07** contra alpha. Ninguno es bloqueante; todos salieron probando a mano.

## Arreglados en esta sesión — rama `fix/cupon-comparacion-monto-y-decimales`

### 1 · Decimales fijos en el diálogo del cupón

`desktop/src/app/modules/financiero/venta-tarjeta/qr-pos/escanear-cupon-dialog/escanear-cupon-dialog.component.html`

```html
{{ data.monto | number: '1.0-2' }} {{ data.monedaSimbolo || 'Gs.' }}
```

Un cobro de **50,00 R$ se muestra como "50 R$"** — `1.0-2` tiene mínimo 0 decimales y se come los
ceros. Verificado en pantalla el 2026-09-07 (registro 10, venta 80043).

La corrección por moneda se aplicó a la **lista** (`digitosMoneda`) pero este diálogo quedó afuera.

**✅ ARREGLADO.** El diálogo ya recibía `decimalesPorMoneda` y `monedaCobroId`, así que se calcula
`digitosMonto` en `ngOnInit` (no en el template: regla del repo). Verificado en pantalla: muestra
`50,00 R$`.

No es sólo cosmético: el cajero compara contra el ticket del POS, y "50" contra "50,00" en una
pantalla donde también hay guaraníes invita a confundir la escala — el error que la feature existe
para evitar.

### 2 · El diálogo de configuración no hace `trim()`

`desktop/src/app/shared/components/config-dialog/`

Un espacio al pegar la IP (`' 100.64.0.2'`) arma `ws:// 100.64.0.2:8080/...` y sale
`DOMException: Failed to construct 'WebSocket': The URL is invalid` — un mensaje que no menciona la
configuración por ningún lado. Costó un rato de diagnóstico.

**Fix pendiente:** `.trim()` al guardar, o un validador que lo rechace.

### 4 · Aviso de monto distinto disparado siempre — ✅ ARREGLADO

`desktop/src/app/modules/pdv/comercial/venta-touch/pago-touch/pago-touch.component.ts:752`

```ts
if (datosCupon.monto != null && datosCupon.monto !== item.valor) {
```

`item.valor` está **tipado como `number`** pero viene del formulario, donde es un string (`"50.00"`).
`50 !== "50.00"` es siempre verdadero, así que el aviso *"Cupón leído, pero el cupón dice 50 y se
cobró 50.00"* salía en **todos** los escaneos, con montos idénticos incluido.

La prueba está en el propio mensaje: los dos lados pasan por `.toLocaleString('es-PY')`, y el lado
del cobro imprimió `50.00` **con punto** — `String.prototype.toLocaleString` ignora el locale. Un
number habría dado `50`.

**Por qué importaba:** no bloquea ni corrompe (el registro guardó 50.00/50.00 bien), pero un aviso
que sale siempre deja de leerse. Cuando el cupón difiera de verdad, el cajero ya lo va a estar
ignorando: **la validación quedaba muerta en la práctica**.

**Fix:** comparar `Number(item.valor)`. Verificado: snackbar verde con montos iguales.

Misma familia que los otros bugs de tipos de este PR (`id` string vs `xxxId` number).

### 3 · `latest.yml` viaja en los releases alpha

`desktop/.github/workflows/release.yml:159` — usa `cp` en vez de `mv`, y después sube
`release/*.yml` completo. **Inofensivo**: `electron-updater` resuelve primero *qué release* leer
(stable va a `/releases/latest`, que excluye prereleases), así que ningún cliente lo alcanza.
Cosmético.

## Infraestructura — ya arreglado en esta sesión

- **`formato_qr_pos` fuera de `central_pub`.** Alpha corre con profile `dev`, que apaga
  `replication.sync.enabled`, así que el scheduler que agrega tablas nuevas a la publicación nunca
  arrancó. Ver issue central #269.
- **`central_pub_filial2_sub` apuntaba a `192.168.0.163`**, IP que mauro ya no tiene. Las 62 tablas
  de `central_pub` no replicaban. Corregido: `configuraciones.local.ip_servidor_central` seteado a
  `172.25.0.172` y suscripción repuntada.
- **Conflicto de PK en `productos.producto`** al reconectar, resuelto con
  `ALTER SUBSCRIPTION ... SKIP (lsn = '0/25203488')`.

## Casos no aplicables

- **Cupón de terminal ajena.** El cruce se detecta por **proveedor**, y un formato comodín nunca
  cuenta como cruce (§4 del manual). El único formato en alpha es comodín. Requiere cargar un
  segundo formato con proveedor asignado.

## Resultados — 8 pruebas manuales contra alpha, 2026-09-08

| # | Caso | Resultado | Evidencia |
|---|---|---|---|
| 1 | Camino feliz guaraníes | ✅ | reg. 9 — venta 80042, 50.000/50.000, `moneda_id` 1 |
| 2 | Camino feliz reales + decimales | ✅ | reg. 10 — venta 80043, 50.00/50.00, `moneda_id` 2 |
| 3 | Moneda del registro, terminal sin moneda | ✅ | reg. 11 — terminal `SM1` con `moneda_id` NULL, registro con `moneda_id` 2 |
| 4 | Pendientes creados y completados desde el diálogo nuevo | ✅ | regs. 12 y 13 |
| 5 | Monto distinto avisa | ✅ | reg. 14 — cobrado 50.000, cupón 49.000 |
| 6 | Moneda cruzada **BLOQUEA** | ✅ | sin registro: bloqueó antes de crear nada |
| 7 | Cupón viejo **CONFIRMA** | ✅ | sin registro: se eligió no registrarlo |
| 8 | Dos tarjetas del mismo monto | ✅ | regs. 16 y 17, venta 80051, identificadores distintos por línea |

La 8 es la de mayor valor: confirma que el vínculo cupón↔línea sobrevive al refactor de
`aplicarCupon()`. Con dos cobros idénticos el backend no puede desempatarlos, así que si esa
asignación se pierde no hay forma de saber después qué cupón corresponde a cuál cobro.

## Casos que quedan sin probar

- **Pago mixto** (parte efectivo, parte tarjeta)
- **Con factura legal** en el PDV 3
- **La venta sobrevive a un PDV sin timbrado** — el fix de facturación del PR del filial
- **Aviso en el cierre de caja** con ventas sin registrar
- **Boleta vacía** (`**`) — la trampa del split pelado
- **Los gates del acceso nuevo**: sin caja abierta, sin rol, flujo deshabilitado
