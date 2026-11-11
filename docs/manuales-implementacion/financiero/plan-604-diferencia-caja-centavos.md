# Plan #604: la diferencia de caja en reales y dólares sale redondeada a centavos (central)

Issue: GabFrank/franco-system-backend-servidor#604. Rama: `fix/caja-diferencia-centavos` (desde `develop` f3bd8e3d).
Pieza: **central** solamente. Gemelo del arreglo del filial (franco-system-backend-filial#255, PR #257).

## Problema

`PdvCajaService.generarBalance` (l.~466-468) arma la diferencia sumando en `double`: conteos de apertura y cierre
(`cantidad × valor` del billete), ventas, vueltos, retiros y gastos. En reales y dólares la suma arrastra el error de
punto flotante:
- una caja que cuadra exacto puede quedar con `diferenciaDs` = −5,55e−17 (0,1 + 0,2 contra 0,3);
- una diferencia de 57 centavos sale −0,5700000000000021 (caja 3016 del alpha);
- vueltos viejos con fracciones de centavo (−0,0033 R$, 2023 a 03/2026) dejan diferencias que no se pueden contar.

Quién lee esa diferencia en el central:
- **API** `balancePorCajaIdAndSucursalId` → `getBalance` (l.~489): detalle de caja del **desktop** y de la **PWA**. La
  PWA marca «La caja cierra con diferencia» con `!== 0` (`caja-detalle.page.ts:170`): una caja que cuadra puede mostrar
  el aviso y un importe «0,00».
- **Desktop, retiros y gastos** (`adicionar-retiro-dialog` l.~303/307, `adicionar-gasto-dialog` l.~885/891): validan
  `valor > diferencia * -1`; con ruido, retirar exacto 27,57 US$ contra 27,570000000000004 se rechazaba.
- **Desktop, análisis de diferencias** (`analisis-diferencia.component.html` l.~412-427): pinta «coincide» con `=== 0`.
- `EventosCaja.balance`/`atributosCierre` también comparan `!= 0`, pero **no tienen llamador en el central** (existen por
  paridad con el filial; solo `EventosProgramador` lee sus contadores).
- **Ticket** de balance (`ImpresionService` l.386/392): ya formatea con `%.2f`, no cambia.

## Enfoque

Igual que el filial: al final de `generarBalance`, redondear a 2 decimales (HALF_UP, por `BigDecimal.valueOf`)
**solo `diferenciaRs` y `diferenciaDs`**, ya calculadas con los totales crudos. Un solo lugar; todos los lectores leen
ese DTO.

- **Los totales no se redondean:** en el filial los vuelve a sumar el control de saldo (#80); acá se mantienen iguales
  para que las dos piezas den los mismos totales.
- **Guaraníes no se tocan:** sumas de enteros en double, exactas.
- **2 decimales fijos:** real y dólar tienen 2 decimales; el balance identifica las monedas por denominación.
- Una diferencia de −0,0033 R$ (datos viejos) pasa a 0.

**Descartado:** pasar todo el cálculo a `BigDecimal` (más cambio, y el filial no lo hizo: los dos darían distinto);
redondear en cada cliente (son dos y los eventos no pasan por ellos).

## Fase única

- `PdvCajaService`: helper `centavos(Double)` y `setDiferenciaRs/Ds(centavos(...))`. Devuelve tal cual `null`, NaN e
  infinito (`BigDecimal.valueOf(NaN)` lanza y tumbaría el detalle de caja entero).
- **Test** `PdvCajaServiceBalanceTest` (unitario, armado como `PdvCajaServiceMontosNulosTest`: mocks de conteos,
  retiros, gastos y `ventaService` con deep stubs; el sumario de ventas es un `Object[16]` agregado). Fallan con el viejo:
  - dólares: apertura 0, venta 27,57, cierre 27 → `diferenciaDs` == −0,57 exacto (viejo: −0,5700000000000003); y +0,57
    con cierre 28,14;
  - caja que cuadra: cierre 3 × 0,1 US$ contra venta 0,3 → `diferenciaDs` == +0,0 (viejo: 5,55e−17);
  - reales con vuelto viejo −0,00333 y cierre igual a la apertura → `diferenciaRs` == 0,0;
  - guaraníes sin cambio y **totales crudos** (`totalDsCierre` sigue 0,30000000000000004);
  - `centavos`: null, NaN y ±0,005 (HALF_UP: ±0,01).

## Datos nuevos

Ninguno: sin migración, campo GraphQL ni variable de entorno.

## Contrato y canales (eje A)

Solo cambia el valor de campos `Float` existentes (menos dígitos). Desktop y PWA los muestran formateados. El central
no compara su balance con el del filial. Sin orden de despliegue con otras piezas.

## Pruebas de runtime

Central de la rama por jar contra la **bodega local** (perfil `dev`, Flyway apagado; réplica, SIFEN, correo,
cotización, acreditación POS, poller de retiros y Firebase apagados). Comparar con el mismo jar de `develop`:
1. Buscar en la base local cajas con conteos en reales o dólares; `balancePorCajaIdAndSucursalId` con develop y con la
   rama: solo cambian `diferenciaRs`/`diferenciaDs`, y por menos de medio centavo.
2. **Por pantalla:** detalle de caja de la PWA local contra este central, en una caja con ruido: con develop muestra el
   aviso «cierra con diferencia» (o un importe con ruido) y con la rama no (o el importe en centavos).
3. **Control negativo:** una caja con diferencia real de centavos sigue mostrando el aviso y el mismo importe.
4. Detalle de caja del desktop contra el mismo central: la diferencia se ve igual que antes (ya formatea).

Los dos jars van uno después del otro en el mismo puerto (memoria de la máquina), mirando antes el cwd del proceso
que lo ocupe.
Si la base local no tiene una caja con ruido, se arma una por SQL (conteo de cierre en dólares) y se restaura.

## Resultado de las pruebas (11/11/2026)

**Tests:** `PdvCajaServiceBalanceTest` 6/6 en verde (más `PdvCajaServiceMontosNulosTest` y `TicketEscposGraphQLTest`).
Con las dos líneas viejas fallan los 4 casos de ruido: −0,5700000000000003, 0,5700000000000003, 5,55e−17 y 0,00333.

**Runtime:** jar de `develop` y jar de la rama, uno después del otro en el 8081, contra la bodega local (perfil `dev`,
Flyway, réplica, SIFEN, correo, cotización, acreditación POS, poller de retiros y Firebase apagados).
`balancePorCajaIdAndSucursalId` de las 400 cajas más recientes de la sucursal 1 con cierre en reales o dólares:

| | develop | rama |
|---|---|---|
| Cajas con `diferenciaRs`/`Ds` fuera de centavos | 275 | 0 |
| Campos que cambiaron | — | solo `diferenciaRs`, en 294 cajas |
| Mayor cambio | — | 0,0048 (siempre el redondeo a centavos del valor de develop) |
| `-0.0` | — | ninguno |
| Totales (`totalVentaRs` 350,00391…, cierres, total general) | — | idénticos |

Ejemplo: caja 6334, 15,790434782608713 R$ → 15,79. La muestra no tenía ruido en dólares (lo cubren los tests).

**Por pantalla:**
- PWA, detalle de caja **6232** (diferencia real −0,0039 R$): con develop «R$ -0,00» y «La caja cierra con
  diferencia»; con la rama «R$ 0,00» y **sin aviso**.
- PWA, caja **6002** (control negativo, −4 R$): «R$ -4,00» con aviso en los dos.
- Desktop web contra el mismo central (Operaciones → Venta → Cajas → Ir a Ventas), caja 6232: «Reales: 0,00» en los
  dos (ya formatea a 2 decimales).

**Base local:** le faltaban columnas de migraciones `.5` de develop y el balance no corría ni con develop. Se agregaron
a mano, con las sentencias idempotentes de esas migraciones y sin fila en el historial de Flyway: `moneda.codigo_iso`
(V258.5) y las seis `ADD COLUMN` de V267.5 (`venta`, `venta_item`, `cobro`, `cobro_detalle`), todas nullable.

## Auditoría del diff (paso 8)

Fijo 1 (permisos): sin cambios de resolvers ni roles. Fijo 2 (migraciones): N/A. Fijo 3 (operaciones/contrato): mismo
tipo `Float`, mismos campos; solo `diferenciaRs`/`Ds` con menos dígitos; nada se persiste. Condicionales: no toca
tablas replicadas ni canales; el central no compara su balance con el del filial.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Lectores: API (desktop, PWA), ticket; nadie persiste ni acumula | Sin cambio de enfoque |
| A2 | Desktop valida retiros/gastos y el análisis de diferencias con el valor | Agregados como beneficio |
| A3 | `estadoDiferenciaConsecutiva` (l.~727-749) compara con `Double.equals` sumas de billetes: mismo ruido | Fuera de alcance, issue aparte |
| A4 | Faltaban signo, null y totales crudos en los tests | Agregados |
| B1 | Los eventos de caja no tienen llamador en el central | Corregido en «Quién lee»; sin test de evento |
| B2 | `BigDecimal.valueOf(NaN)` lanza | `centavos` devuelve NaN/infinito tal cual + test |
| B3 | No sale −0,0 (BigDecimal no tiene cero negativo); HALF_UP simétrico como el filial | Test de ±0,005 |
| B4 | Faltaba control negativo y correr los jars uno por vez | Agregado a runtime |

## Sin verificar

- `estadoDiferenciaConsecutiva` (apertura contra cierre anterior) sigue comparando doubles exactos: issue aparte.
