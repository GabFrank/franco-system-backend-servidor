# Plan — Moneda en los ítems de la liquidación (y conversión)

Rama: `fix/rrhh-liquidacion-moneda-item` (central y desktop), desde `develop`.

## Problema

En el detalle de una liquidación, un ítem que viene de un vale en reales muestra `110` sin decir de
qué moneda es. Revisándolo apareció algo peor que la falta de etiqueta:

- `rrhh.liquidacion_item` y `rrhh.liquidacion_final_item` **no guardan moneda**.
- `LiquidacionSueldoService.construirItemsAutomaticos` y
  `LiquidacionFinalService.agregarDescuentosAutomaticos` copian el monto del origen **sin
  convertir** (`v.getMonto()`, `c.getMonto()`, `pend`). Un vale de R$ 110 en una liquidación en Gs
  descuenta **110 Gs**, y el neto que se paga desde caja mayor queda mal.
- El salario base sale de `Funcionario.sueldo` (en `Funcionario.moneda`), pero la moneda de la
  liquidación la elige quien la genera, y el diálogo de lote propone GUARANI para todos.

Decisión de Franco (14/11): **guardar la moneda en cada ítem, mostrarla y convertir** a la moneda
de la liquidación con la cotización.

## Regla de conversión

- **Moneda destino** = `liq.moneda`. Si es null, se usa `monedaPrincipal()`, el mismo criterio que
  `PagoRrhhTesoreriaService:192`. Al generar, se persiste.
- **Moneda origen** de cada ítem:

  | Ítem | Moneda origen |
  |---|---|
  | VALE / ADELANTO / cuota de vale | `vale.moneda` |
  | PRESTAMO_CUOTA | `prestamo.moneda` |
  | Lo que sale de `f.getSueldo()` / `valorJornal`: SALARIO_BASE, IPS, HORA_EXTRA, JUSTIFICATIVO_DESCUENTO, AGUINALDO, VACACION_VENTA; en el finiquito, el salario proporcional y el fallback de la base | `funcionario.moneda` |
  | CREDITO_CONVENIO_CUOTA | GUARANI (las ventas a crédito son en Gs) |
  | ITEM_PROGRAMADO | moneda de la liquidación de origen (`origenLiquidacionId`); si no tiene, la de destino |
  | BONO, PENALIZACION, ítems manuales | la de destino, sin conversión: no tienen moneda propia, y es lo que pasa hoy |
  | Finiquito: indemnización, preaviso, aguinaldo proporcional y vacaciones calculados desde `BaseRemunerativaService` (suma de liquidaciones pasadas) | **la de destino, sin conversión**. Ver «Fuera de alcance» |

  Si el origen tiene moneda null, se toma la de destino.
- **Cotización:** `CambioService.findLastValorEnGsByMonedaId`. GUARANI vale 1.
  - `factor = valorEnGs(origen) / valorEnGs(destino)`, en `BigDecimal` con `MathContext.DECIMAL64`.
    Se guarda **sin redondear** en `cotizacion` (numeric sin escala).
  - `monto = montoMoneda × factor`, redondeado a los `decimales` de la moneda destino (Gs: 0),
    HALF_UP.
- **Sin cotización** para una moneda que hay que convertir: se lanza
  `CotizacionFaltanteException` (subclase de `GraphQLException`) con «No hay cotización cargada
  para REAL». No se cae a 1.0.
  - En `generarLote`, que hoy traga las excepciones (`catch (Exception ignored)`): se juntan solo
    las de cotización y al final se lanza **un** error con los funcionarios salteados y la moneda
    que faltó. Los demás funcionarios se generan igual, porque cada uno va en su `REQUIRES_NEW`.
- La cotización queda congelada en el ítem. Regenerar el borrador la vuelve a leer. APROBADA y
  PAGADA no se regeneran (`:164-166`).
- **Regla de lectura en todo lado:** cantidad contra el origen = `coalesce(montoMoneda, monto)`.
  Los ítems viejos (null) dan exactamente lo de hoy.

## Datos nuevos

