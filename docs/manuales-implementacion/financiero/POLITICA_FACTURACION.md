# Política de facturación automática por sucursal

Issue filial #127. Decide qué ventas del filial generan factura legal (y documento electrónico
SIFEN) sin que el cajero la pida, y si «Venta + Ticket» y delivery respetan esa decisión.

## Dónde vive

| Pieza | Qué hace |
|---|---|
| central `financiero.configuracion_facturacion` (V230.1) | La política. Una fila global (`sucursal_id NULL`) y a lo sumo una por sucursal (dos índices únicos parciales: la sucursal 0 existe y no puede confundirse con la global) |
| central `ConfiguracionFacturacionGraphQL` | `configuracionesFacturacion` (cualquier rol de tesorería), `saveConfiguracionFacturacion` y `deleteConfiguracionFacturacion` (**solo `ADMIN`**: deciden si se emiten comprobantes en toda la flota). El autor sale de la sesión y el tipo expone solo `usuarioNickname` |
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
   `103.1` en `flyway_schema_history`. Si una filial todavía no tiene la tabla, su REFRESH falla
   (no se cae el apply worker): esa filial queda sin suscribirse a la tabla hasta que migre.
2. Central del canal. La migración **no** agrega la tabla a `central_pub`: lo hace
   «Sincronizar publicaciones» (`syncPublicationsWithReplicationTable`). En alpha corre sola (el
   scheduler está prendido; antes de desplegar, listar `replication_table` menos
   `pg_publication_tables` para ver qué más va a agregar). En farmacia y bodega los schedulers están
   apagados y el alta es manual, en autocommit:
   ```sql
   -- en el central del canal
   ALTER TABLE financiero.configuracion_facturacion REPLICA IDENTITY FULL;
   ALTER PUBLICATION central_pub ADD TABLE financiero.configuracion_facturacion;
   -- en cada filial: copy_data = false (la tabla está vacía en el central) para no
   -- re-copiar otras tablas que estén en central_pub y falten en esa suscripción
   ALTER SUBSCRIPTION <sub> REFRESH PUBLICATION WITH (copy_data = false);
   ```
3. Verificar `pg_subscription_rel.srsubstate = 'r'` para la tabla en cada suscripción (lista de
   filiales desde `hosts.md` de `frc-cicd`, no desde `empresarial.sucursal`).
4. Desktop del canal (la pantalla habla solo con el central).
5. **Recién ahí** cargar configuración desde esa pantalla: primero los overrides por sucursal con
   el `facturaCountDown` vigente de cada filial; la global al final, o nunca. Una global sola
   pisaría los valores ajustados a mano en cada filial.

### Filial que se suma tarde

Una filial nueva, re-suscrita o restaurada desde dump se suscribe con `copy_data = false` y **no
recibe las filas ya existentes**: queda en silencio con su property. Backfill: copiar los datos con
`pg_dump --data-only -t financiero.configuracion_facturacion` desde el central y verificar
`count(*)` igual en los dos lados.

## Kill switch

- **Flota**: `DELETE FROM financiero.configuracion_facturacion` en el central (nunca `TRUNCATE`:
  no se replica). Todas las filiales vuelven a su property en la venta siguiente, sin reinicio.
  No es un rollback de JAR: un rollback del central deja las filas replicadas vigentes.
- **Una filial con la réplica caída** no recibe ese DELETE. Emergencia local: el mismo `DELETE`
  sobre el espejo **en esa filial** (la tabla no está en `filial<N>_pub`, así que no sube a nadie).
  Al reanudarse la réplica, un UPDATE del central sobre una fila borrada localmente se descarta.

## Desmontaje completo (si hubiera que retirar la tabla)

En este orden, o se muere el apply worker de todas las tablas `MAIN_TO_ALL`:
1. `DELETE FROM configuraciones.replication_table WHERE table_name = 'financiero.configuracion_facturacion'`
   (si no, el sync la vuelve a agregar cada hora).
2. `ALTER PUBLICATION central_pub DROP TABLE financiero.configuracion_facturacion`.
3. `REFRESH PUBLICATION` en cada filial.
4. Recién ahí, `DROP TABLE` (en una migración nueva, en los dos repos).
