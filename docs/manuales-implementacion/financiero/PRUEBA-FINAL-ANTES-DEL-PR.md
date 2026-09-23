# Prueba manual final, antes del PR

**Armada el 2026-09-17.** Es la corrida que Gabriel pidió hacer completa recién al final, con todo
implementado. Junta lo que quedó sin probar de las jornadas anteriores y lo que se agregó ahora.

Entorno: filial local `:8082`, central local `:8081`, desktop servido como web (`npm run ng:serve`),
impresora térmica en la máquina del filial, lector de QR conectado.

---

## Bloque A · Lo que se agregó el 2026-09-17

### A1 — El botón «Probar» del formato ✅/❌
Pasos en [BOTON-PROBAR-FORMATO.md](BOTON-PROBAR-FORMATO.md) §4. Los cinco casos: pasa, guarda solo
antes de probar, patrón roto, campo obligatorio ausente, formato de tipo QR.

### A2 — Dejar un cobro sin conciliar, desde la tabla
1. Diálogo de conciliación → fila PENDIENTE → icono **block**.
2. Confirmar sin elegir motivo: no deja.
3. Elegir «Otro motivo» sin escribir nada: no deja.
4. Elegir un motivo y confirmar → la fila pasa a `NO_COMPLETADO` y **muestra el motivo y el
   usuario** debajo del estado.
5. Verificar en la base: `no_completado_motivo`, `no_completado_por_id`, `no_completado_en`.
6. Intentar marcar una fila `COMPLETADO`: el icono no está, y la mutation rechaza si se la fuerza.

### A3 — Cerrar la caja con pendientes, siendo cajero (no ADMIN)
1. Dejar un cobro PENDIENTE y abrir el cierre de caja con un usuario **sin** rol ADMIN.
2. Tiene que ofrecer «Volver a registrarlas» y «Dejar sin conciliar» — **no** exigir supervisor.
3. «Dejar sin conciliar» → pide motivo → marca todas y deja avanzar al cierre.
4. Verificar que las filas quedaron con motivo, usuario y hora.
5. Cancelar el diálogo del motivo: **no** tiene que marcar nada ni dejar cerrar.

### A4 — Reimprimir la seña
1. Fila PENDIENTE → icono **print** → sale el papel con el mismo QR.
2. Escanear ese papel reimpreso en la banda de arriba: abre la fila correcta.
3. Con la configuración de la caja **sin** bloque `printers`: el aviso tiene que decir que falta
   configurar la impresora, no un «no se pudo imprimir» a secas.

### A5 — El `trim()` de la configuración
Pegar ` localhost` (con espacio) en la IP del servidor y guardar: tiene que funcionar igual, sin el
`DOMException: The URL is invalid`.

## Bloque B · Lo que quedó sin probar de las jornadas anteriores

| # | Caso | Por qué importa |
|---|---|---|
| B1 | Prueba 6 del OCR — semáforo por campo | Necesita una foto deliberadamente mala para forzar un campo ámbar |
| B2 | Pago mixto (parte efectivo, parte tarjeta) | El registro de tarjeta sale de un camino distinto |
| B3 | Con factura legal en el PDV 3 | La venta pasa por otro guardado |
| B4 | PDV sin timbrado | La venta tiene que sobrevivir igual |
| B5 | Aviso en el cierre de caja con ventas sin registrar | Es justamente lo que cambió en A3 |
| B6 | Boleta vacía (`**`) | La trampa del split pelado |
| B7 | Gates de acceso: sin caja abierta, sin rol, flujo deshabilitado | Ninguno se probó desde que existen |

## Bloque C · Regresión de lo ya probado

| # | Caso | Estado previo |
|---|---|---|
| C1 | Venta con cobro pospuesto → sale la seña sola | ✅ 2026-09-16 |
| C2 | Escanear la seña → abre la fila exacta | ✅ 2026-09-16 |
| C3 | Seña de un cobro ya COMPLETADO → rechazada con el número | ✅ 2026-09-16 |
| C4 | Seña de otra caja → rechazada nombrando las dos cajas | ✅ 2026-09-16 |
| C5 | Misma seña dos veces seguidas → abre las dos veces | ✅ 2026-09-16 |
| C6 | Completar con el QR del cupón → `COMPLETADO`, `origen=QR`, `qr_crudo` guardado | ✅ 2026-09-16 |

## Antes de abrir el PR

- [ ] `npm run check` (AOT) en desktop, limpio
- [ ] `git fetch origin develop` y **re-confirmar** que `V228.5` (central) y `V102.5` (filial)
      siguen libres
- [ ] Dry-run de las migraciones contra una copia de la base de alpha
- [ ] La descripción del PR dice, columna por columna, qué hace el backend viejo con el esquema
      nuevo
- [ ] El PR de **central va primero** y desplegado antes de mergear el del filial (columnas nuevas
      en una tabla BRANCH_TO_MAIN)
