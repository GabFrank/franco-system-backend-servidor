-- Snapshot del estado que el egreso destruye.
--
-- Egresar no solo apaga tres campos del funcionario: FuncionarioService.save pone su
-- credito en cero, y la cascada de estado inactiva al usuario, inactiva al cliente, lo
-- devuelve a NORMAL y le borra el credito tambien. Nada de eso quedaba guardado en
-- ningun lado, asi que revertir un egreso hecho por error era imposible sin ir a un
-- backup: en el caso real de farmacia (2026-08-21) hubo que sacar el credito de uno de
-- nueve dias antes.
--
-- Con esta tabla la reversa RESTAURA en vez de preguntar. Dos consecuencias concretas:
--
--   1. El credito ya no se escribe a mano.
--   2. El cliente recupera su tipo REAL. Sin el snapshot, la cascada lo reactiva como
--      FUNCIONARIO siempre, asi que revertir el egreso de un cliente VIP lo degradaba
--      en silencio.
--
-- Sigue el patron de las otras dos historicas del modulo (funcionario_salario_historico
-- y funcionario_cargo_historico, V159.0).
--
-- Solo cubre egresos hechos DESDE que existe la tabla: los anteriores no tienen de donde
-- sacar el snapshot, y por eso el dialogo de reversa conserva el campo de credito manual
-- como fallback.
--
-- rrhh.* no esta en central_pub ni en configuraciones.replication_table: no replica.

CREATE TABLE IF NOT EXISTS rrhh.funcionario_egreso_historico (
    id                        BIGSERIAL PRIMARY KEY,
    funcionario_id            BIGINT REFERENCES personas.funcionario(id),

    -- lo que el egreso escribio
    fecha_egreso              TIMESTAMP,
    motivo_egreso             TEXT,
    egresado_por_id           BIGINT REFERENCES personas.usuario(id),

    -- el estado que el egreso destruyo, leido ANTES de guardar
    credito_anterior          NUMERIC(18,2),
    usuario_id                BIGINT REFERENCES personas.usuario(id),
    usuario_activo_anterior   BOOLEAN,
    cliente_id                BIGINT REFERENCES personas.cliente(id),
    cliente_tipo_anterior     VARCHAR(30),
    cliente_credito_anterior  NUMERIC(18,2),
    cliente_activo_anterior   BOOLEAN,

    -- la reversa, si ocurre
    revertido_en              TIMESTAMP,
    revertido_por_id          BIGINT REFERENCES personas.usuario(id),
    motivo_reversion          TEXT,

    creado_en                 TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE rrhh.funcionario_egreso_historico IS
    'Snapshot previo a cada egreso. Permite revertirlo restaurando el estado exacto.';
COMMENT ON COLUMN rrhh.funcionario_egreso_historico.cliente_tipo_anterior IS
    'Tipo del cliente antes del egreso. Sin esto la cascada lo reactiva como FUNCIONARIO y un VIP pierde su categoria.';
COMMENT ON COLUMN rrhh.funcionario_egreso_historico.revertido_en IS
    'NULL mientras el egreso siga vigente. Se completa al revertirlo.';

-- El caso normal es buscar el ultimo egreso no revertido de un funcionario.
CREATE INDEX IF NOT EXISTS idx_func_egreso_hist_funcionario
    ON rrhh.funcionario_egreso_historico(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_func_egreso_hist_vigente
    ON rrhh.funcionario_egreso_historico(funcionario_id) WHERE revertido_en IS NULL;
