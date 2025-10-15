ALTER TABLE operaciones.nota_recepcion_agrupada DROP CONSTRAINT nota_recepcion_agrupada_unique;
ALTER TABLE operaciones.nota_recepcion_agrupada DROP CONSTRAINT nota_recepcion_agrupada_nota_recepcion_fk;
ALTER TABLE operaciones.nota_recepcion_agrupada DROP COLUMN nota_recepcion_id;

ALTER TABLE operaciones.nota_recepcion_agrupada ADD proveedor_id int8 NOT NULL;

ALTER TABLE operaciones.nota_recepcion_agrupada ADD CONSTRAINT nota_recepcion_agrupada_proveedor_fk FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE operaciones.nota_recepcion ADD nota_recepcion_agrupada_id int8 NULL;
ALTER TABLE operaciones.nota_recepcion ADD CONSTRAINT nota_recepcion_nota_recepcion_agrupada_fk FOREIGN KEY (nota_recepcion_agrupada_id) REFERENCES operaciones.nota_recepcion_agrupada(id) ON DELETE SET NULL ON UPDATE CASCADE;

