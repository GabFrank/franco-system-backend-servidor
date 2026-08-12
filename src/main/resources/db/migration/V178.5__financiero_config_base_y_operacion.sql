-- =====================================================================
-- Tesorería — Fase 2: configuración base + operación de caja mayor
-- =====================================================================
-- Aditivo e idempotente. Enriquece catálogos (moneda, forma de pago,
-- configuración general), agrega numeración de comprobantes (CN3),
-- categorías de entrada varia, configuración de visibilidad de caja mayor,
-- y la operación "entrada/salida varia".
-- =====================================================================

-- 1. Moneda — enriquecimiento (CN1 moneda base única, CN7 redondeo, decimales)
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS activo         boolean DEFAULT true;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS principal      boolean DEFAULT false;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS decimales      integer DEFAULT 0;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS regla_redondeo varchar(20);   -- NINGUNO | MULTIPLO_50 | MULTIPLO_100 | ...
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS redondeo_multiplo numeric(18,4);
-- CN1: a lo sumo una moneda principal
CREATE UNIQUE INDEX IF NOT EXISTS uq_moneda_principal ON financiero.moneda (principal) WHERE principal = true;
-- default: guaraní como principal si ninguna lo es todavía
UPDATE financiero.moneda SET principal = true
    WHERE id = (SELECT id FROM financiero.moneda WHERE upper(denominacion) LIKE '%GUARAN%' ORDER BY id LIMIT 1)
      AND NOT EXISTS (SELECT 1 FROM financiero.moneda WHERE principal = true);

-- 2. Forma de pago — router (orden, principal). movimiento_caja ya existe.
ALTER TABLE financiero.forma_pago ADD COLUMN IF NOT EXISTS orden     integer DEFAULT 0;
ALTER TABLE financiero.forma_pago ADD COLUMN IF NOT EXISTS principal boolean DEFAULT false;

-- 3. Configuración general de la empresa — campos de tesorería/facturación
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS timbrado_numero        varchar(30);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS timbrado_vigencia_hasta date;
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS punto_expedicion       varchar(10);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS zona_horaria           varchar(60) DEFAULT 'America/Asuncion';
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS moneda_principal_id     bigint REFERENCES financiero.moneda(id);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS logo_url               varchar(300);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS actividad_economica    varchar(200);

-- 4. Numeración de comprobantes (CN3) — series reutilizables
CREATE TABLE IF NOT EXISTS financiero.comprobante_serie (
    id             bigserial PRIMARY KEY,
    nombre         varchar(60) NOT NULL,
    tipo           varchar(40) NOT NULL,      -- GASTO | ENTRADA_VARIA | OPERACION_FINANCIERA | RECIBO_COBRO | RECIBO_PAGO | RETIRO ...
    prefijo        varchar(20) DEFAULT '',
    siguiente      bigint NOT NULL DEFAULT 1,
    relleno_ceros  integer DEFAULT 0,         -- ancho mínimo con ceros a la izquierda
    activo         boolean DEFAULT true,
    creado_en      timestamp DEFAULT now(),
    CONSTRAINT uq_comprobante_serie_tipo UNIQUE (tipo)
);

-- 5. Categorías de entrada varia (árbol)
CREATE TABLE IF NOT EXISTS financiero.entrada_varia_categoria (
    id         bigserial PRIMARY KEY,
    nombre     varchar(80) NOT NULL,
    padre_id   bigint REFERENCES financiero.entrada_varia_categoria(id),
    icono      varchar(60),
    activo     boolean DEFAULT true,
    creado_en  timestamp DEFAULT now()
);

-- 6. Entrada/Salida varia (ingreso/egreso manual con categoría)
CREATE TABLE IF NOT EXISTS financiero.entrada_varia (
    id                       bigserial PRIMARY KEY,
    descripcion              varchar(300),
    monto                    numeric(18,4) NOT NULL,
    es_ingreso               boolean NOT NULL DEFAULT true,
    caja_virtual_id          bigint REFERENCES financiero.caja_virtual(id),
    moneda_id                bigint REFERENCES financiero.moneda(id),
    categoria_id             bigint REFERENCES financiero.entrada_varia_categoria(id),
    forma_pago_id            bigint REFERENCES financiero.forma_pago(id),
    numero_comprobante       varchar(60),
    movimiento_caja_virtual_id bigint,
    usuario_id               bigint,
    anulado                  boolean DEFAULT false,
    creado_en                timestamp DEFAULT now()
);

-- 7. Configuración de visibilidad por caja virtual
CREATE TABLE IF NOT EXISTS financiero.caja_virtual_configuracion (
    id                        bigserial PRIMARY KEY,
    caja_virtual_id           bigint NOT NULL UNIQUE REFERENCES financiero.caja_virtual(id),
    mostrar_cuentas_por_pagar  boolean DEFAULT false,
    mostrar_cuentas_por_cobrar boolean DEFAULT false,
    operaciones_financieras_habilitado boolean DEFAULT true,
    creado_en                 timestamp DEFAULT now()
);
-- M:M formas de pago visibles por caja
CREATE TABLE IF NOT EXISTS financiero.caja_virtual_config_forma_pago (
    configuracion_id bigint NOT NULL REFERENCES financiero.caja_virtual_configuracion(id),
    forma_pago_id    bigint NOT NULL REFERENCES financiero.forma_pago(id),
    PRIMARY KEY (configuracion_id, forma_pago_id)
);
