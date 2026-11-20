# Plan — motivo de la diferencia en la verificación de retiros

Rama `fix/retiro-verificacion-motivo` en central y desktop. Se borra en el PR final (ciclo §1 paso 11).

## Problema

1. **Motivo al revés.** En bodega hay 15 `retiro_verificacion_detalle` con `categoria = FALTANTE` y
   `diferencia > 0` (al 2026-11-20; 0 al revés). Ej.: retiro 2108 / suc. 22, 200 R$ declarados, 500
   contados, «Faltante».
   - Desktop, `verificar-retiro-bloque.component.ts` `recalcular()`: el motivo se fija la primera vez
     que hay diferencia (`if (!f.categoria)`) y no se recalcula. La máscara del contado emite por tecla:
     tipear «500» contra 200 pasa por 5 y 50 → FALTANTE, y ahí queda. Al revés al corregir hacia abajo.
   - Central, `RetiroVerificacionService.verificar`: usa la categoría del cliente aunque contradiga el signo.
2. **«Rápida» con diferencia.** Desde `c9780c99` (03/11) `rapida` = «no se contó con la grilla», lo que
   incluye montos tipeados. El detalle del caso sigue diciendo «se aceptó lo declarado sin contar»: un
   caso rápido con +300 R$ se lee como contradicción. De 19 rápidas con diferencia, 11 son montos
   tipeados y 8 contado 0 en todas las monedas (desktop anterior a v4.10.0, que confirmaba sin contar;
   el 2924/6 es del 16/11 → revisar la versión de esa PC). Los 8 no se tocan por código.

## Fases

### Fase 1 — central

- `RetiroVerificacionService`: `static CategoriaDiferenciaRetiro categoriaDe(elegida, diferencia)`.
  FALTANTE/SOBRANTE (o null) se derivan del signo; las demás categorías (las elige el usuario a mano)
  se respetan. `verificar` la usa.
- **Sin migración** (decisión de Franco, 2026-11-20). Los 15 registros cruzados de bodega quedan
  como están; el `UPDATE` queda como script opcional fuera del repo
  (`dev-frc/notas-retiro-verificacion/corregir-motivo.sql`).
- Test: `RetiroVerificacionCategoriaTest` por `service.verificar(...)` (mocks como
  `RetiroVerificacionConteoVacioTest`), capturando el `save`: 500 contra 200 con FALTANTE → SOBRANTE;
  null → por signo; BILLETE_NO_RECIBIBLE respetado. Con el código viejo falla por aserción.

### Fase 2 — desktop

- `recalcular()`: recalcular el motivo cuando es null, FALTANTE o SOBRANTE; respetar los manuales.
- El `mat-select` de cada fila ofrece solo el automático de su signo (Faltante o Sobrante) más los
  manuales: no se puede elegir un motivo contrario al signo (antes el central lo corregía en silencio).
- `detalle-caso-dialog.component.html`: texto de la rápida → «Verificación rápida: el monto se cargó
  sin contar por billetes.» (cubre «usar lo declarado» y monto tipeado).
- Spec del bloque: tipear 5 → 50 → 500 contra 200 termina en SOBRANTE (Karma con tsconfig acotado).

## Datos nuevos

Ninguno: no hay columnas, campos GraphQL ni claves nuevas.

## Despliegue

Independientes: el central corrige solo aunque el desktop siga viejo; el desktop nuevo con central
viejo ya manda el motivo bien. Orden sugerido: central primero (arregla lo que mandan los desktops
instalados).

## Prueba de runtime (local: central 8081 dev + desktop `ng serve -c web`)

1. Retiro pendiente con R$ declarados: tipear un contado mayor → motivo Sobrante; corregir a menor →
   Faltante; confirmar y leer `retiro_verificacion_detalle`.
2. Elegir a mano «Billete no recibible», cambiar el contado → se mantiene. Con contado > declarado el
   menú no ofrece «Faltante».
3. Con el desktop de `develop` (sin fase 2) y central nuevo: reproducir el 5→50→500 y ver que la base
   guarda SOBRANTE.
4. Abrir el caso de una rápida → texto nuevo.

## Sin verificar

- Qué versión del desktop usó cada uno de los 8 contados en 0 (no se guarda la versión del cliente).
- Casos ya resueltos cuyo veredicto (`FALTANTE_PDV`/`SOBRANTE_PDV`) se haya elegido por el motivo
  equivocado: el veredicto es humano, no se toca; listar para tesorería.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A y B | FALTANTE/SOBRANTE elegibles a mano contra el signo: se pisaban en silencio | menú por signo (fase 2) + prueba 2 |
| B | migración sin rastro ni guarda | se quitó la migración (Franco); el script opcional lleva el rastro |
| B | test sobre un método nuevo no falla con el código viejo | test por `verificar` |
| A | el `UPDATE` borra lo que creía el que recibió | los ids quedan en el log y en el PR; los veredictos usan el signo de `diferencia`, no la categoría (`RetiroCasoService.validarSigno`) |

Sin otros lectores ni escritores de `categoria` (central, PWA, mobile, filial: grep), sin cambio de
contrato GraphQL, sin variables ni carpetas nuevas.
