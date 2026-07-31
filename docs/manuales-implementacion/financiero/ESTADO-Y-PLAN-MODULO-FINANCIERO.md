# Módulo Financiero — Radiografía de estado + Plan integral

> Rama de trabajo: `feat/modulo-financiero` (central + desktop). Documento vivo.
> Relevamiento: 2026-07-31. Fuente de verdad: el **código**.
>
> **Objetivo:** consolidar el **Financiero** como módulo oficial —con la **Caja Mayor /
> tesorería** como hub central de dinero— portando **fielmente** las funcionalidades ya
> implementadas, testeadas y funcionales de `frc-gourmet`. Entregar un MVP casi completo,
> difiriendo solo lo que no existe ni siquiera en gourmet o lo genuinamente no urgente.
>
> **Blueprint:** `frc-gourmet` (rama develop) tiene una tesorería madura y funcional
> (CajaMayor + ledger de 22 tipos + operaciones financieras + bancos/cheques/POS + CPC/CPP +
> cobro consolidado). Gourmet también corre sobre **PostgreSQL**, así que el modelo de datos es
> directamente portable; la diferencia real es de capa: gourmet es Electron+TypeORM (handlers IPC),
> nosotros JPA+GraphQL (resolvers) y **multi-usuario concurrente** (obliga a locking explícito que
> gourmet, mono-proceso, no necesita). **Se porta el modelo y los flujos, no el código.**

---

## Parte 1 — Radiografía del estado actual

### 1.1 Panorama
Módulo grande y mayormente maduro; la mayoría de las sub-áreas están en producción. Lo que **falta
es la Tesorería / Caja Mayor**: UI rescatada (fd-93) + backend gateado, pero **el circuito de dinero
no está cerrado** y falta casi toda la operatoria financiera que gourmet ya tiene.

### 1.2 Estado por sub-área
**✅ EN PRODUCCIÓN:** Cajas PDV (apertura/cierre/arqueo+conteo), SIFEN/factura legal, Gastos
(PreGasto→autorización→retiro→rendición), Venta tarjeta/Terminal POS, Maletín/Retiro, Venta a
crédito (gestión/UI), Ente financiero, Análisis de diferencias, Cambio/cotización.
**🟡 PARCIALES:** dashboard (config localStorage), GastoPorCategoria/Mes (sin reporting), Moneda standalone.
**🔴 DEAD-CODE (backend hecho, frontend inalcanzable):** Bancos/cuentas/cheques (menú "Bancario" vacío),
Sencillo, Concepto, "Pagos" (case comentado).
**🆕 EN INTEGRACIÓN:** Tesorería/Caja Mayor (UI fd-93 + backend gateado, roles `TESORERIA VER/GESTIONAR`, `V176.5`).

### 1.3 Núcleo de Caja Mayor hoy
- `CajaVirtual`: PK simple, tipo `CAJA_MAYOR`/`CAJA_CHICA`, saldos por **columnas fijas** `saldoGs/Rs/Ds`
  (**sin dimensión de forma de pago**), `limiteGs` informativo, sin estados de apertura/cierre.
- `MovimientoCajaVirtual`: ledger con `tipoMovimiento` (**solo 6**: INGRESO, EGRESO, TRANSFERENCIA_ENTRADA/SALIDA, PAGO_PROVEEDOR, AJUSTE), `saldoAnterior/Posterior`, `referenciaId` suelto.
- `registrarMovimiento`/`realizarTransferencia` transaccionales; validan saldo. **Solo RRHH escribe** (5× copy-paste, `AJUSTE` sin signo).

### 1.4 Mapa del circuito de dinero ACTUAL
La caja mayor está **casi vacía de fuentes**: todo el dinero real (ventas, cobros, pagos, gastos,
retiros) se queda en cajas físicas PDV o **flota sin llegar a tesorería**. Emisores reales de la caja
física PDV = **solo 2** (`Cobro` efectivo→VENTA, `GastoDetalle`→GASTO). Caja mayor alimentada **solo por RRHH**.

| Operación | Hoy | ¿Llega a caja mayor? |
|---|---|---|
| Venta contado (efectivo) | `Cobro`→`MovimientoCaja` física | ❌ |
| Venta tarjeta | `VentaTarjeta` (no toca caja) | ❌ |
| Cobro venta a crédito | `finalizarPorCobro` **solo flipea estado** | ❌ (G1) |
| Gasto/PreGasto | `GastoDetalle`→`MovimientoCaja` física | ❌ |
| Compra/Pago proveedor | `Pago`/`PagoDetalle` no tocan caja (FK muerta) | ❌ |
| SolicitudPago | nunca transiciona a pagado | ❌ (G11) |
| Retiro caja PDV | física→física; `caja_virtual_id` huérfano | ❌ (G3) |
| Devolución/merma | `Gasto` sin caja; `TODO(fd-93)` | ❌ (G2) |
| RRHH | `MovimientoCajaVirtual` | ✅ único |

