-- Fix del enum configuraciones.tipo_dispositivo.
--
-- El schema inicial (V0) define por error un unico label mal formado
-- 'WEBWEB_MOBILE' (dos valores concatenados por un typo), por lo que faltan
-- los valores 'WEB' y 'WEB_MOBILE'. Esto rompe el login cuando el cliente
-- reporta tipo_dispositivo = 'WEB' (app servida como web / PWA): al guardar el
-- registro de inicio de sesion Postgres devuelve "valor de entrada invalido
-- para enum ... 'WEB'" y el login falla con 500. El desktop Electron no lo
-- dispara porque reporta DESKTOP_WIN/LIN/MAC.
--
-- Estrategia aditiva (regla del proyecto): solo se agregan los valores
-- faltantes. El label 'WEBWEB_MOBILE' queda huerfano (Postgres no soporta
-- eliminar valores de un enum); no se usa desde el codigo.
--
-- ADD VALUE IF NOT EXISTS es idempotente y corre dentro de la transaccion de
-- Flyway en PostgreSQL 12+ (el valor nuevo no se usa en la misma transaccion).

ALTER TYPE configuraciones.tipo_dispositivo ADD VALUE IF NOT EXISTS 'WEB';
ALTER TYPE configuraciones.tipo_dispositivo ADD VALUE IF NOT EXISTS 'WEB_MOBILE';
