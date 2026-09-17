-- =====================================================================
-- Notas electronicas (remision y credito) — infraestructura comun
-- =====================================================================
-- documento_electronico deja de ser exclusivo de la factura: una nota de
-- credito o de remision tambien genera su DE. En vez de duplicar la tabla,
-- se generaliza:
--   * factura_legal_id pasa a aceptar NULL (relajacion aditiva: toda fila
--     existente sigue siendo valida, y el JAR anterior sigue cargandola
--     siempre).
--   * nacen nota_credito_id y nota_remision_id, nullable y sin FK todavia:
--     las FK compuestas se agregan en V227.1 / V228.1, cuando existan las
--     tablas de notas. El CHECK de "exactamente un origen" va en V228.1,
--     con las tres columnas ya en juego.
-- La UNIQUE (factura_legal_id, sucursal_id) se conserva: en PostgreSQL los
-- NULL no colisionan entre si, asi que N notas conviven sin romperla.
--
-- Espejo obligatorio en filial (V95.1): la tabla baja por central_pub y con
-- la columna NOT NULL alla, la primera nota emitida cortaria la replicacion
-- de todo el canal. El filial va primero.
--
-- Roles: control por rol de las mutations nuevas (FacturacionSecurityService,
-- mismo patron que TESORERIA / RRHH). Aditivo e idempotente por nombre, con
-- espacios para respetar la convencion de personas.role. No lleva espejo en
-- filial: personas.role se replica desde central (precedente V176.5, V218.5).
-- Esta migracion solo crea los roles; asignarlos es por la pantalla de
-- usuarios, despues del deploy.
-- =====================================================================

ALTER TABLE financiero.documento_electronico
    ALTER COLUMN factura_legal_id DROP NOT NULL;

ALTER TABLE financiero.documento_electronico
    ADD COLUMN IF NOT EXISTS nota_credito_id  BIGINT NULL,
    ADD COLUMN IF NOT EXISTS nota_remision_id BIGINT NULL;

INSERT INTO personas.role (nombre, creado_en)
SELECT r.nombre, now()
FROM (VALUES
    ('FACTURACION VER'),
    ('FACTURACION NR EMITIR'),
    ('FACTURACION NC EMITIR'),
    ('FACTURACION ANULAR')
) AS r(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre
);
