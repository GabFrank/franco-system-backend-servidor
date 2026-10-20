# Plan — Reporte de ventas detallado: el Total final suma el aumento dos veces (#454)

Rama: `fix/reportes-aumento-total-final` (desde `origin/develop` `fa46109a`). Pieza: **central**.

## Problema

`VentaGraphQL.reporteGenericVentasDetallado`, en el loop de movimientos de cada venta, suma la línea
de aumento a `totalAumento` **y** a `totalFinal`. El pago ya trae el excedente, así que el aumento se
cuenta dos veces: venta 180.000, pago 270.000, aumento 90.000 → Total final 360.000.

## Datos verificados (copia local de `bodega`, 20/10/2026)

- El PDV guarda el aumento en positivo (`venta-touch.component.ts:1307`). Los negativos (4.709)
  son todos anteriores a 2024-10, casi todos deliverys.
- Descuento: siempre positivo. Vuelto: negativo (1.984 positivos sobre 418.715).
- Ventas comunes desde 2025: pagos + vuelto = `total_gs − descuento + aumento` en 2.627.355 cobros;
  57 no cuadran.
- Históricos con aumento negativo: el pago también incluía el aumento (4.676 de 4.701).
  → El valor absoluto del aumento es el correcto para cualquier período.
- Deliverys: el pago incluye el costo del envío y `venta.total_gs` no. **Fuera de alcance** (ver abajo).

## Fase 1 — fix(reportes) (un commit)

`src/main/java/com/franco/dev/graphql/operaciones/VentaGraphQL.java`:

1. Sacar el cálculo de totales del loop de movimientos a un método estático
   `totalesDeMovimientos(List<CobroDetalle>)`, junto a `ajustesDe`, que devuelve recibido por
   moneda, recibido total, descuento y aumento. El loop sigue armando `movimientosDto` igual.
2. La línea de aumento **no** suma al Total final: `totalFinal = recibido` (pagos + vuelto en Gs).
3. `totalAumento` suma `Math.abs(valor)` de la línea.
4. `ajustesDe` también suma el aumento en valor absoluto. Lo usan el resumen por forma de pago del
   detallado (843) y del reporte normal (698), así filas y resumen quedan con el mismo criterio.
5. Se mantiene: líneas con moneda distinta de GUARANI/REAL/DOLAR no suman a ningún total
   (incluido el descuento, a diferencia de `ajustesDe`); una línea sin flags («PROBLEMA») sigue en
   la lista y no suma; `cambio` null vale 1. El método nuevo tolera lista `null`, como `ajustesDe`.

`src/test/java/com/franco/dev/graphql/operaciones/VentaGraphQLTotalesTest.java` — tests nuevos:

- ejemplo de la issue: pago 270.000 + aumento 90.000 → final 270.000, aumento 90.000;
- la misma venta con la línea de aumento en negativo → mismo resultado;
- pago + vuelto + aumento en Gs, y pago y aumento en reales y en dólares con cambio → final en Gs;
- descuento y aumento en la misma venta → final = recibido;
- `cambio` null → 1; moneda no reconocida → no suma;
- `ajustesDe` con aumento negativo → aumento positivo, y neto del resumen correcto.

**Orden para que el test pruebe algo** (auditoría eje B): (a) extraer el método **sin cambiar el
cálculo**; (b) escribir los tests y correrlos: los de Total final y aumento negativo tienen que
fallar (`expected 270000 but was 360000`), y la salida se pega en el PR; (c) aplicar el fix y verlos
pasar. Todo en un solo commit de fase.

Reversa: no hay datos ni esquema que revertir; alcanza con revertir el commit. Los PDFs ya
impresos de períodos viejos van a diferir de los regenerados: es el efecto buscado.

## Datos nuevos

Ninguno: no hay campos, columnas ni claves nuevas. Migraciones: **N/A para central** porque no se
toca persistencia. Filial: **N/A** porque no tiene este reporte (`grep` sin resultados).
GraphQL: sin cambios de schema; el reporte sigue devolviendo el PDF en base64.

## Pruebas

- Paso 9: `./mvnw clean verify -B -DskipFlyway=true` (veredicto final: `gh pr checks`).
- Runtime: central local en 8081 con perfil `dev` + desktop `ng serve -c web`. En
  Operaciones → Ventas (`generic-list-venta`) generar el **reporte detallado** de:
  - una venta común reciente con aumento → Total final = lo cobrado; Aumento = el de la línea;
  - un delivery de antes de 2024-10 con aumento negativo → Aumento positivo, Total final = cobrado;
  - una venta común con pago y aumento en reales o dólares + vuelto;
  - el **reporte normal** del mismo período viejo → el resumen por forma de pago suma el aumento.
  Los ids se eligen por SQL sobre la base local antes de probar.

## Fuera de alcance (se anota en la issue)

- Deliverys: `venta.total_gs` no incluye el envío y el pago sí; filas y resumen difieren en el envío.
- Cobros con movimientos duplicados (ej. venta 262022/8): la fila muestra lo recibido duplicado,
  igual que hoy; el resumen no se ve afectado.
- Caja y lucro siguen sumando el aumento **con signo**: `VentaRepository.java:134` (resumen de caja,
  sin `cambio`) y `VentaItemRepository.java:69` (lucro por producto y funcionario). Para períodos
  anteriores a 2024-10 van a diferir del reporte de ventas en los aumentos negativos. Cambiarlos toca
  el balance de caja: va en su propia issue. (Auditoría eje A, hallazgo 1.)
- `printTicket58mm` suma el descuento a la variable `aumento`, pero esa variable no se usa.

## Sin verificar

- Datos anteriores a 2023 en detalle (solo se contaron signos).
- Las 1.984 líneas de vuelto en positivo: siguen sumando a lo recibido como hoy.
