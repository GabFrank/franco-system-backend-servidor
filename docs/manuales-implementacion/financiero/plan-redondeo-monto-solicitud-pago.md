# Plan: redondear el monto de la solicitud de pago a los decimales de su moneda

Rama: `fix/financiero-redondeo-monto-solicitud-pago` (central y desktop, desde `origin/develop`).

## Problema

SP-001338 (id 1343, bodega) no se podia pagar: `monto_total = 899854.5` en guaranies. El dialogo
de pago del desktop propone `round(899854.5) = 899855` y el motor lo rechaza en
`PagoProveedorService.procesarEvento` («El pago excede el saldo de la solicitud»): el exceso es
0,5 y la tolerancia 0,005. Pagar 899.854 tampoco sirve: el dialogo no deja confirmar con faltante
de 1, y el ajuste que ofrece vuelve a aplicar 899.855.

Causa raiz: `SolicitudPagoService.calcularMontoNotaEnMoneda` devuelve
`SUM(cantidad_en_nota * precio_unitario_en_nota)` sin redondear a la moneda de la solicitud, y ese
valor se guarda tal cual en `solicitud_pago_nota_recepcion.monto_incluido` y en
`solicitud_pago.monto_total`. Una moneda sin decimales termina con una deuda que no se puede
saldar con ningun pago valido.

Alcance medido en bodega (2026-10-08): 24 solicitudes en guaranies con decimales sobre 1.321, las
24 abiertas (ninguna se pago nunca). La 1343 se corrigio a mano ese dia (899855); quedan 23 en
PENDIENTE. Las dos tablas **no estan en ninguna publicacion**: no hay espejo en filial.

## Fases

### Fase 1 — central: redondear en el origen

Helper compartido `redondearAMoneda(Double valor, Moneda moneda)`:
`BigDecimal.valueOf(valor).setScale(decimales, HALF_UP)`. `decimales` = `moneda.getDecimales()`; si
es null, 0 para GUARANI y 2 para el resto; si la moneda es null, devuelve el valor sin tocar.

Se aplica en los tres puntos por donde un monto de nota llega a la base:

- `SolicitudPagoNotaRecepcionService.agregarNotaASolicitud`: redondea `monto` a la moneda de la
  solicitud antes de guardar `monto_incluido`. Es el embudo real: lo usan `crearSolicitudPago`,
  `actualizarNotasDeSolicitud` **y** la mutation `agregarNotaASolicitudPago`, que recibe el monto
  del cliente (hallazgo A1).
- `SolicitudPagoNotaRecepcionService.recalcularMontoTotalSolicitud`: redondea la suma (el `double`
  de una suma con centavos deja ruido tipo `300.29999999999995`; hallazgo A2).
- `SolicitudPagoService.crearSolicitudPago`: el total es la suma de los montos ya redondeados,
  vuelta a redondear.

No se toca la tolerancia del motor de pago ni `nota_recepcion.valor` (es el documento del
proveedor; la obligacion de pago es la que se redondea).

Tests (`SolicitudPagoServiceMontoRedondeoTest`, Mockito, mismo armado que
`SolicitudPagoServiceCancelacionTest`):

1. Nota en Gs por 899854.5 → `monto_total` 899855 y `monto_incluido` 899855.
2. Dos notas en Gs de 10.5 → cada una 11, total 22 (total = suma de lo incluido).
3. Moneda con 2 decimales: 10.005 → 10.01; 100.10 + 200.20 → 300.30 exacto.
4. Nota en USD con cabecera en Gs (cotizacion con decimales) → entero.
5. `decimales` null: GUARANI → 0; otra → 2.
6. `agregarNotaASolicitud` con un monto con decimales venido del cliente → guarda redondeado.
7. `recalcularMontoTotalSolicitud` con suma `300.29999999999995` en moneda de 2 decimales → 300.30.

Se verifica que 1, 2 y 4 fallan con el fix revertido.

### Fase 2 — central: migracion `V238.1__redondear_monto_solicitud_pago_abierta.sql`

Normaliza lo que ya esta guardado, solo en solicitudes de **compra, abiertas y sin pagos**:
`tipo = 'COMPRA'`, `estado IN ('PENDIENTE','SOLICITADO','DEVUELTO')` y
`COALESCE(monto_pagado,0) = 0`. Los decimales salen de la moneda de la solicitud con la misma
regla del helper (`CASE`: `decimales`; si es NULL, 0 para GUARANI y 2 para el resto).

1. `monto_incluido = round(monto_incluido, decimales)` donde difiera.
2. `monto_total`: solo en las que hoy tienen mas decimales que su moneda. Con notas vinculadas,
   `SUM(monto_incluido)`; sin notas, `round(monto_total, decimales)`.
