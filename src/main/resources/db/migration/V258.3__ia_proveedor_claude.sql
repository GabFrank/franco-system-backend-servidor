-- =====================================================================
-- Lectura de facturas en papel: elegir el proveedor de IA (OpenAI o Claude).
--
-- * ia.proveedor: proveedor activo (OPENAI | CLAUDE). Arranca en OPENAI para
--   que lo ya configurado siga igual.
-- * anthropic.api_key / anthropic.modelo: clave (cifrada) y modelo de Claude.
--
-- Aditivo e idempotente. Central-only: no entra a configuraciones.replication_table.
-- =====================================================================

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('ia.proveedor', 'OPENAI', 'Proveedor de IA que lee las facturas en papel (OPENAI o CLAUDE)', false)
ON CONFLICT (clave) DO NOTHING;

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('anthropic.api_key', NULL, 'Clave de la API de Claude (cifrada)', true)
ON CONFLICT (clave) DO NOTHING;

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('anthropic.modelo', 'claude-opus-5-5', 'Modelo de Claude que lee las facturas', false)
ON CONFLICT (clave) DO NOTHING;
