-- =====================================================================
-- precio_especial_sucursal: precio especial por sucursal con vigencia
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Las promos de una sola sucursal (Heineken a 5000 en la 1; 2x1 solo en algunas) se hacian
-- editando a mano precio_por_sucursal en la base de la filial. precio_por_sucursal.sucursal_id
-- es decorativo (el resolver lo pisa con la property, 0) y su UNIQUE (presentacion_id,
-- tipo_precio_id) no admite un precio por sucursal. Esta tabla guarda el especial aparte: cada
-- filial toma los de su sucursal y sustituye el valor al devolver el precio. Ver
-- docs/manuales-implementacion/productos/SPEC-PRECIO-ESPECIAL-SUCURSAL.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V104.1) VA ANTES, EN CADA FILIAL
--
-- Es MAIN_TO_ALL. Apenas entra a central_pub, una filial SIN la tabla corta TODA su replicacion
-- entrante al primer especial (apply worker en bucle: "logical replication target relation does
-- not exist"). En alpha el alta a la publicacion es AUTOMATICA ~2 min despues del arranque
-- (ReplicationPublicationSyncScheduler, REPLICATION_SYNC_ENABLED=true): el checklist de filiales
-- va ANTES del deploy. En farmacia/bodega el alta es manual y NO se usa el boton "Sincronizar
-- publicaciones" (publica todas las pendientes y solo refresca filiales con IP cargada): ver el
-- plan, seccion Despliegue.
--
-- copy_data=false: un especial cargado antes de que una filial refresque su suscripcion NO le
-- llega nunca. Ningun especial se carga hasta verificar srsubstate='r' en todas.
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA: ver V231.1 (mismo criterio).
--
-- ESTE ES EL LADO PUBLISHER: las restricciones viven aca. La superposicion de vigencias se valida
-- en PrecioEspecialSucursalService (EXCLUDE pediria btree_gist en cada base).
-- ON DELETE CASCADE desde precio_por_sucursal: borrar un precio borra sus especiales (y su
-- historial) y replica el DELETE. Aceptado: el precio mismo deja de existir.
-- =====================================================================
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS productos.precio_especial_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    precio_id    BIGINT    NOT NULL,
    sucursal_id  BIGINT    NOT NULL,
    precio       NUMERIC   NOT NULL,
    fecha_desde  DATE,
    fecha_hasta  DATE,
    activo       BOOLEAN   NOT NULL DEFAULT TRUE,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_precio_especial_sucursal_precio FOREIGN KEY (precio_id)
        REFERENCES productos.precio_por_sucursal (id) ON DELETE CASCADE,
    CONSTRAINT fk_precio_especial_sucursal_sucursal FOREIGN KEY (sucursal_id)
        REFERENCES empresarial.sucursal (id),
    CONSTRAINT fk_precio_especial_sucursal_usuario FOREIGN KEY (usuario_id)
        REFERENCES personas.usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_precio_especial_sucursal_precio CHECK (precio > 0),
    CONSTRAINT ck_precio_especial_sucursal_rango CHECK (
        fecha_desde IS NULL OR fecha_hasta IS NULL OR fecha_hasta >= fecha_desde),
    CONSTRAINT ck_precio_especial_sucursal_no_central CHECK (sucursal_id <> 0)
);

CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_sucursal_precio
    ON productos.precio_especial_sucursal (sucursal_id, precio_id);
CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_precio
    ON productos.precio_especial_sucursal (precio_id);

COMMENT ON TABLE productos.precio_especial_sucursal IS
    'Precio especial de un precio_por_sucursal en una sucursal, con vigencia opcional (dias inclusivos, -03). MAIN_TO_ALL: cada filial aplica solo los de su sucursal. Corte de emergencia: UPDATE ... SET activo=false WHERE activo.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('productos.precio_especial_sucursal', 'MAIN_TO_ALL', 'Precio especial por sucursal', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
