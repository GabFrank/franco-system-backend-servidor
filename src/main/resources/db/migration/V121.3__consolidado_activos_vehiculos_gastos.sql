CREATE SCHEMA IF NOT EXISTS activos;


ALTER SCHEMA activos OWNER TO franco;


CREATE SCHEMA IF NOT EXISTS vehiculos;


ALTER SCHEMA vehiculos OWNER TO postgres;


DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname = 'estado_vehiculo' AND n.nspname = 'vehiculos') THEN
        EXECUTE 'CREATE TYPE vehiculos.estado_vehiculo AS ENUM (''FUNCIONANDO'', ''AVERIADO'', ''EN_REPARACION'', ''AGUARDANDO_REPARACION'')';
    END IF;
END$$;




CREATE TABLE IF NOT EXISTS activos.ente (
    id bigint NOT NULL,
    tipo_ente character varying(50),
    referencia_id bigint,
    activo boolean,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    descripcion character varying(255)
);


ALTER TABLE activos.ente OWNER TO franco;


CREATE TABLE IF NOT EXISTS activos.ente_archivo (
    id bigint NOT NULL,
    ente_id bigint,
    tipo_archivo character varying(50),
    url text,
    descripcion character varying(255),
    vigente boolean,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE activos.ente_archivo OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.ente_archivo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.ente_archivo_id_seq OWNER TO franco;


ALTER SEQUENCE activos.ente_archivo_id_seq OWNED BY activos.ente_archivo.id;



CREATE SEQUENCE IF NOT EXISTS activos.ente_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.ente_id_seq OWNER TO franco;


ALTER SEQUENCE activos.ente_id_seq OWNED BY activos.ente.id;



CREATE TABLE IF NOT EXISTS activos.ente_sucursal (
    id bigint NOT NULL,
    ente_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    responsable_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);


ALTER TABLE activos.ente_sucursal OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.ente_sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.ente_sucursal_id_seq OWNER TO franco;


ALTER SEQUENCE activos.ente_sucursal_id_seq OWNED BY activos.ente_sucursal.id;



CREATE TABLE IF NOT EXISTS activos.familia_mueble (
    id bigint NOT NULL,
    descripcion character varying(255),
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE activos.familia_mueble OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.familia_mueble_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.familia_mueble_id_seq OWNER TO franco;


ALTER SEQUENCE activos.familia_mueble_id_seq OWNED BY activos.familia_mueble.id;



CREATE TABLE IF NOT EXISTS activos.inmueble (
    id bigint NOT NULL,
    propietario_id bigint,
    nombre_asignado character varying(255),
    pais_id bigint,
    ciudad_id bigint,
    direccion character varying(255),
    google_maps_url text,
    codigo_catastral character varying(100),
    valor_tasacion numeric(19,2),
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    situacion_pago character varying,
    proveedor_id bigint,
    moneda_id bigint,
    monto_total numeric,
    monto_ya_pagado numeric,
    cantidad_cuotas integer,
    dia_vencimiento integer,
    cantidad_cuotas_pagadas integer,
    valor_tasacion_pyg numeric,
    valor_tasacion_brl numeric
);


ALTER TABLE activos.inmueble OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.inmueble_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.inmueble_id_seq OWNER TO franco;


ALTER SEQUENCE activos.inmueble_id_seq OWNED BY activos.inmueble.id;



CREATE TABLE IF NOT EXISTS activos.mueble (
    id bigint NOT NULL,
    propietario_id bigint,
    identificador character varying(100),
    descripcion text,
    familia_id bigint,
    tipo_mueble_id bigint,
    consume_energia boolean,
    consumo_valor character varying(50),
    valor_tasacion numeric(19,2),
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    cantidad_cuotas_pagadas integer,
    valor_tasacion_pyg numeric,
    valor_tasacion_brl numeric
);


ALTER TABLE activos.mueble OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.mueble_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.mueble_id_seq OWNER TO franco;


ALTER SEQUENCE activos.mueble_id_seq OWNED BY activos.mueble.id;



CREATE TABLE IF NOT EXISTS activos.tipo_mueble (
    id bigint NOT NULL,
    descripcion character varying(255),
    familia_mueble_id bigint,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE activos.tipo_mueble OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS activos.tipo_mueble_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE activos.tipo_mueble_id_seq OWNER TO franco;


ALTER SEQUENCE activos.tipo_mueble_id_seq OWNED BY activos.tipo_mueble.id;



CREATE TABLE IF NOT EXISTS vehiculos.dispositivo_gps (
    id bigint NOT NULL,
    imei character varying(50) NOT NULL,
    vehiculo_id bigint,
    modelo_tracker character varying(20),
    sim_numero character varying(20),
    activo boolean DEFAULT true,
    creado_em timestamp without time zone DEFAULT now(),
    ultima_latitud double precision,
    ultima_longitud double precision,
    ultima_fecha_reporte timestamp without time zone,
    ultima_ignicion boolean DEFAULT false,
    ultima_velocidad integer DEFAULT 0,
    modo_sueno boolean DEFAULT false,
    intervalo_reporte integer DEFAULT 30,
    motor_bloqueado boolean DEFAULT false,
    alerta_velocidad boolean DEFAULT true,
    velocidad_limite integer DEFAULT 100,
    alerta_vibracion boolean DEFAULT false,
    alerta_bateria_baja boolean DEFAULT true,
    alerta_acc boolean DEFAULT true
);


ALTER TABLE vehiculos.dispositivo_gps OWNER TO franco;


COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_latitud IS 'Caché de última latitud conocida';



COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_longitud IS 'Caché de última longitud conocida';



COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_fecha_reporte IS 'Fecha/hora del último reporte GPS';



COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_ignicion IS 'Último estado de ignición conocido';



COMMENT ON COLUMN vehiculos.dispositivo_gps.ultima_velocidad IS 'Última velocidad reportada en km/h';



COMMENT ON COLUMN vehiculos.dispositivo_gps.modo_sueno IS 'Estado real del modo sueño en el dispositivo';



COMMENT ON COLUMN vehiculos.dispositivo_gps.intervalo_reporte IS 'Intervalo de reporte configurado en segundos';



COMMENT ON COLUMN vehiculos.dispositivo_gps.motor_bloqueado IS 'Estado real del bloqueo de motor/combustible';



COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_velocidad IS 'Enviar push notification al detectar exceso de velocidad';



COMMENT ON COLUMN vehiculos.dispositivo_gps.velocidad_limite IS 'Límite de velocidad en km/h para alerta de exceso';



COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_vibracion IS 'Enviar push notification al detectar choque/vibración';



COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_bateria_baja IS 'Enviar push notification al detectar batería baja';



COMMENT ON COLUMN vehiculos.dispositivo_gps.alerta_acc IS 'Enviar push notification al detectar cambio de ignición (ACC)';



CREATE SEQUENCE IF NOT EXISTS vehiculos.dispositivo_gps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.dispositivo_gps_id_seq OWNER TO franco;


ALTER SEQUENCE vehiculos.dispositivo_gps_id_seq OWNED BY vehiculos.dispositivo_gps.id;



CREATE TABLE IF NOT EXISTS vehiculos.marca (
    id bigint NOT NULL,
    descripcion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.marca REPLICA IDENTITY FULL;


ALTER TABLE vehiculos.marca OWNER TO postgres;


CREATE SEQUENCE IF NOT EXISTS vehiculos.marca_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.marca_id_seq OWNER TO postgres;


ALTER SEQUENCE vehiculos.marca_id_seq OWNED BY vehiculos.marca.id;



CREATE TABLE IF NOT EXISTS vehiculos.modelo (
    id bigint NOT NULL,
    descripcion character varying,
    marca_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.modelo REPLICA IDENTITY FULL;


ALTER TABLE vehiculos.modelo OWNER TO postgres;


CREATE SEQUENCE IF NOT EXISTS vehiculos.modelo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.modelo_id_seq OWNER TO postgres;


ALTER SEQUENCE vehiculos.modelo_id_seq OWNED BY vehiculos.modelo.id;



CREATE TABLE IF NOT EXISTS vehiculos.telemetria (
    id bigint NOT NULL,
    dispositivo_id bigint NOT NULL,
    fecha_servidor timestamp without time zone DEFAULT now(),
    fecha_gps timestamp without time zone,
    latitud double precision,
    longitud double precision,
    velocidad integer,
    direccion integer,
    ignicion boolean,
    alarma character varying(50),
    json_data jsonb
);


ALTER TABLE vehiculos.telemetria OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS vehiculos.telemetria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.telemetria_id_seq OWNER TO franco;


ALTER SEQUENCE vehiculos.telemetria_id_seq OWNED BY vehiculos.telemetria.id;



CREATE TABLE IF NOT EXISTS vehiculos.tipo_combustible (
    id bigint NOT NULL,
    descripcion character varying(150),
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE vehiculos.tipo_combustible OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS vehiculos.tipo_combustible_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.tipo_combustible_id_seq OWNER TO franco;


ALTER SEQUENCE vehiculos.tipo_combustible_id_seq OWNED BY vehiculos.tipo_combustible.id;



CREATE TABLE IF NOT EXISTS vehiculos.tipo_vehiculo (
    id bigint NOT NULL,
    descripcion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.tipo_vehiculo REPLICA IDENTITY FULL;


ALTER TABLE vehiculos.tipo_vehiculo OWNER TO postgres;


CREATE SEQUENCE IF NOT EXISTS vehiculos.tipo_vehiculo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.tipo_vehiculo_id_seq OWNER TO postgres;


ALTER SEQUENCE vehiculos.tipo_vehiculo_id_seq OWNED BY vehiculos.tipo_vehiculo.id;



CREATE TABLE IF NOT EXISTS vehiculos.vehiculo (
    id bigint NOT NULL,
    color character varying,
    chapa character varying,
    documentacion boolean DEFAULT false,
    refrigerado boolean DEFAULT false,
    nuevo boolean DEFAULT true,
    fecha_adquisicion timestamp without time zone,
    tipo_vehiculo bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    anho numeric,
    modelo_id bigint,
    ultima_latitud double precision,
    ultima_longitud double precision,
    ultima_fecha_reporte timestamp without time zone,
    ignicion_actual boolean DEFAULT false,
    km_virtual numeric(19,2) DEFAULT 0,
    propietario_id bigint,
    identificador_interno character varying(100),
    valor_estimado numeric(19,2),
    valor_estimado_pyg numeric,
    valor_estimado_brl numeric
);

ALTER TABLE ONLY vehiculos.vehiculo REPLICA IDENTITY FULL;


ALTER TABLE vehiculos.vehiculo OWNER TO postgres;


CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_adjuntos (
    vehiculo_id bigint NOT NULL,
    imagenes_vehiculo text,
    imagenes_documentos text
);


ALTER TABLE vehiculos.vehiculo_adjuntos OWNER TO franco;


CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_especificaciones (
    vehiculo_id bigint NOT NULL,
    primer_kilometraje numeric(15,2),
    capacidad_kg numeric(15,2),
    capacidad_pasajeros integer,
    tipo_combustible_id bigint,
    chasis character varying(255),
    aire_acondicionado boolean,
    mantenimiento_motor_intervalo integer,
    mantenimiento_caja_intervalo integer
);


ALTER TABLE vehiculos.vehiculo_especificaciones OWNER TO franco;


CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_finanzas (
    vehiculo_id bigint NOT NULL,
    situacion_pago character varying(50),
    proveedor_id bigint,
    moneda_id bigint,
    monto_total numeric(15,2),
    monto_ya_pagado numeric(15,2),
    cantidad_cuotas integer,
    cantidad_cuotas_pagadas integer,
    dia_vencimiento integer
);


ALTER TABLE vehiculos.vehiculo_finanzas OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS vehiculos.vehiculo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.vehiculo_id_seq OWNER TO postgres;


ALTER SEQUENCE vehiculos.vehiculo_id_seq OWNED BY vehiculos.vehiculo.id;



CREATE TABLE IF NOT EXISTS vehiculos.vehiculo_sucursal (
    id bigint NOT NULL,
    sucursal_id bigint,
    vehiculo_id bigint,
    responsable_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.vehiculo_sucursal REPLICA IDENTITY FULL;


ALTER TABLE vehiculos.vehiculo_sucursal OWNER TO postgres;


CREATE SEQUENCE IF NOT EXISTS vehiculos.vehiculo_sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE vehiculos.vehiculo_sucursal_id_seq OWNER TO postgres;


ALTER SEQUENCE vehiculos.vehiculo_sucursal_id_seq OWNED BY vehiculos.vehiculo_sucursal.id;



ALTER TABLE ONLY activos.ente ALTER COLUMN id SET DEFAULT nextval('activos.ente_id_seq'::regclass);



ALTER TABLE ONLY activos.ente_archivo ALTER COLUMN id SET DEFAULT nextval('activos.ente_archivo_id_seq'::regclass);



ALTER TABLE ONLY activos.ente_sucursal ALTER COLUMN id SET DEFAULT nextval('activos.ente_sucursal_id_seq'::regclass);



ALTER TABLE ONLY activos.familia_mueble ALTER COLUMN id SET DEFAULT nextval('activos.familia_mueble_id_seq'::regclass);



ALTER TABLE ONLY activos.inmueble ALTER COLUMN id SET DEFAULT nextval('activos.inmueble_id_seq'::regclass);



ALTER TABLE ONLY activos.mueble ALTER COLUMN id SET DEFAULT nextval('activos.mueble_id_seq'::regclass);



ALTER TABLE ONLY activos.tipo_mueble ALTER COLUMN id SET DEFAULT nextval('activos.tipo_mueble_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.dispositivo_gps ALTER COLUMN id SET DEFAULT nextval('vehiculos.dispositivo_gps_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.marca ALTER COLUMN id SET DEFAULT nextval('vehiculos.marca_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.modelo ALTER COLUMN id SET DEFAULT nextval('vehiculos.modelo_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.telemetria ALTER COLUMN id SET DEFAULT nextval('vehiculos.telemetria_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.tipo_combustible ALTER COLUMN id SET DEFAULT nextval('vehiculos.tipo_combustible_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.tipo_vehiculo ALTER COLUMN id SET DEFAULT nextval('vehiculos.tipo_vehiculo_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.vehiculo ALTER COLUMN id SET DEFAULT nextval('vehiculos.vehiculo_id_seq'::regclass);



ALTER TABLE ONLY vehiculos.vehiculo_sucursal ALTER COLUMN id SET DEFAULT nextval('vehiculos.vehiculo_sucursal_id_seq'::regclass);



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_archivo_pkey') THEN
        ALTER TABLE activos.ente_archivo ADD CONSTRAINT ente_archivo_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_pkey') THEN
        ALTER TABLE activos.ente ADD CONSTRAINT ente_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_sucursal_ente_id_sucursal_id_key') THEN
        ALTER TABLE activos.ente_sucursal ADD CONSTRAINT ente_sucursal_ente_id_sucursal_id_key UNIQUE (ente_id, sucursal_id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_sucursal_pkey') THEN
        ALTER TABLE activos.ente_sucursal ADD CONSTRAINT ente_sucursal_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'familia_mueble_pkey') THEN
        ALTER TABLE activos.familia_mueble ADD CONSTRAINT familia_mueble_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inmueble_pkey') THEN
        ALTER TABLE activos.inmueble ADD CONSTRAINT inmueble_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'mueble_pkey') THEN
        ALTER TABLE activos.mueble ADD CONSTRAINT mueble_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tipo_mueble_pkey') THEN
        ALTER TABLE activos.tipo_mueble ADD CONSTRAINT tipo_mueble_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dispositivo_gps_imei_key') THEN
        ALTER TABLE vehiculos.dispositivo_gps ADD CONSTRAINT dispositivo_gps_imei_key UNIQUE (imei);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dispositivo_gps_pkey') THEN
        ALTER TABLE vehiculos.dispositivo_gps ADD CONSTRAINT dispositivo_gps_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'marca_pkey') THEN
        ALTER TABLE vehiculos.marca ADD CONSTRAINT marca_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'modelo_pkey') THEN
        ALTER TABLE vehiculos.modelo ADD CONSTRAINT modelo_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'telemetria_pkey') THEN
        ALTER TABLE vehiculos.telemetria ADD CONSTRAINT telemetria_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tipo_combustible_pkey') THEN
        ALTER TABLE vehiculos.tipo_combustible ADD CONSTRAINT tipo_combustible_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tipo_vehiculo_pkey') THEN
        ALTER TABLE vehiculos.tipo_vehiculo ADD CONSTRAINT tipo_vehiculo_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_adjuntos_pkey') THEN
        ALTER TABLE vehiculos.vehiculo_adjuntos ADD CONSTRAINT vehiculo_adjuntos_pkey PRIMARY KEY (vehiculo_id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_especificaciones_pkey') THEN
        ALTER TABLE vehiculos.vehiculo_especificaciones ADD CONSTRAINT vehiculo_especificaciones_pkey PRIMARY KEY (vehiculo_id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_finanzas_pkey') THEN
        ALTER TABLE vehiculos.vehiculo_finanzas ADD CONSTRAINT vehiculo_finanzas_pkey PRIMARY KEY (vehiculo_id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_pkey') THEN
        ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT vehiculo_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_sucursal_pkey') THEN
        ALTER TABLE vehiculos.vehiculo_sucursal ADD CONSTRAINT vehiculo_sucursal_pkey PRIMARY KEY (id);
    END IF;
END$$;



CREATE INDEX IF NOT EXISTS idx_dispositivo_gps_activo_posicion ON vehiculos.dispositivo_gps USING btree (activo) WHERE ((ultima_latitud IS NOT NULL) AND (activo = true));



CREATE INDEX IF NOT EXISTS idx_dispositivo_gps_imei ON vehiculos.dispositivo_gps USING btree (imei);



CREATE INDEX IF NOT EXISTS idx_telemetria_dispositivo_fecha ON vehiculos.telemetria USING btree (dispositivo_id, fecha_gps DESC);



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_archivo_ente_id_fkey') THEN
        ALTER TABLE activos.ente_archivo ADD CONSTRAINT ente_archivo_ente_id_fkey FOREIGN KEY (ente_id) REFERENCES activos.ente(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_sucursal_ente_id_fkey') THEN
        ALTER TABLE activos.ente_sucursal ADD CONSTRAINT ente_sucursal_ente_id_fkey FOREIGN KEY (ente_id) REFERENCES activos.ente(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ente_sucursal_usuario_id_fkey') THEN
        ALTER TABLE activos.ente_sucursal ADD CONSTRAINT ente_sucursal_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inmueble_moneda') THEN
        ALTER TABLE activos.inmueble ADD CONSTRAINT fk_inmueble_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inmueble_proveedor_id_fkey') THEN
        ALTER TABLE activos.inmueble ADD CONSTRAINT inmueble_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'mueble_familia_id_fkey') THEN
        ALTER TABLE activos.mueble ADD CONSTRAINT mueble_familia_id_fkey FOREIGN KEY (familia_id) REFERENCES activos.familia_mueble(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'mueble_tipo_mueble_id_fkey') THEN
        ALTER TABLE activos.mueble ADD CONSTRAINT mueble_tipo_mueble_id_fkey FOREIGN KEY (tipo_mueble_id) REFERENCES activos.tipo_mueble(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tipo_mueble_familia_mueble_id_fkey') THEN
        ALTER TABLE activos.tipo_mueble ADD CONSTRAINT tipo_mueble_familia_mueble_id_fkey FOREIGN KEY (familia_mueble_id) REFERENCES activos.familia_mueble(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispositivo_vehiculo') THEN
        ALTER TABLE vehiculos.dispositivo_gps ADD CONSTRAINT fk_dispositivo_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_telemetria_dispositivo') THEN
        ALTER TABLE vehiculos.telemetria ADD CONSTRAINT fk_telemetria_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES vehiculos.dispositivo_gps(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'modelo_marca_id_fkey') THEN
        ALTER TABLE vehiculos.modelo ADD CONSTRAINT modelo_marca_id_fkey FOREIGN KEY (marca_id) REFERENCES vehiculos.marca(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_adjuntos_vehiculo_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_adjuntos ADD CONSTRAINT vehiculo_adjuntos_vehiculo_id_fkey FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE;
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_especificaciones_tipo_combustible_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_especificaciones ADD CONSTRAINT vehiculo_especificaciones_tipo_combustible_id_fkey FOREIGN KEY (tipo_combustible_id) REFERENCES vehiculos.tipo_combustible(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_especificaciones_vehiculo_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_especificaciones ADD CONSTRAINT vehiculo_especificaciones_vehiculo_id_fkey FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE;
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_finanzas_moneda_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_finanzas ADD CONSTRAINT vehiculo_finanzas_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_finanzas_proveedor_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_finanzas ADD CONSTRAINT vehiculo_finanzas_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_finanzas_vehiculo_id_fkey') THEN
        ALTER TABLE vehiculos.vehiculo_finanzas ADD CONSTRAINT vehiculo_finanzas_vehiculo_id_fkey FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id) ON DELETE CASCADE;
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_modelo_fk') THEN
        ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT vehiculo_modelo_fk FOREIGN KEY (modelo_id) REFERENCES vehiculos.modelo(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_tipo_vehiculo_fkey') THEN
        ALTER TABLE vehiculos.vehiculo ADD CONSTRAINT vehiculo_tipo_vehiculo_fkey FOREIGN KEY (tipo_vehiculo) REFERENCES vehiculos.tipo_vehiculo(id);
    END IF;
END$$;













CREATE TABLE IF NOT EXISTS financiero.gasolinera (
    id bigint NOT NULL,
    nombre character varying(255) NOT NULL,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);


ALTER TABLE financiero.gasolinera OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS financiero.gasolinera_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE financiero.gasolinera_id_seq OWNER TO franco;


ALTER SEQUENCE financiero.gasolinera_id_seq OWNED BY financiero.gasolinera.id;



CREATE TABLE IF NOT EXISTS financiero.gasto_continuo_config (
    id bigint NOT NULL,
    tipo_gasto_id bigint,
    dia_aviso integer,
    monto_sugerido numeric(19,2),
    moneda_id bigint,
    activo boolean DEFAULT true,
    creado_en timestamp without time zone DEFAULT now(),
    usuario_id bigint
);


ALTER TABLE financiero.gasto_continuo_config OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS financiero.gasto_continuo_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE financiero.gasto_continuo_config_id_seq OWNER TO franco;


ALTER SEQUENCE financiero.gasto_continuo_config_id_seq OWNED BY financiero.gasto_continuo_config.id;



CREATE TABLE IF NOT EXISTS financiero.gasto_grupo (
    id bigint NOT NULL,
    descripcion character varying(255),
    proveedor_id bigint,
    creado_en timestamp without time zone DEFAULT now(),
    usuario_id bigint
);


ALTER TABLE financiero.gasto_grupo OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS financiero.gasto_grupo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE financiero.gasto_grupo_id_seq OWNER TO franco;


ALTER SEQUENCE financiero.gasto_grupo_id_seq OWNED BY financiero.gasto_grupo.id;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname = 'estado_pre_gasto' AND n.nspname = 'financiero') THEN
        EXECUTE 'CREATE TYPE financiero.estado_pre_gasto AS ENUM (''PENDIENTE'', ''TRAMITE'', ''AUTORIZADO'', ''RECHAZADO'', ''COMPLETADO'', ''ENVIADO_A_TESORERIA'')';
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS financiero.pre_gasto (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    funcionario_id bigint,
    ente_id bigint,
    tipo_gasto_id bigint,
    descripcion character varying(500),
    moneda_id bigint,
    monto_solicitado numeric(15,2),
    sucursal_caja_id bigint,
    estado financiero.estado_pre_gasto DEFAULT 'PENDIENTE'::financiero.estado_pre_gasto,
    qr_token character varying(255),
    autorizado_por_id bigint,
    delegado_a_id bigint,
    motivo_rechazo character varying(500),
    monto_retirado numeric(15,2),
    monto_gastado numeric(15,2),
    saldo_devolver numeric(15,2),
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    solicitud_pago_id bigint,
    gasto_grupo_id bigint,
    beneficiario_proveedor_id bigint,
    beneficiario_persona_id bigint,
    fecha_vencimiento timestamp with time zone,
    nivel_urgencia character varying(50),
    observaciones character varying(1000),
    caja_id bigint,
    rindio_gasto boolean DEFAULT false,
    estado_rendicion character varying(20),
    fecha_rendicion timestamp with time zone,
    autorizado_en timestamp with time zone,
    rechazado_por_id bigint,
    rechazado_en timestamp with time zone
);


ALTER TABLE financiero.pre_gasto OWNER TO franco;


CREATE TABLE IF NOT EXISTS financiero.pre_gasto_detalle_finanzas (
    id bigint NOT NULL,
    pre_gasto_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    moneda_id bigint,
    forma_pago character varying(100),
    monto numeric(15,2),
    creado_en timestamp with time zone DEFAULT now()
);


ALTER TABLE financiero.pre_gasto_detalle_finanzas OWNER TO franco;


CREATE SEQUENCE IF NOT EXISTS financiero.pre_gasto_detalle_finanzas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE financiero.pre_gasto_detalle_finanzas_id_seq OWNER TO franco;


ALTER SEQUENCE financiero.pre_gasto_detalle_finanzas_id_seq OWNED BY financiero.pre_gasto_detalle_finanzas.id;



ALTER TABLE ONLY financiero.gasolinera ALTER COLUMN id SET DEFAULT nextval('financiero.gasolinera_id_seq'::regclass);



ALTER TABLE ONLY financiero.gasto_continuo_config ALTER COLUMN id SET DEFAULT nextval('financiero.gasto_continuo_config_id_seq'::regclass);



ALTER TABLE ONLY financiero.gasto_grupo ALTER COLUMN id SET DEFAULT nextval('financiero.gasto_grupo_id_seq'::regclass);



ALTER TABLE ONLY financiero.pre_gasto_detalle_finanzas ALTER COLUMN id SET DEFAULT nextval('financiero.pre_gasto_detalle_finanzas_id_seq'::regclass);



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'gasolinera_pkey') THEN
        ALTER TABLE financiero.gasolinera ADD CONSTRAINT gasolinera_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'gasto_continuo_config_pkey') THEN
        ALTER TABLE financiero.gasto_continuo_config ADD CONSTRAINT gasto_continuo_config_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'gasto_grupo_pkey') THEN
        ALTER TABLE financiero.gasto_grupo ADD CONSTRAINT gasto_grupo_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_detalle_finanzas_pkey') THEN
        ALTER TABLE financiero.pre_gasto_detalle_finanzas ADD CONSTRAINT pre_gasto_detalle_finanzas_pkey PRIMARY KEY (id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_pkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_pkey PRIMARY KEY (id, sucursal_id);
    END IF;
END$$;



CREATE INDEX IF NOT EXISTS idx_pre_gasto_autorizado_en ON financiero.pre_gasto USING btree (autorizado_en);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_caja_id ON financiero.pre_gasto USING btree (caja_id);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_detalle_finanzas_fk ON financiero.pre_gasto_detalle_finanzas USING btree (pre_gasto_id, sucursal_id);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_estado ON financiero.pre_gasto USING btree (estado);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_funcionario ON financiero.pre_gasto USING btree (funcionario_id);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_rechazado_en ON financiero.pre_gasto USING btree (rechazado_en);



CREATE INDEX IF NOT EXISTS idx_pre_gasto_sucursal ON financiero.pre_gasto USING btree (sucursal_caja_id);



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pre_gasto_rechazado_por') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT fk_pre_gasto_rechazado_por FOREIGN KEY (rechazado_por_id) REFERENCES personas.persona(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'gasto_continuo_config_moneda_id_fkey') THEN
        ALTER TABLE financiero.gasto_continuo_config ADD CONSTRAINT gasto_continuo_config_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'gasto_continuo_config_tipo_gasto_id_fkey') THEN
        ALTER TABLE financiero.gasto_continuo_config ADD CONSTRAINT gasto_continuo_config_tipo_gasto_id_fkey FOREIGN KEY (tipo_gasto_id) REFERENCES financiero.tipo_gasto(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_autorizado_por_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_autorizado_por_id_fkey FOREIGN KEY (autorizado_por_id) REFERENCES personas.persona(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_beneficiario_persona_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_beneficiario_persona_id_fkey FOREIGN KEY (beneficiario_persona_id) REFERENCES personas.persona(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_beneficiario_proveedor_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_beneficiario_proveedor_id_fkey FOREIGN KEY (beneficiario_proveedor_id) REFERENCES personas.proveedor(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_delegado_a_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_delegado_a_id_fkey FOREIGN KEY (delegado_a_id) REFERENCES personas.persona(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_detalle_finanzas_moneda_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto_detalle_finanzas ADD CONSTRAINT pre_gasto_detalle_finanzas_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_detalle_finanzas_pre_gasto_id_sucursal_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto_detalle_finanzas ADD CONSTRAINT pre_gasto_detalle_finanzas_pre_gasto_id_sucursal_id_fkey FOREIGN KEY (pre_gasto_id, sucursal_id) REFERENCES financiero.pre_gasto(id, sucursal_id) ON DELETE CASCADE;
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_funcionario_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_funcionario_id_fkey FOREIGN KEY (funcionario_id) REFERENCES personas.persona(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_gasto_grupo_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_gasto_grupo_id_fkey FOREIGN KEY (gasto_grupo_id) REFERENCES financiero.gasto_grupo(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_moneda_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_sucursal_caja_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_sucursal_caja_id_fkey FOREIGN KEY (sucursal_caja_id) REFERENCES empresarial.sucursal(id);
    END IF;
END$$;



DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_tipo_gasto_id_fkey') THEN
        ALTER TABLE financiero.pre_gasto ADD CONSTRAINT pre_gasto_tipo_gasto_id_fkey FOREIGN KEY (tipo_gasto_id) REFERENCES financiero.tipo_gasto(id);
    END IF;
END$$;






CREATE TABLE IF NOT EXISTS operaciones.hoja_ruta (
    id BIGSERIAL PRIMARY KEY,
    vehiculo_id BIGINT NOT NULL,
    chofer_id BIGINT NOT NULL,
    fecha_salida TIMESTAMP WITHOUT TIME ZONE,
    fecha_llegada TIMESTAMP WITHOUT TIME ZONE,
    km_salida NUMERIC(19,2),
    km_llegada NUMERIC(19,2),
    estado VARCHAR(255),
    creado_en TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operaciones.hoja_ruta_acompanante (
    hoja_ruta_id BIGINT NOT NULL,
    persona_id BIGINT NOT NULL,
    PRIMARY KEY (hoja_ruta_id, persona_id)
);

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hoja_ruta_vehiculo') THEN 
        ALTER TABLE operaciones.hoja_ruta ADD CONSTRAINT fk_hoja_ruta_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id);
    END IF; 
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hoja_ruta_chofer') THEN 
        ALTER TABLE operaciones.hoja_ruta ADD CONSTRAINT fk_hoja_ruta_chofer FOREIGN KEY (chofer_id) REFERENCES personas.persona(id);
    END IF; 
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hoja_ruta_acompanante_hoja_ruta') THEN 
        ALTER TABLE operaciones.hoja_ruta_acompanante ADD CONSTRAINT fk_hoja_ruta_acompanante_hoja_ruta FOREIGN KEY (hoja_ruta_id) REFERENCES operaciones.hoja_ruta(id);
    END IF; 
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hoja_ruta_acompanante_persona') THEN 
        ALTER TABLE operaciones.hoja_ruta_acompanante ADD CONSTRAINT fk_hoja_ruta_acompanante_persona FOREIGN KEY (persona_id) REFERENCES personas.persona(id);
    END IF; 
END $$;


ALTER TABLE operaciones.transferencia 
ADD COLUMN IF NOT EXISTS hoja_ruta_id BIGINT;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transferencia_hoja_ruta') THEN 
        ALTER TABLE operaciones.transferencia ADD CONSTRAINT fk_transferencia_hoja_ruta FOREIGN KEY (hoja_ruta_id) REFERENCES operaciones.hoja_ruta(id);
    END IF; 
END $$;

UPDATE operaciones.transferencia t
SET hoja_ruta_id = hr_sub.id
FROM (
    SELECT DISTINCT ON (u.id) 
        u.id as usuario_id, 
        hr.id
    FROM personas.usuario u
    JOIN personas.persona p ON u.persona_id = p.id
    JOIN operaciones.hoja_ruta hr ON hr.chofer_id = p.id
    ORDER BY u.id, hr.creado_en DESC
) as hr_sub
WHERE t.usuario_transporte_id = hr_sub.usuario_id
AND t.hoja_ruta_id IS NULL
AND t.usuario_transporte_id IS NOT NULL;
UPDATE operaciones.transferencia t
SET hoja_ruta_id = NULL
FROM operaciones.hoja_ruta hr
WHERE t.hoja_ruta_id = hr.id
AND (
    NOT EXISTS (
        SELECT 1 FROM personas.usuario u 
        WHERE u.id = t.usuario_transporte_id 
        AND u.persona_id = hr.chofer_id
    )
    OR
    t.creado_en < hr.creado_en - INTERVAL '24 hours'
    OR
    t.creado_en > hr.creado_en + INTERVAL '48 hours'
);
ALTER TABLE financiero.tipo_gasto ADD COLUMN IF NOT EXISTS tipo_naturaleza VARCHAR;

ALTER TABLE financiero.tipo_gasto ADD COLUMN IF NOT EXISTS afecta_finanzas_activo boolean DEFAULT false;
ALTER TABLE financiero.tipo_gasto ADD COLUMN IF NOT EXISTS es_pago_cuota_activo BOOLEAN DEFAULT FALSE;
ALTER TABLE financiero.tipo_gasto
    ADD COLUMN IF NOT EXISTS modulo_padre VARCHAR(30);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'financiero'
          AND table_name = 'tipo_gasto'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'financiero'
              AND table_name = 'tipo_gasto'
              AND column_name = 'activo_en_sucursales'
        ) THEN
            ALTER TABLE financiero.tipo_gasto ADD COLUMN activo_en_sucursales BOOLEAN;
        END IF;

        ALTER TABLE financiero.tipo_gasto ALTER COLUMN activo_en_sucursales SET DEFAULT TRUE;
        UPDATE financiero.tipo_gasto SET activo_en_sucursales = TRUE WHERE activo_en_sucursales IS NULL;
        ALTER TABLE financiero.tipo_gasto ALTER COLUMN activo_en_sucursales SET NOT NULL;
    END IF;
END $$;

-- Ajustes para la tabla vehiculo preexistente (Bodega3)
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_latitud double precision;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_longitud double precision;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_fecha_reporte timestamp without time zone;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ignicion_actual boolean DEFAULT false;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS km_virtual numeric(19,2) DEFAULT 0;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS propietario_id bigint;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS identificador_interno character varying(100);
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado numeric(19,2);
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_pyg numeric;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_brl numeric;

ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS primer_kilometraje CASCADE;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS imagenes_documentos CASCADE;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS imagenes_vehiculo CASCADE;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS capacidad_kg CASCADE;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS capacidad_pasajeros CASCADE;
