-- Tabla key/value generica para configuraciones runtime (API keys IA, datos empresa para impresion, logo, futuros toggles)
CREATE TABLE IF NOT EXISTS empresarial.configuracion_sistema (
    clave         VARCHAR(100) PRIMARY KEY,
    valor         TEXT,
    descripcion   TEXT,
    encriptado    BOOLEAN NOT NULL DEFAULT FALSE,
    modificado_en TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE empresarial.configuracion_sistema IS 'Configuraciones runtime key/value. Separada de configuracion_general (identidad fiscal SIFEN). Para valores cosmeticos/presentacion (logo, nombre comercial corto) y secretos (API keys IA encriptadas Blowfish+Base64).';

-- Seeds IA (OpenAI Vision para importacion automatica de facturas)
INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado) VALUES
    ('openai.api_key', NULL, 'API Key de OpenAI (encriptada). Usada para extraer datos de facturas imagen/PDF.', TRUE),
    ('openai.modelo', 'gpt-4o', 'Modelo OpenAI por defecto. Compatibles: gpt-4o, gpt-4o-mini.', FALSE),
    ('openai.prompt_adicional', '', 'Texto adicional concatenado al prompt base de extraccion de facturas.', FALSE)
ON CONFLICT (clave) DO NOTHING;

-- Seeds datos empresa para impresiones (cosmetico, distinto de configuracion_general que es identidad fiscal)
INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado) VALUES
    ('empresa.nombre_impresion', NULL, 'Nombre comercial corto a mostrar en encabezado de impresiones.', FALSE),
    ('empresa.subtitulo_impresion', NULL, 'Subtitulo opcional debajo del nombre (ej: lema, especialidad).', FALSE),
    ('empresa.direccion_impresion', NULL, 'Direccion a mostrar en pie de impresiones.', FALSE),
    ('empresa.telefono_impresion', NULL, 'Telefono a mostrar en pie de impresiones.', FALSE),
    ('empresa.logo_base64', NULL, 'Logo de la empresa en base64 (data URI sin prefijo). Tamano recomendado &lt;200KB.', FALSE)
ON CONFLICT (clave) DO NOTHING;
