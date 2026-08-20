# Plan — Diseñador visual de hojas de cheque e impresión

> Pedido 2026-08-19. Portar de `frc-gourmet` el diseñador visual drag-and-drop de plantillas
> (hoy usado para facturas legales), adaptarlo a **hojas de cheque**, vincular un diseño a una
> **chequera**, e imprimir el cheque al pagar con él.
>
> **Va en un PR aparte de `feat/pagos-hub-acl-cajas`** — ver §7.

## 1. Qué hay en frc-gourmet (medido)

`frc-gourmet/src/app/pages/facturacion/plantillas/` — ~1.500 líneas:

| Archivo | Líneas | Qué hace |
|---|---|---|
| `designer/factura-plantilla-designer.component.ts` | 426 | El canvas drag-and-drop |
| `designer/*.html` + `*.scss` | 233 + 216 | Lienzo, panel de propiedades, paleta |
| `plantilla-design.model.ts` | 145 | Modelo del diseño serializado |
| `plantilla-render.util.ts` | 278 | Diseño → PDF |
| `list-plantillas/` + `create-plantilla-dialog/` | 199 | CRUD |

**Lo que lo hace un buen punto de partida:**

- Coordenadas y tamaños en **milímetros**, no píxeles: mapean directo a la hoja física e
  independizan del zoom del diseñador.
- Tiene un modo **`PRE_IMPRESO`**: *"la hoja ya está impresa; el sistema solo posiciona texto
  sobre coordenadas"*. **Un cheque es exactamente eso** — el formulario ya viene impreso del
  banco y solo hay que poner fecha, beneficiario, monto y monto en letras en su lugar.
- **Imagen de fondo de referencia** con transformación (escala/desplazamiento): se escanea un
  cheque real y se posicionan los campos encima. Es la función que hace usable un diseñador de
  pre-impreso.
- El diseño se serializa a **JSON**, no a un formato del motor de impresión.

**Lo que no sirve tal cual:** `itemsTable` / `itemColumn` (tabla de ítems de factura). Un cheque
no tiene ítems. Se puede dejar fuera del port y el modelo se simplifica bastante.

## 2. La decisión que define el costo: quién genera el PDF

**frc-gourmet no usa Jasper.** `plantilla-render.util.ts` genera el PDF con **pdfMake en el
cliente** (`pdfMake.createPdf(dd)`), que en Electron abre la ventana de impresión.

El pedido dice "generarlo en pdf usando jasper". Son dos caminos con costos muy distintos:

### Opción A — portar también pdfMake (cliente)

- **Costo:** bajo. El renderer viene hecho; se le sacan las partes de factura.
- **Contra:** rompe la convención del repo. En `frc-comercial` la impresión de comprobantes va por
  `ImpresionService` + `ImprimirDialogComponent` (PDF A4 / ticket), con Jasper del lado del
  central. Sumar pdfMake mete un segundo motor de PDF en el desktop.
- **Riesgo real bajo:** el cheque es un caso de nicho (pre-impreso, sin ítems, una hoja).

### Opción B — generar con Jasper desde el JSON (lo pedido)

- Hay que escribir un **generador dinámico**: JSON del diseño → `JasperDesign` armado por código
  (`JRDesignStaticText` / `JRDesignTextField` posicionados en puntos) → compilar → llenar →
  exportar. No existe hoy.
- **Costo:** el grueso del trabajo, más que el diseñador.
- **Riesgo alto y específico de este repo:** `central/CLAUDE.md` avisa que los `.jrxml` **compilan
  en runtime**, así que un error de plantilla **no se ve en el build ni en CI: revienta al generar
  el PDF en producción**. Un generador dinámico multiplica esa superficie, porque la plantilla ya
  no la escribe un humano sino el diseñador. Sumar la regla de fuentes (solo `SansSerif` /
  `Verdana`, nada nuevo sin instalarlo en todos los servidores).

### Opción C (recomendada) — Jasper con plantilla fija + posiciones variables

Punto medio que respeta la convención sin escribir un generador dinámico:

- Un **`.jrxml` único** para cheques, con los ~8 campos posibles (fecha, beneficiario, monto
  numérico, monto en letras, concepto, ciudad, "no a la orden", cruzado) como `textField`
  **con posición parametrizada**.
- El JSON del diseño se pasa como **parámetros** (x, y, tamaño de fuente, visible) por campo.
- La plantilla es fija y revisable; el diseño solo mueve cosas dentro de ella.
- **Limita el diseñador**: no se pueden agregar elementos arbitrarios, solo acomodar y
  mostrar/ocultar el catálogo de campos. **Para un cheque eso alcanza** — los campos de un cheque
  son siempre los mismos.

**Recomiendo la C.** Conserva Jasper (convención + impresión server-side), evita el generador
dinámico, y el diseñador se simplifica: en vez de una paleta libre, es "acomodá estos 8 campos
sobre la imagen de tu cheque".

## 3. Modelo de datos

