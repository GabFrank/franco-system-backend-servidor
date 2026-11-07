# Plan: «Salida varia» en el PDF de movimientos de caja (seguimiento de desktop #692 / PR #694)

Repo: central. Rama `fix/caja-mayor-salida-varia-pdf` desde `origin/develop`. Sin cambios de esquema ni de GraphQL.

## 1. El problema [verificado leyendo]

El PDF de movimientos de caja (botón PDF del dashboard de caja mayor → `ReporteMovimientosCajaService`) arma la columna
Tipo con `etiquetaCaja(origen, tipo)`: si el origen tiene etiqueta en `ORIGEN_LABELS`, gana el origen. Entradas y
salidas varias comparten el origen `ENTRADA_VARIA`, así que una salida varia sale «Entrada varia». El desktop lo
corrigió en el PR #694 (`labelMovimiento`); el comentario de `ORIGEN_LABELS` dice «Mismas etiquetas que el dashboard»,
y con #694 dejan de serlo.

Además `ORIGEN_LABELS` no tiene `VUELTO` (el desktop sí: «Vuelto»), y en el PDF un vuelto sale «Ingreso».

## 2. Cambio (una fase)

`ReporteMovimientosCajaService.etiquetaCaja`:
- origen `ENTRADA_VARIA` y tipo `EGRESO` (comparando el enum) → «Salida varia», misma regla que `labelMovimiento` del
  desktop. Estricta en `EGRESO`: `EntradaVariaService.registrar` solo crea `INGRESO` o `EGRESO` con ese origen (local:
  115 INGRESO, 5 EGRESO, ningún otro tipo).
- Comentario de `ORIGEN_LABELS` / `etiquetaCaja`: remite a `labelMovimiento` del desktop y nombra la excepción, para que
  quien toque una copia sepa que hay otra.
- `ORIGEN_LABELS`: agregar `VUELTO` → «Vuelto» (confirmado por Franco el 07/11: misma deuda de paridad).

## 3. Lo que no cambia
- La plantilla `.jrxml`, la query, los totales, el banco.
- El desktop (ya en #694).

## 4. Datos nuevos
Ninguno.

## 5. Tests
- Unit en `ReporteMovimientosCajaServiceTest`: `etiquetaCaja(ENTRADA_VARIA, EGRESO)` = «Salida varia»,
  `(ENTRADA_VARIA, INGRESO)` = «Entrada varia», `(VUELTO, INGRESO)` = «Vuelto», `(null, EGRESO)` = «Egreso».
- Fill de la plantilla (auditoría B): extender `plantillaImprimeEncabezadoResumenYFilas` con una salida varia y un
  vuelto, y afirmar «Salida varia» y «Vuelto» sobre el texto impreso. Revertir el fix y ver que los dos tests fallan.
- Batería: `./mvnw clean verify -B -DskipFlyway=true` (en una copia fuera de `dev-frc`, por jdtls).
- Por pantalla: central de esta rama (8081, dev) + desktop web; en la caja 19 (tiene salidas, entradas y un vuelto de
  la prueba de #692) generar el PDF desde el dashboard y leer la columna Tipo. Desktop de la rama de #694, para que el dashboard muestre la
  misma regla y se pueda comparar fila por fila.

## 6. Auditoría del plan (paso 5)
- Eje A: `etiquetaCaja` solo la usa este PDF; nadie parsea el texto; sin GraphQL, enum, réplica ni variables; las 16
  etiquetas coinciden con el desktop y solo falta `VUELTO`; sin orden de despliegue. Menores: comentario de las dos
  copias, test del fallback, probar contra el desktop de #694. Incorporados.
- Eje B: sin datos ni estado; no puede romper el PDF (columna `StretchHeight` de 70 pt, «Salida varia» entra); el
  total no cambia (usa el tipo, no la etiqueta). Medio: probar el fill, no solo la función. Incorporado.

## 7. Sin verificar
- Nada propio de un canal: el PDF lo arma el central en cada pedido, sin caché.
