# Rol `RRHH SUCURSAL`

RRHH acotado para un gerente de sucursal: genera y edita **liquidaciones de sueldo y finiquitos** de los
funcionarios de las sucursales a su cargo, y paga en efectivo desde su caja los ya aprobados. No ve ni
hace el resto de RRHH.

Va junto con [`TESORERIA CAJA`](../financiero/ROL-TESORERIA-CAJA.md): las sucursales a cargo salen de
ahí.

## Qué puede hacer

| Acción | Detalle |
|---|---|
| Ver liquidaciones | El listado y el detalle, solo de funcionarios de sus sucursales. |
| Generar liquidaciones | De un funcionario suyo, de varios, o de «todos»: los activos de sus sucursales. |
| Editar una liquidación en borrador | Agregar, editar y quitar ítems; regenerar. |
| Finiquito | Vista previa, generar el borrador y editar sus ítems, de un funcionario suyo. |
| Recibos | El de la liquidación, el del finiquito y los de sus ítems. |
| Pagar | Liquidaciones y finiquitos **aprobados** de sus sucursales, en efectivo, desde una caja que puede mover. |

## Qué no hace

- **Aprobar**: es de RRHH central (`RRHH APROBAR`). Nadie arma y aprueba un pago él solo.
- Volver a borrador, anular, y el pago directo desde la pantalla de RRHH.
- Programar ítems para otro período o anular uno programado (los ve, no los cambia).
- Aguinaldos, bonos, vales, préstamos, vacaciones, horas extra, penalizaciones, legajos.
- Dashboard, reportes de nómina, IPS y configuración de RRHH.
- Anular un pago ya hecho: lo anula tesorería o RRHH.

## Cómo está resuelto

- El rol **no** está en `RrhhSecurityService.TODOS`: no abre la lectura general de RRHH. Lo acepta
  solo lo que pasa por `RrhhAlcanceService`.
- **Sucursales a cargo** = las que cubren las cajas que el usuario puede mover
  (`TesoreriaSecurityService.sucursalesOperablesIds()`): la sucursal de cada caja más sus adicionales
  («Sucursales que cubre», V248.1). Es una sola configuración para retiros y para RRHH.
- **Alcance por funcionario**: `funcionario.sucursal` tiene que estar entre las sucursales a cargo. La
  liquidación y el finiquito no guardan sucursal: vale la **actual** del funcionario. Uno sin sucursal
  no está al alcance de nadie con este rol.
- **Acotado por acción**, igual que en tesorería:

  | Método | Deja de aplicar con | Decide |
  |---|---|---|
  | `veAcotado()` | cualquier rol de `TODOS` | listados y detalle |
  | `liquidaAcotado()` | `RRHH LIQUIDAR` | generar y editar |
  | `pagaAcotado()` | `RRHH PAGAR` | pagar |

- Las consultas de alcance (`RrhhAlcanceRepository`) son proyecciones: devuelven la sucursal del
  funcionario de un documento sin cargar la entidad antes del lock que toman los servicios.
- El rechazo no distingue «no existe», «sin sucursal» ni «de otra sucursal».
- Una lista vacía de sucursales significa «ninguna»: el listado devuelve vacío y el lote de «todos»
  no llama al servicio, para el que una lista vacía es toda la empresa.
- Un lote con un funcionario fuera de alcance se rechaza entero: `generarLote` se traga los errores
  por funcionario y el rechazo pasaría en silencio.
- **Pago**: por `pagarRrhhMixto`. En modo acotado exige liquidación o finiquito de sus sucursales,
  fuente efectivo, caja con escritura y un rol de tesorería que pague (`requirePagarCppCaja`), en la
  moneda del documento y por el monto que sale de la caja. El resto de las reglas (aprobado, pago
  entero, cuotas vigentes) las pone el servicio como siempre.
- `RolRrhhSucursalMatrizTest` tiene la **lista cerrada** de resolvers que aceptan el rol. Sumar uno es
  agregarlo ahí a propósito.

## Puesta en marcha

1. Dejar al gerente operando sus cajas con `TESORERIA CAJA` (ver su manual), con «Sucursales que
   cubre» cargado si tiene más de una sucursal.
2. En la pantalla de usuarios, asignarle además `RRHH SUCURSAL`, **sin** otro rol de RRHH.
3. Revisar que sus funcionarios tengan cargada la **sucursal**: sin ella no los ve.

## Límites conocidos

- La sucursal es la actual del funcionario: si lo trasladan, sus liquidaciones viejas pasan a verse
  desde la sucursal nueva.
- Pagar el finiquito da de baja al funcionario, como siempre. Sigue al alcance del gerente (la baja no
  le quita la sucursal) para ver el finiquito y su recibo.
- El gerente no puede deshacer un borrador que generó de más: lo anula RRHH.

## Despliegue

- Orden: **central primero**, después el desktop, y recién entonces asignar el rol.
- Migración `V249.1`: siembra el rol; llega a las filiales por la réplica de `personas.role`. No
  cambia tablas.
- Depende de `V248.1` (sucursales que cubre una caja).

## Prueba manual

Con un usuario con `RRHH SUCURSAL` + `TESORERIA CAJA` y una caja que cubra sus sucursales, y otro con
`RRHH APROBAR`:

| # | Qué hacer | Qué tiene que pasar |
|---|---|---|
| 1 | Menú R.R.H.H. | Solo «Liquidaciones». |
| 2 | Liquidaciones. | Solo funcionarios de sus sucursales. Hay un botón «Finiquito». |
| 3 | Generar → Todos. | Dice «de mis sucursales» y genera solo esos borradores. |
| 4 | Generar → Funcionarios seleccionados → buscar. | El buscador trae solo a los suyos. |
| 5 | Detalle de un borrador → Agregar ítem. | Se agrega y cambia el neto. No hay «Aprobar» ni campo de período. |
| 6 | Finiquito → elegir funcionario → Generar borrador. | Se abre el finiquito en borrador, editable, sin «Aprobar». |
| 7 | Con el otro usuario: aprobar la liquidación y el finiquito. | Quedan APROBADA. |
| 8 | Caja → Egreso. | Aparecen «Pagar Liquidación» y «Pagar Finiquito»; no vales ni aguinaldo. |
| 9 | Pagar Finiquito → tildar → Efectivo → Confirmar. | La caja baja por el total, el finiquito queda PAGADO y el funcionario, de baja. |
| 10 | Por API: generar, ver o pagar algo de un funcionario de otra sucursal. | «No autorizado». |
