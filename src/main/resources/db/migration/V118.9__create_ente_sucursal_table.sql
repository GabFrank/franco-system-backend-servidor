CREATE TABLE activos.ente_sucursal (
    id bigserial PRIMARY KEY,
    ente_id bigint NOT NULL REFERENCES activos.ente(id),
    sucursal_id bigint NOT NULL,
    responsable_id bigint, -- Optional, can map to personas.funcionario
    usuario_id bigint REFERENCES personas.usuario(id),
    creado_en timestamp with time zone DEFAULT now(),
    UNIQUE(ente_id, sucursal_id)
);

-- Migrate existing vehiculo_sucursal to ente_sucursal
INSERT INTO activos.ente_sucursal (ente_id, sucursal_id, responsable_id, usuario_id, creado_en)
SELECT e.id, vs.sucursal_id, vs.responsable_id, vs.usuario_id, COALESCE(vs.creado_en, now())
FROM vehiculos.vehiculo_sucursal vs
JOIN activos.ente e ON e.referencia_id = vs.vehiculo_id AND e.tipo_ente = 'VEHICULO'
ON CONFLICT DO NOTHING;
