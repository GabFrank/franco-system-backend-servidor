# Rol `TESORERIA CAJA` — operador de caja acotado

Rol para un gerente de sucursal (o cualquier operador) que tiene que manejar **solo las cajas a
las que se le dio acceso**, sin ver la información financiera del resto de la empresa. Vive en
central y en el desktop; la filial y los clientes móviles no participan.

## Qué puede hacer

Sobre las cajas donde tiene acceso (`financiero.caja_virtual_acceso`, o porque es su responsable):

| Capacidad | Detalle |
|---|---|
| Ver la caja | Saldos, movimientos y reporte de movimientos. |
| Ingresos y egresos | Efectivo y entradas o salidas varias. No hace ajustes de saldo ni conteo. |
| Transferir | Entre dos cajas donde tenga escritura, mismo monto y misma moneda. |
| Ingresar retiros del PDV | Solo los de la sucursal de la caja. |
| Depositar al banco | A una cuenta habilitada en «Configurar» de esa caja, mismo monto y moneda. No ve el saldo de la cuenta. |
| Pagar gastos | Los que cargó él mismo, en efectivo, en la moneda del gasto. |
| Pagar a proveedores | Las solicitudes que tesorería **asignó** a una de sus cajas, en efectivo y desde esa caja. |
| Anular | Sus movimientos manuales, sus depósitos y transferencias, y sus pagos en efectivo. |

## Qué no ve ni hace

Saldo consolidado y vencimientos, cuentas bancarias y extractos, cheques, el listado de
operaciones financieras, control de retiros, pagos de RRHH, pagos por banco o con cheque,
configuración y alta de cajas, y los saldos de cualquier caja a la que no tenga acceso.

En el desktop ve una sola entrada de menú: **Financiero → Caja y Operativa → Tesorería**.

## Cómo está resuelto

- El rol **no está en `TesoreriaSecurityService.TODOS`**: `requireVer`, `requireGestionar` y
  `requirePagarCpp` lo rechazan. Cada resolver delimitado por caja lo habilita con
  `requireVerCaja`, `requireOperarCaja` o `requirePagarCppCaja`.
- `RolTesoreriaCajaMatrizTest` tiene la **lista cerrada** de resolvers que aceptan el rol y falla
  si aparece uno nuevo sin declarar. Agregar uno es una decisión: hay que confirmar que está
  delimitado por caja.
- El modo acotado se decide **por acción**, no por «no tiene ningún otro rol»:

  | Predicado | Es acotado si tiene `TESORERIA CAJA` y no tiene… | Se usa para |
  |---|---|---|
  | `esSoloCaja()` | ningún rol amplio de tesorería | lo que **ve** (listados, saldos) |
  | `operaAcotado()` | `TESORERIA GESTIONAR` | movimientos, operaciones financieras, retiros |
  | `pagaAcotado()` | `TESORERIA CPP PAGAR` ni `GESTIONAR` | pagar y anular pagos |

  Un usuario con `CAJA` + `VER` ve lo global pero sigue operando y pagando como acotado.
- Las validaciones del modo acotado están en el **servicio**, antes del primer lock y del primer
  asiento: un pedido rechazado no deja nada a medias.
- En modo acotado una lista vacía (de cajas o de sucursales) significa «ninguna»: el servicio
  devuelve vacío sin consultar. Nunca se usa `null` como «sin filtro».
- `CuentaBancariaResolver` y `CajaVirtualResolver` ocultan los saldos **en el campo**, porque esos
  tipos llegan anidados por varios caminos (operación financiera, movimiento, configuración).
- Pagar a proveedores usa `solicitud_pago.caja_virtual_asignada_id`. La asigna tesorería
  (`asignarSolicitudPagoACaja`, roles `CPP PAGAR` o `GESTIONAR`) desde la lista de solicitudes de
  pago del desktop, sobre solicitudes de compra o de gasto en `SOLICITADO` o `PARCIAL`.

