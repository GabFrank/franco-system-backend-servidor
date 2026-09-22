-- Reparto de ids en las cuatro tablas de facturacion electronica: el central genera impares y
-- cada filial pares (migracion espejo V96.1 del filial). Extiende a documento_electronico,
-- lote_de, evento_cancelacion_de y evento_nominacion_de el esquema que V223.1 ya aplico al resto.
--
-- Por que hacen falta: estas tablas viajan en las dos direcciones. El filial las publica al
-- central (filialN_pub) y el central baja a cada filial las filas de su sucursal. Hoy las dos
-- puntas generan ids con secuencias planas en el mismo espacio: basta que coincidan para que el
-- INSERT replicado choque con la clave primaria y se corte la suscripcion entera (issue #287, mismo
-- mecanismo). Con las notas electronicas el central pasa a escribir en documento_electronico todos
-- los dias, asi que la coincidencia deja de ser cuestion de suerte.
--
-- ORDEN DE DESPLIEGUE: el filial va primero (su V96.1 lo pasa a pares). Entre los dos deploys no
-- hay riesgo nuevo: los espacios de ids siguen siendo los de hoy.

-- 1. Las secuencias del central pasan a generar impares, siguiendo desde el mayor valor ya usado.
--    El MAX(id) incluye las filas que llegaron por replicacion, asi que el central arranca por
--    encima de todo lo existente.
DO $$
DECLARE
    t       record;
    maximo  bigint;
    proximo bigint;
BEGIN
    FOR t IN SELECT * FROM (VALUES
            ('financiero.documento_electronico_id_seq', 'financiero.documento_electronico'),
            ('financiero.lote_de_id_seq', 'financiero.lote_de'),
            ('financiero.evento_cancelacion_de_id_seq', 'financiero.evento_cancelacion_de'),
            ('financiero.evento_nominacion_de_id_seq', 'financiero.evento_nominacion_de')
        ) AS v(secuencia, tabla)
    LOOP
        IF to_regclass(t.secuencia) IS NULL OR to_regclass(t.tabla) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('SELECT GREATEST(COALESCE((SELECT MAX(id) FROM %s), 0), (SELECT last_value FROM %s))',
                       t.tabla, t.secuencia) INTO maximo;
        proximo := maximo + 1;
        IF proximo % 2 = 0 THEN
            proximo := proximo + 1;
        END IF;
        EXECUTE format('ALTER SEQUENCE %s INCREMENT BY 2', t.secuencia);
        PERFORM setval(t.secuencia::regclass, proximo, false);
    END LOOP;
END;
$$;

-- 2. La base rechaza un INSERT local con id par. La funcion ya existe desde V223.1.
--    Los workers de replicacion corren con session_replication_role = replica y NO disparan
--    triggers comunes, asi que las filas que suben del filial (pares) siguen entrando.
DO $$
DECLARE
    tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY[
            'financiero.documento_electronico',
            'financiero.lote_de',
            'financiero.evento_cancelacion_de',
            'financiero.evento_nominacion_de'
        ]
    LOOP
        IF to_regclass(tabla) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('DROP TRIGGER IF EXISTS rechazar_id_de_filial ON %s', tabla);
        EXECUTE format('CREATE TRIGGER rechazar_id_de_filial BEFORE INSERT ON %s '
                           || 'FOR EACH ROW EXECUTE FUNCTION configuraciones.rechazar_id_de_filial()', tabla);
    END LOOP;
END;
$$;
