-- Lotes archivados: lotes que ya no tienen saldo en ninguna sucursal y se sacan de los
-- buscadores y selectores para que no estorben (un producto de alta rotacion junta un lote por
-- recepcion).
--
-- Tabla aparte y SOLO del central, a proposito: no se agrega columna a operaciones.lote porque esa
-- tabla replica a todas las filiales por replicacion logica, y una columna nueva en el publicador
-- que la filial no tiene corta la replicacion hasta migrar todas las filiales en orden. Esta tabla
-- no se registra en configuraciones.replication_table: la filial no la necesita, porque un lote
-- archivado tiene saldo 0 en todas las sucursales y FEFO ya no lo elige (v_stock_lote filtra
-- SUM(cantidad) <> 0).
--
-- Aditiva: el codigo viejo no la lee. Vuelta atras = dejarla.
CREATE TABLE IF NOT EXISTS operaciones.lote_archivado (
    lote_id      BIGINT PRIMARY KEY REFERENCES operaciones.lote (id),
    usuario_id   BIGINT,
    motivo       TEXT      NOT NULL,
    archivado_en TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE operaciones.lote_archivado IS
    'Lotes archivados (saldo 0 en toda la red) ocultos de buscadores y selectores. Solo central, no replica.';