| Campo (en `liquidacion_item` y en `liquidacion_final_item`) | Escribe | Lee |
|---|---|---|
| `moneda_id` | `item()` / `descItem()` y sus pares del finiquito, `itemsConvenio`, `itemProgramado`, `agregarItemManual` | recibos (sufijo), GraphQL `moneda` → desktop |
| `monto_moneda` (en la moneda origen) | idem; `editarItem` lo recalcula | `validarLiquidacion`/`validarFiniquito` y `aplicar*`/`revertir*` de cuotas de préstamo y de vale; `ItemProgramadoAplicacionService` (validar/aplicar); `sumConvenioCobrado` (las dos queries); recibos; desktop |
| `cotizacion` (factor sin redondear) | idem | `editarItem` (`montoMoneda = monto / cotizacion`, con el redondeo de la moneda origen), recibos, desktop |

## Fases (un commit + push cada una; un PR por repo, primero el central)

### Fase 1 — central, liquidación mensual

1. `V283.1__rrhh_item_liquidacion_moneda.sql`: `ADD COLUMN IF NOT EXISTS moneda_id bigint
   REFERENCES financiero.moneda(id), monto_moneda numeric, cotizacion numeric` en las dos tablas.
   Es aditiva y sin backfill. `rrhh` es central-only (no figura en ninguna PUBLICATION), así que
   no hay espejo en el filial. El backend viejo arranca igual: `ddl-auto=none`.
2. Clase pura `service/rrhh/builder/ConversionMonedaRrhh`: factor, convertir y redondeo por
   `decimales`. Con test unitario: Gs→Gs, R$→Gs, Gs→R$ y vuelta sin desvío, y sin cotización →
   excepción.
3. `LiquidacionItem` con los tres campos. `generarBorrador` resuelve el destino y las cotizaciones
   una vez por liquidación, e `item(...)` convierte.
4. Cantidad contra el origen con `coalesce(montoMoneda, monto)` en:
   - `aplicarEfectosCruzados`, para préstamo, cuota de vale y programado, al aplicar y al revertir;
   - las validaciones previas al pago: `PrestamoCuotaDescuentoService:58-69`,
     `ValeCuotaDescuentoService:82-93,222` e `ItemProgramadoAplicacionService:68-78,117`. Esto
     cubre también el pago desde tesorería (`PagoRrhhTesoreriaService:259-274`).
5. Convenio:
   - el ítem guarda `montoMoneda` en Gs y `monto` en la moneda destino;
   - el tope `disponible` se pasa a Gs para `planificar`;
   - la conversión Gs→destino de cada cuota va con `RoundingMode.DOWN`, para no pasarse del neto
     ni trabar `exigirNetoNoNegativo`;
   - `sumConvenioCobrado` (`LiquidacionItemRepository:33-38`) suma `coalesce(monto_moneda, monto)`.
6. `editarItem`: si `cotizacion` no es null ni 1, recalcula `montoMoneda`.
7. `liquidacion-sueldo.graphqls`: `LiquidacionItem.moneda: Moneda`, `montoMoneda: Float` y
   `cotizacion: Float`, opcionales.
8. Recibos (`ReciboLiquidacionService`: A4, ticket PDF, ESC/POS; y
   `ReporteRrhhService.reciboItemLiquidacionBase64`): al ítem convertido se le agrega
   `(R$ 110,00 × 1.450)` a la descripción. Los montos se formatean con los decimales de la moneda
   de la liquidación: hoy `#,##0` le come los centavos a una liquidación en R$.
9. Tests que **fallan con el código viejo**, con los servicios de cuotas **reales**, no mocks:
   - generar con un vale en R$ en una liquidación en Gs descuenta el convertido;
   - `pagar` con una cuota de préstamo en R$ valida y aplica R$ 110, y `anular` revierte R$ 110;
   - `remanenteCuota` tras cobrar convenio en una liquidación en R$ da 0;
   - el lote sin cotización avisa.

### Fase 2 — central, finiquito

Lo mismo en `LiquidacionFinalService`:

- la conversión de vales, cuotas, convenio, programados y salario proporcional;
- `validarFiniquito` y `aplicarFiniquito` con `coalesce`, y `sumConvenioCobrado` de
  `LiquidacionFinalItemRepository:24-28`;
