# Devoluciones — Decisión sobre replicación lógica

## Resumen

El módulo de **Devoluciones de producto** es **central-only**: sus tablas
(`operaciones.devolucion`, `operaciones.devolucion_item`,
`operaciones.motivo_averia`, `operaciones.producto_vencimiento`) **NO** se
agregan a las publicaciones de replicación lógica hacia las filiales.

## Por qué

1. **Consistencia con módulos análogos.** Se verificó que
   `operaciones.transferencia`, `transferencia_item`, `recepcion_mercaderia` y
   `producto_vencimiento` **no figuran en ninguna publicación**
   (`central_pub` ni `central_*_filialX_pub`). Los flujos operativos de
   transferencia/recepción/devolución son centralizados; los clientes (desktop
   y mobile) hablan GraphQL contra **central**, no contra el filial.

2. **Riesgo de romper la replicación.** La replicación lógica requiere que el
   **subscriptor (filial) tenga el mismo esquema** que las tablas publicadas.
   Las filiales corren su **propio set de migraciones Flyway** (no ejecutan las
   del central) y hoy **no tienen** las tablas/columnas nuevas de devoluciones.
   Publicar estas tablas sin las migraciones filial correspondientes haría
   fallar la aplicación de cambios en el subscriptor y podría **detener la
   replicación de todo el stream**.

3. **No es necesario para la funcionalidad.** Las devoluciones se crean y
   consultan en central. El filial no necesita estos datos para operar ventas.

## Si en el futuro se quiere devoluciones en filial (offline)

Trabajo coordinado, en este orden:

1. Agregar migraciones Flyway **en el repo filial** que creen las tablas y
   columnas equivalentes (mismas definiciones que V141.0–V147.0 del central).
2. Recién entonces, agregar las tablas a las publicaciones del central
   (`central_pub` + cada `central_*_filialX_pub`) siguiendo el patrón dinámico
   de `V119__add_replication_test_to_publications.sql`, con el filtro por la
   columna de sucursal correcta:
   - `devolucion` → filtro `sucursal_origen_id = X`
   - `producto_vencimiento` → filtro `sucursal_id = X`
   - `devolucion_item` → **no tiene columna de sucursal**; requeriría
     denormalizar un `sucursal_id` (como `venta_item`) antes de poder filtrarlo
     por filial.
   - `motivo_averia` (catálogo) → sin filtro, a `central_pub` (MAIN_TO_ALL).
3. Desplegar filial y central de forma coordinada.

Mientras tanto, este módulo queda intencionalmente **fuera** de la replicación.
