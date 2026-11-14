# Plan — anular un bono recurrente corta su repetición

## Problema

`BonoService.anular()` solo marca `anulado = true` en el bono. La plantilla
(`rrhh.bono_recurrente`) que lo generó sigue `activo = true`, así que
`BonoRecurrenteScheduler` genera el bono del mes siguiente igual.

Caso real (bodega, funcionario 448, 14/11/2026): RRHH anuló en octubre el bono de
256.000 y en noviembre reapareció. El mismo defecto dejó viva la plantilla de un
bono de 456.000 que se cargó con fecha equivocada y se anuló: en noviembre había
tres bonos pendientes en vez de uno. Los datos se corrigieron por SQL el 14/11.

Hoy la única forma de cortar la repetición es editar el bono del mes y apagar el
toggle «Recurrente», y eso solo se puede en el mes corriente.

## Decisión (Franco, 14/11/2026)

Anular un bono recurrente **siempre** apaga su plantilla. Para saltear un mes y
seguir, se vuelve a cargar el bono con el toggle encendido.

## Fase 1 — central (único repo)

`BonoService.anular(id)`: después de marcar el bono, si tiene
`bonoRecurrenteId` y la plantilla está activa, ponerla en `activo = false`.
Misma transacción (`@Transactional` ya existente).

- No se tocan los demás bonos de la plantilla: un bono ya generado de otro mes
  sigue como está (mismo criterio que el toggle apagado y que el egreso).
- Sin migración, sin cambios de GraphQL ni de desktop: la mutation `anularBono`
  mantiene firma y retorno.
- Seguridad sin cambios: `anularBono` ya exige `RRHH GESTIONAR`
  (`BonoGraphQL.anularBono`).

`BonoService.motivoNoEditable(b)`: si `b.anulado`, devolver «Este bono esta
anulado.». Cierra la puerta lateral: hoy «Editar» está habilitado sobre un bono
anulado del mes, el toggle viene encendido y `saveConRecurrencia` reactiva la
plantilla con los datos del bono anulado. El desktop ya abre el diálogo en solo
lectura cuando `editable = false` (`edit-bono-dialog.component.ts:78`), así que no
se toca.

### Tests (`BonoServiceAnularTest`, Mockito, sin Spring)

1. Bono con plantilla activa → bono anulado y plantilla `activo = false`.
2. Bono sin plantilla → bono anulado, `plantillaRepository` no se guarda.
3. Bono con plantilla ya inactiva → no vuelve a guardar la plantilla.
4. Bono inexistente → `GraphQLException` (comportamiento actual).
5. Bono de un mes viejo con plantilla activa → la plantilla se apaga igual
   (decisión «siempre»; ver Auditoría).
6. Bono anulado del mes corriente → `motivoNoEditable` no es null y
   `saveConRecurrencia` lo rechaza sin tocar la plantilla.

Revertir el fix y comprobar que fallan el 1 y el 6.

## Auditoría del plan (paso 5, 14/11/2026)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Editar un bono anulado y guardar con el toggle encendido reactiva la plantilla | A y B | **Aplicado**: `motivoNoEditable` bloquea el anulado (test 6) |
| Anular un bono de un mes viejo apaga una plantilla que ya generó el mes actual | A | **Se mantiene «siempre»**. Restringirlo a «periodo ≥ mes actual» no habría cortado el bono 343 de Matías (periodo 2026-01, cargado por error en octubre), que es justo el caso que se arregla. Test 5 deja la decisión escrita |
| `anular()` permite anular un bono ya liquidado; ahora además apaga su plantilla | B | **Fuera de alcance**: anular un bono pagado es otro defecto, previo a este fix. Con la decisión «siempre», apagar la plantilla es coherente. Anotado para otro issue |
| Carrera con el scheduler de las 06:30 (lee `activo` antes de que commitee el anular) | B | Aceptado: ventana de milisegundos, a lo sumo un bono más visible en la grilla y anulable |
| Contrato GraphQL, filial, PWA, mobile | A | Sin impacto: firma igual, único llamador el desktop, `rrhh` es central-only |

## Datos nuevos

Ninguno: no nace columna, campo ni clave. Se escribe `bono_recurrente.activo`,
que ya lee `BonoRecurrenteRepository.idsActivos()` (scheduler) y
`BonoRecurrenteService.generarUno()`.

## Despliegue

Central-only: `rrhh` no se replica. Toma efecto al desplegar la instancia; no
corrige bonos ya generados (los de Matías se corrigieron a mano).

## Sin verificar / cómo

- Prueba por pantalla: central local en el 8081 + desktop web, anular un bono
  recurrente desde la lista de Bonos y comprobar en la base que la plantilla quedó
  inactiva y que `generarPeriodo(mes siguiente)` no genera nada.
- Otros funcionarios en producción con la misma situación (plantilla activa cuyo
  último bono está anulado): consulta aparte, fuera de este PR.
