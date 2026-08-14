# Plan de refactor UI — Módulo Financiero / Tesorería

> Autónomo, no requiere aprobación. Basado en el análisis a fondo de **frc-gourmet** (rama develop,
> `src/app/pages/financiero/caja-mayor/`) + estado actual del desktop. Rama de trabajo `feat/modulo-financiero`
> (central + desktop). Flujo canónico: implementar por fases → iteraciones de review → tests → docs → commit/push → test manual.
>
> Referencia de origen: dashboard `caja-mayor-detalle`, lista `list-cajas-mayor`, config `configurar-caja-mayor-dialog` de gourmet.
> Adaptación: nuestro stack es GraphQL (Apollo/desktop ↔ graphql-java-kickstart/central) + JPA/Postgres multiusuario,
> no TypeORM/IPC. Se porta el **modelo y la UX**, no el código literal.

## Principios
- Nada hardcodeado ni improvisado; columnas/campos reales. Reusar componentes maduros del repo.
- Dark mode, sin funciones en HTML (precalcular), textos guardados en MAYÚSCULAS, paginación estándar `PageInfo<T>`.
- Migraciones aditivas sufijo `.5`. Todo cambio de schema GraphQL es aditivo (no romper desktop/mobile).

---

## Fase R1 — Menú lateral (T4)  · desktop
Mover los 2 items del grupo **"Bancario"** (`list-cuentas-bancarias`, `list-bancos`) dentro del grupo **"Financiero"**
(después de "Terminales POS") y eliminar el grupo "Bancario". Imports y `case` de `onItemClick()` no cambian.
Unificar esos items al helper `openTabIfAuthorized` si se toca el bloque.
Archivo: `shared/components/side-mini-variant/side-mini-variant.component.ts`.

## Fase R2 — Backend de soporte (central)  · aditivo
1. **V186.5** — config de caja: M:M `caja_virtual_config_cuenta_bancaria` (cuentas bancarias visibles) + columna
   `cuentas_bancarias_orden` TEXT (JSON de IDs) en `caja_virtual_configuracion`. Aditiva.
2. **Query saldos por caja**: `cajaVirtualSaldos(cajaVirtualId): [CajaVirtualSaldoItem]` → `{moneda, saldo}` leído de
   `caja_virtual_saldo` (fuente de verdad, no el shim `saldo_gs/rs/ds`). Reemplaza el matching frágil por `denominacion.includes()`.
3. **Filtro de lista**: `cajaVirtualesFilter(nombre, tipo, sucursalId, activo, page, size): CajaVirtualPage`
   (todos opcionales). Sustituye la lista que hoy solo filtra por `tipo`.
4. **Config CRUD**: ya existe `cajaVirtualConfiguracion`/`saveCajaVirtualConfiguracion`. Extender el input+tipo con
   `cuentasBancariasVisiblesIds: [Int]` y `cuentasBancariasOrden: String`.
5. **Resumen bancario para el sidebar**: `cajaVirtualResumenBancario(cajaVirtualId): [CuentaBancariaResumen]` →
   solo las cuentas marcadas visibles en la config, cada una con `saldo` (actual), `saldoReservado` (cheques diferidos),
   `saldoFuturo` (acreditaciones POS pendientes). Read-model.
6. **Resumen CPP/CPC para el sidebar** (solo si la config lo pide): `cajaVirtualResumenCpp/Cpc: [ResumenPorMoneda]`
   → por moneda `{esteMes, mesQueViene, total, vencidas}`. Reusa `TesoreriaReporteService`.

## Fase R3 — Lista de cajas (T1/T2/T3)  · desktop
Reescribir `list-caja-virtual` con `app-generic-list` (slots `[filtros]`/`[table]`, patrón `list-vale`):
- **Filtros**: nombre (input), tipo (select), sucursal (select), activo (select Sí/No/Todos) → `cajaVirtualesFilter`.
- **Tabla** `mat-table` + columnas actuales (id, nombre, tipo chip, sucursal, responsable, saldos, activo) + paginador.
- **Acciones**: una sola columna con `mat-menu` (⋮): Ver dashboard, Historial, Configurar, Editar, Eliminar
  (gating por `puedeGestionar`). Quitar los iconos sueltos.

