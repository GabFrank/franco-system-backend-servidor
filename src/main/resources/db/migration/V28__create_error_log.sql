CREATE TYPE configuraciones.tipo_error AS ENUM (
    'APLICACION',
    'BASE_DE_DATOS'
);

CREATE TYPE configuraciones.nivel_error AS ENUM (
    'INFO',
    'ALERTA',
    'PELIGRO',
    'CRITICO'
);

CREATE TABLE configuraciones.error_log (
    id BIGSERIAL PRIMARY KEY,
    sucursal_id int8 NOT NULL,
    tipo configuraciones.tipo_error NOT NULL,
    mensaje TEXT NOT NULL,
    nivel configuraciones.nivel_error NOT NULL,
    fecha_primera_ocurrencia TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_ultima_ocurrencia TIMESTAMP,
    cantidad_ocurrencias INTEGER DEFAULT 1,
    CONSTRAINT fk_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id)  -- Adjust this to the correct table/column for sucursal
);
