INSERT INTO activos.ente (tipo_ente, referencia_id, activo, usuario_id)
SELECT
    'VEHICULO',
    v.id,
    TRUE,
    v.usuario_id
FROM vehiculos.vehiculo v
WHERE NOT EXISTS (
    SELECT 1
    FROM activos.ente e
    WHERE e.tipo_ente = 'VEHICULO'
      AND e.referencia_id = v.id
);
