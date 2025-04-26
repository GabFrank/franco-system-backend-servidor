-- Create media schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS media;

-- Create imagen_master table
CREATE TABLE media.imagen_master (
    id BIGSERIAL PRIMARY KEY,
    tipo_referencia VARCHAR(50) NOT NULL,
    referencia_id BIGINT NOT NULL,
    nombre TEXT,
    url TEXT NOT NULL,
    principal BOOLEAN DEFAULT FALSE,
    checksum TEXT,
    extension VARCHAR(10),
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    usuario_id BIGINT REFERENCES personas.usuario(id)
);

-- Create index for faster searches
CREATE INDEX idx_imagen_master_tipo_ref_id ON media.imagen_master(tipo_referencia, referencia_id);
CREATE INDEX idx_imagen_master_principal ON media.imagen_master(principal); 