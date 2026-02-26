CREATE TABLE administrativo.horario (
    id bigserial PRIMARY KEY,
    descripcion varchar(255),
    hora_entrada time,
    hora_salida time,
    tolerancia_minutos integer DEFAULT 0,
    inicio_descanso time,
    fin_descanso time,
    usuario_id int8 REFERENCES personas.usuario(id),
    creado_en timestamp DEFAULT now()
);

ALTER TABLE personas.funcionario
ADD COLUMN horario_id int8 REFERENCES administrativo.horario(id);