## Puesta en marcha

1. Tesorería crea la caja (queda como responsable) y le pone **sucursal**.
2. En «Gestionar accesos» le da al operador **lectura y escritura**. Con solo escritura la caja no
   le aparece en los listados.
3. En «Configurar» de la caja marca las **cuentas bancarias** a las que el operador puede depositar.
4. En la pantalla de usuarios le asigna el rol `TESORERIA CAJA`, **sin** otro rol de tesorería.
5. Para que pague a un proveedor: Compras → Solicitud de pago → ⋮ → **Asignar a caja**.

El operador no tiene que ser el responsable de la caja: el responsable administra los accesos.

## Límites conocidos

- Paga solo en la moneda de la solicitud. Un gasto en guaraníes no se paga con dólares de la caja.
- Quien asigna una solicitud elige entre las cajas que él mismo puede ver.
- El operador puede registrar ingresos y egresos manuales de cualquier monto en su caja: el
  control es el ACL y el historial de movimientos, no un tope.

## Despliegue

- Orden: **central primero**, después el desktop, y recién entonces asignar el rol a alguien.
- Migraciones: `V245.1` (rol, llega a las filiales por la réplica de `personas.role`) y `V246.1`
  (columna nullable en `operaciones.solicitud_pago`, que no se publica a las filiales). Antes de
  desplegar en cada instancia, confirmar que sigue sin publicarse:

  ```sql
  SELECT pubname, schemaname, tablename FROM pg_publication_tables WHERE tablename = 'solicitud_pago';
  ```

  Tiene que dar 0 filas. Si diera alguna, `V246.1` necesita su espejo en el filial.
- Cambio para roles existentes: las consultas de cuentas bancarias exigen un rol de tesorería.

## Prueba manual

Con un usuario que tenga solo `TESORERIA CAJA`, lectura y escritura sobre una caja con sucursal,
y una cuenta bancaria habilitada en esa caja:

| # | Paso | Esperado |
|---|---|---|
| 1 | Abrir el menú Financiero. | Solo «Caja y Operativa → Tesorería». Sin Dashboard, Cuentas y Bancos ni Operaciones Financieras. |
| 2 | Abrir Tesorería. | Solo sus cajas. Sin botón de alta. |
| 3 | Abrir el dashboard de su caja. | Ingreso, Egreso y Operaciones Financieras. Sin cuentas bancarias, sin selector de fuente, sin conteo, sin Configurar. |
| 4 | Ingreso. | Efectivo, Retiro de PDV y Entrada Varia. Sin Maletín ni Ajuste. |
| 5 | Ingreso → Retiro de PDV. | Sucursal fija en la de la caja; solo retiros de esa sucursal. |
| 6 | Egreso. | Efectivo, Egreso Vario, Pagar Compras y Pagar Gasto. Sin pagos de RRHH ni Ajuste. |
| 7 | Egreso → Pagar Gasto: crear un gasto y pagarlo. | Fuente única «Efectivo (caja mayor)», sin selector de moneda. El saldo de la caja baja. |
| 8 | Operaciones Financieras. | Solo Depósito Bancario y Transf. entre Cajas. En el depósito se elige primero la caja y aparecen solo sus cuentas habilitadas, sin saldo. |
| 9 | Registrar un depósito y anularlo desde su fila. | El saldo baja y vuelve. El menú de la fila solo ofrece Anular. |
| 10 | Como tesorería: Solicitud de pago → ⋮ → Asignar a caja, eligiendo la caja del operador. | Aviso «Solicitud asignada a …». |
| 11 | Como el operador: Egreso → Pagar Compras. | Aparece solo la solicitud asignada; se paga en efectivo. Sin el botón de devolver a compras. |
| 12 | Con un usuario `TESORERIA GESTIONAR`: repetir 3, 6 y 8. | Todo como antes del cambio. |
