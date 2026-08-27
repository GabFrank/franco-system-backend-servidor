-- Direccion y telefono de la empresa, para el encabezado de los recibos de RRHH.
--
-- Van aca y no en empresarial.configuracion_general (que es donde "corresponderian")
-- por dos razones:
--
--   1. configuracion_general ESTA en la publicacion de replicacion central->filial
--      (V0__initial_schema.sql:14081, sin lista de columnas). Agregarle columnas en
--      central sin agregarlas tambien en cada filial hace fallar el apply worker de la
--      suscripcion en cuanto el central escriba esa fila -- que es exactamente lo que
--      va a pasar cuando alguien cargue estos datos despues del deploy. Es el mismo
--      accidente que documentan V154.1 (central) y V84.1 (filial), y que ya obligo a
--      alinear esta tabla una vez (filial V88.3).
--   2. La pantalla de configuracion de RRHH ya sabe editar claves de esta tabla, asi
--      que el operador los carga desde donde ya carga los otros ~20 parametros. Con
--      configuracion_general habria que construir UI nueva para dos campos.
--
-- Ninguna tabla del schema rrhh se replica (V155.0: "gestion central-only"), verificado
-- contra las 63 tablas de central_pub.
--
-- Nacen vacias a proposito: el recibo degrada a razon social + RUC (que si salen de
-- configuracion_general) hasta que alguien las cargue en cada instancia.

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT v.clave, v.valor, v.tipo, v.descripcion, now()
FROM (VALUES
    ('EMPRESA_DIRECCION', '', 'STRING', 'Direccion de la empresa, para el encabezado de los recibos'),
    ('EMPRESA_TELEFONO',  '', 'STRING', 'Telefono de la empresa, para el encabezado de los recibos')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave
);