3. Guarda: un bloque `DO` previo aborta con `RAISE EXCEPTION` si alguna solicitud alcanzada tiene
   `|SUM(monto_incluido) - monto_total| >= 1` (ahi la diferencia no es redondeo y no se toca a
   ciegas; en bodega hoy son 0).

No toca GASTO ni RRHH (su monto es la obligacion del documento de origen: pre-gasto, vale,
liquidacion), ni PARCIAL, CONCLUIDO o CANCELADO (hay pagos imputados contra el monto viejo), ni
`solicitud_pago_detalle` (en bodega las 23 afectadas no tienen plan de formas de pago; el backend
no valida su suma contra el total). Es idempotente. Sin DDL: `monto_total` es `numeric` y
`monto_incluido` `numeric(15,2)`, `round(numeric,int)` aplica directo. PK simple `(id)` en las dos
tablas. Sin espejo en filial (tablas no publicadas, verificado en `pg_publication_tables` de bodega).

Dry-run (paso 10): contra la copia local `bodega@5551`, contando filas antes y despues, y
comprobando que ninguna solicitud con pagos cambia.

Rollback: el JAR anterior convive con montos redondeados (son montos validos). Los datos no se
revierten solos: antes del deploy a cada instancia se guarda un CSV con `id, monto_total` y
`id, monto_incluido` de las filas que la migracion va a tocar (misma condicion). Si se vuelve al
JAR anterior, las solicitudes nuevas vuelven a nacer con decimales hasta re-desplegar; la
migracion no se repite sola.

### Fase 3 — desktop: que el dialogo no esconda medio guarani

`pagar-compras-dialog.component.ts`, `round()`: `Math.round(-0.5)` da `-0`, asi que un pago que
excede la deuda por 0,5 se muestra como balance exacto y se manda al motor, que lo rechaza con un
mensaje que el usuario no puede resolver. Cambiar a redondeo simetrico (mitad lejos del cero). Con
la fase 1 no deberian nacer mas montos asi, pero la fase 2 no toca las PARCIAL y el dialogo tiene
que mostrar la diferencia en vez de taparla. Efecto colateral buscado: el true-up de
`distribuirFifo` deja de ignorar un residuo de -0,5. El resto de los llamados opera sobre
positivos o detras de `Math.max(0, …)` y no cambia.

Tests: N/A para desktop (su CI no corre tests). Gate: `npm run check` leido del log.

PR aparte en el repo del desktop. No hay cambio de contrato GraphQL: el orden entre los dos PRs es
indistinto.

## Datos nuevos

Ninguno: no nacen columnas, campos GraphQL ni claves de configuracion.

## Que queda sin verificar

- Farmacia: no se midio cuantas solicitudes con decimales tiene; la migracion aplica la misma regla.
- Gastos y RRHH: no pasan por `calcularMontoNotaEnMoneda`. En bodega no hay ninguno en Gs con
  decimales; un gasto cargado a mano con decimales en Gs seguiria entrando. Fuera de alcance.
- `create-edit-solicitud-pago-dialog` (desktop) suma las notas crudas y redondea el total; el
  backend suma notas ya redondeadas. Con varias notas de x,5 el panel puede mostrar una
  diferencia de 1-2 Gs. Es visual, no bloquea el guardado. No se toca en este fix.
- Una nota en USD/BRL con cabecera en Gs multiplica en `double`: un producto que deberia dar x,5
  exacto puede quedar en x,4999… y redondear hacia abajo. Un guarani, aceptado.
- Prueba de runtime: crear en local una solicitud con una nota de total x,5 y pagarla desde la caja.

## Auditoria del plan (paso 5)

| Hallazgo | Que se hizo |
|---|---|
| A1: la mutation `agregarNotaASolicitudPago` guarda el monto del cliente sin pasar por `calcularMontoNotaEnMoneda` | El redondeo se movio a `agregarNotaASolicitud`, que cubre las tres vias |
| A2: `recalcularMontoTotalSolicitud` deja ruido de `double` | Se redondea ahi tambien |
| B1: la migracion alcanzaba GASTO/RRHH y no tenia guarda | Filtro `tipo = 'COMPRA'` + `RAISE EXCEPTION` |
| B2: DEVUELTO quedaba afuera | Incluido |
| B3: el plan de cheques no se redondea | Fuera de alcance, anotado (0 casos en bodega) |
| B4: `decimales` NULL en SQL | `CASE` con la misma regla del helper |
| B5: el «antes» de los datos era una nota al pie | Paso explicito previo al deploy |
| A4: total calculado en el cliente al armar la solicitud | Anotado en «sin verificar» |
