-- Create the enumerated type for 'estado'
CREATE TYPE operaciones.solicitud_pago_estado AS ENUM ('PENDIENTE', 'PARCIAL', 'CONCLUIDO', 'CANCELADO');

-- Create the 'solicitud_pago' table
CREATE TABLE operaciones.solicitud_pago (
    id bigserial NOT NULL,
    creado_en timestamptz DEFAULT now() NULL,
    usuario_id int8 NULL,
    estado operaciones.solicitud_pago_estado NOT NULL,
    CONSTRAINT solicitud_pago_pk PRIMARY KEY (id),
    CONSTRAINT solicitud_pago_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);


-- Create the enumerated type for 'estado' in 'pago'
CREATE TYPE operaciones.pago_estado AS ENUM ('ABIERTO', 'PENDIENTE', 'PARCIAL', 'CONCLUIDO', 'CANCELADO');

-- Create the 'pago' table
CREATE TABLE operaciones.pago (
    id bigserial NOT NULL,
    creado_en timestamptz DEFAULT now() NULL,
    usuario_id int8 NULL,
    autorizado_por int8 NULL,
    solicitud_pago_id int8 NULL,
    estado operaciones.pago_estado NOT NULL,
    programado boolean NOT NULL,
    CONSTRAINT pago_pk PRIMARY KEY (id),
    CONSTRAINT pago_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL,
    CONSTRAINT pago_autorizado_por_fk FOREIGN KEY (autorizado_por) REFERENCES personas.usuario(id) ON DELETE SET NULL,
    CONSTRAINT pago_solicitud_pago_fk FOREIGN KEY (solicitud_pago_id) REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE
);

-- Create the 'pago_detalle' table
CREATE TABLE operaciones.pago_detalle (
    id bigserial NOT NULL,
    pago_id int8 NOT NULL,
    creado_en timestamptz DEFAULT now() NULL,
    usuario_id int8 NULL,
    moneda_id int8 NOT NULL,
    forma_pago_id int8 NOT NULL,
    total numeric NOT NULL,
    sucursal_id int8 NULL,
    caja_id int8 NOT NULL,
    activo boolean NOT NULL,
    fecha_programado timestamp NULL,
    CONSTRAINT pago_detalle_pk PRIMARY KEY (id),
    CONSTRAINT pago_detalle_pago_fk FOREIGN KEY (pago_id) REFERENCES operaciones.pago(id) ON DELETE CASCADE,
    CONSTRAINT pago_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL,
    CONSTRAINT pago_detalle_moneda_fk FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE RESTRICT,
    CONSTRAINT pago_detalle_forma_pago_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id) ON DELETE RESTRICT,
    CONSTRAINT pago_detalle_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL,
    CONSTRAINT pago_detalle_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE RESTRICT
);

