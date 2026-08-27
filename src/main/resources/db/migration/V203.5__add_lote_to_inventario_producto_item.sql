-- Lote del renglon de conteo.
--
-- Con control de lote un renglon ES un lote: cantidad_fisica pasa a guardar el saldo DE ESE LOTE
-- en la sucursal, no la existencia del producto. Es lo que le permite a
-- finalizarInventarioEnSucursal() escribir el desglose en operaciones.movimiento_stock_lote en vez
-- de dejar el ledger sin tocar, que es lo que hacia que la mercaderia contada nunca volviera a ser
-- asignable por FEFO.
--
-- Nullable a proposito: los ~8.700 productos sin control de lote siguen contando en un renglon sin
-- lote, y el renglon sin lote tambien es valido en un producto con lote (mercaderia que todavia no
-- se atribuyo a ninguno).
--
-- NO se registra en configuraciones.replication_table: ninguna tabla operaciones.inventario* se
-- replica. El inventario vive solo en el central, que es con quien hablan el escritorio y la PWA.

ALTER TABLE operaciones.inventario_producto_item
    ADD COLUMN lote_id BIGINT NULL;

ALTER TABLE operaciones.inventario_producto_item
    ADD CONSTRAINT fk_ipi_lote FOREIGN KEY (lote_id) REFERENCES operaciones.lote (id);

CREATE INDEX idx_ipi_lote ON operaciones.inventario_producto_item (lote_id) WHERE lote_id IS NOT NULL;
