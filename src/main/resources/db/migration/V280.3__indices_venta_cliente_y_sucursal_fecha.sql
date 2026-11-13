-- Flyway:executeInTransaction=false

-- #636 / #582: la lista de ventas (ventasGenericFilter) recorria venta_pk entera (6,9 M filas en la
-- copia de bodega) cuando el filtro dejaba pocas ventas.
--
-- 1. Filtro por cliente: venta.cliente_id no tenia indice. Medido en bodega (EXPLAIN ANALYZE, pagina
--    de 15): 2.807 ms -> 0,07 ms; con rango de fechas 1.719 ms -> 0,03 ms.
-- 2. Sucursal + rango de fechas ordenado por fecha (la lista ordena por fecha cuando hay rango, mismo
--    PR): rango ancho de una sucursal 231 ms -> 0,05 ms. Tambien acelera el conteo del paginador.
--
-- Solo central: venta llega de las filiales por replicacion logica y un indice en el suscriptor no
-- cambia lo que se replica. CONCURRENTLY para no bloquear las escrituras de la replicacion mientras se
-- crea (en la copia de bodega: 2,4 s y 4,3 s). IF NOT EXISTS: si un CONCURRENTLY fallara quedaria un
-- indice INVALID que hay que borrar a mano (DROP INDEX CONCURRENTLY) antes de reintentar.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_cliente
    ON operaciones.venta (cliente_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_sucursal_creado_en
    ON operaciones.venta (sucursal_id, creado_en);