- `editarItem` y `liquidacion-final.graphqls`;
- el recibo de finiquito (`ReporteRrhhService.finiquitoBase64` y `reciboItemLiquidacionFinalBase64`).

Con sus tests.

### Fase 3 — desktop (se publica después de desplegar el central)

- `LIQ_FIELDS` / finiquito: `moneda { id denominacion simbolo }`. `ITEM_FIELDS`:
  `moneda { id simbolo denominacion } montoMoneda cotizacion`.
- `liquidacion-detalle-dialog` y el detalle del finiquito:
  - la columna Monto, los totales y los programados llevan el símbolo de la liquidación;
  - un ítem de otra moneda muestra debajo `R$ 110,00 · cot. 1.450`.
- Valores calculados en el `.ts` al cargar: nada de funciones ni getters en el template.

Desktop viejo contra central nuevo: funciona, porque los campos son aditivos. Desktop nuevo contra
central viejo: falla por campos desconocidos. Por eso va primero el central.

## Registro de implementación

### Fase 1

Desvíos del plan:

- **Paso 2, sin clase pura nueva.** Ya existían `ConversorMoneda` / `CotizacionService` (pivote en
  guaraní, `DECIMAL128`, vacío si falta cotización) y `MonedaService.decimales` / `esGuarani` /
  `empresa()`. Se reusan. Lo único nuevo es `MonedaItemRrhh`: una conversión por documento, con caché
  de factores, y `CotizacionFaltante`. El finiquito la reusa en la Fase 2.
- **Cotización `delComercio`.** Es la `valor_en_gs` vigente, la misma regla del PDV, en lugar de
  `findLastValorEnGsByMonedaId`. Es la misma columna, pero filtra las filas inactivas o en 0.
- **Moneda por defecto.** Se usa `MonedaService.empresa()`, que es la principal o, si no hay, el
  guaraní. Tesorería usa la principal y, si no hay, null.
- **`liquidacion_sueldo.salario_base`.** También queda en la moneda de la liquidación, igual que el
  ítem SALARIO_BASE.
- **Recibos.** El sufijo lleva solo el monto original, `(R$ 110,00)`, sin el factor: en Gs→R$ el
  factor sería 0,00069 y no le dice nada a quien firma. El factor se ve en el desktop.
- **Escritor extra.** `agregarItemProgramado` / `quitarItemProgramado` arman ítems fuera de
  `generarBorrador`; también convierten.
- **Tests.** Cinco tests arman `LiquidacionSueldoService` con el constructor y llevan el conversor
  como parámetro nuevo.

Tests nuevos:

- `MonedaItemRrhhTest` (5);
- `LiquidacionMonedaTest` (6), con `generarBorrador`, `pagar` y `anular` reales, más el servicio de
  cuotas de préstamo y el conversor reales.

Verificación contra el código viejo, con dos mutaciones:

- **Factor siempre 1** (el bug de hoy): fallan 4 tests (vale en 110, sin cotización, lote, editar).
- **`montoEnOrigen()` = `monto`** (el crítico de la auditoría): falla el pago.

Lo que esta fase no prueba con test unitario:

- **`sumConvenioCobrado`.** Es JPQL y necesita base: va en la prueba por pantalla 6.
- **«En letras».** El texto «en letras» del recibo sigue diciendo GUARANIES aunque la liquidación
  sea en R$. Ya pasaba antes y queda fuera de alcance.

### Fase 2

Desvíos y precisiones:

- **Moneda del finiquito por defecto.** Se mantiene la del funcionario, como ya hacía el código;
  solo si falta, la de la empresa.
- **Base del IPS.** Usa el salario del mes ya convertido. Antes sumaba ese salario, que está en la
  moneda del funcionario, con vacaciones y aguinaldo, que están en la del finiquito.
- **Preaviso.** El descuento por preaviso queda en la moneda del finiquito, como indemnización,
  vacaciones y aguinaldo, que vienen de la base remunerativa o del diálogo.
- **Programados.** Toman la moneda de su liquidación mensual de origen.
- **Recibos.** `finiquitoBase64` y `reciboItemLiquidacionFinalBase64` llevan el sufijo del monto
  original. Un finiquito que no está en Gs se imprime con sus decimales.

