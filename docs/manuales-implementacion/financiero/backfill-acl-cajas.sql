-- Backfill del ACL de cajas — CORRER A MANO, NO ES UNA MIGRACION FLYWAY.
--
-- Se mantiene fuera de Flyway a proposito: reparte permisos sobre plata y depende de como
-- este cada instancia, asi que tiene que correrse con la lista revisada delante, no al azar
-- de un deploy. Sin backfill, al activar el filtro toda caja sin filas queda invisible salvo
-- para su propietario y para ADMIN.
--
-- Estrategia (la recomendada en PLAN-ACL-CAJAS-VIRTUALES.md §7.2): red de seguridad — nadie
-- pierde el acceso que ya tenia. Despues cada responsable depura su lista desde la UI.
--   · TESORERIA GESTIONAR → lectura + escritura
--   · TESORERIA VER       → lectura
--
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 0 — mirar el terreno antes de tocar nada
-- ─────────────────────────────────────────────────────────────────────────────────────────
-- Quien queda como responsable de cada caja. OJO: hasta esta version, usuario_id se
-- sobreescribia en cada edicion, asi que puede ser el ultimo que guardo y no el creador.
-- Revisar y corregir con transferirPropiedadCaja lo que no corresponda.
--
--   select c.id, c.nombre, c.tipo, c.activo, u.nickname as responsable
--     from financiero.caja_virtual c
--     left join personas.usuario u on u.id = c.usuario_id
--    order by c.id;
--
-- Cuantos usuarios va a alcanzar el backfill:
--
--   select r.nombre as rol, count(distinct ur.user_id) as usuarios
--     from personas.usuario_role ur
--     join personas.role r on r.id = ur.role_id
--    where upper(trim(r.nombre)) in ('TESORERIA VER','TESORERIA GESTIONAR')
--    group by r.nombre;

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 1 — TESORERIA GESTIONAR: lectura + escritura sobre las cajas activas
-- ─────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO financiero.caja_virtual_acceso (caja_virtual_id, usuario_id, puede_leer, puede_escribir, otorgado_por_id, creado_en)
SELECT c.id, ur.user_id, true, true, NULL, now()
  FROM financiero.caja_virtual c
  CROSS JOIN (
      SELECT DISTINCT ur.user_id
        FROM personas.usuario_role ur
        JOIN personas.role r ON r.id = ur.role_id
       WHERE upper(trim(r.nombre)) = 'TESORERIA GESTIONAR'
  ) ur
 WHERE COALESCE(c.activo, true) = true
   AND (c.usuario_id IS NULL OR c.usuario_id <> ur.user_id)   -- el responsable ya tiene acceso implicito
ON CONFLICT (caja_virtual_id, usuario_id) DO UPDATE
   SET puede_leer = true, puede_escribir = true;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 2 — TESORERIA VER: solo lectura. No pisa a quien ya quedo con escritura en el paso 1.
-- ─────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO financiero.caja_virtual_acceso (caja_virtual_id, usuario_id, puede_leer, puede_escribir, otorgado_por_id, creado_en)
SELECT c.id, ur.user_id, true, false, NULL, now()
  FROM financiero.caja_virtual c
  CROSS JOIN (
      SELECT DISTINCT ur.user_id
        FROM personas.usuario_role ur
        JOIN personas.role r ON r.id = ur.role_id
       WHERE upper(trim(r.nombre)) = 'TESORERIA VER'
  ) ur
 WHERE COALESCE(c.activo, true) = true
   AND (c.usuario_id IS NULL OR c.usuario_id <> ur.user_id)
ON CONFLICT (caja_virtual_id, usuario_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- PASO 3 — verificar ANTES de confirmar
-- ─────────────────────────────────────────────────────────────────────────────────────────
--   select c.nombre,
--          count(*) filter (where a.puede_escribir) as con_escritura,
--          count(*) filter (where a.puede_leer and not a.puede_escribir) as solo_lectura
--     from financiero.caja_virtual c
--     left join financiero.caja_virtual_acceso a on a.caja_virtual_id = c.id
--    group by c.nombre order by c.nombre;
--
-- Cajas que quedarian sin nadie mas que su responsable (revisar que sea lo esperado):
--
--   select c.id, c.nombre from financiero.caja_virtual c
--    where COALESCE(c.activo, true) = true
--      and not exists (select 1 from financiero.caja_virtual_acceso a where a.caja_virtual_id = c.id);

COMMIT;
-- Si algo no cuadra: ROLLBACK; en vez de COMMIT.
