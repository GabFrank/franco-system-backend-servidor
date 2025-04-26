-- Crear tabla chequera
CREATE TABLE financiero.chequera (
    id bigserial NOT NULL,
    cuenta_bancaria_id int8 NOT NULL,
    rango_desde numeric NOT NULL,
    rango_hasta numeric NOT NULL,
    fecha_retiro timestamp NULL,
    creado_en timestamp DEFAULT now() NULL,
    usuario_id int8 NULL,
    CONSTRAINT chequera_pk PRIMARY KEY (id),
    CONSTRAINT chequera_cuenta_bancaria_fk FOREIGN KEY (cuenta_bancaria_id) REFERENCES financiero.cuenta_bancaria(id) ON DELETE RESTRICT,
    CONSTRAINT chequera_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

-- Crear tabla cheque
CREATE TABLE financiero.cheque (
    id bigserial NOT NULL,
    chequera_id int8 NOT NULL,
    pago_detalle_id int8 NULL,
    numero numeric NOT NULL,
    fecha_entrega timestamp NULL,
    fecha_pago timestamp NULL,
    orden text NULL,
    concepto text NULL,
    diferido boolean DEFAULT false NULL,
    total numeric NOT NULL,
    firmante int8 NULL,
    creado_en timestamp DEFAULT now() NULL,
    usuario_id int8 NULL,
    CONSTRAINT cheque_pk PRIMARY KEY (id),
    CONSTRAINT cheque_chequera_fk FOREIGN KEY (chequera_id) REFERENCES financiero.chequera(id) ON DELETE RESTRICT,
    CONSTRAINT cheque_pago_detalle_fk FOREIGN KEY (pago_detalle_id) REFERENCES operaciones.pago_detalle(id) ON DELETE SET NULL,
    CONSTRAINT cheque_firmante_fk FOREIGN KEY (firmante) REFERENCES personas.persona(id) ON DELETE SET NULL,
    CONSTRAINT cheque_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

-- Modificar tabla pago_detalle
ALTER TABLE operaciones.pago_detalle 
ADD COLUMN plazo boolean DEFAULT false NULL,
ADD COLUMN cuotas int NULL; 