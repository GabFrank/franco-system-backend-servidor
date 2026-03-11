-- V77.5: Alter proceso_etapa table and enum
-- This migration comes from the compras branch

DO $$
BEGIN
    -- 1. Drop the table if it exists (since it's empty and safe to remove)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'proceso_etapa') THEN
        DROP TABLE operaciones.proceso_etapa CASCADE;
    END IF;
    
    -- 2. Drop the incorrect enum type if it exists
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'proceso_etapa_estado') THEN
        DROP TYPE operaciones.proceso_etapa_estado;
    END IF;
    
    -- 3. Recreate the enum with the correct value
    CREATE TYPE operaciones.proceso_etapa_estado AS ENUM (
      'PENDIENTE',
      'EN_PROCESO',
      'COMPLETADA',
      'OMITIDA',
      'CANCELADA'
    );
END $$;

-- 4. Recreate the table with the corrected enum
CREATE TABLE IF NOT EXISTS operaciones.proceso_etapa (
  id bigserial NOT NULL,
  pedido_id int8 NOT NULL,
  tipo_etapa operaciones."proceso_etapa_tipo" NOT NULL,
  estado_etapa operaciones."proceso_etapa_estado" DEFAULT 'PENDIENTE'::operaciones.proceso_etapa_estado NOT NULL,
  fecha_inicio timestamp NULL,
  fecha_fin timestamp NULL,
  usuario_inicio_id int8 NULL,
  creado_en timestamp DEFAULT CURRENT_TIMESTAMP NULL,
  CONSTRAINT proceso_etapa_pkey PRIMARY KEY (id),
  CONSTRAINT uk_proceso_etapa_pedido_tipo UNIQUE (pedido_id, tipo_etapa),
  CONSTRAINT fk_proceso_etapa_pedido FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id),
  CONSTRAINT fk_proceso_etapa_usuario FOREIGN KEY (usuario_inicio_id) REFERENCES personas.usuario(id)
);

-- 5. Recreate the indexes
CREATE INDEX IF NOT EXISTS idx_proceso_etapa_estado ON operaciones.proceso_etapa USING btree (estado_etapa);
CREATE INDEX IF NOT EXISTS idx_proceso_etapa_pedido ON operaciones.proceso_etapa USING btree (pedido_id);
CREATE INDEX IF NOT EXISTS idx_proceso_etapa_tipo ON operaciones.proceso_etapa USING btree (tipo_etapa);
