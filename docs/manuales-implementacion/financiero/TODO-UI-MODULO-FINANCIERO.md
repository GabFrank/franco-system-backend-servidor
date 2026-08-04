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

## Maletín — histórico de contenido (propuesta futura, 2026-08-04)

**Contexto:** hoy la entidad `Maletin` (`financiero.maletin`) NO guarda ninguna información sobre su
contenido; es solo un identificador (descripción=código de barras, activo, abierto, sucursal). El "valor
dentro del maletín" se **infiere** en tiempo real comparando el último `conteoCierre` de la `PdvCaja` que
lo usó (ver `MaletinTesoreriaService.valorMaletin`). El ingreso/egreso de maletín a caja mayor (implementado
2026-08-04) postea movimientos en `MovimientoCajaVirtual` con `origenTipo=MALETIN`, pero el maletín en sí
no lleva registro de su saldo.

**Propuesta (Gabriel):** crear un **histórico de contenido del maletín**, multi-moneda, que registre el
contenido en cada **evento** de su ciclo de vida:
- apertura de caja (el maletín entrega efectivo para abrir la caja)
- cierre de caja (el efectivo contado vuelve al maletín)
- ingreso a caja mayor (el maletín llega a tesorería y descarga su valor)
- carga desde caja mayor / egreso (se despacha efectivo dentro del maletín hacia una sucursal)

**Beneficios:**
- Saldo del maletín **persistido y auditable** por moneda (no inferido), con trazabilidad evento a evento.
- Habilita **retiros/cargas parciales** del maletín (hoy el ingreso descarga todo el cierre; con histórico
  se puede sacar/meter solo una parte) dejando cada movimiento registrado.
- Permite conciliar diferencias por maletín contra su histórico, no solo contra la caja anterior.

**Bosquejo técnico (a definir):**
- Nueva entidad tipo `MaletinMovimiento` / `MaletinContenido` (M:1 a `Maletin`), con `evento` (enum:
  APERTURA_CAJA, CIERRE_CAJA, INGRESO_CAJA_MAYOR, CARGA_CAJA_MAYOR, RETIRO_PARCIAL, AJUSTE), `moneda`,
  `monto` (con signo o entrada/salida), `saldoPosterior` por moneda, `referencia` al origen (pdv_caja id,
  movimiento_caja_virtual id, etc.), usuario, fecha. Patrón ledger inmutable, igual que `MovimientoCajaVirtual`.
- Enganchar los hooks: `PdvCajaService.save` (apertura/cierre) y `MaletinTesoreriaService` (ingreso/egreso).
- Migración aditiva (sufijo `.5`), replicación filial→central (el maletín se origina en filial).
- UI: pantalla de "contenido/histórico del maletín" (multi-moneda) + saldo actual por maletín.

**Estado:** NO iniciado. Es un feature grande (nueva entidad + hooks en flujo PDV productivo + replicación).
Planificar fasado antes de construir.

## Notas
- Capa desktop (`frc-comercial/desktop`), módulo `financiero`. Backend de soporte en central (migración V186.5 + queries nuevas).
- Convenciones respetadas: dark mode, sin funciones en HTML (display precalculado en `toRow`), fechas string vía `dateToString`.