### 1.5 GAPS del núcleo
G1 cobro crédito no mueve plata · G2 devolución sin egreso · G3 puente PDV→mayor inexistente ·
G4 sin anulación central (AguinaldoService sin `anular`) · G5 `AJUSTE` sin signo · G6 sin arqueo/cierre ·
G7 concurrencia sin lock · G8 multi-moneda por string · G9 sin tests · G10 enums duplicados · G11 CPP desconectado.

### 1.6 Hallazgos de infra que faltan (vs gourmet)
- **No hay cuenta corriente de terceros**: `Cliente` solo tiene `credito: Float` (límite), sin `saldoActual`; no existe `MovimientoCliente`/`MovimientoProveedor`. → hay que crearlos (parte del port CPC/CPP).
- **Saldo sin dimensión de forma de pago**: `CajaVirtual` usa columnas Gs/Rs/Ds; gourmet usa saldo por `(caja, moneda, formaPago)`. Portar el modelo rico es prerequisito de casi todo.
- Ya existen en frc-comercial (backend, a completar/wire): `Banco`/`CuentaBancaria`, `Cheque`/`Chequera`, `TerminalPos`/`VentaTarjeta`, `VentaCredito`/`VentaCreditoCuota`, `SolicitudPago`, `Gasto`/`PreGasto`, `Retiro`, `Conteo`, `Cambio`.

### 1.7 Decisión de arquitectura (resuelta)
`PdvCaja` (física, turno del cajero) y la Caja Mayor (tesorería consolidada) **coexisten**; el **Retiro
es el puente**. [Confirmado por el equipo, 2026-07-31]

---

## Parte 2 — Plan integral (port fiel de frc-gourmet)

**Meta:** replicar en frc-comercial el módulo Financiero/Tesorería de gourmet — caja mayor con saldo
por moneda×forma de pago, ledger inmutable, operaciones financieras, bancos, cheques, POS, CPC/CPP con
cuenta corriente de terceros — reutilizando lo que ya existe y construyendo lo que falta.

### 2.0 Principios de diseño (de gourmet, adaptados a JPA/Postgres)
1. **Ledger inmutable + contra-movimiento** (`ANULACION`/`AJUSTE` con signo, self-ref); nunca editar/borrar un movimiento posteado.
2. **Helper único transaccional de saldo con lock real** (`@Lock(PESSIMISTIC_WRITE)` o `@Version`+retry) — único punto donde nos apartamos de gourmet (somos multi-usuario).
3. **Trazabilidad `origenTipo`+`origenId`** (mejor que las ~10 columnas planas de gourmet).
4. **Bloqueo de anulación cross-módulo** (si el movimiento nace en otro dominio, se anula desde ahí).
5. **Doble ledger para terceros** (`MovimientoCliente`/`MovimientoProveedor` + saldo, FK cruzada, parcial nativo, tolerancia de redondeo).
6. **`recalcularSaldos`** como red de seguridad (reconstruir saldo desde movimientos activos).

### 2.1 Modelo confirmado (2026-07-31)
**Caja mayor = efectivo; banco = ledger aparte; forma de pago = router; tesorería = vista consolidada.**
Validado con el autor de gourmet: allí `CajaMayorSaldo.formaPago` **existe pero no se usa** (vestigial) —
la caja mayor es funcionalmente solo-efectivo y la unificación es de UI. Reforzado por el barrido:
`FormasPago.movimentaCaja`/`principal` + sus M:M a `MaquinaPos`/`CuentaBancaria` **son literalmente el router**
(la forma de pago decide si el dinero va a caja mayor o a una cuenta bancaria).

- **D5 (CONFIRMADO) — CajaVirtual = solo efectivo, saldo por `(caja, moneda)` en tabla propia.**
  Nada de columnas fijas `Gs/Rs/Ds` (soporta N monedas, sin hardcodeo). **Sin** dimensión de forma de pago
  en el saldo de caja mayor. Enum `TipoMovimiento` ampliado, `origenTipo`+`origenId`, ledger inmutable.
  Migrar el uso actual de RRHH (`saldoGs` → `(caja, GS)`), sin romperlo.
- **Cuenta bancaria = ledger de primer nivel** (no hija de la caja mayor; operable desde cualquier lado),
  con su propio `MovimientoBancario` y saldos. Cheques y acreditaciones POS la mueven.
- **Router forma-de-pago→destino**: la UI de operación (cobro/pago/gasto/entrada) elige destino
  (efectivo→caja mayor | cuenta bancaria X). Config en `FormaPago` (M:M a cuenta/POS) como gourmet.
- **Vista Tesorería = read-model de solo lectura** que consolida saldos de caja(s) + cuentas bancarias.
  Presentación, cero mezcla de datos.
