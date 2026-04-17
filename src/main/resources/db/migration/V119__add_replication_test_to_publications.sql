-- Add replication_test to central_pub (MAIN_TO_ALL direction) and to all central_*_filialX_pub publications.
-- Publication names are dynamic (prefixed with DB name), so we discover them.

-- 1. Add to central_pub if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'central_pub') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = 'central_pub' AND tablename = 'replication_test'
        ) THEN
            EXECUTE 'ALTER PUBLICATION central_pub ADD TABLE configuraciones.replication_test';
            RAISE NOTICE 'Added configuraciones.replication_test to central_pub';
        END IF;
    END IF;
END $$;

-- 2. Add to all central_*_filialX_pub publications (with sucursal_id WHERE clause)
DO $$
DECLARE
    pub RECORD;
    suc_id TEXT;
BEGIN
    FOR pub IN
        SELECT pubname FROM pg_publication
        WHERE pubname LIKE 'central_%_filial%_pub'
        ORDER BY pubname
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = pub.pubname AND tablename = 'replication_test'
        ) THEN
            -- Extract sucursal_id: pattern is central_{dbname}_filial{id}_pub
            suc_id := regexp_replace(pub.pubname, '^central_.*_filial(\d+)_pub$', '\1');
            IF suc_id ~ '^\d+$' THEN
                EXECUTE format(
                    'ALTER PUBLICATION %I ADD TABLE configuraciones.replication_test WHERE (sucursal_id = %s)',
                    pub.pubname, suc_id
                );
                RAISE NOTICE 'Added configuraciones.replication_test to % (sucursal_id=%)', pub.pubname, suc_id;
            END IF;
        END IF;
    END LOOP;
END $$;
