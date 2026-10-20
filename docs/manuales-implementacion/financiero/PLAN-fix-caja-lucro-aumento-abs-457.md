# Plan — Caja y lucro suman el aumento con signo (#457)

Rama: `fix/caja-aumento-valor-absoluto` (desde `origin/develop` `32c19a1d`). Pieza: **central**.

## Problema

La caja y los dos reportes de lucro suman la línea de aumento de `operaciones.cobro_detalle` con su
signo. Antes de 2024-10 hay aumentos guardados en negativo aunque el pago los incluía, así que en
esos períodos restan en vez de sumar. Desde #454 el reporte de ventas los toma en valor absoluto, y
caja/lucro ya no coinciden con él.

Además el resumen de caja no multiplica por `cambio`: un aumento en reales o dólares se suma en la
moneda original junto a los guaraníes.

## Datos verificados (copia local de `bodega`, 20/10/2026)

| Moneda | Negativos | Positivos | `cambio` |
|---|---|---|---|
| GUARANI | 1.267 (hasta 2024-10-04) | 29.641 | 1 |
| REAL | 3.437 (hasta 2024-10-04) | 42.289 | 1.050–1.350 |
| DOLAR | 5 (hasta 2024-05-02) | 55 | 6.250–7.800 |

Ninguna línea de aumento tiene `cambio` nulo. En 4.676 de 4.701 cobros con aumento negativo el pago
incluía el valor absoluto (dato de la issue). → El valor absoluto en Gs es el correcto en cualquier
período.

`totalAumento` del balance de caja **solo se muestra**: no entra en `diferenciaGs/Rs/Ds` ni en
`totalGeneral` (`PdvCajaService.generarBalance`, 407-445). El desktop lo muestra en Gs.

## Fase 1 — fix(caja) (un commit)

Las tres sumas del aumento pasan a `ABS(cd.valor * COALESCE(cd.cambio, 1))` (en la JPQL, literal
`1.0` para que el `COALESCE` quede `Double`; auditoría eje B):

1. `VentaRepository.sumarioVentasPorCajaAndSurusal` (134, nativa) — resumen del balance de caja.
   Gana además el `cambio` que no tenía.
2. `VentaItemRepository.findLucroPorFuncionarioNative` (69, nativa) — lucro por funcionario.
3. `ProductoRepository.findLucroPorProducto` (129, JPQL) — lucro por producto. **No figura en la
   issue** (cita la línea 69 para «producto y funcionario», pero el de producto es otra query).

Se mantiene: el descuento sigue como está (siempre positivo; en caja sin `cambio`, ver fuera de
alcance). Ninguna firma, DTO ni schema cambia.

Test nuevo `src/test/java/com/franco/dev/repository/operaciones/AumentoValorAbsolutoTest.java`, con
el patrón de `LucroRespaldoCostoMedioTest` (las queries no se ejecutan en CI; el test fija su texto
leyendo la anotación `@Query`): uno por query, que exige la expresión exacta del código (`1` en las
nativas, `1.0` en la JPQL) en la suma del aumento. Solo fija el texto: el resultado lo prueban el SQL
antes/después y la pantalla.

**Orden para que el test pruebe algo:** (a) escribir el test y correrlo sobre el código viejo: los 3
tienen que fallar; (b) aplicar el fix y verlos pasar. La salida de (a) va al PR.

Reversa: no hay datos ni esquema; alcanza con revertir el commit. El ticket de balance
(`ImpresionService.printBalance`) no imprime el aumento, así que nada impreso cambia.

Efecto visible (va al PR): en períodos anteriores a 2024-10 sube el «Aumento» del balance de caja y
**sube el lucro histórico**, porque `VentaService` (830-832) y `ProductoService` (801-804) calculan
lucro = venta − costo − descuento + aumento.

## Datos nuevos

Ninguno. Migraciones: **N/A para central** porque no se toca persistencia. GraphQL: sin cambios.
Filial: **N/A** — no tiene estas queries; arma su propio balance en Java (ver fuera de alcance).
Clientes (desktop, PWA): reciben el mismo campo `totalAumento`, solo cambia el valor.

## Pruebas

