CREATE TABLE IF NOT EXISTS operaciones.hoja_ruta (
    id BIGSERIAL PRIMARY KEY,
    vehiculo_id BIGINT NOT NULL,
    chofer_id BIGINT NOT NULL,
    fecha_salida TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    fecha_llegada TIMESTAMP WITHOUT TIME ZONE,
    km_salida NUMERIC(19,2),
    km_llegada NUMERIC(19,2),
    estado VARCHAR(20) DEFAULT 'EN_RUTA',
    creado_en TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT fk_hoja_ruta_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id),
    CONSTRAINT fk_hoja_ruta_chofer FOREIGN KEY (chofer_id) REFERENCES personas.persona(id)
);
CREATE TABLE IF NOT EXISTS operaciones.hoja_ruta_acompanante (
    hoja_ruta_id BIGINT NOT NULL,
    persona_id BIGINT NOT NULL,
    PRIMARY KEY (hoja_ruta_id, persona_id),
    CONSTRAINT fk_hra_hoja_ruta FOREIGN KEY (hoja_ruta_id) REFERENCES operaciones.hoja_ruta(id),
    CONSTRAINT fk_hra_persona FOREIGN KEY (persona_id) REFERENCES personas.persona(id)
);

ALTER TABLE operaciones.transferencia
ADD COLUMN IF NOT EXISTS hoja_ruta_id BIGINT,
ADD COLUMN IF NOT EXISTS orden_entrega INTEGER;

ALTER TABLE operaciones.transferencia
ADD CONSTRAINT fk_transferencia_hoja_ruta FOREIGN KEY (hoja_ruta_id) REFERENCES operaciones.hoja_ruta(id);

CREATE TABLE IF NOT EXISTS vehiculos.dispositivo_gps (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(50) NOT NULL UNIQUE,
    vehiculo_id BIGINT,
    modelo_tracker VARCHAR(20),
    sim_numero VARCHAR(20),
    activo BOOLEAN DEFAULT TRUE,
    creado_em TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_dispositivo_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculos.vehiculo(id)
);

CREATE TABLE IF NOT EXISTS vehiculos.telemetria (
    id BIGSERIAL PRIMARY KEY,
    dispositivo_id BIGINT NOT NULL,
    fecha_servidor TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    fecha_gps TIMESTAMP WITHOUT TIME ZONE,
    latitud DOUBLE PRECISION,
    longitud DOUBLE PRECISION,
    velocidad INTEGER,
    direccion INTEGER,
    ignicion BOOLEAN,
    alarma VARCHAR(50),
    json_data JSONB,
    CONSTRAINT fk_telemetria_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES vehiculos.dispositivo_gps(id)
);

CREATE INDEX IF NOT EXISTS idx_telemetria_dispositivo_fecha ON vehiculos.telemetria(dispositivo_id, fecha_gps DESC);

ALTER TABLE vehiculos.vehiculo
ADD COLUMN IF NOT EXISTS ultima_latitud DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS ultima_longitud DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS ultima_fecha_reporte TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS ignicion_actual BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS km_virtual NUMERIC(19,2) DEFAULT 0;
