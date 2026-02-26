SELECT setval('administrativo.marcacion_id_seq', 
    (SELECT CASE 
        WHEN (SELECT last_value FROM administrativo.marcacion_id_seq) % 2 = 0 
        THEN (SELECT last_value FROM administrativo.marcacion_id_seq) + 1
        ELSE (SELECT last_value FROM administrativo.marcacion_id_seq)
    END), 
    false);