Tests:

- `LiquidacionFinalMonedaTest` (4), con generar, pagar, anular y editar reales.
- Las mismas dos mutaciones: con el factor en 1 fallan 3; con `montoEnOrigen` = `monto` falla el
  pago.

### Fase 3 (desktop) y pruebas por pantalla (15/11)

Entorno de prueba:

- Central de esta rama en `:8091`; el `:8081` era de otra sesión.
- Desktop `web-production` en `127.0.0.1:4201`, con la sesión de MR y el `localStorage` respaldado y
  restaurado.
- Base `bodega` local con la cotización de REAL en 1.150.

Resultado por punto:

1. **Vale y préstamo en reales.** Vale R$ 110 → Gs. 126.500 y cuota R$ 100 → Gs. 115.000. En el
   detalle se ve `R$ 110,00 · cot. 1.150` debajo del monto, y los totales llevan `Gs.`.
2. **Pagar y anular desde la pantalla.**
   - Al pagar, la cuota queda con `monto_pagado` 100,00 y PAGADA, y el vale DESCONTADO.
   - Al anular, la cuota vuelve a 0,00 PENDIENTE y el vale a CONFIRMADO.
3. **Editar y recibos.**
   - Editar el VALE a 115.000 deja `monto_moneda` en 100,00.
   - El recibo A4 y la constancia del ítem muestran `VALE (R$ 100,00)`.
4. **Sin cotización** (PESO ARG desactivada y restaurada).
   - Generación individual: «No hay cotización cargada para pasar de PESO ARG a GUARANI», y el
     borrador queda intacto.
   - Lote: «Se generaron 0 liquidaciones. Quedaron sin generar: 1 (…)».
5. **Liquidación vieja** (la 495, con ítems sin moneda): se ve igual, con el símbolo `Gs.`.
6. **Liquidación en R$ con convenio.**
   - La cuota de Gs. 59.987 entra como R$ 52,16 y guarda `monto_moneda` 59.987.
   - Una liquidación del mes siguiente ya no la cobra: el remanente da 0 porque se suma en Gs.
   - Pagar en R$ desde la caja RRHH no se pudo: «Saldo insuficiente», la caja no tiene saldo en
     reales. El pago y la anulación con efectos sobre el convenio quedan cubiertos por la prueba
     de la liquidación en Gs.
7. **Finiquito.**
   - Vale y cuotas convertidos.
   - Pagar aplica R$ 100 a cada cuota y anular las revierte.
   - Editar recalcula `monto_moneda`.
   - El recibo completo y la constancia del ítem llevan el sufijo.

Dos arreglos que salieron de la prueba:

- **Origen con moneda vacía.** Funcionario, vale, préstamo o liquidación de origen con `moneda_id`
  null ahora toman la moneda de la empresa, no la del documento. En la base local casi todos los
  funcionarios tienen moneda null: una liquidación en R$ tomaba el sueldo de 2.550.000 como
  R$ 2.550.000. Bono, penalización y manual siguen en la moneda del documento.
- **Cuota de convenio que da 0.** Si al convertirla hacia abajo da 0, no entra: antes registraba
  Gs cobrados sin descontar nada. La pérdida de redondeo que queda es menor a 0,01 de la moneda
  del documento por cuota, a favor del funcionario.

Fuera de alcance, visto en la prueba:

- La lista de liquidaciones y el diálogo «¿Pagar el neto de 2165.23…?» muestran montos sin
  moneda. Es anterior a este cambio.
- El recibo dice «en letras … GUARANIES» y usa coma de miles: es el locale de la JVM.

### Auditoría del diff (paso 8, 15/11)

Tres ejes fijos; los condicionales no aplican porque no hay release ni tablas replicadas.

