-- empresarial.cargo.supervisado_por_id es el cargo superior ("Depende de"), pero la FK
-- heredada de V0 apuntaba a personas.funcionario(id) (copy-paste de la FK homonima de
-- funcionario). La entidad Cargo, el schema GraphQL y el desktop siempre lo trataron como
-- otro cargo, asi que elegir un "Depende de" fallaba por FK o, peor, pasaba validando
-- contra un funcionario que casualmente tenia el mismo id.

ALTER TABLE empresarial.cargo DROP CONSTRAINT IF EXISTS cargo_supervisado_por_fk;

-- Lo que se haya guardado con la FK vieja es un id de funcionario, no de cargo: si no
-- coincide con un cargo existente (o apunta a si mismo) no significa nada y se limpia.
UPDATE empresarial.cargo c
SET supervisado_por_id = NULL
WHERE c.supervisado_por_id IS NOT NULL
  AND (c.supervisado_por_id = c.id
       OR NOT EXISTS (SELECT 1 FROM empresarial.cargo s WHERE s.id = c.supervisado_por_id));

-- Sin ON DELETE SET NULL a proposito: CargoGraphQL.deleteCargo ya rechaza el borrado de un
-- cargo con subcargos, y si algo lo saltea preferimos el error a perder la jerarquia en
-- silencio.
ALTER TABLE empresarial.cargo
    ADD CONSTRAINT cargo_supervisado_por_fk
        FOREIGN KEY (supervisado_por_id) REFERENCES empresarial.cargo(id);

CREATE INDEX IF NOT EXISTS cargo_supervisado_por_id_idx
    ON empresarial.cargo (supervisado_por_id);
