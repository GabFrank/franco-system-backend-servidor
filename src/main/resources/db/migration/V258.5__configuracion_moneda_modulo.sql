-- =====================================================================
-- configuracion_moneda_modulo + moneda.codigo_iso + rol CONFIGURAR MONEDA
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La moneda con la que nacen los documentos estaba fija en el codigo: "GUARANI" por nombre, el
-- id 1 o la primera de la lista. Esta tabla la vuelve configurable por modulo (COMPRAS, COSTOS,
-- PRECIOS, PDV), para que una empresa pueda llevar compras y costos en dolares mientras sigue
-- vendiendo en guaranies, y pasar de un modulo a otro de a uno.
--
-- Cada fila dice "desde vigente_desde, el modulo X usa la moneda Y". Una fila nueva reemplaza a
-- la anterior de la misma clave (modulo + sucursal) y ninguna se edita: el historial es la tabla
-- misma. Solo el PDV admite una fila por sucursal; los demas modulos son de toda la empresa.
--
-- Tabla VACIA = comportamiento de hoy: todo resuelve a la moneda de la empresa (principal), y si
-- no hay ninguna marcada, a PYG. Una fila afecta solo a los documentos que nacen despues de su
-- vigente_desde: los abiertos terminan en su moneda.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V108.5) VA ANTES
--
-- Es MAIN_TO_ALL. Cuando la tabla entre a central_pub y se refresquen las suscripciones, el
-- REFRESH de una filial sin la tabla falla entero. Antes de desplegar este JAR, cada filial del
-- canal tiene que tener 108.5 en flyway_schema_history. En farmacia y bodega el alta a la
-- publicacion es manual (frc-cicd/runbook-publicar-formatos-pos-farmacia.md). NINGUNA escritura
-- sobre esta tabla hasta ver pg_subscription_rel.srsubstate='r' en cada suscripcion.
--
-- Lo mismo para moneda.codigo_iso: la columna se agrega aca, pero se RELLENA en una migracion
-- posterior (entrega de compras), recien cuando toda la flota tenga la columna espejo. Un UPDATE
-- de moneda en este mismo release bajaria a filiales que quizas todavia no la tienen y las
-- dejaria con el apply worker en crash-loop ("missing replicated column").
--
-- POR QUE NO HAY SEED NI ALTER PUBLICATION ACA
--
-- - Seed: la tabla entra a la publicacion con copy_data=false, asi que una fila sembrada aca
--   nunca llegaria a las filiales. Y farmacia y bodega son empresas distintas: la configuracion
--   de cada una se carga desde su pantalla.
-- - ALTER PUBLICATION: lo hace syncPublicationsWithReplicationTable a partir del registro en
--   replication_table, o el paso manual del runbook donde el sync esta apagado.
--
-- ESTE ES EL LADO PUBLISHER: las restricciones viven aca y no en el filial.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_moneda_modulo (
    id            BIGSERIAL PRIMARY KEY,
    modulo        VARCHAR(20) NOT NULL,
    sucursal_id   BIGINT,
    moneda_id     BIGINT      NOT NULL,
    vigente_desde TIMESTAMP   NOT NULL DEFAULT NOW(),
    activo        BOOLEAN     NOT NULL DEFAULT TRUE,
    usuario_id    BIGINT,
    creado_en     TIMESTAMP   DEFAULT NOW(),
    CONSTRAINT fk_configuracion_moneda_modulo_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal (id),
    CONSTRAINT fk_configuracion_moneda_modulo_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda (id),
    CONSTRAINT fk_configuracion_moneda_modulo_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id),
    CONSTRAINT ck_configuracion_moneda_modulo_modulo CHECK (modulo IN ('COMPRAS', 'COSTOS', 'PRECIOS', 'PDV')),
    -- Solo la moneda del PDV puede ser distinta por sucursal (decision de negocio): compras,
    -- costos y precios son de toda la empresa.
    CONSTRAINT ck_configuracion_moneda_modulo_sucursal CHECK (sucursal_id IS NULL OR modulo = 'PDV')
);

-- El lector busca la fila mas reciente de una clave con vigente_desde <= ahora.
CREATE INDEX IF NOT EXISTS idx_configuracion_moneda_modulo_clave
    ON financiero.configuracion_moneda_modulo (modulo, sucursal_id, vigente_desde DESC, id DESC);

COMMENT ON TABLE financiero.configuracion_moneda_modulo IS
    'Moneda de cada modulo. MAIN_TO_ALL. Sin filas = la moneda de la empresa (principal, o PYG). Cada fila nueva reemplaza a la anterior de su clave desde vigente_desde; ninguna se edita.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.sucursal_id IS
    'NULL = todas las sucursales. Solo el modulo PDV admite una fila por sucursal (CHECK).';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.vigente_desde IS
    'Desde cuando rige. Afecta solo a los documentos que nacen despues; los abiertos terminan en su moneda.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.activo IS
    'false = la fila se ignora y rige la anterior activa de la misma clave (o la moneda de la empresa). Es la vuelta atras.';

INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.configuracion_moneda_modulo', 'MAIN_TO_ALL', 'Configuracion Moneda Modulo', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;

-- ---------------------------------------------------------------------
-- moneda.codigo_iso: identifica la moneda sin mirar su nombre. Se rellena en una migracion
-- posterior (ver arriba). Unico entre las que lo tienen.
-- ---------------------------------------------------------------------
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS codigo_iso VARCHAR(3);

CREATE UNIQUE INDEX IF NOT EXISTS uq_moneda_codigo_iso
    ON financiero.moneda (upper(codigo_iso))
    WHERE codigo_iso IS NOT NULL;

COMMENT ON COLUMN financiero.moneda.codigo_iso IS
    'Codigo ISO 4217 (PYG, USD, BRL). Identifica la moneda sin mirar su nombre.';

-- ---------------------------------------------------------------------
-- Rol para administrar la moneda por modulo. Mismo patron que V237.3: idempotente por nombre,
-- con trim porque personas.role tiene nombres con espacio final. No lo otorga a nadie; personas.role
-- se replica desde el central, asi que no lleva espejo en el filial.
-- ---------------------------------------------------------------------
INSERT INTO personas.role (nombre, creado_en)
SELECT 'CONFIGURAR MONEDA', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'CONFIGURAR MONEDA'
);

-- ---------------------------------------------------------------------
-- Version minima del desktop. Un desktop mas viejo no opera: el cajero puede postergar la
-- actualizacion indefinidamente y, cuando un modulo cambie de moneda, un cliente viejo calcularia
-- con las reglas de antes. Vacio = control apagado (comportamiento de hoy). Central-only, igual
-- que el resto de configuracion_sistema (V256.3).
-- ---------------------------------------------------------------------
INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('cliente.desktop.version_minima', '',
        'Version minima del desktop (ej. 4.12.0). Vacio = sin control. Un desktop anterior no deja operar hasta actualizarse.',
        false)
ON CONFLICT (clave) DO NOTHING;
