-- =====================================================================
-- Control de stock negativo
-- =====================================================================
-- Una fila por cada salida (venta del PDV o item de transferencia) de un
-- producto cuyo stock en la sucursal ya era 0 o negativo antes de salir.
-- La lee el equipo de inventario para ir a controlar ese stock.
--
--   VENTA          la inserta el poller del central cuando el movimiento
--                  de stock de la venta llega replicado de la filial
--   TRANSFERENCIA  la inserta saveTransferenciaItem al cargar un item nuevo
--
-- Idempotencia, distinta por tipo:
--   VENTA          por movimiento de stock: el poller re-lee un tramo en cada
--                  ciclo, y un mismo item de venta puede tener mas de un
--                  movimiento activo (descuentos duplicados) que hay que ver
--   TRANSFERENCIA  por item: se registra una sola vez, al cargarlo
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS operaciones.control_stock_negativo (
    id                   bigserial PRIMARY KEY,
    sucursal_id          bigint NOT NULL,
    producto_id          bigint NOT NULL,
    tipo                 varchar(15) NOT NULL,
    cantidad             numeric NOT NULL,
    stock_previo         numeric NOT NULL,
    usuario_id           bigint,
    fecha                timestamp with time zone NOT NULL,
    referencia_id        bigint,
    item_id              bigint NOT NULL,
    movimiento_stock_id  bigint,
    creado_en            timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_control_stock_negativo_tipo CHECK (tipo IN ('VENTA', 'TRANSFERENCIA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_control_stock_negativo_movimiento
    ON operaciones.control_stock_negativo (sucursal_id, movimiento_stock_id)
    WHERE movimiento_stock_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_control_stock_negativo_transferencia
    ON operaciones.control_stock_negativo (item_id, sucursal_id)
    WHERE tipo = 'TRANSFERENCIA';

CREATE INDEX IF NOT EXISTS idx_control_stock_negativo_fecha
    ON operaciones.control_stock_negativo (fecha);
CREATE INDEX IF NOT EXISTS idx_control_stock_negativo_sucursal_fecha
    ON operaciones.control_stock_negativo (sucursal_id, fecha);

-- Hasta que movimiento de cada sucursal ya evaluo el poller.
-- inicial_movimiento_id marca donde empezo: nada anterior se registra.
-- ultimo_creado_en es la fecha de ese ultimo movimiento: el poller re-lee los
-- 15 minutos anteriores, por las ventas que se confirman fuera de orden.
CREATE TABLE IF NOT EXISTS operaciones.control_stock_negativo_cursor (
    sucursal_id            bigint PRIMARY KEY,
    ultimo_movimiento_id   bigint NOT NULL,
    inicial_movimiento_id  bigint NOT NULL,
    ultimo_creado_en       timestamp with time zone,
    actualizado_en         timestamp with time zone NOT NULL DEFAULT now()
);
