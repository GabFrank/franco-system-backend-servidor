-- Proveedor de servicios: empresas que proveen las terminales POS y su soporte tecnico.
-- Entidad independiente de personas.proveedor (ese modela al proveedor de mercaderia).
-- Se vincula a personas.persona porque "proveedor de servicio" es un rol de una persona,
-- no un cliente nuestro.
--
-- La tabla y la columna de terminal_pos van juntas en una sola migracion para que la FK
-- nunca apunte a una tabla inexistente.
--
-- OJO: financiero.terminal_pos esta replicada MAIN_TO_ALL. El ADD COLUMN de abajo exige
-- que la columna ya exista en TODAS las filiales (migracion espejo V81.2 del filial),
-- si no el apply worker se detiene con "missing replicated column".

CREATE TABLE personas.proveedor_servicio (
    id                 BIGSERIAL PRIMARY KEY,
    persona_id         BIGINT REFERENCES personas.persona(id),
    cuenta_bancaria_id BIGINT REFERENCES financiero.cuenta_bancaria(id),
    nombre_contacto    VARCHAR(255),
    numero_contacto    VARCHAR(50),
    usuario_id         BIGINT REFERENCES personas.usuario(id),
    creado_en          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT proveedor_servicio_un UNIQUE (persona_id)
);

CREATE INDEX idx_proveedor_servicio_persona ON personas.proveedor_servicio(persona_id);

ALTER TABLE financiero.terminal_pos
    ADD COLUMN proveedor_servicio_id BIGINT NULL
    REFERENCES personas.proveedor_servicio(id);
