-- Control de acceso por caja virtual: quien puede ver y quien puede mover plata en cada caja.
--
-- Hasta ahora el acceso era por rol global: cualquiera con TESORERIA VER veia TODAS las cajas
-- de la empresa, y cualquiera con TESORERIA GESTIONAR podia mover plata en cualquiera. El rol
-- sigue habilitando la capacidad; esta tabla delimita el alcance (modelo AND).
--
-- El propietario de la caja (caja_virtual.usuario_id) tiene lectura y escritura implicitas y
-- NO lleva fila aca: es quien administra la lista.
--
-- Aditiva: tabla nueva. Sin backfill en esta migracion — se corre aparte, con la lista
-- revisada, para no dejar a nadie sin cajas el dia del despliegue.

CREATE TABLE IF NOT EXISTS financiero.caja_virtual_acceso (
    id                bigserial PRIMARY KEY,
    caja_virtual_id   bigint NOT NULL REFERENCES financiero.caja_virtual(id),
    usuario_id        bigint NOT NULL REFERENCES personas.usuario(id),
    puede_leer        boolean NOT NULL DEFAULT true,
    puede_escribir    boolean NOT NULL DEFAULT false,
    otorgado_por_id   bigint REFERENCES personas.usuario(id),
    creado_en         timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_caja_virtual_acceso UNIQUE (caja_virtual_id, usuario_id)
);

-- La consulta caliente es "que cajas ve este usuario" (filtra todos los listados).
CREATE INDEX IF NOT EXISTS ix_caja_virtual_acceso_usuario ON financiero.caja_virtual_acceso (usuario_id);
CREATE INDEX IF NOT EXISTS ix_caja_virtual_acceso_caja    ON financiero.caja_virtual_acceso (caja_virtual_id);
