INSERT INTO vehiculos.tipo_combustible (id, descripcion, creado_en) VALUES (1, 'NAFTA', CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;
INSERT INTO vehiculos.tipo_combustible (id, descripcion, creado_en) VALUES (2, 'DIESEL', CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;
INSERT INTO vehiculos.tipo_combustible (id, descripcion, creado_en) VALUES (3, 'ELÉCTRICO', CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;
INSERT INTO vehiculos.tipo_combustible (id, descripcion, creado_en) VALUES (4, 'HÍBRIDO', CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

-- Update the sequence to start after 4
SELECT setval('vehiculos.tipo_combustible_id_seq', 4, true);
