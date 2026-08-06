-- Agrega el valor 'CANCELADO' al ENUM de estado de retiro, de forma idempotente.
-- Habilita cancelar un retiro reutilizando financiero.retiro.estado, en lugar de
-- borrar la fila (deleteRetiro), que no deja rastro ni corrige el balance de caja.
--
-- El mismo valor debe existir en la base de CADA FILIAL antes de que el central
-- emita la primera cancelacion: financiero.retiro replica central -> filial y el
-- apply worker de la suscripcion falla si el enum local no conoce la etiqueta.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type t
        JOIN pg_enum e ON t.oid = e.enumtypid
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE t.typname = 'estado_retiro' AND n.nspname = 'financiero' AND e.enumlabel = 'CANCELADO'
    ) THEN
        ALTER TYPE financiero.estado_retiro ADD VALUE 'CANCELADO';
    END IF;
END $$;
