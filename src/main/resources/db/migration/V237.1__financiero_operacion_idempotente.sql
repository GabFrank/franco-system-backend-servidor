-- =====================================================================
-- Tesorería — claves de idempotencia de las operaciones que mueven plata (issue #376)
-- =====================================================================
-- Una fila por pedido con clave. Se inserta en la misma transacción que la
-- operación, así que solo queda si la operación commiteó: un pedido repetido
-- con la misma clave encuentra la fila y devuelve lo ya creado (resultado_id)
-- en vez de registrarlo otra vez.
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, así que no entra en ninguna publicación.
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.operacion_idempotente (
    clave         varchar(64) NOT NULL,
    operacion     varchar(40) NOT NULL,
    usuario_id    bigint,
    huella        varchar(64) NOT NULL,
    resultado_id  bigint,
    creado_en     timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_operacion_idempotente PRIMARY KEY (clave)
);

-- Para una purga futura por antigüedad sin recorrer la tabla entera.
CREATE INDEX IF NOT EXISTS ix_operacion_idempotente_creado_en
    ON financiero.operacion_idempotente (creado_en);
