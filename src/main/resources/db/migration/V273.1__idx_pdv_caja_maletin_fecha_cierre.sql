-- #609: el analisis de diferencias busca, para cada caja, la caja anterior del mismo maletin
-- (fecha_cierre < fecha_apertura de esta, la mas reciente). Sin este indice esa busqueda recorre
-- pdv_caja por cada fila: el maletin mas grande de la bodega (1.844 cajas) tardaba 20 s; con el
-- indice, 0,12 s. Tambien lo usa findPreviousCajaByMaletinAndFecha (JPQL).
--
-- Solo central. pdv_caja llega del filial por replicacion (BRANCH_TO_MAIN): un indice en el
-- suscriptor no cambia lo que se replica. Sin CONCURRENTLY: con ~50k filas se crea en menos de
-- un segundo, y un CONCURRENTLY que falla deja un indice INVALID que IF NOT EXISTS no rehace.
CREATE INDEX IF NOT EXISTS idx_pdv_caja_maletin_fecha_cierre
    ON financiero.pdv_caja (maletin_id, fecha_cierre);
