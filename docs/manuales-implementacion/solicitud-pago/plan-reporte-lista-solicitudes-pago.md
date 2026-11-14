# Plan — Reporte PDF de la lista de solicitudes de pago

Pedido (Franco, 14/11/2026): la lista de solicitudes de pago del desktop ya filtra por monedas
(central #639 + desktop #745). Ahora tiene que poder **generar un PDF de la lista filtrada**. Hoy solo
se imprime una solicitud por vez (`imprimirSolicitudPagoPDF(id)`).

Patrón que se copia: el reporte de movimientos de caja (`ReporteMovimientosCajaService` +
`reports/movimientos-caja.jrxml` en el central; `impresionService.imprimir(..., soloPdf=true)` en
`caja-virtual-dashboard` del desktop).

## Qué imprime

- Los **mismos filtros** que la lista: número, proveedor, estado, monedas y rango de fechas. El encabezado
  dice qué filtros se aplicaron, quién generó el reporte y cuándo.
- Las **mismas columnas** que la tabla: N.º de solicitud, proveedor, fecha, monto con su símbolo y estado.
  El monto es **`SolicitudPago.montoTotal`**, el mismo campo que muestra la tabla. No es la suma por notas
  que usa `imprimirSolicitudPagoPDF`.
- Sin paginar, ordenado por fecha de solicitud y después por id. La tabla no ordena (`PageRequest` sin
  `Sort`), así que el orden puede no coincidir con lo que se ve en pantalla.
- Un **total por moneda** dentro del cuadro «Resumen», rotulado «Total solicitado:» (Franco, 14/11: sin la
  aclaración «sin canceladas»). Las monedas no se suman entre sí.
- El **logo de la empresa** arriba a la izquierda (`ImageService.getLogoReporte()`, como el KuDE). Si el servidor
  no tiene `logo.png`, el reporte sale sin logo y no falla.
  - **Las CANCELADO aparecen en la lista pero no suman al total**, igual que los anulados del reporte de
    caja. Confirmado por Franco el 14/11.
  - Una PARCIAL suma su monto completo, no lo que le falta pagar. Por eso el pie no se rotula como deuda.
- Va sin límite de filas, como el de caja. La lista arranca con los últimos 7 días.

## Fases

### Fase 1 — central (`feature/solicitud-pago-reporte-lista`)

1. `SolicitudPagoGraphQL`: extraer la `Specification` de `solicitudesPagoPaginated` a
   `specCompras(proveedorId, estado, numero, fechaDesde, fechaHasta, monedaIds)`. La query paginada pasa a usarla.
   No cambia de comportamiento.
2. Query nueva `imprimirReporteSolicitudesPago(proveedorId, estado, numero, fechaDesde, fechaHasta,
   monedaIds): String`, que devuelve el PDF en base64.
   - **Primera línea `exigirRolCompras()`**: los mismos roles que la lista.
   - Le pasa `specCompras(...)` y las etiquetas de los filtros a `ReporteSolicitudesPagoService.generar(...)`.
3. Servicio `service/operaciones/ReporteSolicitudesPagoService` (`@Transactional(readOnly = true)`):
   - **La consulta corre adentro de la transacción**: `findAll(spec + fetch, Sort)`. El fetch trae
     proveedor, persona y moneda, que son LAZY, y así se evita el N+1. Como `findAll` sin `Pageable` no hace
     count, el fetch no rompe la query paginada, que sigue usando la spec sin fetch. (Auditoría A y B.)
   - `armar(...)` devuelve los parámetros y las filas. Es package-private para poder testearlo.
   - Compila la plantilla una vez y la cachea, igual que el de caja.
   - Un `JRException` se convierte en `GraphQLException`.
   - Montos con `ReporteMovimientosCajaService.monto` (decimales de la moneda y locale GERMANY). Ese
     método pasa de package-private a `public`.
4. Plantilla `reports/solicitudes-pago-lista.jrxml`, A4 vertical, con el layout de `movimientos-caja` más el logo.
5. `.graphqls`: declarar la query nueva en `solicitud-pago.graphqls`.

Tests de la fase 1:

- `ReporteSolicitudesPagoServiceTest`:
  - el total por moneda deja afuera las CANCELADO;
  - las etiquetas de filtros salen bien, con y sin filtros («Todas», «Sin límite»);
  - montos en PYG sin decimales y en USD con decimales;
  - **llenar la plantilla de verdad** (fill + export) y comprobar que el texto sale, no que el PDF pese
    (ver la memoria del `.jrxml` con alto insuficiente).
- `SolicitudPagoGraphQLTipoTest`: el reporte también excluye GASTO/RRHH y se niega sin rol de compras.

### Fase 2 — desktop (`feature/solicitud-pago-reporte-lista`)

1. GQL `imprimirReporteSolicitudesPago.ts` + entrada en `graphql-query.ts` + `onImprimirReporteSolicitudesPago(...)`
   en `solicitud-pago.service.ts`, con `onCustomQuery` igual que la caja.
2. `list-solicitud-pago`:
   - `loadPage()` guarda las variables que usó en `filtrosAplicados`. El PDF usa esos filtros, no los del
     formulario sin aplicar: imprime lo que se ve en pantalla.
   - Botón «Generar reporte (PDF)» (`picture_as_pdf`) junto a los filtros. Se deshabilita con un flag
     calculado (no una función en el HTML) cuando la lista está vacía.
   - `onGenerarReporte()` llama a `impresionService.imprimir(nombre, () => ..., true)`.
3. Gate de rol en el botón: N/A, porque toda la pantalla ya pide los roles de compras desde el menú
   (`openTabIfAuthorized`). La query exige los mismos roles en el backend.

Tests de la fase 2: N/A para el desktop (el CI no corre Karma). El gate es `npm run check`.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| query `imprimirReporteSolicitudesPago` | `SolicitudPagoGraphQL` (central) | `SolicitudPagoService.onImprimirReporteSolicitudesPago` (desktop) |
| `filtrosAplicados` (desktop, en memoria) | `loadPage()` | `onGenerarReporte()` |

Sin migraciones ni tablas nuevas, y nada que replique: `solicitud_pago` se lee en el central.

## Despliegue

El central va primero: un desktop nuevo contra un central viejo falla **solo al tocar el botón**
(validation error, avisado por `ImpresionService`). La lista y el resto siguen funcionando. Por eso el
orden importa menos que en #639/#745, pero se mantiene: central #… → desktop #….

## Prueba de runtime (paso 9)

Central local en 8081 + desktop web, con usuario MR. Casos:

1. Sin filtros.
2. Filtrando por moneda USD.
3. Filtrando por moneda PYG + estado.
4. Proveedor + rango sin resultados: el botón queda deshabilitado.
5. Una CANCELADO dentro del rango: aparece y no suma.
6. Usuario sin rol de compras, por API: rechazo.

Hay que comprobar que el PDF coincide con lo que muestra la tabla en cada caso.

## Auditoría del plan (paso 5)

- **Eje A:** ningún contrato existente cambia. La PWA y el mobile usan la query paginada solo con
  `proveedorId` y `estado`, y la extracción no cambia nada para ellos.
  - Riesgo aplicado: la consulta va dentro de la transacción del servicio.
  - Detalles anotados: el orden distinto al de la tabla, y que `monto` pasa a `public`.
- **Eje B:** no toca la base y es reversible en cualquier orden de rollback.
  - Aplicados: el monto es `montoTotal`, el rótulo del pie y el fetch contra el N+1.
  - **Sin aplicar:** el tope de 5.000 filas. El reporte de caja tampoco lo tiene; se puede sumar si hace
    falta.
  - **Heredado, sin tocar:** el filtro hasta `23:59` deja afuera lo de las 23:59:xx, igual que la lista.

## Sin verificar todavía

- El volumen: cuántas solicitudes de COMPRA hay en bodega para un rango largo. Si son miles, el PDF tarda
  pero no rompe. No se pone límite salvo que el usuario lo pida.
