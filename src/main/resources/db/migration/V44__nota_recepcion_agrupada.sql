CREATE TABLE operaciones.nota_recepcion_agrupada (
	id bigserial NOT NULL,
	nota_recepcion_id int8 NULL,
	usuario_id int8 NULL,
	sucursal_id int8 DEFAULT 0 NULL,
	creado_en timestamp NULL,
	CONSTRAINT nota_recepcion_agrupada_pkey PRIMARY KEY (id),
	CONSTRAINT nota_recepcion_agrupada_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id),
	CONSTRAINT nota_recepcion_agrupada_nota_recepcion_fk FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id)
);