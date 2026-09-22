# Política de facturación automática por sucursal

Issue filial #127. Decide qué ventas del filial generan factura legal (y documento electrónico
SIFEN) sin que el cajero la pida, y si «Venta + Ticket» y delivery respetan esa decisión.

## Dónde vive

| Pieza | Qué hace |
|---|---|
| central `financiero.configuracion_facturacion` (V230.1) | La política. Una fila global (`sucursal_id NULL`) y a lo sumo una por sucursal (índice único sobre `COALESCE(sucursal_id, 0)`) |
| central `ConfiguracionFacturacionGraphQL` | `configuracionesFacturacion` (`TESORERIA VER`), `saveConfiguracionFacturacion` y `deleteConfiguracionFacturacion` (`TESORERIA GESTIONAR`) |
| filial `financiero.configuracion_facturacion` (V103.1) | Espejo replicado `MAIN_TO_ALL`, sin restricciones. El filial nunca lo escribe |
| filial `ConfiguracionFacturacionLector` / `PoliticaFacturacionService` | Resuelven la política en cada venta y deciden el comprobante (ver `CLAUDE.md` del filial) |
| desktop `ConfiguracionFacturacionDialogComponent` | La pantalla de administración, contra el central |

## Semántica

- **Resolución en el filial**: fila de la sucursal → fila global → property `facturaCountDown` del
  filial. **Sin filas, cada filial se comporta como siempre.**
- **Modos**: `TODAS` (toda venta con punto de venta), `INTERVALO` (una de cada
  `ventasSinFactura + 1`, el viejo `facturaCountDown`), `A_PEDIDO` (nunca automática).
- **`ventaTicketRespetaPolitica`**: `false` → «Venta + Ticket» y delivery facturan siempre (lo
  histórico); `true` → también los decide la política. Las ventas a crédito quedan fuera de esta
  bandera hasta resolver filial #133.
- El guardado es **reemplazo completo** con upsert por sucursal: guardar sobre una sucursal que ya
  tiene fila la actualiza.

## Despliegue (tabla `MAIN_TO_ALL` nueva)

1. **Filial primero.** Antes de desplegar el central del canal, cada filial tiene que mostrar
   `103.1` en `flyway_schema_history`.
2. Central del canal. La migración **no** agrega la tabla a `central_pub`: lo hace
   «Sincronizar publicaciones» (`syncPublicationsWithReplicationTable`). En alpha corre sola (el
   scheduler está prendido); en farmacia y bodega los schedulers están apagados y el alta es manual:
   `ALTER PUBLICATION central_pub ADD TABLE financiero.configuracion_facturacion` en autocommit y
   `ALTER SUBSCRIPTION … REFRESH PUBLICATION WITH (copy_data = true)` en cada filial.
3. Verificar `pg_subscription_rel.srsubstate = 'r'` en cada suscripción.
4. **Recién ahí** cargar configuración: primero los overrides por sucursal con el
   `facturaCountDown` vigente de cada filial; la global al final, o nunca. Una global sola pisaría
   los valores ajustados a mano en cada filial.

## Kill switch

`DELETE FROM financiero.configuracion_facturacion` en el central. El borrado se replica y todas las
filiales vuelven a su property en la venta siguiente, sin reinicio. No es un rollback de JAR: un
rollback del central deja las filas replicadas vigentes.
