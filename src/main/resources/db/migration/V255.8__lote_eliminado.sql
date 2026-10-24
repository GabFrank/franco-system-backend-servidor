-- Lotes eliminados (lotes fase 3, entrega 3): baja logica de un lote creado por error.
--
-- No se borra la fila de operaciones.lote: un DELETE replicado dejaria huerfanas las filas que una
-- filial ya hubiera escrito y todavia no subio, y no se puede deshacer. En su lugar el lote pasa a
-- BLOQUEADO (valor que existe en todas las versiones: una filial vieja no rompe y FEFO deja de
-- ofrecerlo) y aca se guarda una copia completa y el estado anterior para "Restaurar".
--
-- Tabla SOLO del central, igual que lote_archivado (V255.5): no se registra en
-- configuraciones.replication_table. Aditiva: el codigo viejo no la lee. Vuelta atras: restaurar
-- los lotes eliminados (estado_anterior) y dejar la tabla.
CREATE TABLE IF NOT EXISTS operaciones.lote_eliminado (
    lote_id         BIGINT PRIMARY KEY REFERENCES operaciones.lote (id),
    copia           JSONB       NOT NULL,
    estado_anterior VARCHAR(20) NOT NULL,
    motivo          TEXT        NOT NULL,
    usuario_id      BIGINT,
    eliminado_en    TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE operaciones.lote_eliminado IS
    'Lotes eliminados por error (baja logica: el lote queda BLOQUEADO). Copia para restaurar. Solo central, no replica.';
