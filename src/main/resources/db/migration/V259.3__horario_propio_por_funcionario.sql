-- #523: el horario de cada funcionario pasa a ser propio de su usuario (horario.usuario_id).
--
-- Antes funcionario.horario_id apuntaba a un horario compartido (el 6 lo usaban 18
-- funcionarios y figuraba del usuario que los cargo). Ahora la marcacion mira primero los
-- horarios propios del usuario que marca, asi que a quien todavia no tiene ninguno se le
-- copia el horario compartido como propio, con sus dias, y se lo apunta a la copia.
--
-- Se llega al usuario por la persona: funcionario.usuario_id es "creado por".
-- Quien ya tiene horarios propios no se toca. Solo INSERT y UPDATE: los horarios
-- compartidos quedan como estaban.

DO $$
DECLARE
    r RECORD;
    nuevo_id BIGINT;
BEGIN
    FOR r IN
        SELECT DISTINCT ON (u.id)
               f.id AS funcionario_id, u.id AS usuario_id, h.id AS horario_id,
               h.descripcion, h.hora_entrada, h.hora_salida, h.tolerancia_minutos,
               h.inicio_descanso, h.fin_descanso, h.turno
        FROM personas.funcionario f
        JOIN personas.usuario u ON u.persona_id = f.persona_id
        JOIN administrativo.horario h ON h.id = f.horario_id
        WHERE h.usuario_id IS DISTINCT FROM u.id
          AND h.hora_entrada IS NOT NULL
        ORDER BY u.id, f.id
    LOOP
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM administrativo.horario p
            WHERE p.usuario_id = r.usuario_id AND p.hora_entrada IS NOT NULL);

        INSERT INTO administrativo.horario (descripcion, hora_entrada, hora_salida, tolerancia_minutos,
                                            inicio_descanso, fin_descanso, turno, usuario_id, creado_en)
        VALUES (r.descripcion, r.hora_entrada, r.hora_salida, r.tolerancia_minutos,
                r.inicio_descanso, r.fin_descanso, r.turno, r.usuario_id, now())
        RETURNING id INTO nuevo_id;

        INSERT INTO administrativo.horario_dias (horario_id, dia)
        SELECT nuevo_id, d.dia FROM administrativo.horario_dias d WHERE d.horario_id = r.horario_id;

        UPDATE personas.funcionario SET horario_id = nuevo_id WHERE id = r.funcionario_id;
    END LOOP;
END $$;
