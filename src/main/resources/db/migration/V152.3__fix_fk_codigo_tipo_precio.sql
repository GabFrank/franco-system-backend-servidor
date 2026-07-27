-- =============================================================================
-- V152.3 - Corregir las llaves foraneas de productos.codigo_tipo_precio
--
-- Par de la V80.3 del repo filial. El defecto viene del dump inicial
-- (V0__initial_schema.sql linea 13655) y esta presente en ambas bases, asi que
-- hay que corregirlo en las dos: si solo se arregla la filial, el central sigue
-- sin poder insertar mas de 6 filas y sigue publicando DELETEs que rompen a los
-- suscriptores.
--
--
-- PROBLEMA 1 - la FK apunta a la columna equivocada
--
--   codigo_tipo_precio_fk_1  FOREIGN KEY (id) REFERENCES productos.tipo_precio(id)
--                                        ^^ el PK autoincremental, no tipo_precio_id
--
--   Cada fila exige que su PROPIO id de secuencia exista en tipo_precio. Como
--   tipo_precio tiene 6 filas (ids 1..6), entran las primeras 6 inserciones y la
--   septima falla:
--
--     ERROR: inserción o actualización en la tabla «codigo_tipo_precio» viola
--            la llave foránea «codigo_tipo_precio_fk_1»
--     DETALLE: La llave (id)=(7) no está presente en la tabla «tipo_precio».
--
--   Efecto colateral: tipo_precio_id nunca estuvo restringido por ninguna FK.
--
--
-- PROBLEMA 2 - ON DELETE SET NULL sobre una columna NOT NULL
--
--   codigo_tipo_precio_fk_usu es ON DELETE SET NULL sobre usuario_id, que es
--   NOT NULL. Un SET NULL sobre una columna NOT NULL no puede tener exito
--   nunca. En las filiales las tres FK tienen esa politica, y como
--   productos.codigo, productos.tipo_precio y personas.usuario son MAIN_TO_ALL,
--   un DELETE hecho aca se replica y mata al apply worker de cada filial:
--
--     ERROR: el valor nulo en la columna «id» viola la restricción "not-null"
--     CONTEXTO: UPDATE ONLY "productos"."codigo_tipo_precio" SET "id" = NULL ...
--
--   Es el mismo bucle infinito que resolvio V79.3 en la filial para vehiculos.
--
--
-- SOLUCION
--   FK sobre tipo_precio_id (la columna correcta) y politicas de borrado que no
--   puedan trabar a un suscriptor. El criterio es que NINGUN DELETE hecho aca
--   pueda hacer fallar al apply worker de una filial:
--
--     codigo_id      -> ON DELETE CASCADE
--     tipo_precio_id -> ON DELETE CASCADE
--         Semantica correcta de tabla de cruce: el vinculo no significa nada sin
--         sus dos padres. Ademas, cuando el DELETE del padre llega replicado, la
--         filial borra sus hijos localmente y el DELETE del hijo que llega
--         despues simplemente no encuentra la fila, cosa que la replicacion
--         logica tolera (se contabiliza en confl_delete_missing, no es error).
--         Con NO ACTION en cambio el borrado del padre se rechazaria en la
--         filial y volveria a matar al apply worker.
--
--     usuario_id     -> ON DELETE SET NULL, con la columna hecha NULLABLE
--         Es la intencion original del esquema (esta declarada asi desde el
--         dump inicial), solo que era inaplicable con la columna NOT NULL. Es
--         dato de auditoria: si se borra el usuario se pierde quien creo el
--         vinculo, pero el vinculo se conserva. CASCADE seria incorrecto aca
--         (borrar un usuario no debe borrar precios).
--
--   Los nombres quedan descriptivos e iguales en ambas bases (el central usaba
--   codigo_tipo_precio_fk_usu y la filial codigo_tipo_precio_fk_2).
--
-- Idempotente: se puede correr mas de una vez sin efecto.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. usuario_id pasa a ser NULLABLE
--    Requisito para que su ON DELETE SET NULL sea aplicable, y para poder
--    desreferenciar huerfanos en la seccion 2 sin borrar el vinculo.
-- -----------------------------------------------------------------------------
ALTER TABLE productos.codigo_tipo_precio ALTER COLUMN usuario_id DROP NOT NULL;


-- -----------------------------------------------------------------------------
-- 2. Limpieza defensiva de huerfanos
--
--    tipo_precio_id nunca tuvo una FK que lo validara (esa era justamente la que
--    estaba mal escrita), asi que puede apuntar a cualquier cosa. Sin esto, los
--    ADD CONSTRAINT de la seccion 4 fallarian y bloquearian el arranque.
--
--    Un vinculo sin codigo o sin tipo_precio es inutilizable y se borra. Un
--    usuario inexistente en cambio solo se desreferencia: es auditoria, no
--    justifica perder el vinculo.
--
--    La tabla no tiene consumidores: no hay entidad JPA ni resolver GraphQL que
--    la lea, ni aca, ni en la filial, ni en el frontend.
-- -----------------------------------------------------------------------------
DELETE FROM productos.codigo_tipo_precio c
 WHERE NOT EXISTS (SELECT 1 FROM productos.tipo_precio t WHERE t.id = c.tipo_precio_id)
    OR NOT EXISTS (SELECT 1 FROM productos.codigo      k WHERE k.id = c.codigo_id);

UPDATE productos.codigo_tipo_precio c
   SET usuario_id = NULL
 WHERE c.usuario_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = c.usuario_id);


-- -----------------------------------------------------------------------------
-- 3. Eliminar las FK defectuosas
--    Se listan todos los nombres historicos (central y filial) para que la
--    migracion sirva igual en cualquiera de las dos y sea reejecutable.
-- -----------------------------------------------------------------------------
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_fk;
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_fk_1;
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_fk_2;
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_fk_usu;

ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_codigo_fk;
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_tipo_precio_fk;
ALTER TABLE productos.codigo_tipo_precio DROP CONSTRAINT IF EXISTS codigo_tipo_precio_usuario_fk;


-- -----------------------------------------------------------------------------
-- 4. Recrear las FK correctas
-- -----------------------------------------------------------------------------
ALTER TABLE productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_codigo_fk
    FOREIGN KEY (codigo_id) REFERENCES productos.codigo(id) ON DELETE CASCADE;

ALTER TABLE productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_tipo_precio_fk
    FOREIGN KEY (tipo_precio_id) REFERENCES productos.tipo_precio(id) ON DELETE CASCADE;

ALTER TABLE productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_usuario_fk
    FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;
