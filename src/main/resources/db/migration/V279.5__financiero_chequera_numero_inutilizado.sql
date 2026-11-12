-- Numeros de chequera inutilizados (issue #603, desktop #720): la hoja danada, perdida o mal llenada.
--
-- Un numero inutilizado vigente (revertido_en NULL) no sale en ningun cheque nuevo: la emision y el plan de cuotas
-- lo saltean. Si el numero ya tenia cheque, accion_cheque dice que se hizo con el: ANULADO (cheque suelto) o
-- RENUMERADO (el cheque paso a otro numero). Revertir no revive un cheque anulado.
--
-- Aditiva (tabla nueva). Central-only: financiero.chequera no esta en ninguna publicacion.

CREATE TABLE IF NOT EXISTS financiero.chequera_numero_inutilizado (
    id               bigserial PRIMARY KEY,
    chequera_id      bigint      NOT NULL REFERENCES financiero.chequera(id),
    numero           bigint      NOT NULL,
    motivo           text        NOT NULL,
    usuario_id       bigint      REFERENCES personas.usuario(id),
    creado_en        timestamp   NOT NULL DEFAULT now(),
    cheque_id        bigint      REFERENCES financiero.cheque(id),
    accion_cheque    varchar(20) NOT NULL DEFAULT 'NINGUNA',
    numero_nuevo     bigint,
    revertido_en     timestamp,
    revertido_por_id bigint      REFERENCES personas.usuario(id),
    CONSTRAINT ck_chequera_numero_inutilizado_accion CHECK (accion_cheque IN ('NINGUNA', 'ANULADO', 'RENUMERADO'))
);

-- Un numero se inutiliza una sola vez mientras siga vigente (revertido se puede volver a inutilizar).
CREATE UNIQUE INDEX IF NOT EXISTS uq_chequera_numero_inutilizado_vigente
    ON financiero.chequera_numero_inutilizado (chequera_id, numero) WHERE revertido_en IS NULL;

COMMENT ON TABLE financiero.chequera_numero_inutilizado IS
    'Numeros de chequera que no se usan (hoja danada, perdida). Los saltea la emision (V279.5, issue #603).';
