# TODO UI — Módulo Financiero / Tesorería

Refinamientos de UI detectados durante las pruebas manuales guiadas (2026-07-31).
**Ejecutados en el refactor UI** (rama `feat/modulo-financiero`) — ver [PLAN-REFACTOR-UI-FINANCIERO.md](PLAN-REFACTOR-UI-FINANCIERO.md).

## Lista de Cajas Virtuales (`list-caja-virtual`)

- [x] **T1 — Componente genérico `app-generic-list`** (slots `[filtros]`/`[table]`, patrón `list-vale`). Hecho.
- [x] **T2 — Más filtros**: nombre, tipo, sucursal, estado (activo/inactivo) → query backend `cajaVirtualesFilter`. Hecho.
- [x] **T3 — Acciones en `mat-menu`** (⋮): Ver dashboard, Historial, Editar, Eliminar. Iconos sueltos quitados. Hecho.

## Dashboard de detalle de caja (`caja-virtual-dashboard`)

- [x] **T5 — Rediseño completo** portando el layout de `caja-mayor-detalle` de frc-gourmet:
  header con estado + acciones (Ingreso, Egreso, Ingreso/Egreso Vario, Transferir, Operación, **Configurar**);
  grid main/sidebar responsive; sidebar con **Saldo en Caja** (por moneda, `cajaVirtualSaldos`), **Cuentas bancarias**
  (`cajaVirtualResumenBancario`: actual/reservado/futuro, config-driven) y CPP/CPC (config-driven);
  tabla de movimientos con filtros (fecha/tipo), toggle "ver anulaciones", paginación server-side y **Anular** por fila.
  Placeholder "OTRO LISTADO" y buscador decorativo eliminados; código muerto (`monedaSeleccionada`/`getSaldoActual`) removido;
  refresh de saldo ahora vía `cajaVirtualSaldos` (no el hack anterior). Hecho.
- [x] **Dialog `configurar-caja-virtual-dialog`** (nuevo): cuentas bancarias visibles + drag&drop de orden (CDK) +
  toggles mostrar CPP/CPC + operaciones financieras habilitado (CN9). Persiste vía `saveCajaVirtualConfiguracion`. Hecho.

## Manejo de errores de negocio

- [x] **T6 — Errores de negocio user-friendly**: `err.graphQLErrors[0].message` → snackbar limpio en anular (dashboard),
  ingreso/egreso (`add-movimiento` dialog, que era el que mostraba el error crudo del CN2) y guardar configuración. Hecho.

## Estructura de menú lateral

- [x] **T4 — "Bancario" movido dentro de "Financiero"** (items Cuentas Bancarias + Bancos), grupo top-level eliminado. Hecho.

## Diferido (fuera de este refactor)
- Números por-moneda en las cards CPP/CPC del sidebar (requiere read-model por caja; hoy son entradas config-driven que
  remiten al Dashboard de Tesorería / Ventas a crédito).
- Ledger consolidado caja+banco en la tabla de movimientos + toggle "ver POS/ocultos".
- Egreso de caja inicial (conteo billete) y diálogos de cobro CPC / pago compras / emitir cheque embebidos en el dashboard.

## Notas
- Capa desktop (`frc-comercial/desktop`), módulo `financiero`. Backend de soporte en central (migración V186.5 + queries nuevas).
- Convenciones respetadas: dark mode, sin funciones en HTML (display precalculado en `toRow`), fechas string vía `dateToString`.