## Fase R4 — Dashboard de caja (T5)  · desktop  (núcleo del refactor)
Reconstruir `caja-virtual-dashboard` según el layout de gourmet (`caja-mayor-detalle`):
- **Header**: nombre + chip de estado (activo) + acciones (gating `puedeGestionar`):
  Registrar Ingreso, Registrar Egreso, Ingreso Vario, Egreso Vario, Transferir, Operación Financiera, **Configurar**.
- **Grid** `minmax(0,1fr) 280px` (main + sidebar), responsive a 1 col en <1100px.
- **Sidebar**:
  - "Saldo en Caja": todas las monedas de `cajaVirtualSaldos` (rojo si negativo).
  - "Cuentas bancarias" (si config): card por cuenta visible con Actual / Reservado (naranja) / Futuro (azul), click abre movimientos.
  - "Cuentas por Pagar"/"Cuentas por Cobrar" (si config): mini-cards por moneda con buckets; click abre listado.
- **Main — Movimientos**: `mat-table` con columnas fecha, tipo (chip), descripción, monto, saldoPosterior, acciones.
  - Filtros: desde/hasta (datepicker), tipo (Ingresos/Egresos/Todos), toggle "Ver anulaciones".
  - Paginación server-side. Acción por fila `mat-menu` (⋮): **Anular** (ya cableado; reusar), Ver detalle.
  - Reemplaza la lista expandible ad-hoc actual. Quita el panel "OTRO LISTADO PENDIENTE" y el buscador decorativo.
- **Limpieza**: eliminar código muerto (`monedaSeleccionada`/`getSaldoActual`/`getPrecision` no usados), arreglar el
  hack de refresh de saldo en `recargar()` (releer la caja con `cajaVirtual(id)` o usar `cajaVirtualSaldos`).
- **Configurar dialog** (nuevo `configurar-caja-virtual-dialog`): lista de cuentas bancarias con checkbox visible +
  drag&drop de orden (CDK) + toggles "Mostrar CPP"/"Mostrar CPC" + (bonus) "Operaciones financieras habilitado" (CN9).
  Guarda con `saveCajaVirtualConfiguracion`.

## Fase R5 — Errores de negocio amigables (T6)  · desktop
Helper para mapear errores GraphQL de negocio a snackbar limpio (`err.graphQLErrors[0].message`) en todas las mutations
del módulo (ingreso/egreso/transferencia/operación/config/anular). Ya aplicado en `onAnular`; extender al resto.

---

## Diferido (fuera de este refactor — requiere read-models pesados o flujos productivos)
- Ledger **consolidado caja+banco** en la tabla de movimientos + toggle "ver POS/ocultos" (ruido bancario).
- Egreso de **caja inicial** (conteo billete a billete) desde el dashboard.
- Diálogos de **cobro CPC rápido / pagar compras en lote / emitir cheque** embebidos en el dashboard (tienen pantalla propia).
- Acceso directo (shortcuts) de cajas en dashboards Home/Financiero (feature de gourmet no prioritaria).

## Tests
- Backend: tests de `cajaVirtualesFilter`, `cajaVirtualSaldos`, resumen bancario/CPP/CPC, config con cuentas visibles.
- Desktop: `npm run check` (AOT) al final de todas las fases.

## Checklist de cierre
- [ ] R1 menú · [ ] R2 backend+migración · [ ] R3 lista · [ ] R4 dashboard+config · [ ] R5 errores
- [ ] 3 iteraciones de review (bugs, imports, migraciones, mala práctica) → corregir
- [ ] tests backend verdes + AOT desktop verde
- [ ] docs actualizadas (ARQUITECTURA, ESTADO-Y-PLAN, TODO-UI marca hechos) + skill
- [ ] commit+push central y desktop · esperar test manual
