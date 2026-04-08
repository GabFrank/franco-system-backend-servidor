-- Migration to add GastoGrupo and GastoContinuoConfig tables
CREATE TABLE financiero.gasto_grupo (
    id BIGSERIAL PRIMARY KEY,
    descripcion VARCHAR(255),
    proveedor_id BIGINT,
    creado_en TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    usuario_id BIGINT
);

ALTER TABLE financiero.pre_gasto ADD COLUMN gasto_grupo_id BIGINT REFERENCES financiero.gasto_grupo(id);

CREATE TABLE financiero.gasto_continuo_config (
    id BIGSERIAL PRIMARY KEY,
    tipo_gasto_id BIGINT REFERENCES financiero.tipo_gasto(id),
    dia_aviso INTEGER,
    monto_sugerido NUMERIC(19,2),
    moneda_id BIGINT REFERENCES financiero.moneda(id),
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    usuario_id BIGINT
);
