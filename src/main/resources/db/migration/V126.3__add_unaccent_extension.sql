-- Opcional: unaccent en PostgreSQL (no requerido para el buscador Lucene).
-- La normalización sin tildes la hace el analyzer de Hibernate Search (asciifolding).
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS unaccent;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Extension unaccent no disponible en este servidor PostgreSQL; busqueda inteligente sigue operativa via Lucene.';
END $$;
