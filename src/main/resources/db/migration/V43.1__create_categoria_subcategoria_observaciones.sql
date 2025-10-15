CREATE TABLE operaciones.categoria_observacion (
    id bigserial NOT NULL,
    usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT categoria_observacion_pk PRIMARY KEY (id),
	CONSTRAINT categoria_observacion_unique UNIQUE (descripcion),
	CONSTRAINT categoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE operaciones.subcategoria_observacion (
	id bigserial NOT NULL,
	categoria_id int8 NULL,
	usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT subcategoria_observacion_pk PRIMARY KEY (id),
	CONSTRAINT subcategoria_observacion_unique UNIQUE (descripcion),
	CONSTRAINT subcategoria_observacion_categoria_fk FOREIGN KEY (categoria_id) REFERENCES operaciones.categoria_observacion(id) ON DELETE SET NULL,
	CONSTRAINT subcategoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE operaciones.motivo_observacion (
	id bigserial NOT NULL,
    subcategoria_id int8 NULL,
    usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT motivo_observacion_pk PRIMARY KEY (id),
	CONSTRAINT motivo_observacion_unique UNIQUE (descripcion),
	CONSTRAINT motivo_observacion_subcategoria_observacion_fk FOREIGN KEY (subcategoria_id) REFERENCES operaciones.subcategoria_observacion(id) ON DELETE SET NULL,
	CONSTRAINT motivo_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE operaciones.venta_observacion (
	id bigserial NOT NULL,
	motivo_id int8 NULL,
	venta_id int8 NULL,
	sucursal_id int8 NULL,
	usuario_id int8 NULL,
	descripcion varchar NULL,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT venta_observacion_pk PRIMARY KEY (id),
	CONSTRAINT venta_observacion_motivo_observacion_fk FOREIGN KEY (motivo_id) REFERENCES operaciones.motivo_observacion(id) ON DELETE SET NULL,
	CONSTRAINT venta_observacion_venta_fk FOREIGN KEY (venta_id, sucursal_id) REFERENCES operaciones.venta(id, sucursal_id) ON DELETE SET NULL,
	CONSTRAINT venta_observacion_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL,
	CONSTRAINT venta_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);
