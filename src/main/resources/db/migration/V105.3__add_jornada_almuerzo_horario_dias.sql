
-- Agregar columnas para control de almuerzo en Jornada
ALTER TABLE administrativo.jornada ADD COLUMN marcacion_salida_almuerzo_id BIGINT;
ALTER TABLE administrativo.jornada ADD COLUMN marcacion_entrada_almuerzo_id BIGINT;

ALTER TABLE administrativo.jornada 
    ADD CONSTRAINT fk_jornada_salida_almuerzo 
    FOREIGN KEY (marcacion_salida_almuerzo_id) 
    REFERENCES administrativo.marcacion(id);

ALTER TABLE administrativo.jornada 
    ADD CONSTRAINT fk_jornada_entrada_almuerzo 
    FOREIGN KEY (marcacion_entrada_almuerzo_id) 
    REFERENCES administrativo.marcacion(id);

-- Agregar columnas para turno y días en Horario
ALTER TABLE administrativo.horario ADD COLUMN turno VARCHAR(20);

-- Crear tabla para almacenar los días del horario (ElementCollection)
CREATE TABLE administrativo.horario_dias (
    horario_id BIGINT NOT NULL,
    dia VARCHAR(20)
);

ALTER TABLE administrativo.horario_dias 
    ADD CONSTRAINT fk_horario_dias_horario 
    FOREIGN KEY (horario_id) 
    REFERENCES administrativo.horario(id);
