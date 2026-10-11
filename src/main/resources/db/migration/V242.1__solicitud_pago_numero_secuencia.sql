-- El numero de una solicitud de pago ("SP-000123") salia de contar las solicitudes: dos altas simultaneas
-- contaban lo mismo, y con una solicitud borrada del medio el conteo volvia a dar un numero ya usado y
-- ninguna alta entraba. Ahora lo da esta secuencia.
--
-- Arranca en el numero mas alto con forma SP-<digitos> mas uno (1 si no hay ninguna). El regex acota a 15
-- digitos y el CASE asegura que solo se convierta lo que tiene esa forma, para que un valor raro no rompa
-- el cast: esta migracion no puede fallar. Si la secuencia queda
-- atras por otro motivo (un rollback del JAR), SolicitudPagoNumeroVerificador la adelanta al arrancar.

CREATE SEQUENCE IF NOT EXISTS operaciones.solicitud_pago_numero_seq;

SELECT setval('operaciones.solicitud_pago_numero_seq',
       COALESCE((SELECT MAX(CASE WHEN numero_solicitud ~ '^SP-[0-9]{1,15}$'
                                 THEN substring(numero_solicitud from 4)::bigint END)
                   FROM operaciones.solicitud_pago), 0) + 1,
       false);
