-- Flyway:executeInTransaction=false

-- #636 / #582: la lista de facturas (facturaLegales) filtra siempre por sucursal y rango de fechas, y
-- factura_legal no tenia mas indice que la PK. Medido en la copia de bodega (2,29 M facturas), Central
-- en septiembre, 25 por pagina: pagina 239 ms -> 6,7 ms (con el orden id + 0 del mismo PR) y conteo
-- del paginador 113 ms -> 3,2 ms. Mismas filas y mismo orden.
--
-- Solo central: factura_legal llega de las filiales por replicacion logica y un indice en el
-- suscriptor no cambia lo que se replica. CONCURRENTLY para no bloquear las escrituras de la
-- replicacion mientras se crea (en la copia de bodega: 1,4 s). IF NOT EXISTS: si un CONCURRENTLY
-- fallara quedaria un indice INVALID que hay que borrar a mano (DROP INDEX CONCURRENTLY) antes de
-- reintentar.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_legal_sucursal_creado_en
    ON financiero.factura_legal (sucursal_id, creado_en);
