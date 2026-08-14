-- =====================================================================
-- RRHH — Vincula el justificativo con su documento respaldatorio.
-- =====================================================================
-- El catalogo `rrhh.tipo_justificativo` ya define `requiere_documento`, pero
-- la bandera no estaba cableada a nada: se podia marcar un tipo como
-- "requiere documento" (ej. REPOSO MEDICO) y guardar el justificativo sin
-- adjunto, sin ningun aviso. Esta migracion agrega el vinculo al legajo
-- digital que ya existe (`rrhh.funcionario_documento`), y el backend valida
-- que el adjunto este presente cuando el tipo lo exige.
--
-- Aditiva: columna nullable, sin default, sin backfill. Los justificativos
-- existentes quedan sin documento (correcto: no lo tenian).
-- Idempotente.
-- =====================================================================

ALTER TABLE rrhh.justificativo
    ADD COLUMN IF NOT EXISTS documento_id BIGINT;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_justificativo_documento') THEN
        ALTER TABLE rrhh.justificativo
            ADD CONSTRAINT fk_justificativo_documento
            FOREIGN KEY (documento_id) REFERENCES rrhh.funcionario_documento (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_justificativo_documento
    ON rrhh.justificativo (documento_id);
