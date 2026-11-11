# Plan #607: el estado de diferencia consecutiva compara moneda por moneda

Issue: GabFrank/franco-system-backend-servidor#607. Rama: `fix/caja-diferencia-consecutiva-por-moneda` (desde
`develop` f3bd8e3d). Pieza: **central** solamente.

## Problema

`PdvCajaService.calcularEstadoDiferenciaConsecutivaConCajaAnterior` (~l.731-756) compara el conteo de **cierre de la
caja anterior** del mismo maletín con el de **apertura** de la caja actual y devuelve `CON_DIFERENCIA` /
`SIN_DIFERENCIA`. Hace `sum(cantidad × valor)` de **todos los billetes, de todas las monedas**, en `double`, y compara
con `Double.equals`:

1. **Mezcla monedas:** si 100 R$ pasaron a ser 100 US$, la suma da igual y sale `SIN_DIFERENCIA`; un faltante de
   5.000 Gs se compensa con 5.000 «unidades» de otra moneda.
2. **Doubles exactos:** 3 billetes de 0,1 R$ dan 0,30000000000000004 contra 1 de 0,3 → `CON_DIFERENCIA` falso. (El
   orden de suma no importa: `DoubleStream.sum` compensa; el ruido sale de `cantidad × valor`.)
3. Un billete con `cantidad` o `valor` nulo tira NPE y rompe la página entera del análisis.

Quién lo ve: **desktop, Análisis de diferencias → diferencia de maletín** (`cajasAnalisisDiferencias`): la etiqueta
CON/SIN DIFERENCIA de cada caja y el filtro por estado, que el central aplica con este mismo cálculo
(`findAllForAnalisisDiferencias`, `difEstado`). Lo usan `enriquecerConCajaAnterior` (cada caja de la página) y
`findCajasByAnteriorIdOptimized` (búsqueda por caja anterior).

## Enfoque

Comparar **por denominación** (la que tenga el billete, cualquiera; en la práctica GUARANI / REAL / DOLAR) con tolerancia de **medio centavo**
(0,005), el mismo criterio que ya usa `EventosCaja.hayDiferencia` para el evento `caja.abierta` (que dice en su
comentario que el central suma todo sin convertir). Totales por moneda con `EventosCaja.totalesDeConteo(billetes,
null)`, que ya existe, saltea billetes con datos nulos y agrupa por denominación.

- `CON_DIFERENCIA` si **alguna** moneda (unión de las dos listas; una que falta en un lado cuenta como 0) difiere en
  más de 0,005. Si no, `SIN_DIFERENCIA`. `SIN_DATOS` sigue igual (sin conteo, sin maletín, sin caja anterior).
- Solo billetes, como hoy: los movimientos de respaldo que `totalesDeConteo` también sabe sumar no entran (el central
  no los cargaba acá; cambiarlo es otro criterio).
- Guaraníes: enteros, la tolerancia no cambia nada.

**Descartado:**
- Redondear la suma total: sigue mezclando monedas.
- Tocar `findAllForAnalisisDiferenciasNative` (SQL con la misma suma mezclada): **no tiene llamador** en el código;
  se deja anotado.
- Arreglar el cálculo del desktop (`adicionar-caja-dialog`, aviso `diferencia-maletin`): es otra pieza.

## Fase única

- `PdvCajaService`: helper `static String estadoPorMoneda(List<ConteoMoneda> cierreAnterior,
  List<ConteoMoneda> aperturaActual)` (package-private; `EventosCaja.estadoConsecutivo` ya existe con otra firma) con
  la regla de arriba; `calcularEstadoDiferenciaConsecutivaConCajaAnterior` lo usa.
- `EventosCaja.totalesDeConteo`: saltear también el billete con `denominacion` nula. Corregir el comentario de
  `EventosCaja.estadoConsecutivo` («el central suma todo»), que deja de ser cierto.
