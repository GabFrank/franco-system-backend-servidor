# Prueba manual — ACL de cajas + pagos de RRHH desde el hub

> Rama `feat/pagos-hub-acl-cajas` (central + desktop). Entorno local: central con perfil `dev`
> contra `bodega_producto_devoluciones` (`:5551`), desktop servido en el browser o Electron.
> Login con `frc-comercial/dev_user_cred.txt`.

## Preparación

1. **Migraciones**: al levantar el central se aplican `V199.5` (solicitud_pago_id en
   liquidación / finiquito / aguinaldo) y `V200.5` (`financiero.caja_virtual_acceso`).
   Verificar:
   ```sql
   select version, description, success from flyway_schema_history
    where version in ('199.5','200.5') order by version;
   ```
2. **NO correr el backfill todavía**: las pruebas de ACL necesitan usuarios sin acceso.
3. Tener a mano dos usuarios: uno con rol de tesorería que **no** sea responsable de la caja,
   y el responsable de la caja (o ADMIN).

---

## A — ACL de cajas

### A1. El responsable ve su caja y puede administrarla
1. Lista de cajas → menú de acciones de una caja creada por vos.
2. ✅ Aparece **"Gestionar accesos"**.
3. Abrirlo. ✅ El encabezado dice quién es el responsable; la lista arranca vacía.

### A2. Un usuario sin acceso no ve la caja
1. Loguearse con el otro usuario (con rol de tesorería, sin acceso a esa caja).
2. ✅ La caja **no aparece** en la lista.
3. ✅ El contador de la paginación no la cuenta (no hay "1 de N" con filas que no se ven).

### A3. Otorgar lectura
1. Como responsable: "Gestionar accesos" → buscar al otro usuario → **sin** tildar "Puede mover
   plata" → Agregar.
2. Con el otro usuario: ✅ la caja aparece; ✅ puede abrir el dashboard y ver movimientos.
3. Intentar un egreso. ✅ Falla con *"No tenés permiso para mover plata en esta caja"*.

### A4. Otorgar escritura
1. Como responsable: activar el toggle **"Mover plata"** de esa fila.
2. Con el otro usuario: ✅ el egreso ahora funciona.

### A5. Revocar
1. Como responsable: menú de la fila → **Quitar acceso** → confirmar.
2. Con el otro usuario: ✅ la caja desaparece de la lista.

### A6. Transferir responsabilidad
1. Como responsable: otorgar acceso al otro usuario, luego menú → **Hacerlo responsable**.
2. ✅ El diálogo se cierra y la lista se recarga.
3. ✅ Ahora **vos** ya no ves "Gestionar accesos" en esa caja; el otro sí.
4. ✅ La fila de acceso explícita del nuevo responsable desapareció (ya tiene permisos implícitos).

### A7. ADMIN pasa por encima
1. Con un usuario ADMIN: ✅ ve todas las cajas y puede administrar accesos de cualquiera.

### A8. Procesos automáticos (regresión crítica)
1. Con un retiro de PDV pendiente, esperar/forzar el procesamiento automático
   (`RetiroTesoreriaProcesador` / scheduler).
2. ✅ El retiro ingresa a la caja mayor igual. **Si esto falla, el ACL está rechazando procesos
   sin sesión y hay que revisar `esProcesoDeSistema()`.**

---

## B — Pagos de RRHH desde el hub

Precondición: una liquidación mensual **APROBADA** sin pagar, un finiquito **APROBADO** y un
aguinaldo **APROBADO** (se aprueban desde RRHH).

### B1. Pagar liquidación mensual
1. Caja Mayor → **Registrar Egreso** → **Pagar Liquidación**.
2. ✅ La tabla lista las liquidaciones aprobadas con funcionario, período y monto.
3. ✅ Tildar una **destilda** cualquier otra (selección simple).
4. ✅ El monto no es editable.
5. Elegir forma de pago (efectivo de la caja) → confirmar.
6. ✅ Mensaje de éxito. En RRHH la liquidación queda **PAGADA**.
7. ✅ En la caja hay un egreso por el neto, etiquetado **"Liquidación"** (no "Egreso" ni "Compra").
8. ✅ Los efectos cruzados se aplicaron: vales de esa liquidación en **DESCONTADO**, cuotas en
   **PAGADA**.

### B2. Pago mixto (efectivo + banco)
1. Idem B1 pero repartiendo entre caja y cuenta bancaria.
2. ✅ El total de las formas de pago debe igualar el neto para poder confirmar.
3. ✅ Se generan el movimiento de caja **y** el bancario.

### B3. Finiquito
1. **Pagar Finiquito** → pagar uno.
2. ✅ Queda PAGADA y **el funcionario queda inactivo**.
3. ✅ El movimiento se etiqueta **"Finiquito"**.

### B4. Aguinaldo
1. **Pagar Aguinaldo** → pagar uno.
2. ✅ Queda PAGADO y **deja de sumarse en la liquidación de diciembre** (verificar generando el
   borrador de diciembre: no debe aparecer el ítem de aguinaldo).
3. ✅ El movimiento se etiqueta **"Aguinaldo"**.

### B5. Pago parcial rechazado
1. Intentar pagar una liquidación con un monto menor al neto.
2. ✅ Rechaza: *"se paga entero o no se paga: falta cubrir …"*.

### B6. Anulación
1. Anular el pago desde el evento.
2. ✅ La liquidación vuelve a **APROBADA**, los efectos cruzados se revierten y la caja recibe el
   contra-movimiento.

---

## C — Etiqueta y detalle del pago consolidado (F4 / F6)

### C1. Un solo documento
1. Pagar **un** gasto desde el hub.
2. ✅ El movimiento conserva la descripción específica del gasto.
3. ✅ Se etiqueta **"Gasto"**, no "Compra" ni "Pago Proveedor".

### C2. Varios documentos
1. Pagar **tres** gastos en una sola operación.
2. ✅ El movimiento dice **"Pago consolidado de 3 gastos"** — no la descripción del primero.

### C3. Detalle
1. En el dashboard de la caja, menú del movimiento → **"Ver detalle del pago"**.
2. ✅ Lista los 3 gastos con el monto imputado a cada uno y su estado.
3. ✅ Los totales se agrupan por moneda (no se suman monedas distintas).
4. ✅ La suma de los imputados coincide con el monto del movimiento.

### C4. Compras
1. Pagar dos notas de un proveedor.
2. ✅ Etiqueta **"Pago a PROVEEDOR (2 notas)"**, etiquetada como **"Compra"**.

---

## Qué mirar en la DB

```sql
-- Accesos de una caja
select a.id, u.nickname, a.puede_leer, a.puede_escribir, o.nickname as otorgado_por
  from financiero.caja_virtual_acceso a
  join personas.usuario u on u.id = a.usuario_id
  left join personas.usuario o on o.id = a.otorgado_por_id
 where a.caja_virtual_id = :cajaId;

-- Puente de pago de un documento de RRHH
select id, estado, solicitud_pago_id, caja_virtual_id, movimiento_caja_virtual_id
  from rrhh.liquidacion_sueldo where id = :id;

-- Desglose de un evento de pago
select solicitud_pago_id, monto_solicitud, movimiento_caja_virtual_id, anulado
  from financiero.pago_solicitud_detalle where pago_id = :pagoId;

-- Origen y descripcion del movimiento consolidado
select id, tipo_movimiento, origen_tipo, referencia_id, descripcion, cantidad
  from financiero.movimiento_caja_virtual order by id desc limit 10;
```
