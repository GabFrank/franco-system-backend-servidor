CREATE SEQUENCE IF NOT EXISTS financiero.ente_cuota_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE TABLE IF NOT EXISTS financiero.ente_cuota (
    id bigint NOT NULL DEFAULT nextval('financiero.ente_cuota_id_seq'::regclass),
    ente_financiero_id bigint,
    numero_cuota integer,
    monto numeric,
    pagado boolean,
    fecha_vencimiento date,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ente_cuota_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS financiero.ente_financiero_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE TABLE IF NOT EXISTS financiero.ente_financiero (
    id bigint NOT NULL DEFAULT nextval('financiero.ente_financiero_id_seq'::regclass),
    ente_id bigint,
    situacion_pago character varying(50),
    proveedor_id bigint,
    moneda_id bigint,
    monto_total numeric,
    monto_ya_pagado numeric,
    cantidad_cuotas integer,
    dia_vencimiento integer,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ente_financiero_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS financiero.ente_vinculacion_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE TABLE IF NOT EXISTS financiero.ente_vinculacion (
    id bigint NOT NULL DEFAULT nextval('financiero.ente_vinculacion_id_seq'::regclass),
    ente_id bigint,
    sucursal_id bigint,
    es_propio boolean,
    alquiler_proveedor_id bigint,
    alquiler_monto numeric,
    alquiler_dia_vencimiento integer,
    alquiler_vigencia date,
    observacion text,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ente_vinculacion_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS financiero.gasto_rendicion_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE TABLE IF NOT EXISTS financiero.gasto_rendicion (
    id bigint NOT NULL DEFAULT nextval('financiero.gasto_rendicion_id_seq'::regclass),
    pre_gasto_id bigint,
    sucursal_id bigint,
    tipo_gasto_id bigint,
    monto_total numeric,
    foto_factura_url character varying(500),
    foto_producto_url character varying(500),
    ente_id bigint,
    gasolinera_id bigint,
    km_actual numeric,
    litros numeric,
    precio_por_litro numeric,
    ubicacion_provisoria character varying(500),
    establecimiento_alimentacion character varying(255),
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    CONSTRAINT gasto_rendicion_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS financiero.gasto_rendicion_funcionario (
    gasto_rendicion_id bigint NOT NULL,
    funcionario_id bigint NOT NULL,
    CONSTRAINT gasto_rendicion_funcionario_pkey PRIMARY KEY (gasto_rendicion_id, funcionario_id)
);