- Paso 9: `./mvnw clean verify -B -DskipFlyway=true` (veredicto final: `gh pr checks`).
- SQL contra la base local, antes y después: las tres queries para un período anterior a 2024-10,
  comparando el aumento con el del reporte de ventas (#454).
- Runtime: central local 8081 perfil `dev` + desktop `ng serve -c web`:
  - Financiero → Cajas, balance de la caja **2945 / sucursal 1** (24/09/2024, aumentos negativos en
    reales): hoy `Aumento` ≈ −273, después ≈ 4.570 Gs.
  - Balance de la caja **1177 / sucursal 24** (04/07/2026, aumento en reales): hoy 8.002 (mezcla Rs
    y Gs), después 10.200 Gs.
  - Lucro por producto y lucro por funcionario del mismo período viejo → el total de aumento coincide
    con el reporte de ventas.
  - `diferencia` y `total general` de esos balances no cambian.
  - Valores esperados medidos por SQL con la misma unión (auditoría eje B): 2945/1 de −273,3 a 4.570;
    1177/24 de 8.002 a 10.200.

## Fuera de alcance (se anota en la issue)

- **Desktop, lista de ventas de la caja** (auditoría eje A, hallazgo 1): `list-venta.component.ts:354`
  y `generic-list-venta.component.ts:353` recalculan el aumento en el cliente con signo (y en Gs sin
  `cambio`, que vale 1). En una caja anterior a 2024-10 el encabezado (`list-venta.component.html:318`,
  del central) va a decir 4.570 y el total de la lista (`:968`) seguirá negativo. Es otro repo y otro
  PR: issue aparte en el desktop.

- **Filial**: `PdvCajaService.generarBalance` del filial suma el aumento con signo y, en una rama,
  sin `cambio`. Solo afecta cajas del filial de antes de 2024-10.
- El descuento del resumen de caja tampoco multiplica por `cambio`. Hay que mirar si existen
  descuentos en otra moneda antes de tocarlo.

## Residual conocido (auditoría eje B, hallazgo 1)

- 18 líneas con `aumento` **y** `vuelto` en negativo (13 en Gs, 5 en reales, hasta 2024-08-29), en
  ventas que caja y lucro cuentan. Son restas de redondeo del vuelto. Con ABS pasan a sumar: **15.660
  Gs en total, en toda la historia**. Se aceptan para no cambiar las ~1.980 positivas con los mismos
  flags, que ya suman hoy.
- 17 cobros compartidos por 2 ventas: la query de caja puede contar doble su aumento. Ya pasaba; no
  depende de este fix.

## Sin verificar

- Si la `bodega` local está al día con producción en las cajas elegidas (es una copia).

## Resultados (20/10/2026)

- Test nuevo: los 3 fallan con el código viejo (`expected: <true> but was: <false>`) y pasan con el fix.
- Paso 9: `./mvnw clean verify -B -DskipFlyway=true` sobre la rama rebasada: 2.160 tests, 0 fallas.
- Arranque del central local (8084, perfil `dev`, réplica apagada): la JPQL nueva valida al levantar y
  corre (`abs(` en el SQL de Hibernate). Flyway: «Schema is up to date».
- Auditoría del diff (Fijo 1, 2 y 3): sin hallazgos que bloqueen. Condicionales A y B: N/A, el diff no
  toca release ni tablas que viajan entre nodos.
- Por pantalla (desktop web `develop` contra el central local, usuario MR):

| Pantalla | Caso | Antes (SQL) | Pantalla | Esperado (SQL) |
|---|---|---|---|---|
| Ventas de la caja → Descuento/Aumento | caja 2945 / suc 1 (24/09/2024) | −273 | 4.570 | 4.570 |
| idem | caja 1177 / suc 24 (04/07/2026, reales) | 8.002 | 10.200 | 10.200 |
| Lucro por producto, Aumento Total | 24/09/2024, todas | 10.027 | 21.344 | 21.345 |
| Lucro por funcionario, Aumento Total | 24/09/2024, todas | 10.027 | 21.345 | 21.345 |

  La diferencia de 1 Gs en lucro por producto es redondeo del prorrateo.
