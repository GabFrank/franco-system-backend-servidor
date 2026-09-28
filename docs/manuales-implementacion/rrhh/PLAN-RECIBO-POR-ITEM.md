# Plan — recibo por ítem de liquidación

Rama: `feature/rrhh-recibo-por-item` en central y desktop (las dos desde `origin/develop`).
Pedido: poder generar un recibo de **cualquier ítem** de una liquidación, con un ícono en la fila
del ítem.

## Decisiones del usuario (2026-09-28)

| Tema | Decisión |
|---|---|
| Alcance | Liquidación de sueldo **y** liquidación final (finiquito) |
| Contenido | Recibo **genérico del ítem** (funcionario, concepto, monto en números y letras), igual para cualquier ítem. No reimprime el recibo de origen (vale, bono…) |
| Ítem DESCUENTO | **Constancia de descuento**: otra cláusula («Tomo conocimiento y acepto el descuento…»). HABER: «Recibí conforme…» |
| Estado | Solo **APROBADA** o **PAGADA**. En BORRADOR el monto todavía se puede editar; ANULADA no tiene nada que firmar |

## Diseño

### Central

Dos queries nuevas en `reportes-rrhh.graphqls`, con la firma de todos los recibos:

```graphql
imprimirReciboItemLiquidacion(itemId: ID!, anchoMm: Int, escpos: Boolean): String
imprimirReciboItemLiquidacionFinal(itemId: ID!, anchoMm: Int, escpos: Boolean): String
```

- Resolver: `ReporteRrhhGraphQL`. **Primera línea `seg.requireVer()`**: son datos de nómina.
  Los recibos por id que ya existen no gatean (deuda anterior, documentada en `seguridad-roles.md`);
  lo nuevo sí, igual que `imprimirActaAdvertencia`.
- Servicio: `ReporteRrhhService.reciboItemLiquidacionBase64` y `reciboItemLiquidacionFinalBase64`.
  Reusan el builder privado `reciboRrhh(...)`, que ya resuelve los tres formatos (PDF A4 con
  `recibo-rrhh.jrxml`, PDF ticket 58/80 y ESC/POS). **No hay `.jrxml` nuevo.**
- Dependencias nuevas del servicio: `LiquidacionItemRepository` y `LiquidacionFinalItemRepository`
  (repositorios, no servicios, para no abrir ciclos). El test que arma el servicio a mano se
  actualiza en el mismo commit.
- Reglas, en el servicio (el frontend solo esconde el botón):
  - ítem inexistente, o sin liquidación (`liquidacion_id` es nullable) → `GraphQLException`;
  - liquidación que no está en APROBADA o PAGADA → `GraphQLException` con el estado;
  - monto null o ≤ 0 → `GraphQLException` (no hay nada que firmar).
  - Todo se lee dentro del método `@Transactional(readOnly = true)` del servicio: ítem →
    liquidación → funcionario son `LAZY`, y el resolver no es transaccional.
- Contenido:

| Campo | HABER | DESCUENTO |
|---|---|---|
| Título | `RECIBO DE LIQUIDACION Nro. <liqId>` | `CONSTANCIA DE DESCUENTO Nro. <liqId>` |
| Fila | descripción del ítem + monto, sin paréntesis. Sin descripción: el `codigo` (sueldo) o el `concepto` (finiquito) con `_` → espacio | ídem |
| Cláusula | `Recibí conforme, en concepto de <concepto>,` | `Tomo conocimiento y acepto el descuento en concepto de <concepto>,` |
| Observación | `Liquidación de sueldo <periodo> (<ESTADO>)` / `Liquidación final (<ESTADO>)` | ídem |
| Total / en letras | monto del ítem | monto del ítem |

  El número del título es el de la **liquidación**, no el del ítem: es el que se busca en la
  lista a partir del papel firmado. El período y el estado van en la observación, **no en el
  título**, porque el título del A4 (`recibo-rrhh.jrxml:21-25`) es un campo de 400 pt a 13 pt
  negrita sin `isStretchWithOverflow`, y Jasper recorta sin error (auditoría B-2). Con esto el
  título más largo tiene 35 caracteres, en el rango de los que ya existen («RECIBO DE
  PENALIZACION Nro. 5»).

  Cómo se lee la cláusula en cada formato: el A4 y el ticket PDF agregan «La suma de <letras>
  (Gs. N).», y el ESC/POS agrega solo «<letras>.» (`ReciboTicketEscPos.java:73`). Es igual para
  los 7 recibos que ya existen (el de penalización usa la misma forma que la constancia). No se
  toca acá: cambiar el builder compartido cambia los 7.

- Sin migración, sin enum nuevo, sin tabla nueva. El schema `rrhh` es central-only: **N/A para
  filial porque ninguna tabla `rrhh` se publica** [ev: `V154.0__crear_schema_rrhh_fundaciones.sql`,
  skill `rrhh-expert`].

### Desktop

- `graphql-query.ts` de `liquidacion/` y de `liquidacion-final/`: una query cada uno + una clase
  GQL por archivo (`ImprimirReciboItemLiquidacion.ts`, `ImprimirReciboItemLiquidacionFinal.ts`).
- `LiquidacionService.onImprimirReciboItem(itemId, anchoMm, escpos)` y
  `LiquidacionFinalService.onImprimirReciboItem(...)`.
- En `liquidacion-detalle-dialog` y `liquidacion-final-dialog`, columna `acciones`: botón
  `mat-icon-button` con ícono `receipt` y tooltip «Generar recibo», visible con un flag
  `permiteReciboItem` que se calcula cada vez que se carga la liquidación (APROBADA o PAGADA),
  no en el HTML.
