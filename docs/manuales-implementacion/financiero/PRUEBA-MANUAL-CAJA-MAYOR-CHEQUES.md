# Prueba manual — Caja Mayor, retiros y cheques

Plan: [PLAN-CAJA-MAYOR-CHEQUES.md](PLAN-CAJA-MAYOR-CHEQUES.md). Ramas `feature/financiero-caja-mayor-cheques` en central
y desktop. **Central y desktop de la misma rama**: el desktop nuevo pide operaciones que un central viejo no tiene
(salvo los órdenes y la cotización, que degradan solos).

Preparación: usuario con TESORERIA GESTIONAR (y otro con el rol acotado TESORERIA CAJA para los casos marcados), una
caja mayor con saldo en Gs, R$ y US$, una cuenta bancaria con chequera activa (rango con al menos 10 hojas) y
cotizaciones cargadas para US$ y R$.

Cada caso: **pasos → qué tiene que pasar**. Marcar ✅ / ❌ y anotar lo que difiera.

## F1 · Caja chica retirada (srv #220)

1. Tesorería → Cajas → Nueva caja. → No hay selector de tipo ni «Límite en Gs». Se guarda como caja mayor.
2. Listado de cajas. → No hay filtro ni columna de tipo.
3. RRHH → Confirmar vale (o aguinaldo/préstamo/liquidación). → En bodega aparece «CAJA CHICA ADMINSTRACION» entre las
   cajas para pagar.
4. SQL: `select id, nombre, tipo, tipo_original from financiero.caja_virtual where tipo_original is not null;` → las que
   eran caja chica, con `CAJA_CHICA` en `tipo_original`.

## F2 · Órdenes de la caja (srv #540, desk #653, #720 §1)

1. Caja mayor → Configurar. → Tres listas nuevas: monedas, operaciones financieras (con casilla visible) y sucursales.
2. Ordenar monedas US$, R$, Gs y guardar. → Las cards de saldo quedan en ese orden; registrar un movimiento en Gs no las
   mueve de lugar.
3. Ocultar «Cambio de Divisa» y poner «Depósito Bancario» primero. Abrir Operación financiera **desde la caja**. →
   Depósito seleccionado, la caja precargada como origen, sin Cambio de Divisa. Abrirla desde el listado de operaciones
   → el orden de siempre.
4. Ordenar sucursales y abrir «Ingresar retiro de PDV». → El select respeta el orden. Repetir con el rol acotado.
5. Con el rol acotado, una caja sin configuración. → Todo con el orden de siempre, sin avisos de error.

## F3 · Retiros (srv #578, desk #670)

1. Verificar un retiro nuevo de PDV y acreditarlo. → En Movimientos de la caja mayor: «Retiro #N - SUCURSAL - Caja #M».
2. Retiro declarado solo en Gs: Verificar → «Agregar moneda no declarada» → Real → contar 500. → La fila dice «no
   declarada», queda Sobrante, la confirmación muestra `REAL: +500 R$`; acredita Gs y R$ y abre el caso.
3. Agregar una moneda, cerrar el diálogo sin confirmar y reabrir. → La fila de Real y su conteo vuelven.
4. Agregar una moneda por error sin contar nada → aparece la ✕ para quitarla; con algo contado, no.

## F4 · Cotización (srv #603 §2, desk #720 §2-3)

1. Cambio de divisa R$ → US$. → Etiqueta «Cotización (R$ por 1 US$)». Escribir 5,60 → debajo «= 0,1786 US$ por 1 R$»
   y el candado cerrado.
2. Con la cotización fija, escribir origen 560 → destino 100. Escribir destino 50 → origen 280. Alternar varias veces:
   la cotización no cambia.
3. Borrar la cotización, escribir origen 600 y destino 100 → cotización 6,00 «calculada de los montos»; cambiar el
   origen → se recalcula.
4. US$ → Gs con «Usar cotización del sistema» → 6.400 con miles. R$ → US$ con el sistema → la cruzada (≈ 5,33),
   «cruzada del sistema».
