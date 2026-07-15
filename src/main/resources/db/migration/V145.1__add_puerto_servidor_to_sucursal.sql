-- El campo existente empresarial.sucursal.puerto se usa como puerto de PostgreSQL
-- para la replicacion logica (ver LogicalReplicationService). Varios servicios que
-- necesitan hablarle por HTTP a la app de la filial (FilialCajaProxyService,
-- PrintRouterService, MaletinGraphQL, FacturaLegalFilialService) reutilizaban ese
-- mismo campo o un puerto hardcodeado (8082), lo cual falla cuando el puerto de
-- Postgres y el de la app HTTP de la filial no coinciden.
--
-- puerto_servidor almacena el puerto HTTP/GraphQL de la app de la filial,
-- independiente del puerto de Postgres usado para la replicacion.
ALTER TABLE empresarial.sucursal
    ADD COLUMN IF NOT EXISTS puerto_servidor INTEGER;
