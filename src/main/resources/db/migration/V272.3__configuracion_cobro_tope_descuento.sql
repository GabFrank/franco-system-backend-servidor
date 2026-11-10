-- =====================================================================
-- configuracion_cobro: tope del descuento del cobro
-- =====================================================================
-- Igual que el tope del aumento (V251.1): el descuento del cobro (F9 y delivery) pasa a tener un
-- tope configurable, con tope (porcentaje del total) o sin tope. Sin tope por defecto: hasta que
-- Tesoreria lo active, el cobro se comporta como antes.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V112.3) VA ANTES
--
-- La tabla ya es MAIN_TO_ALL y ya esta en central_pub. Una filial sin estas columnas falla al
-- aplicar el proximo UPDATE ("missing replicated columns") y se traba la suscripcion. Antes de
-- desplegar este JAR, cada filial tiene que tener 112.3 en flyway_schema_history. No hace falta
-- REFRESH ni ALTER PUBLICATION: agregar columnas no cambia la lista de tablas publicadas.
--
-- La fila que ya exista toma los DEFAULT aca, pero eso no se replica (es DDL): en la filial queda
-- NULL, que se lee igual (sin tope, 15%). El proximo guardado la iguala.
-- =====================================================================
ALTER TABLE financiero.configuracion_cobro
    ADD COLUMN IF NOT EXISTS descuento_con_tope BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE financiero.configuracion_cobro
    ADD COLUMN IF NOT EXISTS descuento_tope_porcentaje NUMERIC(6, 2) NOT NULL DEFAULT 15;

ALTER TABLE financiero.configuracion_cobro DROP CONSTRAINT IF EXISTS ck_configuracion_cobro_tope_descuento;
ALTER TABLE financiero.configuracion_cobro ADD CONSTRAINT ck_configuracion_cobro_tope_descuento
    CHECK (descuento_tope_porcentaje > 0 AND descuento_tope_porcentaje <= 100);

COMMENT ON COLUMN financiero.configuracion_cobro.descuento_con_tope IS
    'true = el descuento del cobro no puede pasar descuento_tope_porcentaje del total; false = sin tope.';

COMMENT ON COLUMN financiero.configuracion_cobro.descuento_tope_porcentaje IS
    'Tope del descuento, en porcentaje del total del cobro. Se conserva aunque descuento_con_tope sea false.';
