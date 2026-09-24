# Plan — Monto escaneado del reporte de ventas con tarjeta, en su moneda

**Pedido y aprobado por Gabriel el 2026-09-24** («metelo también en central, junto con lo demás»), después de la auditoría del paso 5 (ejes A y B). **Paso 9 en alpha, no en local**, por decisión de Gabriel por urgencia (2026-09-24): se mergea terminado —test, batería, CI y auditorías— y la prueba de runtime se hace en alpha.

Rama: `fix/venta-tarjeta-monto-escaneado-reporte` (desde `develop`, 2026-09-24). Un PR a `develop`
del central. Va en la **misma entrega** que el PR del desktop
`fix/venta-tarjeta-monto-decimal-ocr` (plan `PLAN-PLUGPAY-MONTO-USD-Y-URL-CAPTURA-MUESTRA.md` en
el desktop): el desktop empieza a guardar el monto en dólares del cupón de PLUG PAY con sus
centavos (`146.5`), y este reporte es el que lo muestra. Pedido por Gabriel el 2026-09-24.

## Por qué

`ImpresionService.imprimirReporteVentaTarjeta` (reporte impreso de conciliación de cupones):

1. **El monto escaneado se formatea con `DecimalFormat("#,##0")` sin mirar la moneda**
   (`ImpresionService.java:1510`). el cupón de PLUG PAY sale sin centavos —`146.50` → **146**, `156.83` → **157** (HALF_EVEN, medido con jshell)—, en el campo que se usa para
   cotejar el cupón contra lo cobrado. Dos líneas más abajo el monto cobrado ya usa
   `formatMontoPorMoneda()`, que da 0 decimales en guaraníes y 2 en el resto. Encontrado por la
   auditoría del diff del desktop (eje Fijo 2), verificado.
2. **El símbolo de moneda sale de la TERMINAL, no del registro** (`:1515`). La propia entidad
   `VentaTarjeta.moneda` dice que es la moneda del cobro «no la de la terminal» y que usar la de la
   terminal reescribía el significado del histórico al cambiar esa configuración. La lista del
   desktop ya se corrigió así (moneda del registro, cae a la de la terminal sólo para filas
   anteriores a la columna); el reporte impreso quedó atrás.

## Fases

Una sola, un commit:

- `simbolo` = moneda del registro → si no hay, la de la terminal → si no, `"Gs."` (lo de hoy).
- `montoEscaneado` pasa por `formatMontoPorMoneda(vt.getMontoEscaneado(), simbolo)`; nulo sigue
  siendo `"-"`.
- Para poder probarlo sin levantar el servicio (que tiene decenas de dependencias):
  `formatMontoPorMoneda` pasa a `static` de paquete (no usa estado) y la elección del símbolo sale
  a un `static String simboloDeMoneda(VentaTarjeta)` de paquete. Mismo patrón que
  `TicketRetiroLayout` en el mismo paquete.

Tests: `ImpresionServiceVentaTarjetaTest` (JUnit 5, sin Spring):
- `146.50` en `US$` → `146,50`; `918957` en `Gs.` → `918.957`; nulo → `"-"`.
- símbolo: del registro aunque la terminal diga otra cosa; de la terminal si el registro no tiene;
  `Gs.` si no hay ninguno.
- Revertido el fix (formato `#,##0` / símbolo de la terminal), los casos de dólares y de símbolo
  fallan.

## Datos nuevos

N/A: ninguna columna, campo GraphQL, migración ni enum. Solo presentación de un reporte.

## Piezas

| Pieza | Cambia | Por qué |
|---|---|---|
| central | sí | el reporte vive acá |
| filial | no | no tiene este reporte: `imprimirReporteVentaTarjeta` sólo existe en central (grep en `filial/src`: 0) |
| desktop | no (en este PR) | sólo pide el PDF; el cambio de formato es del servidor |

## Prueba de runtime (paso 9)

Local: central con `spring-boot:run -Dspring-boot.run.profiles=dev` (lo corre Gabriel), una venta
con tarjeta en USD con `monto_escaneado = 146.5` en la base 5551, y el reporte desde «Conciliación
de cupones» → imprimir: el monto escaneado sale `146,50` con su símbolo. Y un segundo registro en
USD con un monto grande (`12345.67` → `12.345,67`) para ver que la columna del `.jrxml` no lo corta:
Jasper compila la plantilla recién al generar el PDF, así que ni el build ni el CI lo ven (eje B).

## Efecto visible esperado (no es un bug)

- **Los totales por moneda del PDF se reagrupan** para las terminales que cambiaron de moneda en
  el tiempo: las filas se suman bajo la moneda de su registro, no la actual de la terminal. Es lo
  que `V219.5__venta_tarjeta_moneda.sql` vino a resolver («pasar TPOS-BCD-01 a R$ hizo que cinco
  ventas hechas en guaraníes se mostraran como reales») — auditoría del plan, eje A.

## Qué queda sin verificar

- El layout del `.jrxml` con 2 decimales. La columna `montoEscaneado` (width 60) es gemela de
  `montoFormateado` (width 60), que ya pasa por `formatMontoPorMoneda` en producción (eje A); se
  mira igual en la prueba de runtime.
- **Grafía del símbolo del guaraní en las bases reales.** `formatMontoPorMoneda` decide 0 o 2
  decimales comparando el símbolo con `"Gs"`/`"Gs."`; una moneda guaraní cargada por ABM con otra
  grafía saldría con 2 decimales. Preexistente —afecta igual hoy a `montoFormateado`— y este cambio
  no lo empeora: el monto escaneado queda formateado igual que el cobrado de su misma fila. Los
  tests del central usan `"Gs."` / `"US$"`. A confirmar con `SELECT DISTINCT simbolo FROM
  financiero.moneda` en alpha (eje A).

## Despliegue

- Merge a `develop` → release alpha del central (mauro). Farmacia lo recibe cuando se promueva y se
  corra el workflow `Deploy` con `instance=farmacia` (reinicia `frc-farmacia.service`).
- Sin orden de despliegue con el desktop: son independientes (el reporte lee lo que haya en la
  base; con el desktop viejo seguiría mostrando el 14650 mal guardado, ahora con su símbolo).
- Rollback: revertir el PR. Sin estado.