5. SQL tras guardar: `select cotizacion, cotizacion_moneda_id from financiero.operacion_financiera order by id desc limit 1;`
   → 5,60 y el id del dólar.
6. Backfill: las operaciones viejas con cotización invertida tienen `cotizacion_original` y la cotización nueva ≥ 1.

## F5 · Descripciones y «Ver detalle» (desk #721)

1. Depósito con boleta 2443309. → Banco: «Depósito · Boleta 2443309 · <caja>»; caja: «Depósito · Boleta 2443309 ·
   <banco> <número>». Sin boleta: «Depósito (op #N) · …». Los depósitos viejos se ven igual (V277.5).
2. Tabla de banco → ⋮ de un depósito → «Ver detalle» abre la operación. Un pago con banco → el detalle del pago. Una fila
   anulada → solo «Ver detalle». Doble clic en una fila → el detalle.
3. Tabla de caja → un ingreso de retiro → «Ver detalle» → sucursal, caja del PDV, responsable, declarado y contado por
   moneda. Con el rol acotado: sin la verificación y sin avisos de error.
4. Detalle de un cambio de divisa → la cotización como «5,6 R$ por 1 US$» con la inversa.

## F6 · Cheques (srv #601, desk #708)

1. Cheques → abre en «Emitidos hoy» (fecha de emisión = hoy, todos los estados), sin gráfico.
2. Emitir cheque: proveedor (buscar), beneficiario editable, nominal, fecha de emisión, diferido, 3 cheques cada 30
   días. → La vista previa muestra Nº, fecha y monto; editar un monto deja la suma en rojo hasta corregir. Emitir → «3
   cheques emitidos», números consecutivos y los tres en el tablero con Origen «Manual».
3. Cortar la red justo al emitir (o repetir con la misma clave) → «Reenviar» no duplica.
4. Diferido emitido hoy con pago el mes que viene → aparece en «Emitidos hoy».
5. Tipo de fecha Pago + «Próx. 30» → los vencimientos. Buscar por beneficiario y por número.
6. Un cheque de un pago → Origen «Pago #N»; ⋮ → «Anular» deshabilitado con el motivo; «Ver detalle» abre el pago.
7. Anular un cheque suelto → pide el motivo y queda en `motivo_anulacion`.
8. Exportar → un .xlsx con lo que se ve.
9. **Bandera `cheque.al_dia_emitido`** (solo en alpha/local):
   `UPDATE empresarial.configuracion_sistema SET valor='true' WHERE clave='cheque.al_dia_emitido';`
   → Un cheque al día sale «emitido (pendiente de cobro)», reserva el monto y no debita; sin fondos, avisa pero emite.
   «Cobrar» lo debita y libera la reserva. Anularlo libera la reserva. Volver a `false` al terminar.
10. Tesorería → consolidado: además del saldo, «disponible» (saldo − reservado).

## F7 · Números inutilizados (srv #603 §3, desk #720 §4)

1. Gestionar chequeras → ⋮ → «Números inutilizados». Inutilizar el próximo número con motivo. → Emitir cheque muestra
   el siguiente libre; el cheque nuevo sale con ese.
2. Inutilizar un número pasado sin cheque → queda en la lista.
3. Un número con un cheque suelto, «Anular el cheque también» → el cheque queda anulado y la reserva liberada.
4. Un número con cheque de un pago, «Anular» → rechazo que manda a revertir el pago. «Pasar a otro número» con uno libre
   → el cheque cambia de número; con uno ocupado → rechazo.
5. Inutilizar el último libre del rango → la chequera queda AGOTADA. Revertir → vuelve a ACTIVA.
6. Tablero → Estado «Números inutilizados» → la lista, sin montos.
7. Pagar compras con 3 cheques del plan → los «Nº» del panel saltean los inutilizados.

## Volver atrás

- Apagar la bandera antes de un rollback del central y correr
  [reconciliar-saldo-reservado.sql](reconciliar-saldo-reservado.sql) (parte A, revisar, parte B).
- Cada migración documenta su reversa en su cabecera (`tipo_original`, `cotizacion_original`, `descripcion_original`,
  `fecha_entrega_backfill`).
