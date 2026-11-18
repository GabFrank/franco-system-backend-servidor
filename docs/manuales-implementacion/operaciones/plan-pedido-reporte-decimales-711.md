# Plan — PDF y ticket del pedido con los decimales de la moneda (desktop #711)

Rama: `fix/pedido-reporte-decimales` desde `develop` (b1e8cc42). Solo central; sin migración ni cambio de API.

## Qué pasa

`ImpresionService.imprimirPedido` imprime el pedido de compra en dos formatos y los dos redondean a enteros:

- Ticket ESC/POS: `NumberFormat.format((long) Math.round(precio))`, igual para subtotal y total.
- PDF (`reports/pedido-compra.jrxml`): `pattern="#,##0"` fijo en precio unitario, subtotal y total.

El pedido 42 en USD (24 × 0,8125) sale «P. Unit. 1 / Subtotal 20 / TOTAL 20» en vez de 0,8125 / 19,50 / 19,50.
Pasaba antes del desktop #711 (81,25 → 81), pero ahora que el desktop carga costos de 4 decimales el desfase
entre la pantalla y el papel es mayor.

## Fase 1 — un commit

1. `ImpresionService`, helpers estáticos (paquete, testeables):
   - `decimalesMoneda(Moneda)`: `moneda.decimales`; si es null, 0 para la denominación GUARANI y 2 para el resto
     (la misma regla que el desktop, `MonedaService.currencyOptionsByMoneda`).
   - `patronMonto(dec)`: `#,##0` con 0 decimales; `#,##0.00` con 2.
   - `patronCosto(dec)`: como el monto, con hasta 4 decimales si la moneda tiene decimales (`#,##0.00##`):
     0,8125 → «0,8125», 81,25 → «81,25». Guaraní sigue `#,##0`.
2. Ticket: precio con `patronCosto`, subtotal y total con `patronMonto`, mismos separadores alemanes que hoy
   (`DecimalFormatSymbols(Locale.GERMANY)`, como `formatMontoPorMoneda`).
3. PDF: parámetros `patronCosto` y `patronMonto`; en el `.jrxml`, `<patternExpression>` en precio, subtotal y total
   (el `pattern` fijo no admite parámetros). Sin fuentes nuevas.
4. Guaraní: sale igual que hoy (`#,##0`).

**Tests:** `ImpresionServicePedidoFormatoTest` (Gs 0 → «42.000»; USD 2 → «0,8125», «19,50», «81,25»;
moneda sin `decimales` → por denominación). Revertir el fix y comprobar que falla. Además, compilar y llenar el
`.jrxml` con datos de prueba (la plantilla se compila en runtime: el build no la valida).

5. PDF: `REPORT_LOCALE = Locale.GERMANY` (decisión de Franco, 12/11/2026). Hoy el PDF toma el locale de la JVM
   (alpha `en_US`: «42,000»); queda «42.000» y «0,8125», igual que la pantalla y el ticket.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Llamadores: solo `PedidoGraphQL.imprimirPedidoPDF/Ticket` (ambos con `exigirCompras`); el filial no tiene la plantilla | Sin otros cambios |
| A | El PDF formatea con el locale de la JVM: con decimales saldría «0.8125» frente al «0,8125» del ticket | Punto 5 (decisión de Franco) |
| A | `Pedido.moneda` es LAZY y `decimales` nullable | Mismo camino que `getDenominacion()` hoy; fallback por denominación |
| B | `patternExpression` va después de `textFieldExpression` (XSD 6.20); hay que quitar el `pattern` fijo | Así se hace |
| B | Un `.jrxml` mal armado revienta recién al generar el PDF | Test que compila y llena la plantilla |
| B | Una línea larga del ticket («1000 x 12.345,6789 = …») pasa de 32 columnas | Se acepta: la térmica la envuelve sin perder datos |
| B | Rollback | Redeploy del jar anterior; sin estado ni migración |

## Datos nuevos

Ninguno: dos parámetros de reporte nuevos, escritos por `imprimirPedido` y leídos solo por `pedido-compra.jrxml`.

## Prueba por pantalla

Desktop local contra este central: pedido 42 (USD) → Ver PDF en la app: 0,8125 / 19,50 / 19,50. Un pedido en Gs
(41) → sin decimales como hoy. Ticket: en una cola de captura `zz_` (se avisa antes) o, si no, leer los bytes.

## Sin verificar / fuera de alcance

- Solicitud de pago: muestra montos, no costos unitarios; el total sale con `#,##0.###`. Sin cambios.
- Constancia de recepción: solo cantidades.
