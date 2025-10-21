-- Migración V77: Claves primarias compuestas para replicación
-- Agrega sucursal_id y modifica PKs a (id, sucursal_id)
-- Elimina tabla legacy evento_dte

-- PASO 0: Eliminar tabla evento_dte (legacy)
DROP TABLE IF EXISTS financiero.evento_dte CASCADE;

-- Eliminar FKs específicas que conocemos
ALTER TABLE financiero.evento_cancelacion_de DROP CONSTRAINT IF EXISTS fk_evento_cancelacion_documento;
ALTER TABLE financiero.evento_nominacion_de DROP CONSTRAINT IF EXISTS fk_evento_nominacion_documento;
ALTER TABLE financiero.documento_electronico DROP CONSTRAINT IF EXISTS documento_electronico_lote_de_fk;
ALTER TABLE financiero.documento_electronico DROP CONSTRAINT IF EXISTS documento_electronico_factura_legal_fk;
ALTER TABLE financiero.documento_electronico DROP CONSTRAINT IF EXISTS fk_documento_electronico_factura_legal;

-- Eliminar PKs con CASCADE (para dependencias restantes)
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname INTO constraint_name FROM pg_constraint 
    WHERE conrelid = 'financiero.evento_cancelacion_de'::regclass AND contype = 'p';
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE financiero.evento_cancelacion_de DROP CONSTRAINT ' || constraint_name || ' CASCADE';
    END IF;
    
    SELECT conname INTO constraint_name FROM pg_constraint 
    WHERE conrelid = 'financiero.evento_nominacion_de'::regclass AND contype = 'p';
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE financiero.evento_nominacion_de DROP CONSTRAINT ' || constraint_name || ' CASCADE';
    END IF;
    
    SELECT conname INTO constraint_name FROM pg_constraint 
    WHERE conrelid = 'financiero.lote_de'::regclass AND contype = 'p';
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE financiero.lote_de DROP CONSTRAINT ' || constraint_name || ' CASCADE';
    END IF;
    
    SELECT conname INTO constraint_name FROM pg_constraint 
    WHERE conrelid = 'financiero.documento_electronico'::regclass AND contype = 'p';
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE financiero.documento_electronico DROP CONSTRAINT ' || constraint_name || ' CASCADE';
    END IF;
END $$;

-- PASO 1: evento_cancelacion_de
ALTER TABLE financiero.evento_cancelacion_de ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;

UPDATE financiero.evento_cancelacion_de ec
SET sucursal_id = de.sucursal_id
FROM financiero.documento_electronico de
WHERE ec.documento_electronico_id = de.id AND ec.sucursal_id IS NULL;

DELETE FROM financiero.evento_cancelacion_de WHERE sucursal_id IS NULL;

ALTER TABLE financiero.evento_cancelacion_de ALTER COLUMN sucursal_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_evento_cancelacion_de_sucursal_id ON financiero.evento_cancelacion_de(sucursal_id);
ALTER TABLE financiero.evento_cancelacion_de ADD CONSTRAINT evento_cancelacion_de_pkey PRIMARY KEY (id, sucursal_id);

-- PASO 2: evento_nominacion_de
ALTER TABLE financiero.evento_nominacion_de ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;

UPDATE financiero.evento_nominacion_de en
SET sucursal_id = de.sucursal_id
FROM financiero.documento_electronico de
WHERE en.documento_electronico_id = de.id AND en.sucursal_id IS NULL;

DELETE FROM financiero.evento_nominacion_de WHERE sucursal_id IS NULL;

ALTER TABLE financiero.evento_nominacion_de ALTER COLUMN sucursal_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_evento_nominacion_de_sucursal_id ON financiero.evento_nominacion_de(sucursal_id);
ALTER TABLE financiero.evento_nominacion_de ADD CONSTRAINT evento_nominacion_de_pkey PRIMARY KEY (id, sucursal_id);

-- PASO 3: lote_de
ALTER TABLE financiero.lote_de ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;

UPDATE financiero.lote_de l
SET sucursal_id = (
    SELECT de.sucursal_id
    FROM financiero.documento_electronico de
    WHERE de.lote_de_id = l.id
    LIMIT 1
)
WHERE l.sucursal_id IS NULL;

DELETE FROM financiero.lote_de WHERE sucursal_id IS NULL;

ALTER TABLE financiero.lote_de ALTER COLUMN sucursal_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_lote_de_sucursal_id ON financiero.lote_de(sucursal_id);
ALTER TABLE financiero.lote_de ADD CONSTRAINT lote_de_pkey PRIMARY KEY (id, sucursal_id);

-- PASO 4: documento_electronico
ALTER TABLE financiero.documento_electronico ADD CONSTRAINT documento_electronico_pkey PRIMARY KEY (id, sucursal_id);

-- PASO 5: Recrear FKs con las nuevas PKs compuestas
-- FKs a sucursal
ALTER TABLE financiero.evento_cancelacion_de ADD CONSTRAINT fk_evento_cancelacion_de_sucursal 
    FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;
ALTER TABLE financiero.evento_nominacion_de ADD CONSTRAINT fk_evento_nominacion_de_sucursal 
    FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;
ALTER TABLE financiero.lote_de ADD CONSTRAINT fk_lote_de_sucursal 
    FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;

-- FKs entre tablas DE (ahora con claves compuestas)
ALTER TABLE financiero.evento_cancelacion_de ADD CONSTRAINT fk_evento_cancelacion_documento 
    FOREIGN KEY (documento_electronico_id, sucursal_id) REFERENCES financiero.documento_electronico(id, sucursal_id) ON DELETE CASCADE;
ALTER TABLE financiero.evento_nominacion_de ADD CONSTRAINT fk_evento_nominacion_documento 
    FOREIGN KEY (documento_electronico_id, sucursal_id) REFERENCES financiero.documento_electronico(id, sucursal_id) ON DELETE CASCADE;
ALTER TABLE financiero.documento_electronico ADD CONSTRAINT documento_electronico_lote_de_fk 
    FOREIGN KEY (lote_de_id, sucursal_id) REFERENCES financiero.lote_de(id, sucursal_id) ON DELETE SET NULL;

-- FK a factura_legal (mantener la compuesta existente)
ALTER TABLE financiero.documento_electronico ADD CONSTRAINT fk_documento_electronico_factura_legal
    FOREIGN KEY (factura_legal_id, sucursal_id) REFERENCES financiero.factura_legal(id, sucursal_id) ON DELETE CASCADE;

-- PASO 6: Agregar a publicaciones
DO $$
DECLARE
    v_sucursal_id INTEGER;
    v_pub TEXT;
BEGIN
    FOR v_sucursal_id IN SELECT id FROM empresarial.sucursal WHERE id != 0 ORDER BY id
    LOOP
        v_pub := 'central_filial' || v_sucursal_id || '_pub';
        
        IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = v_pub) THEN
            BEGIN
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.documento_electronico WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_cancelacion_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_nominacion_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.lote_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
            EXCEPTION WHEN duplicate_object THEN
                EXECUTE format('ALTER PUBLICATION %I DROP TABLE IF EXISTS financiero.documento_electronico, financiero.evento_cancelacion_de, financiero.evento_nominacion_de, financiero.lote_de', v_pub);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.documento_electronico WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_cancelacion_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_nominacion_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
                EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.lote_de WHERE (sucursal_id = %L)', v_pub, v_sucursal_id);
            WHEN OTHERS THEN
                NULL;
            END;
        END IF;
    END LOOP;
END $$;