- Clic → `ImpresionService.imprimir(nombre, (anchoMm, escpos) => …)`: el diálogo oficial PDF /
  Ticket 58 / Ticket 80, igual que los otros recibos firmables. El detalle mensual hoy usa
  `ReporteService` directo para «Ver Recibo»; eso no se toca.

## Tabla de datos nuevos

No nace ningún campo, columna ni clave. Las dos queries nuevas tienen un escritor (el botón de
cada diálogo, en el desktop) y un lector (el resolver `ReporteRrhhGraphQL`).

## Fases

| # | Repo | Contenido | Tests / verificación |
|---|---|---|---|
| 1 | central | servicio + resolver + `.graphqls` + test nuevo `ReciboItemLiquidacionTest` + ajuste del constructor en `RecibosRrhhNumeroObservacionTest` | `./mvnw clean verify -B -DskipFlyway=true` leído del log |
| 2 | desktop | GQL, services y botón en los dos diálogos | `npm run check` (AOT) leído del log |
| 3 | los dos | docs: `desktop/docs/IMPRESION.md` (recibo nº 8) y skill `rrhh-expert` local; se borra este plan | — |

Tests de la fase 1 (`ReciboItemLiquidacionTest`, mismo patrón que `RecibosRrhhNumeroObservacionTest`:
se mira el texto del ticket ESC/POS decodificado y el del PDF):

1. HABER de sueldo APROBADA: el ticket 80 trae título con periodo y Nro. de la liquidación,
   la descripción, el monto y «Recibi conforme»; PDF A4 / 58 / 80 generan y traen el título.
2. DESCUENTO de sueldo PAGADA: «CONSTANCIA DE DESCUENTO» y «Tomo conocimiento».
3. Sueldo en BORRADOR y en ANULADA → `GraphQLException`.
4. Ítem inexistente → `GraphQLException`.
5. Finiquito APROBADA: título `RECIBO DE LIQUIDACION Nro.`, observación `LIQUIDACION FINAL`;
   ítem sin descripción cae al `concepto`.
6. Finiquito en BORRADOR → `GraphQLException`.
7. Monto 0 / null e ítem sin liquidación → `GraphQLException`.
8. A4 de la constancia: el fill del `.jrxml` (patrón `ReciboRrhhJrxmlTest`, `getFullText()`)
   trae el título **completo**, no recortado.

## Orden de PRs y despliegue

El central primero, después el desktop. Un desktop que llama una query que el central no tiene
falla en runtime al tocar el botón, así que el desktop no se mergea antes que el central. Es un
cambio **aditivo**: el central nuevo con un desktop viejo no rompe nada. Sin filial ni
replicación en juego.

## Qué queda sin verificar

- La impresión térmica real (ESC/POS vía `printLocal`) no se cubre con `ng serve -c web`: se
  verifica el payload en el test y el PDF ticket en el visor. Para probarla hace falta el desktop
  en Electron con la impresora `ticket_soporte`.
- Roles: se verifica con el usuario de la prueba local. Con un usuario sin ningún rol RRHH no se prueba, salvo
  que haya uno en la base local.

## Riesgos aceptados

- **Papel firmado que queda desactualizado.** `volverBorrador`
  (`LiquidacionSueldoService.java:505`, `LiquidacionFinalService.java:575`) y `anular` siguen
  disponibles después de imprimir. Un recibo o una constancia ya firmada no se invalida si
  después se edita el monto o se anula. Lo mismo pasa hoy con el recibo de la liquidación
  completa. La mitigación es la fecha y el estado que se imprimen en el papel. Bloquear el
  volver a borrador cuando ya hay recibos impresos es otra feature (habría que registrar cada
  impresión).
- **El recibo por ítem y el total pueden mostrarse distinto.** Con
  `LIQUIDACION_CONSOLIDAR_CUOTAS_CREDITO` prendida, el recibo total junta las cuotas de crédito
  en una línea (`ReciboAgrupador`), y el recibo por ítem sale por cuota. Las cuotas suman lo
  mismo.

## Registro del ciclo

- Paso 5 (auditoría del plan, 2026-09-28). Dos auditores, eje A y eje B, sin verse entre sí.
  Cada hallazgo se verificó contra el código:
  - **A: nombres de query sin colisión.** Grepeado en central, desktop, mobile-pwa y mobile.
    Sin filial, sin enum, sin variable de entorno. **Nada que cambiar.**
  - **A: ítem de sueldo sin descripción.** La severidad baja de media a baja: en el alta manual
    la descripción cae a la del catálogo (`LiquidacionSueldoService.java:427`), así que solo
    queda vacía si el catálogo tampoco tiene. **Se adoptó** el respaldo `codigo` → texto.
  - **A: ítem sin liquidación → NPE.** **Se adoptó** el guard.
  - **A: el desktop contra un central viejo.** El aviso ya lo da `GenericCrudService`, y el
    orden de PRs lo evita. **Nada que cambiar.**
  - **B: título recortado en el A4.** Confirmado (`recibo-rrhh.jrxml:21-25`). **Se adoptó**
    título corto, contexto en la observación y el test 8.
  - **B: frase distinta en ESC/POS.** Confirmado, pero es de los 7 recibos existentes.
    **Documentado**, sin cambios.
  - **B: papel firmado que sobrevive a volver a borrador o anular.** **Riesgo aceptado**
    (arriba), con el estado impreso como mitigación.
  - **B: monto null o 0.** **Se adoptó** el guard y el test 7.
  - **B: consolidación de cuotas.** **Documentado.**
  - **A: tilde en «Recibí» del ticket.** `ReciboTicketEscPos.na()` le saca los acentos al
    payload, así que el test busca «Recibi».
