-- =====================================================================
-- Quien cancela o reactiva una venta
-- =====================================================================
-- Una fila por cada vez que VentaService.cancelarVenta cambia el estado de
-- una venta: CANCELADA al cancelar, CONCLUIDA al reactivarla. La lista de
-- ventas muestra la ultima si la venta esta cancelada.
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- Sin FK a operaciones.venta: la venta llega por replica y una FK haria
-- fallar el apply de un DELETE replicado.
-- =====================================================================

CREATE TABLE IF NOT EXISTS operaciones.venta_cancelacion (
    id          bigserial PRIMARY KEY,
    venta_id    bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    estado      operaciones.venta_estado NOT NULL,
    usuario_id  bigint REFERENCES personas.usuario (id),
    creado_en   timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS venta_cancelacion_venta_idx
    ON operaciones.venta_cancelacion (venta_id, sucursal_id, id DESC);