- **Test** `PdvCajaEstadoConsecutivoTest` (unitario, sin mocks), los marcados fallan con el viejo:
  - cierre 1 × 0,3 R$, apertura 3 × 0,1 R$ → `SIN_DIFERENCIA` (viejo: CON) ✱;
  - 100 R$ al cierre y 100 US$ a la apertura → `CON_DIFERENCIA` (viejo: SIN) ✱;
  - −5.000 Gs y +5.000 R$ (se compensaban) → `CON_DIFERENCIA` ✱;
  - 0,01 R$ de diferencia → `CON_DIFERENCIA` (la tolerancia no tapa un centavo);
  - conteos iguales en las tres monedas → `SIN_DIFERENCIA`;
  - una moneda solo en un lado con total 0 → `SIN_DIFERENCIA`;
  - billete con `cantidad` nula, `valor` nulo o `denominacion` nula → se saltea, sin excepción ✱;
  - las dos listas vacías → `SIN_DIFERENCIA`; cierre con plata y apertura vacía → `CON_DIFERENCIA`;
  - una moneda solo en un lado con total distinto de 0 → `CON_DIFERENCIA`;
  - mismo resultado que `EventosCaja.estadoConsecutivo` para los mismos conteos (paridad con el evento `caja.abierta`).

## Datos nuevos

Ninguno: sin migración, sin cambio de GraphQL (mismo `String` con los mismos tres valores).

## Contrato y canales (eje A)

Mismo campo y mismos valores; cambia cuál le toca a algunas cajas. Cliente: desktop (etiqueta y filtro de
Análisis de diferencias). La PWA no lo lee. El filial no tiene esta consulta. Sin orden de despliegue.

## Pruebas de runtime

Jar de `develop` y jar de la rama, uno después del otro en el mismo puerto, contra la bodega local (perfil `dev`,
Flyway, réplica, SIFEN, correo, cotización, acreditación POS, poller de retiros y Firebase apagados):
1. `cajasAnalisisDiferencias` de varias páginas (sucursal 1): contar cajas por estado con cada jar y revisar las que
   cambian contra los conteos en SQL (cuál moneda difiere y por cuánto).
2. **Por pantalla:** desktop, Análisis de diferencias → diferencia de maletín: una caja que cambia de estado muestra la
   etiqueta nueva, y el filtro CON/SIN DIFERENCIA la incluye o excluye en consecuencia.
3. Si la base no trae un caso de monedas cruzadas, se arma por SQL en conteos de prueba y se restaura.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Consumidores: solo el desktop (chip y clase de fila); no recalcula ni muestra importes al lado. PWA, mobile y filial no lo leen | Sin cambio de enfoque |
| A2 | El evento `caja.abierta` ya publica el estado por moneda con 0,005: central y evento pasan a coincidir; su comentario queda falso | Comentario corregido + test de paridad |
| A3 | `findAllForAnalisisDiferenciasNative` sin llamador (confirmado) | Anotado, no se toca |
| A4/B2 | Denominación nula: `merge` con clave null | Se saltea en `totalesDeConteo` + test |
| B1 | `estadoDiferenciaConsecutiva` es `@Transient`: nada se persiste | Rollback = volver el jar |
| B3 | El caso «otro orden» no falla con el viejo (`DoubleStream.sum` usa Kahan) | Reemplazado por 3 × 0,1 contra 1 × 0,3 |
| B4 | Faltaban listas vacías, moneda de un solo lado, `valor` nulo | Agregados |
| B5 | Nombre `estadoConsecutivo` choca con el de `EventosCaja` | `estadoPorMoneda` |
| A5/B6 | Con `difEstado`, el central filtra en memoria la página ya cortada (páginas > 0 incompletas) y calcula el estado dos veces por caja | Defecto previo, fuera de alcance |

## Sin verificar

- `findAllForAnalisisDiferenciasNative` sigue con la suma mezclada (sin llamador).
- Paginación del filtro `difEstado` (defecto previo, ver auditoría A5/B6).
- El aviso `diferencia-maletin` del desktop calcula por su cuenta.
