-- =====================================================================
-- Interruptor por tipo de notificacion, y suspension de los envios
-- =====================================================================
-- Decision del 2026-08-24: se suspenden TODOS los envios automaticos
-- menos dos, y el esquema de destinatarios se rediscute de cero.
--
-- El motivo no es el volumen sino el contenido. Un retiro de PDV le
-- llegaba a 37 personas por tener el rol de analisis de caja, y avisar
-- movimientos de plata a alguien por su rol es un riesgo, no un servicio.
-- Lo que sigue vivo es lo que va dirigido a la persona involucrada:
--
--   GASTO                  -> al responsable del gasto
--   VENTA_CREDITO_CLIENTE  -> a quien hizo la compra a credito
--
-- El aviso de vale queda afuera: el unico que existe hoy va a los
-- aprobadores por rol, y el personal al funcionario no esta escrito.
--
-- ⚠️ Esto NO se resuelve borrando el mapa de roles. Hay tipos que se
-- mandan con destinatario explicito y no pasan por la tabla
-- —NUEVO_DISPOSITIVO le llega al propio usuario que inicio sesion—, asi
-- que vaciar notificacion_tipo_role los dejaria vivos. El interruptor va
-- donde pasan todos: PushNotificationService.
--
-- Fila ausente = ACTIVO. Un tipo nuevo no nace apagado por accidente; lo
-- que se apaga se apaga explicitamente y queda escrito por que.
--
-- Se reactiva desde la configuracion del sistema, sin migracion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS configuraciones.notificacion_tipo_estado (
    id                BIGSERIAL PRIMARY KEY,
    tipo_notificacion VARCHAR(100) NOT NULL UNIQUE,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    motivo            TEXT,
    actualizado_en    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE configuraciones.notificacion_tipo_estado IS
    'Interruptor por tipo de notificacion. Fila ausente = activo.';

INSERT INTO configuraciones.notificacion_tipo_estado (tipo_notificacion, activo, motivo)
VALUES
    -- Movimientos de plata y de stock que salian por rol.
    ('RETIRO',                            FALSE, 'Suspendido 2026-08-24: informacion financiera saliendo por rol'),
    ('VENTA_TRANSFERENCIA',               FALSE, 'Suspendido 2026-08-24: informacion financiera saliendo por rol'),
    ('FACTURA_ALTO_VALOR',                FALSE, 'Suspendido 2026-08-24: informacion financiera saliendo por rol'),
    ('DIFERENCIA_MALETIN',                FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios por sucursal'),
    ('COTIZACION_ACTUALIZADA',            FALSE, 'Suspendido 2026-08-24: salia a todas las sesiones activas'),
    ('VENTA_STOCK_CRITICO',               FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('AJUSTE_STOCK',                      FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('AJUSTE_COSTO',                      FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('PRODUCTO_CREADO',                   FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('PRECIO_ACTUALIZADO',                FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('TRANSFERENCIA_INICIADA',            FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('CAMBIO_SUCURSAL_PRE_TRANSFERENCIA', FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('INVENTARIO_INICIADO',               FALSE, 'Suspendido 2026-08-24: hasta definir destinatarios'),
    ('RRHH_ALERTA',                       FALSE, 'Suspendido 2026-08-24: resumen diario, hasta definir destinatarios'),
    ('RRHH_SOLICITUD',                    FALSE, 'Suspendido 2026-08-24: el vale sale a los aprobadores por rol'),

    -- Alerta de seguridad al propio usuario. Se suspende por pedido
    -- expreso; no es un envio masivo y conviene revisarla temprano.
    ('NUEVO_DISPOSITIVO',                 FALSE, 'Suspendido 2026-08-24: revisar en la definicion, es alerta personal'),

    -- Envio manual y avisos genericos.
    ('PERSONALIZADA',                     FALSE, 'Suspendido 2026-08-24: envio manual masivo desde el escritorio'),
    ('MANUAL',                            FALSE, 'Suspendido 2026-08-24'),
    ('SISTEMA',                           FALSE, 'Suspendido 2026-08-24'),
    ('GENERAL',                           FALSE, 'Suspendido 2026-08-24'),

    -- Los dos que siguen vivos, explicitos para que se lean de una.
    ('GASTO',                             TRUE,  'Vive: va al responsable del gasto'),
    ('VENTA_CREDITO_CLIENTE',             TRUE,  'Vive: va a quien hizo la compra a credito')
ON CONFLICT (tipo_notificacion) DO NOTHING;