- **D6 — Reutilizar vs crear.** Reutilizar/extender: `VentaCredito`/`Cuota`=**CPC**; `SolicitudPago`=**CPP**;
  `Cheque`/`Chequera`, `TerminalPos`(≈MaquinaPos), `Banco`/`CuentaBancaria`, `Moneda`/`Cambio`, `Conteo`, `PdvCaja` ya están.
  Crear lo ausente: `MovimientoBancario`, `AcreditacionPos`, `OperacionFinanciera`, `EntradaVaria`,
  `MovimientoCliente`/`Proveedor`, estados de `RetiroCaja`, `CajaMayorConfiguracion`, `Adjunto`.
- **D7 — Nombres.** Mantener `CajaVirtual`/`MovimientoCajaVirtual` evolucionando el modelo (evita churn). A confirmar.

### 2.2 Piezas extra del barrido de completitud (a incorporar)
- **Adjuntos polimórficos** (`Adjunto` `entidadTipo`+`entidadId`): adjuntar comprobante/factura a cualquier movimiento (gasto, CPP/CPC, cheque, retiro, etc.). → F2 (base) usado en todas las fases.
- **Comprobantes térmicos ESC/POS** (template genérico): retiro, vale, recibo cobro/pago cuota, pagaré, acreditación POS, acta de conteo. frc-comercial ya imprime ESC/POS (RRHH) → portar por fase.
- **Reportes "cierre de mes"** (hub separado del dashboard): aging CPC/CPP, flujo de caja semanal, composición ingresos/egresos, comisiones POS, vencimientos 30d. → F8.
- **Dashboard KPIs concretos**: saldo consolidado multi-caja por moneda, CPP vencidas, cheques por vencer 7d, vencimientos combinados CPP+cheques, cotización USD/BRL. → F2/F8.
- **3 árboles de categoría** (Gasto/EntradaVaria/OperacionFinanciera). → F2/F4.
- **Matriz de permisos backend** (~60 permisos en gourmet): replicar el control por operación con el patrón `TesoreriaSecurityService` (issue #177). → cross-cutting, cada fase.
- **Cotización "última activa = vigente"** + override manual por operación. frc-comercial ya tiene scraper (`CotizacionMercadoScheduler`/`NorteCambiosScraper`). → F4/F5.

### 2.3 Decisiones estratégicas (RESUELTAS 2026-07-31)
| # | Decisión | Resolución |
|---|---|---|
| DA1 | Multi-sucursal de caja mayor/bancos | **Central-only.** Una tesorería central única (como hoy: `CajaVirtual` central-only, no replicada). Las sucursales suben su efectivo vía **retiro** hacia la caja mayor central. ⚠️ implicancia: el puente Retiro→Caja Mayor (F3) debe resolver el flujo filial→central (el retiro nace en la filial y debe impactar la caja mayor de central) — detalle de arquitectura a definir en F3. Bancos: centrales. |
| DA2 | Compras de contado | **Toda compra → CPP** (modelo gourmet): contado = CPP con 1 cuota a vencer hoy, pagada en el acto. Un solo camino de pago a proveedor, más trazable. |
| DA3 | Período contable / cierre | **Diferir** (ni gourmet ni frc-comercial lo tienen; sin requerimiento contable explícito). |
| DA4 | Numeración de comprobantes | **Mejora al portar** — agregar secuencia correlativa real (gourmet no la tiene). Diseño en la fase que corresponda. |
| DA5 | Adjuntos | **Evaluar reuso** del upload a Google Drive existente en central antes de portar el `Adjunto` polimórfico. |
| DA6 | Permisos CPP | **Dedicados** (`CPP_*` / `TESORERIA_*`), sin heredar la asimetría de gourmet (que usa `COMPRAS_GESTIONAR`). |
| DA7 | Convención auditoría/soft-delete | **Unificar** en `estado` + contra-movimiento (ledger inmutable), sin `activo` redundante. |

### Matriz de paridad (gourmet → frc-comercial → fase)
| Feature gourmet | Estado en frc-comercial | Fase |
|---|---|---|
| Núcleo CajaMayor/Saldo/Movimiento + 22 tipos + helper saldo + recalcular | `CajaVirtual` simple (6 tipos, Gs/Rs/Ds) | **F1** |
| Anulación + bloqueo cross-módulo | parcial (AJUSTE manual) | **F1** |
| Entradas/Salidas varias (destino caja/banco) | ausente | **F2** |
| Gastos multi-detalle + destino caja/banco | `Gasto`/`GastoDetalle` (solo caja física) | **F2** |
| CajaMayorConfiguracion + dashboard KPI + consolidado | ausente / dashboard parcial | **F2** |
| Egreso caja inicial + abrir caja desde conteo | ausente (`Conteo` existe) | **F2** |
| Retiro PdV→CajaMayor (origen MANUAL/CIERRE, idempotente) | columna huérfana (G3) | **F3** |
| Venta efectivo → caja PDV → retiro → caja mayor | caja PDV ok; falta puente | **F3** |
| Devolución/merma → EGRESO | `TODO(fd-93)` (G2) | **F3** |
| CuentaBancaria 3 saldos + MovimientoBancario + ledger unificado | backend CRUD, sin saldos/movimientos | **F4** |
| Operaciones financieras (5 tipos, incl. TRANSFERENCIA_BANCARIA) | ausente | **F4** |
| CPC robusto (cobro caja/banco, parcial, conversión) + MovimientoCliente + saldo cliente | `VentaCredito` sin cobro real (G1) | **F5** |
| Cobro consolidado por convenio (recibo PDF) | ausente (RRHH tiene convenio crédito) | **F5** |
| CPP: pago simple/mixto/lote, préstamo funcionario invertido, PAGO_PROVEEDOR + MovimientoProveedor | `SolicitudPago` sin pago (G11) | **F6** |
| Cheques: emitir/diferir/cobrar/anular + reserva de saldo | backend CRUD, sin flujo/UI | **F7** |
| POS: AcreditacionPos + scheduler + verificación | `VentaTarjeta`/`TerminalPos` sin acreditación | **F7** |
| Egresos de cajón PDV (EgresoCaja/GastoCaja) | ausente | **F8 / diferible** |

### 2.4 Configuración del módulo (catálogo a portar)
Objetivo: portar **todas** las opciones de configuración del financiero de gourmet + agregar las que falten.
Los catálogos base (monedas, formas de pago, categorías, config de caja mayor) son **prerequisito** de las
operaciones → se construyen temprano (junto con F1/F2). El resto viene con su fase (POS en F7, notificaciones en F8).

**Nota de alcance:** la config de **PdV/tickets/balanza/dispositivos/impresoras** (`PdvConfig`, `Printer`,
`Dispositivo`, umbrales de diferencia de caja) **no es de central** — vive en desktop/filial. Queda fuera de este
plan de central (coordinar aparte si hace falta). frc-comercial central ya tiene parcialmente: `ConfiguracionGeneral`,
`ConfiguracionVentaTarjeta`, `Moneda`, `FormaPago` (con `movimientoCaja`/`autorizacion`), `TipoPrecio`, `Chequera` (modelo viejo), `Banco`/`CuentaBancaria`.

**A portar / extender (por área):**
- **Caja mayor:** `CajaMayorConfiguracion` (formas de pago visibles M:M, cuentas visibles M:M + orden, flags `mostrarCPP`/`mostrarCPC`); 3 árboles de categoría configurables con icono (`GastoCategoria`, `EntradaVariaCategoria`, `OperacionFinancieraCategoria`).
- **Monedas/cotización:** `Moneda` + `decimales`/`principal`/`activo`/`flagIcon`; `MonedaCambio` con **4 tasas** (compra/venta oficial/local); `CajaMoneda` (habilitadas + predeterminado + orden); normalizar `MonedaBillete`.
- **Formas de pago (el router):** `orden`, `principal`, `movimentaCaja` (ya existe); **M:M `FormaPago`↔`MaquinaPos`** y **M:M `FormaPago`↔`CuentaBancaria`** (define a qué destino rutea cada forma de pago).
- **Bancos/cheques/POS:** `CuentaBancaria` (`saldo`/`saldoReservado`/`activo`/`titular`/`alias`); `Chequera` secuencial (`numeroInicial`/`Final`/`siguiente` + estado ACTIVA/AGOTADA/ANULADA); `Cheque.estado` + `esDiferido`; `MaquinaPos` (`porcentajeComision`, `minutosAcreditacion`, cuenta destino); intervalo del scheduler de acreditación parametrizable.
- **Notificaciones:** `ConfiguracionNotificacion` (SMTP + Evolution/WhatsApp, secretos fuera de BD), `EventoNotificacion` (catálogo on/off), `ReceptorNotificacion`, `SuscripcionNotificacion` (ruteo evento×receptor×canal). Eventos financieros: `CAJA_CIERRE`, `CUENTA_PAGAR_VENCE`, `GASTO_RECURRENTE`. (frc-comercial tiene notificaciones push/Firebase por rol — arquitectura distinta; portar el modelo evento→receptor→canal como capa nueva.)
- **Empresa/global:** completar `ConfiguracionGeneral` con timbrado/puntoExpedición/zonaHoraria/monedaPrincipal/logo; `TipoPrecio.autorizadoPor`.

### 2.5 Configuraciones NUEVAS (decidido 2026-07-31 — criterio: más config disponible = mejor)
| # | Config nueva | Estado |
|---|---|---|
| CN1 | **Moneda base única** (unique partial index `WHERE principal=true`) | ✅ INCLUIR (F "config base") |
| CN2 | **Permitir/bloquear saldo negativo** (flag por caja/cuenta + validación) | ✅ INCLUIR (F1) |
| CN3 | **Numeración de comprobantes** (serie configurable: prefijo + próximo número) | ✅ INCLUIR (resuelve DA4) |
| CN4 | **Límite de anulación por antigüedad** (`diasLimiteAnulacion`, global o por tipo) | ✅ INCLUIR (F1) |
| CN5 | Tolerancia de diferencia de caja | ⛔ FUERA (es de PdV → desktop/filial) |
| CN6 | Monto que exige autorización | ⏸️ DIFERIR (revisión: huérfano — sin fase/test/flujo; agrega complejidad sin dueño). Retomar con diseño de workflow completo si se necesita. |
| CN7 | **Redondeo configurable por moneda** (regla + múltiplo 50/100 Gs para vueltos) | ✅ INCLUIR (F config base) |
| CN8 | **Días de anticipación de aviso de vencimiento** (cheques/CPP/CPC) | ✅ INCLUIR (con notificaciones, F8) |
| CN9 | ~~Feature flags por sub-módulo~~ | ❌ ELIMINADO (revisión: duplica `CajaMayorConfiguracion.mostrarCPP/CPC`). Se cubre extendiendo `CajaMayorConfiguracion` con booleanos (`operacionesFinancierasHabilitado`, etc.) en F2. |
| CN10 | **Auditoría de cambios de configuración** (quién cambió qué umbral y cuándo) | ✅ INCLUIR (compliance, bajo costo) |

> Decisión post-revisión: **incluir CN1–CN4, CN7, CN8, CN10** (config base con F1/F2; vencimientos/notif con F8).
> **CN5** fuera (PdV). **CN6** diferido (huérfano). **CN9** eliminado (lo cubre `CajaMayorConfiguracion` en F2).
> **CN4** se implementa con default permisivo (sin límite) — la validación por antigüedad vive en el helper, la UI de config va en F8.

### 2.6 Ajustes de la revisión del plan (2026-07-31) — AUTORITATIVO
Tres agentes revisaron el plan. Estos ajustes **prevalecen** sobre el texto de las fases donde haya conflicto.

**🔴 Estructural (reescribe F3 y F5): datos que llegan por replicación, no por Spring.**
- `financiero.retiro`, `financiero.venta_credito`, `financiero.venta_credito_cuota`, `operaciones.cobro` son **BRANCH_TO_MAIN**: llegan a central por **replicación lógica PG (WAL-apply)**, NO por `save()`/eventos Spring → ningún listener corre sobre esas filas.
- **AJ-1 (F3):** el puente Retiro→Caja Mayor **no** puede ser un hook a `RetiroService.save()`. Usar un **`@Scheduled` poller reconciliador** (patrón `SifenSchedulerService`/`CotizacionMercadoScheduler`): escanea `financiero.retiro WHERE caja_virtual_id IS NULL AND estado IN (...)`, postea el `MovimientoCajaVirtual` y marca la fila procesada. 1 clase nueva, patrón ya probado.
- **AJ-2 (F5):** mismo poller para reconciliar `venta_credito_cuota` cobradas → `MovimientoCliente`/`Cliente.saldoActual`.
- **DA8 (nueva decisión de negocio, evita doble-cobro):** la replicación es **unidireccional filial→central** (no hay HTTP central→filial). Por eso: **el cobro de cuota en efectivo sigue siendo exclusivo del POS filial**; Tesorería central (F5) **solo cobra los canales que el POS no maneja** (banco/cheque). Así no hay carrera de doble cobro.
- **AJ-3 (F2):** "egreso de caja inicial" es **puro asiento contable central-only** (no viaja a la filial ni dispara apertura de PdvCaja automáticamente) → no requiere bridge central→filial. Aclarado así; si se quisiera el vínculo real, iría después de F3.

**🟠 Simplificaciones de duplicación:**
- **AJ-4 (F8 notificaciones):** central **ya tiene** el modelo evento→receptor→canal (`NotificacionTipoRole`, `NotificacionPreferenciaUsuario`, `NotificacionEnvioLog`, `RrhhNotificacionScheduler`, `PushNotificationService`). **No portar** las 4 entidades de gourmet: reusar lo existente + agregar solo una tabla angosta de **canal saliente** (PUSH/EMAIL/WHATSAPP) — SMTP/WhatsApp es lo único genuinamente nuevo.
- **AJ-5 (§2.5):** CN9 eliminado (lo cubre `CajaMayorConfiguracion`); CN6 diferido.
- **AJ-6 (F6/F8):** DA2 "toda compra → CPP" es papel mientras `CompraGraphQL` (`saveCompra`) siga wireado. Verificar callers vivos en desktop/mobile y deprecar el camino paralelo para que DA2 sea invariante real.

**🟡 F1 (núcleo) — precisiones antes de codear:**
- **AJ-7:** "migrar 5 consumidores RRHH" es engañoso — son **0 líneas en RRHH**; la migración de saldo (`Gs/Rs/Ds` → tabla `(caja,moneda)`) es **interna** a `CajaVirtualService`/`MovimientoCajaVirtualService`, sin cambiar la firma de `registrarMovimiento`.
- **AJ-8:** mantener `saldoGs/Rs/Ds` como **campos derivados (shim)** recalculados desde la tabla nueva, o las UIs de desktop (RRHH + Tesorería fd-93) muestran `0`/`null` sin error de build (falla silenciosa). El soporte real de N monedas se ve recién al portar la UI (F2/F8).
- **AJ-9:** helper de saldo con lock debe **lockear cajas en orden canónico (id asc)** → evita deadlock en transferencias A→B / B→A.
- **AJ-10:** helper **genérico** (parametrizado por par saldo/ledger) para que F4 (`CuentaBancaria`/`MovimientoBancario`) lo **reutilice**, no reimplemente. Consolidar los 3 `findById` redundantes de `registrarMovimiento` en una sola lectura+lock+update.
- **AJ-11:** backfill de `origenTipo`/`origenId` en filas ya posteadas, reusando el FK inverso `movimientoCajaVirtualId` que las 5 entidades RRHH ya guardan (`UPDATE ... FROM rrhh.vale v WHERE v.movimiento_caja_virtual_id = m.id`, ×5 tablas).
- **AJ-12:** `AJUSTE` con signo = permitir **`cantidad` negativa** (no partir el enum). `esEgreso` deja de ser la única fuente de signo.
- **AJ-13:** extender `TipoMovimiento` (enum PG) con el patrón idempotente `ALTER TYPE ... ADD VALUE` ya usado (`V105`, `V125.3`, `V141.0`, `V146.0`, `V149.0`) — nunca usar el valor nuevo en la misma transacción.
- **AJ-14:** CN3 (numeración) → F2. CN4 (límite anulación) → validación en helper con default permisivo; UI de config en F8.

**🟡 Otros:**
- **AJ-15 (F3):** `Retiro.java` **no mapea** `caja_virtual_id` (columna V114.1) — agregar el campo antes de la lógica. Los estados `FLOTANTE/INGRESADO` del texto **no existen** en `EstadoRetiro` real (`EN_PROCESO, CONCLUIDO, ...`) — mapear a estados reales o declarar nuevos.
- **AJ-16 (F6):** dejar el enum "fuente de pago" **abierto** (`CAJA | BANCO | CHEQUE`) — `Cheque.pagoDetalleCuota` ya existe → F7 no reabre el servicio de F6.
- **AJ-17 (F7):** migrar `VentaTarjeta.estado` (hoy `String`) a enum tipado (`PostgreSQLEnumType`). Scheduler POS: **update atómico por estado** (no lectura+escritura separada) para no duplicar acreditaciones.
- **AJ-18 (F2):** atar `ConfiguracionGeneral` (timbrado/zonaHoraria/monedaPrincipal/logo) y `TipoPrecio.autorizadoPor` a F2 (config base).
- **AJ-19 (manual):** agregar a §4 del manual de prueba: bloqueo de saldo negativo en cuenta bancaria + anulación de un depósito/retiro bancario.

**✅ Confirmado sólido por la revisión:** D5/D6/D7, router `FormaPago.movimientoCaja`+`cuentaBancaria`, `SolicitudPago` como CPP (central-only, sin problema de replicación), secuenciamiento F1→F8, CN1/CN2/CN3/CN4/CN7/CN8.

### Fases

**Fase 0 — Integración (HECHO).** UI fd-93 + roles + gating + guards backend.

**Fase 1 — Núcleo de tesorería robusto (el ledger correcto). ✅ HECHO (2026-07-31)**
Modelo rico (D5): saldo por `(caja, moneda)`, `origenTipo`+`origenId`, ledger inmutable. Helper único de saldo
con lock (P2). `anularMovimiento` central + bloqueo cross-módulo. Fix `AJUSTE` con signo. `recalcularSaldos`. Tests.
- Implementado: migración `V177.5` (tabla `caja_virtual_saldo`, `origen_tipo/id`, `permite_saldo_negativo` CN2, backfill saldo + origen RRHH); entidad `CajaVirtualSaldo` + `OrigenMovimientoTipo`; `TesoreriaService` (helper con lock pesimista en orden canónico AJ-9, saldo por moneda, shim `saldoGs/Rs/Ds` AJ-8, AJUSTE firmado, anular con contra-movimiento + bloqueo cross-módulo, recalcular); `MovimientoCajaVirtualService` delega en él (RRHH intacto, 0 líneas de saldo — AJ-7); `origenTipo` seteado en los 5 servicios RRHH; `TesoreriaServiceTest` (10 tests verdes).
- CN3 (numeración) → F2; CN4 (límite anulación) → default permisivo, UI en F8.
- Enum `TipoMovimiento` se amplía por fase con `ALTER TYPE ADD VALUE` idempotente (AJ-13) cuando cada fase lo necesite.

**Fase 2 — Operación de caja mayor + config base. ✅ HECHO backend (2026-07-31)**
- Migración `V178.5`: Moneda enriquecida (`activo`/`principal`/`decimales`/`regla_redondeo`/`redondeo_multiplo` + unique partial index CN1); FormaPago (`orden`/`principal`); ConfiguracionGeneral (timbrado/puntoExpedición/zonaHoraria/monedaPrincipal/logo/actividad — AJ-18); `comprobante_serie` (CN3); `entrada_varia_categoria` (árbol); `entrada_varia`; `caja_virtual_configuracion` + M:M formas de pago visibles (absorbe CN9).
- Entidades: `EntradaVaria`, `EntradaVariaCategoria`, `ComprobanteSerie`, `CajaVirtualConfiguracion` + enriquecimiento de `Moneda`/`FormaPago`/`ConfiguracionGeneral`.
- Servicios: `ComprobanteSerieService` (numeración atómica con lock), `EntradaVariaService` (registra ingreso/egreso → postea a `TesoreriaService`, anula vía `revertir`). `TesoreriaService.revertir`/`findMovimiento` (reversión desde módulo dueño, sin guard cross-módulo).
- GraphQL con guards: `EntradaVariaGraphQL` (registrar/anular/categorías), `CajaVirtualConfiguracionGraphQL`.
- Tests: `ComprobanteSerieServiceTest` (4) + `TesoreriaServiceTest` (10) verdes.
- **Pendiente para el pase de UI/config (F8):** gastos multi-detalle (colisiona con `Gasto` de PDV → se modela aparte), egreso de caja inicial + abrir caja desde conteo (AJ-3: asiento central-only), dashboard KPIs, editar campos nuevos de Moneda/FormaPago vía sus resolvers, UI desktop de tesorería.

**Fase 3 — Puente con el PDV (circuito de efectivo). ✅ HECHO backend (2026-07-31)**
- `V179.5`: marcador `retiro.movimiento_caja_virtual_id` + índice parcial de pendientes (`caja_virtual_id` ya existía en V114.1).
- `Retiro` mapea `cajaVirtualId` + `movimientoCajaVirtualId` (AJ-15). Repo `findPendientesIngresoCajaMayor`.
- **`RetiroTesoreriaScheduler`** (AJ-1): poller `@Scheduled` `@ConditionalOnProperty(tesoreria.retiro-poller.enabled, off por default)` — reconcilia retiros replicados (BRANCH_TO_MAIN) destinados a una caja mayor, agrupa efectivo por moneda, postea `INGRESO` (origen `RETIRO_CAJA`), marca la fila (idempotente + guard anti doble-ingreso, releído en la transacción).
- Devolución/merma → `EGRESO` (activado el `TODO(fd-93)` en `DevolucionService`, origen `DEVOLUCION`, si la devolución tiene caja asignada).
- Test `RetiroTesoreriaSchedulerTest` (3) verde.
- Nota (DA1): el poller corre en central sobre las tablas ya replicadas. **El wiring filial-side** (que el retiro nazca con `caja_virtual_id` seteado) es follow-up cuando se toque el flujo de cierre de caja en filial/desktop. Estado `CONCLUIDO`/`VERIFICADO_*` real (no `FLOTANTE/INGRESADO`, que no existen — AJ-15).

**Fase 4 — Bancos + operaciones financieras.**
`CuentaBancaria` con 3 saldos (actual/reservado/futuro). `MovimientoBancario` + enum (5 tipos) + helper.
Ledger unificado de cuenta (dedupe multi-fuente). Ajuste manual. **Operaciones financieras**: los 5 tipos
(CAMBIO_DIVISA, DEPOSITO/RETIRO_BANCARIO, TRANSFERENCIA_ENTRE_CAJAS, TRANSFERENCIA_BANCARIA) + manejo de
diferencia. Exponer UI de bancos (poblar menú "Bancario").

**Fase 5 — CPC: cobros a clientes (robusto).**
`VentaCredito`/`Cuota` como CPC. Cobro con bifurcación caja/banco, parcial, conversión de moneda, cobro rápido.
`MovimientoCliente` + `Cliente.saldoActual` (cuenta corriente) con reversión idempotente. Cobro consolidado por
convenio (recibo/preview PDF) — alinear con el crédito por convenio de RRHH.

**Fase 6 — CPP: pagos a proveedores.**
`SolicitudPago` como CPP + transición PENDIENTE→PARCIAL→CONCLUIDO. Pago **simple / mixto (multi-línea moneda/forma/fuente)
/ lote**. `PAGO_PROVEEDOR` como emisor real. Préstamo a funcionario con dirección invertida (unificar con RRHH).
`MovimientoProveedor` + saldo proveedor.

**Fase 7 — Cheques + POS.**
Cheques: emitir (diferido reserva saldo / contado débito+COBRADO), cobrar diferido, anular; numeración de chequera.
POS: `AcreditacionPos` (comisión + minutos), **scheduler `@Scheduled`** de acreditación automática, verificación manual
con ajuste diferencial. Acreditación PIX/transferencia directa. UI de cheques y POS.

**Fase 8 — Reportes, consolidación y limpieza.**
Cuenta corriente por cliente/proveedor (vista). GastoPorCategoria/Mes. Saldos consolidados. Config dashboard server-side.
Modelo de permisos/roles por operación (espejo de gourmet: cajero/tesorero/bancos/CPC/CPP). Limpieza dead-code
(Concepto; decidir Sencillo, MonedaComponent). Egresos de cajón PDV (`EgresoCaja`) si se confirma necesario.

### Tests por fase
Cada fase cierra con sus tests (backend JUnit sobre servicios/builders puros — patrón `service/rrhh/builder/*Test`;
lógica de dinero testeable sin Spring). Sin tests → fase no cerrada. Resumen de cobertura por fase:

- **F1 — Núcleo:** `TesoreriaServiceTest` — registrar ingreso/egreso, actualizar saldo por `(caja,moneda)`, saldo insuficiente (según CN2), `anularMovimiento` (contra-movimiento con signo correcto), bloqueo de anulación cross-módulo, `AJUSTE` positivo/negativo, `recalcularSaldos` reconstruye igual, límite de anulación por antigüedad (CN4), concurrencia (2 movimientos sobre la misma caja no pierden update — test con lock). Migración RRHH: los 5 flujos siguen posteando igual.
- **F2 — Operación:** entradas/salidas varias (destino caja), gastos multi-detalle (N movimientos), config de caja mayor (visibilidad), egreso de caja inicial genera conteo + movimiento, numeración de comprobante (CN3) incrementa sin huecos, redondeo por moneda (CN7).
- **F3 — Puente PDV:** retiro `FLOTANTE→INGRESADO` postea `INGRESO_CIERRE`, idempotencia por conteo (no duplica), guard anti doble-ingreso, devolución/merma → `EGRESO`. Flujo filial→central (según se resuelva).
- **F4 — Bancos + op. financieras:** cada uno de los 5 tipos (cambio divisa = par de movimientos con cotización; depósito/retiro banco tocan cuenta+caja; transferencia entre cajas; transferencia bancaria NO toca caja); `MovimientoBancario` + saldos (actual/reservado); ledger unificado dedupe; saldo negativo bloqueado (CN2).
- **F5 — CPC:** cobro parcial y total, bifurcación caja/banco, conversión de moneda al acreditar a banco, `MovimientoCliente` + `saldoActual` (doble ledger), anulación idempotente, cobro consolidado por convenio.
- **F6 — CPP:** pago simple/mixto (multi-línea moneda/forma/fuente)/lote, préstamo funcionario dirección invertida, transición `SolicitudPago` PENDIENTE→PARCIAL→CONCLUIDO, `PAGO_PROVEEDOR`, "toda compra genera CPP" (contado = 1 cuota).
- **F7 — Cheques + POS:** emitir diferido (reserva saldo) vs contado (débito + COBRADO), cobrar diferido (libera reserva), anular (bloquea si cobrado), numeración de chequera + AGOTADA; `AcreditacionPos` scheduler acredita vencidas, verificación con ajuste diferencial idempotente.
- **F8 — Reportes + notificaciones:** aging CPC/CPP correcto, KPIs de dashboard, ruteo evento→receptor→canal, aviso de vencimiento con anticipación configurable (CN8), feature flags (CN9) apagan/prenden sub-módulos, auditoría de config (CN10).

Además, **prueba guiada manual** de todo el módulo al final: ver `PRUEBA-GUIADA-MODULO-FINANCIERO.md` (se ejecuta en conjunto tras completar todas las fases).

### Diferido (no existe en gourmet o no urgente)
- **Conciliación bancaria formal contra extracto** (archivo del banco) — no existe en gourmet, se diseñaría de cero.
- **Tasa de interés en préstamos** (CPP PRESTAMO) — no existe en gourmet.
- **Multi-moneda >3 / replicación de caja mayor a filiales** — decisión de negocio pendiente (hoy central-only).

### Criterio de secuenciamiento
F1 es prerequisito duro (ledger correcto antes de conectar nada). F2–F3 dan operación básica + circuito de efectivo
(mayor volumen). F4 habilita bancos (base de cheques/POS/pagos por banco). F5–F6 son el mayor valor de negocio
(cobrar/pagar con cuenta corriente). F7 cierra cheques/POS. F8 pule. Cada fase es testeable e incremental.
Sin push/PR hasta cerrar el módulo (decisión del equipo). El módulo es grande: **es un roadmap multi-tanda**, no una sola entrega.