```sql
-- Migración aditiva (numeración a asignar en su momento, sufijo .5)
CREATE TABLE IF NOT EXISTS financiero.cheque_plantilla (
    id           bigserial PRIMARY KEY,
    nombre       varchar(120) NOT NULL,
    banco_id     bigint REFERENCES financiero.banco(id),   -- opcional: el formato es del banco
    -- Diseño serializado: posiciones en mm de cada campo + config de página.
    config       text NOT NULL,
    -- Imagen de referencia (escaneo del cheque) en base64 o URL. Solo para el diseñador.
    fondo        text,
    activo       boolean NOT NULL DEFAULT true,
    creado_en    timestamp NOT NULL DEFAULT now(),
    usuario_id   bigint REFERENCES personas.usuario(id)
);

ALTER TABLE financiero.chequera
    ADD COLUMN IF NOT EXISTS cheque_plantilla_id BIGINT REFERENCES financiero.cheque_plantilla(id);
```

El vínculo va en la **chequera** y no en la cuenta: dos chequeras del mismo banco pueden tener
formatos distintos (chequeras viejas, series distintas), y la chequera ya es lo que se elige al
pagar.

### Modelo del diseño (JSON en `config`)

Simplificado respecto de gourmet — sin `itemsTable`/`itemColumn`:

```ts
interface ChequeDiseno {
  pagina: { anchoMm: number; altoMm: number };
  fondo?: BackgroundTransform;          // se porta tal cual de gourmet
  campos: Array<{
    campo: 'fecha' | 'beneficiario' | 'montoNumero' | 'montoLetras'
         | 'concepto' | 'ciudad' | 'noALaOrden' | 'cruzado';
    visible: boolean;
    xMm: number; yMm: number; wMm?: number;
    fontSize?: number; bold?: boolean; align?: 'left'|'center'|'right';
  }>;
}
```

### De dónde sale cada dato al imprimir

`Cheque` ya tiene casi todo (`domain/financiero/Cheque.java`):

| Campo del diseño | Origen |
|---|---|
| `fecha` | `cheque.fechaPago` (el diferido se imprime con su fecha, no la de hoy) |
| `beneficiario` | `cheque.orden` |
| `montoNumero` | `cheque.total` + `cheque.moneda` |
| `montoLetras` | **ya existe**: `utilitarios.NumeroALetrasService`, el mismo que usan los recibos de RRHH (`ReporteRrhhService`). Reutilizarlo, no escribir otro |
| `concepto` | `cheque.concepto` |
| `ciudad`, `noALaOrden`, `cruzado` | del diseño / configuración, no del cheque |

## 4. Puntos de integración

1. **CRUD de plantillas** — lista + diseñador. Entrada de menú bajo *Financiero → Cuentas y
   Bancos* (y **agregarla al buscador global**, ver issue desktop #235).
2. **Vincular a la chequera** — selector de plantilla en `gestionar-chequeras-dialog`.
3. **Imprimir al pagar** — el pago con cheque ya emite N cheques desde `pagar-compras-dialog`
   (chequera elegida una vez, números consecutivos). Al terminar, ofrecer **"Imprimir cheques"**:
   por cada cheque emitido, resolver la plantilla de su chequera y generar el PDF.
   - Si la chequera no tiene plantilla: avisar y no romper el pago. **La impresión no puede ser
     bloqueante del pago.**
4. **Reimpresión** — desde el dashboard de cheques, acción "Imprimir" en la fila. Necesario:
   se traba el papel, sale mal, se reimprime.

## 5. Costo estimado

| Parte | Opción C (recomendada) |
|---|---|
| Diseñador (port + simplificación a campos fijos) | 1,5 – 2 días |
| Backend: entidad, migración, GraphQL, CRUD | 0,5 día |
| `.jrxml` de cheque + parámetros de posición + servicio de impresión | 1 día |
| Vínculo con chequera + UI | 0,5 día |
| Impresión al pagar + reimpresión | 0,5 día |
| **Total** | **~4 días** |

Con la opción B (generador dinámico de Jasper) sumar **2–3 días** más y el riesgo de §2.

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Calibración**: lo que se ve en pantalla no cae donde debe en el papel | Botón **"Imprimir prueba"** en el diseñador, que saca la hoja con marcas de posición. Es lo primero que hay que construir, no lo último |
| Márgenes no imprimibles de la impresora corren todo | Documentar por impresora; el ajuste fino vive en el diseño (por eso está en mm) |
| Un `.jrxml` mal armado revienta recién en producción | Test JUnit que compile + `fillReport` con datos dummy, como ya hacen `ReciboFiniquitoJrxmlTest` y `ReciboRrhhJrxmlTest` |
| Fuentes | Solo `SansSerif` (lógica de Java, siempre disponible). No ofrecer selector de fuente en el diseñador |
| Cheque impreso mal y ya emitido | La reimpresión (§4.4) no debe emitir un cheque nuevo ni cambiar su número |

## 7. Por qué no entra en `feat/pagos-hub-acl-cajas`

Esa rama ya lleva 10+ commits en dos repos, tres migraciones y **toca seguridad** (el ACL de
cajas). Necesita review cuidadoso y un backfill coordinado antes de mergear. Meterle un módulo de
diseño visual con un motor de impresión nuevo significaría que el control de acceso espere a que
se revise un diseñador que no tiene nada que ver, y mezclar en un mismo review "quién puede mover
plata" con "dónde va la fecha en el cheque".

## 8. Primer paso sugerido

Antes de portar nada: **conseguir un cheque real escaneado** de cada banco con el que se opera
(BNF, STONE) y medirlo. El diseñador se prueba contra eso desde el día uno; sin esa referencia,
la calibración (§6) se descubre tarde y es el riesgo que hunde este tipo de features.
