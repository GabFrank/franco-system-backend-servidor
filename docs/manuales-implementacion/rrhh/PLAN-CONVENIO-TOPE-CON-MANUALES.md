# Plan — el tope del crédito convenio cuenta los ítems manuales al regenerar

Rama: `fix/rrhh-convenio-tope-con-manuales` (central, desde `origin/develop` be6c7b7d). Una sola pieza: central.

## Problema

Al **regenerar** el borrador de una liquidación mensual, las cuotas de crédito por convenio
(`CREDITO_CONVENIO_CUOTA`) se cobran con tope = Σ haberes − Σ descuentos de los ítems **automáticos**.
Los ítems **manuales**, que la regeneración conserva, no entran en ese tope. Resultado: el convenio
cobra de más y el neto queda negativo.

Caso real (bodega, liquidación #377, 2026-10): salario 2.550.000, adelantos 2.279.000, manual
«PAGO SEGURO MAPFRE 10/26» 27.000. El tope salió 271.000 (sin el MAPFRE) y la cuota de la venta
#34515 (205.000) se cobró parcial por 97.900; con el MAPFRE contado habría sido 70.900. Neto −27.000.

Causa: `LiquidacionSueldoService.generarBorrador` arma `items = manuales + construirItemsAutomaticos(...)`,
pero `construirItemsAutomaticos` calcula el convenio (`itemsConvenio(liq, f, fin, items)`) sobre su
propia lista, sin los manuales.

## Fase 1 — fix + test (un commit `fix(rrhh): ...`)

- Sacar la llamada a `itemsConvenio` del final de `construirItemsAutomaticos` y hacerla en
  `generarBorrador`, después de juntar manuales + automáticos: `items.addAll(itemsConvenio(liq, f, fin, items))`.
  `construirItemsAutomaticos` tiene un solo llamador (`generarBorrador`; `generarLote` pasa por
  `generarBorrador`). El comentario «se arma AL FINAL» se muda con la llamada.
- Decisión: un manual **HABER** sube el tope igual que un DESCUENTO lo baja. Es lo que ya hace
  `rearmarConvenio` (tope sobre todos los ítems persistidos); regenerar queda coherente con eso.
- Tests nuevos en `LiquidacionItemProgramadoGenerarTest` (mismo fixture, `generarBorrador` real):
  con un manual DESCUENTO de 27.000 ya cargado, regenerar llama a `planificar` con disponible =
  sueldo − 27.000; con un manual HABER de 100.000, sueldo + 100.000. Revertir el fix y ver que fallan.

Auditoría del plan (paso 5): eje A sin riesgos de contrato (sin GraphQL, sin tablas publicadas,
un solo llamador; ningún cliente calcula el tope). Eje B sin bloqueantes; pidió declarar lo del
HABER y testearlo (hecho arriba). Riesgo conocido: borrar/editar un manual HABER después de generar
puede dejar neto < 0 sin rearmar; `pagar` lo bloquea y regenerar lo corrige.

Sin migración, sin cambio de GraphQL, sin desktop. Datos nuevos: ninguno.

## Fuera de alcance (a propósito)

- **Agregar / editar / eliminar un ítem después de generar no rearma el convenio.** El neto puede
  quedar negativo, pero `pagar` ya lo bloquea (mensual y finiquito) y regenerar ahora da el valor
  correcto. Rearmar en esos caminos pisaría cuotas que el usuario editó o eliminó a mano.
- **Finiquito**: su regeneración borra todos los ítems (manuales incluidos) y calcula el tope sobre
  lo persistido, así que no tiene este bug.
- **Datos existentes**: la #377 se corrige regenerándola después del deploy (o editando la cuota a
  70.900 antes). Las #16, #17, #20 y #21 (2026-08, BORRADOR, neto negativo) son borradores viejos sin
  pagar: no se tocan.

## Pruebas

- Paso 9: `./mvnw clean verify -B -DskipFlyway=true`; veredicto `gh pr checks`.
- Runtime (local, central 8081 perfil dev + desktop `ng serve -c web`): en la base local, un
  funcionario con cuotas de convenio vencidas y un descuento manual; regenerar desde la pantalla de
  liquidaciones y ver que la cuota parcial = neto disponible real y el neto queda en 0 o más.
  Ramas: sin manuales (igual que antes), con manual DESCUENTO (tope baja), con manual HABER (tope sube).

## Sin verificar

- Si la base local tiene un funcionario con cuotas de convenio: si no, se siembra por SQL y se restaura.
