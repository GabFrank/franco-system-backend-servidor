# Plan #640 — el QR de los tickets sale con un número arriba

Issue: https://github.com/GabFrank/franco-system-backend-servidor/issues/640
Gemela del filial: issue #259, PR filial #260 (probado en papel y escaneado el 14/11/2026).
Rama: `fix/impresion-qr-modelo` (desde `develop`)

## Problema

`utilitarios/print/escpos/barcode/QRCode.java` manda siempre la función 165 de ESC/POS («elegir modelo»,
`GS ( k 4 0 49 65 n1 n2`) con `n1 = 48`, que además es inválido. La OLIVETTI PRT80 no entiende esa función e imprime
`n1` como texto: «0» con 48 y «2» con 50 (probado en el filial). Las otras térmicas la descartaban.

## Cambio (una fase, igual al del filial)

| Archivo | Cambio |
|---|---|
| `QRCode.java` | No manda la función 165 salvo que se pida un modelo con `setModel`; enum con los valores del estándar `_1(49)`, `_2(50)` |
| `src/test/java/.../utilitarios/print/escpos/barcode/QRCodeTest.java` (nuevo) | por defecto no hay función 165; con `setModel`, `n1` es 49 o 50; el dato sigue en los bytes |

Llamadores (todos `new QRCode()`, ninguno usa `setModel` ni nombra el enum): `ImpresionService` (balance, gastos,
vuelto de gasto, solicitud de pago, retiros, pedido), `FacturaLegalGraphQL`, `VentaCreditoGraphQL`, `VentaGraphQL`,
`MovimientoPrintService`, `EtiquetaDevolucionService`, `PrintingService`. Toman el nuevo default sin cambios.

## Tabla de datos nuevos / persistencia

N/A: no nace ningún dato ni toca la base.

## Tests

- `QRCodeTest`; con la función 165 de vuelta tiene que fallar (se comprueba revirtiendo).
- `./mvnw clean verify -B -DskipFlyway=true`.

## Prueba de runtime

- Central de la rama (jar) en el 8084 contra la bodega local, perfil `dev` (trae `replication.sync`/`refresh` y SIFEN en
  false) y `--spring.flyway.enabled=false` explícito: el perfil dev no apaga Flyway y un central de develop migraría la bodega local.
- Escritorio web apuntado a ese central: Financiero › Cajas › Imprimir balance de una caja, en `frc-ticket`: QR sin número
  y escaneado. Además un ticket que se escanea en otra pantalla (retiro o solicitud de pago), escaneado. Control: el mismo balance desde el central alpha (`develop`) sale con «0».

## Auditoría del plan (paso 5)

- Eje A: solo `QRCode.java` y el test nombran el enum; 8 archivos con `new QRCode()`, ninguno con `setModel`; nadie arma
  `GS ( k` del QR a mano (`PDF417` es otra simbología); `EtiquetaDevolucionServiceTest` mira la función 181, no la 165;
  el desktop reenvía los bytes sin leerlos. Observaciones aplicadas: javadoc de `setModel` (null = no mandar) y test que
  fija 49 para `_1` y 50 para `_2`.
- Eje B: sin estado persistente; rollback = revert + deploy manual. Riesgos: Flyway no estaba apagado de verdad en la
  prueba (corregido arriba) y solo se probaba el balance (sumado un segundo ticket escaneable). Antes de desplegar,
  imprimir y escanear en una térmica de otra marca; si alguna exige la función, `setModel(_2)` ya lo permite.

## Rollback

Revertir el commit. El central se despliega a mano (workflow Deploy), así que no hay propagación automática.

## Sin verificar

- El papel en otras marcas de térmica (igual que en el filial).
