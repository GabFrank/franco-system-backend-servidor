CREATE TABLE financiero.caja_categoria_observacion (
    id bigserial NOT NULL,
    usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT caja_categoria_observacion_pk PRIMARY KEY (id),
	CONSTRAINT caja_categoria_observacion_unique UNIQUE (descripcion),
	CONSTRAINT caja_categoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE financiero.caja_subcategoria_observacion (
	id bigserial NOT NULL,
	caja_categoria_id int8 NULL,
	usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT caja_subcategoria_observacion_pk PRIMARY KEY (id),
	CONSTRAINT caja_subcategoria_observacion_unique UNIQUE (descripcion),
	CONSTRAINT caja_subcategoria_observacion_categoria_fk FOREIGN KEY (caja_categoria_id) REFERENCES financiero.caja_categoria_observacion(id) ON DELETE SET NULL,
	CONSTRAINT caja_subcategoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE financiero.caja_motivo_observacion (
	id bigserial NOT NULL,
    caja_subcategoria_id int8 NULL,
    usuario_id int8 NULL,
	descripcion varchar NULL,
	activo bool DEFAULT true,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT caja_motivo_observacion_pk PRIMARY KEY (id),
	CONSTRAINT caja_motivo_observacion_unique UNIQUE (descripcion),
	CONSTRAINT caja_motivo_observacion_subcategoria_observacion_fk FOREIGN KEY (caja_subcategoria_id) REFERENCES financiero.caja_subcategoria_observacion(id) ON DELETE SET NULL,
	CONSTRAINT caja_motivo_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

CREATE TABLE financiero.caja_observacion (
	id bigserial NOT NULL,
	caja_motivo_id int8 NULL,
	caja_id int8 NULL,
	sucursal_id int8 NULL,
	usuario_id int8 NULL,
	descripcion varchar NULL,
	creado_en timestamptz DEFAULT now() NULL,
	CONSTRAINT caja_observacion_pk PRIMARY KEY (id),
	CONSTRAINT caja_observacion_caja_motivo_observacion_fk FOREIGN KEY (caja_motivo_id) REFERENCES financiero.caja_motivo_observacion(id) ON DELETE SET NULL,
	CONSTRAINT caja_observacion_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL,
	CONSTRAINT caja_observacion_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL,
	CONSTRAINT caja_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);