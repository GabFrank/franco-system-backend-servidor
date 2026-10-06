# Plan — más espacio para firmar en el recibo de sueldo (A4)

Rama: `fix/rrhh-recibo-sueldo-espacio-firma` (desde `origin/develop`). Pieza: **central**, sola.

## Pedido

En el recibo de sueldo, la cláusula «Recibi de …» queda pegada a la línea de «Firma del
Funcionario»: no hay lugar para firmar. Dar más espacio entre el texto y la línea.

## Análisis

- El recibo es `src/main/resources/reports/recibo-liquidacion.jrxml` (A4, dos vías en la banda
  `summary`: «COPIA PARA EL FUNCIONARIO» arriba del corte y «ORIGINAL - QUEDA EN LA EMPRESA» abajo).
- En cada vía la cláusula ocupa `y..y+32` y la línea de firma está 10 pt más abajo (≈3,5 mm).
- Los demás recibos ya dejan lugar y **no se tocan**: genérico y finiquito 40 pt, tickets 42-43 pt,
  ESC/POS `feed(3)`.
- El `summary` es de alto fijo (292 pt) y Jasper no lo parte: si no entra, las dos vías se van
  enteras a una segunda hoja. Hoy el techo de una hoja es **25 ítems**
  (802 − 110 − 18 − 292 = 382 pt, a 15 pt por fila; sobran 7 pt).
- Medido en la base local de bodega (495 liquidaciones): mediana 1 ítem, p95 9 ítems; 6 ya pasan
  de 25 (hoy salen en dos hojas) y **3 tienen 24 o 25**.

## Cambio

Subir el hueco de **10 pt a 28 pt (≈1 cm)** en las dos vías: +18 pt por vía, `summary` 292 → 328.

| Elemento del `summary` | y hoy | y nuevo |
|---|---|---|
| Vía funcionario: línea de firma / «Firma del Funcionario» / nombre-doc | 104 / 106 / 118 | 122 / 124 / 136 |
| Línea de corte / «cortar por aqui» | 140 / 142 | 158 / 160 |
| Vía original: rótulo, totales, observación, cláusula, marca de agua | 158…220 | +18 cada uno |
| Vía original: línea de firma / rótulo / nombre-doc | 262 / 264 / 276 | 298 / 300 / 312 |

Costo: el techo de una hoja baja de **25 a 23 ítems** (802 − 110 − 18 − 328 = 346 pt → 23 filas).
Las liquidaciones de 24 o 25 ítems pasan a salir en dos hojas (3 de 495 en la muestra). 28 pt es el
máximo que mantiene el techo en 23; cada 15 pt más cuesta otra fila.

Sin migración, sin cambio de GraphQL, sin parámetros nuevos de la plantilla, sin datos nuevos
(tabla de datos nuevos: N/A, no nace ningún campo).

## Fase única

Commit: `fix(rrhh): dar mas espacio para la firma en el recibo de sueldo`

1. `recibo-liquidacion.jrxml`: correr las coordenadas de la tabla de arriba y el alto de la banda.
2. Tests:
   - nuevo `ReciboLiquidacionJrxmlTest.hayLugarParaFirmarEnLasDosVias`: sobre el `JasperPrint`
     lleno, en cada vía la distancia entre el pie de la cláusula y la línea de firma es ≥ 28 pt.
     Se comprueba que **falla con la plantilla vieja**.
   - el mismo test se llena con una cláusula de largo real (razón social y monto en letras de
     bodega, 3 líneas) y suma un caso de 4 líneas que afirma que la cláusula **no pisa** la línea.
   - `MarcaAguaRecibosRrhhJrxmlTest.liquidacionTechoDeUnaHoja`: techo 25/26 → 23/24, con su javadoc.
   - `ReciboLiquidacionJrxmlTest.lasDosViasEntranEnUnaHoja` sigue corriendo hasta 20 (entra);
     actualizar su javadoc (292 → 328, 382 → 346, 25 → 23).
   - el resto de `MarcaAguaRecibosRrhhJrxmlTest` (marca de cada vía de su lado del corte, nada
     opaco encima) sigue verde sin tocarlo.
   - nuevo: la observación de cada vía no se solapa con ningún otro texto (hoy nada lo vigila).
3. Batería: `./mvnw clean verify -B -DskipFlyway=true`.
4. Prueba visual: PDF generado con la plantilla nueva, vía de arriba y de abajo.

## Qué queda sin verificar

- La impresión en papel desde el desktop (se verifica el PDF, no la hoja).
- La distribución de ítems en farmacia (se midió solo la base local de bodega).

## Auditoría del plan (paso 5)

| Hallazgo | Qué se hizo |
|---|---|
| A · La cláusula estira y la línea de firma es fija: con 4 líneas hoy **pisa** la línea (hueco −1); con el cambio queda en 17 pt. El stretch también agranda la banda: el techo es 23 con cláusula de hasta 3 líneas y 22 con 4 (hoy 25 y 24) | Verificado por el auditor con un fill real. Se acepta: la cláusula de bodega mide 3 líneas. Se agregan los dos casos al test |
| A · La PWA también consume la query del recibo | Solo recibe el base64; no asume una hoja. Sin cambio |
| B · La marca de agua y la observación de la vía 2 están lejos del resto de la vía en el `.jrxml` y ningún test falla si se olvidan | Se agrega el test de solape de la observación; la marca la cubre el test existente del corte |
| B · Con «DATO DUMMY» la cláusula ocupa 2 líneas y el test daría 28 justo | El test usa cláusula de largo real |
| A/B · Faltaba el javadoc de `MarcaAguaRecibosRrhhJrxmlTest`; el «~73 pt» de los tickets estaba mal medido | Corregido arriba |
| B · `docs/manuales-implementacion/reportes/PLAN-LOGO-RECIBOS-RRHH-KUDE.md` cita el techo viejo | Se revisa en el paso 11 |