| Eje | Veredicto | Hallazgo → qué se hizo |
|---|---|---|
| Fijo 1, autorización | Sin hallazgos | Sin endpoints nuevos. Los campos nuevos viajan por queries que ya exigían rol y alcance. |
| Fijo 2, esquema | Con reservas | **MAJOR:** `editarItem` recalculaba `monto_moneda` aunque el monto no cambiara (el cliente lo manda siempre). Con un destino de menor resolución corría el origen y trababa el pago. → Solo se recalcula si el monto cambió, con test. **Menor:** el ítem agrupado de convenio del recibo no lleva moneda; afecta solo cómo se muestra. |
| Fijo 3, contrato | Con reservas | **MAJOR:** guardar `salario_base` convertido cambiaba lo que suma el resumen IPS. → Se revirtió: sigue siendo el sueldo sin convertir. Que el resumen IPS mezcle monedas ya pasaba antes y queda fuera de alcance. **Menor:** el ticket en otra moneda usaba el locale de la JVM → separadores fijos. Los cambios visibles van en la descripción del PR. |

Desvío anotado: los ítems programados del detalle siguen sin símbolo, porque pueden estar en la moneda
de otra liquidación.

## Pruebas por pantalla (paso 9)

Central propio desde el worktree en un puerto libre y desktop `ng serve -c web`, con `MR`:

1. Cotización de REAL cargada, vale de R$ 110 y préstamo en R$. Generar en Gs: los ítems muestran
   el convertido, con el original debajo, y el neto lo descuenta.
2. Aprobar y pagar desde la pantalla: pasa la validación y la cuota queda con `monto_pagado` en R$
   (SQL). Anular revierte.
3. Editar un ítem convertido: `monto_moneda` se recalcula. Recibos A4, ticket y de ítem con el
   sufijo.
4. Sin cotización: error legible en la generación individual y en el lote.
5. Liquidación vieja (ítems null): se ve igual que hoy, con el símbolo.
6. Liquidación en R$ con convenio en Gs: se cobra y la venta queda saldada sin volver a cobrarse.
7. Finiquito con un vale en R$: las mismas comprobaciones.
8. Restaurar los datos de prueba.

## Fuera de alcance / riesgos

- **Base remunerativa del finiquito.** `BaseRemunerativaService.percibidoAnual` y
  `promedioUltimosMeses` suman `i.monto` de liquidaciones pasadas sin agrupar por moneda. Si el
  funcionario tuvo meses en monedas distintas, la base mezcla monedas. Desde este fix esos montos
  ya están en la moneda de cada liquidación, así que mientras todas sean de la misma moneda que el
  finiquito, la base sale bien. Arreglarlo del todo (convertir por mes) queda para un issue
  aparte.
- No se corrigen liquidaciones ya pagadas. Producción no se midió: el clasificador rechaza el
  acceso. En la base local no hay vales ni préstamos en otra moneda; se crean para la prueba.
- BONO y PENALIZACION no tienen moneda y se asumen en la de la liquidación.
- Tamaño: probablemente pase las 400 líneas del PR del central. Si pasa, Fase 2 va en un PR propio.

## Auditoría del plan (paso 5)

Hallazgos de los dos auditores, eje A (contrato) y eje B (estado), y qué se hizo con cada uno:

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Las validaciones previas al pago comparan `monto` contra la cuota en su moneda: se trababa todo pago | A y B | Paso 4: `coalesce` en validar/aplicar/revertir |
| `sumConvenioCobrado` suma `monto`: el convenio se cobraba dos veces en una liquidación no Gs | A y B | Paso 5 |
| El lote traga la falta de cotización | B | Excepción propia + error al final del lote |
| El factor redondeado desvía la edición | B | Factor DECIMAL64 sin redondear |
| Convenio convertido con HALF_UP puede dejar el neto en −0,01 | B | `RoundingMode.DOWN` |
| La moneda por defecto difiere de tesorería | A y B | `monedaPrincipal()` |
| La base remunerativa del finiquito suma liquidaciones pasadas | A | Sin conversión, documentado como fuera de alcance |
| Programado nacido en otra moneda | A | Hereda la moneda de su liquidación de origen |
| Recibo de ítem y de finiquito en `ReporteRrhhService`, `#,##0` sin centavos | A | Pasos 8 y Fase 2 |
| `LIQ_FIELDS` sin `simbolo`; orden de despliegue | A | Fase 3 |
| Los tests con mocks no detectan los críticos | B | Tests de pago con los servicios de cuotas reales |
