-- administrativo.jornada deja de bajar del central a las filiales.
--
-- Bajaba por MAIN_TO_ALL desde V117, pero nadie la lee del otro lado: tanto la lista de
-- horarios del desktop como el marcado --desktop, mobile y PWA-- consultan siempre al central
-- (clientName "servidor" en el cliente de Apollo). En la filial la tabla solo se llena.
--
-- Y se llena rota: la marcacion no baja --es BRANCH_TO_MAIN-- asi que cada jornada que llega
-- apunta a filas de administrativo.marcacion que en esa base no existen ni van a existir. La FK
-- no lo impide porque los workers de replicacion corren con session_replication_role = replica,
-- donde los triggers RI no se disparan. Cualquier consulta que cargue esas jornadas revienta con
-- EntityNotFoundException: la asociacion es eager y se cae la consulta entera, no la fila.
--
-- Ademas el backend de la filial escribe jornada por su cuenta (MarcacionService.procesarJornada)
-- con la misma PK (id, sucursal_id) que usa el central, asi que las dos fuentes pueden chocar y
-- cortar la suscripcion. Sin la bajada, la tabla queda con un solo escritor en cada base.
--
-- No se toca ni una fila de datos: las jornadas que las filiales ya tienen quedan donde estan,
-- simplemente dejan de crecer. Tampoco cambia nada del lado del central, que sigue siendo el
-- unico que las calcula y el unico al que se le consultan.
--
-- Reversible: alcanza con volver enabled a true y correr "Sincronizar publicaciones", que la
-- agrega de nuevo a central_pub.

UPDATE configuraciones.replication_table
   SET enabled = false
 WHERE table_name = 'administrativo.jornada';

-- Sacarla de central_pub. Es lo que corta el envio: desde aca el publisher no manda mas cambios
-- de esta tabla a ninguna filial. enabled = false evita que el sync la vuelva a agregar
-- (getMainToAllTableNames solo mira las habilitadas).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication_tables
         WHERE pubname = 'central_pub'
           AND schemaname = 'administrativo'
           AND tablename = 'jornada'
    ) THEN
        BEGIN
            ALTER PUBLICATION central_pub DROP TABLE administrativo.jornada;
            RAISE NOTICE 'administrativo.jornada sacada de central_pub';
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'No se pudo sacar administrativo.jornada de central_pub: %', SQLERRM;
        END;
    ELSE
        RAISE NOTICE 'administrativo.jornada no estaba en central_pub, nada que hacer';
    END IF;
END;
$$;

-- Del lado de cada filial queda un ALTER SUBSCRIPTION ... REFRESH PUBLICATION para que la saque
-- de pg_subscription_rel. No es urgente --el publisher ya dejo de mandarla-- y lo hacen solos el
-- boton "Sincronizar publicaciones" y ReplicationRefreshScheduler.


-- Correccion de lo que dice V216.5. Su encabezado afirma que administrativo.marcacion "se
-- replica en las dos direcciones: sube por BRANCH_TO_MAIN (ver V112) y baja por las
-- publicaciones central_filialN_pub". La segunda mitad es falsa y lo fue siempre: marcacion no
-- esta en central_pub ni en ninguna central_*_filialN_pub, asi que del central no baja nada.
-- Esa migracion no se toca --Flyway compara checksums y ya corrio en todas las bases--, pero el
-- dato correcto queda aca y en el comentario de la tabla, que es donde se lo va a buscar.
--
-- Que implica para una columna nueva en marcacion: el publisher es la FILIAL, no el central. Al
-- central le pueden sobrar columnas (el suscriptor ignora las que no tiene mapeadas) pero no le
-- pueden faltar, o su suscripcion se corta con "missing replicated column". Correr la migracion
-- en las filiales sigue siendo necesario, pero por el codigo que escribe esas columnas, no por
-- una bajada que no existe.
COMMENT ON TABLE administrativo.marcacion IS
    'Solo sube: BRANCH_TO_MAIN (filial -> central). No baja a las filiales, no esta en central_pub ni en las central_*_filialN_pub. El publisher es la filial, asi que una columna nueva tiene que existir en el central antes que en la filial. Lo que dice V216.5 sobre que baja por central_filialN_pub es incorrecto.';
