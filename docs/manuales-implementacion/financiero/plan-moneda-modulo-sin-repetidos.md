# Plan — Moneda por módulo sin registros repetidos

Rama `fix/moneda-modulo-sin-repetidos` en central y desktop, desde `develop` (2026-11-14).

## Problema

La tabla `financiero.configuracion_moneda_modulo` es de solo altas: cada «Nueva configuración» crea
una fila y nunca se edita (solo `activo`). La ventana «Moneda por módulo» lista el historial entero,
y nada impide crear una fila con la misma moneda que ya rige. En alpha hay 20 filas para 5 claves:
PDV / sucursal 2 / DÓLAR ×4, PRECIOS / DÓLAR ×4, COMPRAS / DÓLAR ×5.

Decisión de Franco (2026-11-14): agrupar la lista + bloquear el alta repetida. **No se tocan datos**:
las filas viejas son historial y se replican a las filiales.

## Fase 1 — central: rechazar el alta igual a la que rige

`ConfiguracionMonedaModuloService.crear`, después de resolver sucursal y `vigenteDesde`, y antes de
los controles de cotización:

- `MonedaModuloLector.fila(filas del módulo, sucursalId, vigenteDesde)` — la misma regla con la que
  se resuelve la moneda: fila activa más reciente de **esa clave exacta** (módulo + sucursal) con
  `vigente_desde <= vigenteDesde`.
- Si existe y su moneda es la misma (`MonedaService.misma`) → `GraphQLException`
  «PDV de la sucursal X ya usa DOLAR desde el 10/11/2026: no hace falta otra configuracion igual.»
- Clave exacta, sin caer a la fila de toda la empresa: una fila de sucursal igual a la global sigue
  siendo válida (la fija ante un cambio futuro de la global).
- Sin filas activas de la clave no se rechaza nada (asignar explícitamente la moneda de la empresa
  sigue permitido).
- Una fila inactiva con la misma moneda no bloquea: desactivada no rige.
- **COSTOS queda afuera** (auditoría eje B): una fila COSTOS nueva con la misma moneda re-fija
  `cotizacion_partida` (la lee `CosteoMoneda.enMonedaDeCosteo` vía `filaGlobal`). Es la única forma de
  corregir una partida mal tomada; el rodeo de desactivar y crear reescribiría el costeo histórico.

Tests (`ConfiguracionMonedaModuloServiceTest`): rechaza igual a la vigente; COSTOS igual se acepta; acepta otra moneda;
acepta igual a una inactiva; acepta sucursal igual a la global; una programada distinta en el medio
permite repetir después de ella; vigencia futura compara contra la que regirá en ese instante.
Revertir el fix y ver fallar el primero (paso 7).

Sin migración, sin cambio de `.graphqls`, sin cambio en el filial (solo lee la tabla).

## Fase 2 — desktop: lista agrupada con «Ver historial»

`configuracion-moneda-modulo.component`:

- Por defecto, una vista por clave (módulo + sucursal): las activas con vigencia posterior a
  `corte = ahora − 1 día` **más** la primera activa con vigencia `<= corte`; si la clave no tiene
  ninguna activa, su fila más reciente (para que no desaparezca una clave desactivada entera).
- El «ahora» es el reloj de la PC, no el del central (ejes A y B). El margen de un día hace que un
  reloj corrido menos que eso solo pueda **sumar** filas a la vista, nunca esconder la que rige en
  el servidor. Caso en el spec con el «ahora» adelantado.
- Al desactivar, si la clave tiene otras activas anteriores con la **misma moneda**, el diálogo lo
  dice («hay N más con DÓLAR: desactivalas desde Ver historial»), para que no parezca que no pasó
  nada (eje A).
- Toggle «Ver historial» en los filtros: muestra todas las filas, como hoy.
- La función de agrupado es pura y exportada desde `configuracion-moneda-modulo.service.ts` (mismo
  patrón que `mensajeSinCotizacion`), con casos en `configuracion-moneda-modulo.service.spec.ts`.
- El texto de ayuda dice que la lista muestra la vigente y cómo ver el historial.
- El rechazo del central lo avisa el genérico (`onSaveCustom`), como el resto de los rechazos.

Sin funciones ni getters en el template (regla del desktop): el agrupado se calcula al cargar y al
cambiar el toggle.

## Datos nuevos

Ninguno: sin columnas, claves ni configuración nueva.

## Orden de despliegue

Independientes: el desktop agrupado funciona con un central viejo (agrupa en el cliente) y un
central nuevo con un desktop viejo solo rechaza el alta repetida con el mensaje del genérico.

## Qué queda sin verificar

- Concurrencia: dos altas iguales simultáneas pasan las dos (sin constraint). Aceptado: es una
  pantalla de administración de un solo rol.
