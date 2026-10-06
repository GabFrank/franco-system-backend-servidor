-- =====================================================================
-- Rol NOTA REMISION EMITIR
-- =====================================================================
-- Crear (con su envio a SIFEN) e imprimir la nota de remision de una
-- transferencia, sin abrir el menu de Financiero: eso sigue siendo de
-- FACTURACION VER / FACTURACION EMITIR. El alcance lo hace cumplir
-- FacturacionSecurityService; este rol no reenvia ni anula.
--
-- Aditivo e idempotente por nombre, mismo patron que V225.1. El trim es
-- porque personas.role tiene nombres con espacio final. No lleva espejo
-- en filial: personas.role se replica desde central. Esta migracion solo
-- crea el rol; asignarlo es por la pantalla de usuarios, despues del
-- deploy.
-- =====================================================================

INSERT INTO personas.role (nombre, creado_en)
SELECT 'NOTA REMISION EMITIR', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'NOTA REMISION EMITIR'
);
