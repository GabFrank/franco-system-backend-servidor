-- =====================================================================
-- venta_tarjeta: quien dejo un cobro sin conciliar, cuando y por que (lado SUBSCRIBER)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- `NO_COMPLETADO` es un estado terminal: el cobro con tarjeta ya no se puede registrar, y esa
-- plata queda sin cupon contra el cual conciliar la liquidacion del proveedor. Hoy la fila dice
-- que eso paso y NADA MAS: no dice quien lo decidio, ni cuando, ni por que. Una caja con tres
-- cobros no conciliados es indistinguible de otra con tres, y no hay a quien preguntarle.
--
-- Estas cuatro columnas son lo que convierte el escape en algo auditable. Sin ellas, permitir que
-- el cajero cierre su propia caja con cobros sin conciliar seria abrir un agujero sin rastro.
--
-- SUBSCRIBER: SIN CHECK Y SIN FK
--
-- financiero.venta_tarjeta es BRANCH_TO_MAIN --la filial PUBLICA, central se SUSCRIBE-- asi que
-- este lado no valida: un CHECK o una FK aca pueden ABORTAR el apply de una fila que el filial
-- considera valida, y el apply worker entra en crash-loop con el slot reteniendo WAL. Es el corte
-- del 2026-08-20. La validacion vive donde se escribe, que es el filial.
--
-- `no_completado_por_id` va sin FK a personas.usuario por lo mismo, aunque el usuario replique:
-- si por cualquier motivo la fila del usuario todavia no llego a central, la FK frena el stream
-- entero de ventas con tarjeta.
--
-- ⚠️ ORDEN DE DESPLIEGUE: ESTA VA PRIMERO
--
-- La fila de esta tabla en pg_publication_rel del filial no tiene column list, asi que PostgreSQL
-- publica cualquier columna nueva automaticamente. Apenas una filial aplique SU migracion y
-- procese un cobro, el stream incluye estas columnas; si central no las tiene, se corta. Que
-- central tenga columnas de mas es inocuo.
--
--   1. central: esta migracion
--   2. central: desplegado y confirmado
--   3. recien entonces: la migracion del filial
--
-- SIN BACKFILL
--
-- Los NO_COMPLETADO viejos quedan en NULL = no se sabe quien los marco. Inventar un usuario seria
-- peor que el hueco: el NULL dice la verdad.
-- =====================================================================
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS no_completado_motivo VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS no_completado_observacion VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS no_completado_por_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS no_completado_en TIMESTAMP NULL,
    -- Reapertura: quien devolvio un NO_COMPLETADO a PENDIENTE y cuando. Van en ESTA migracion y no
    -- en una posterior porque el costo no es simetrico en el tiempo: agregarlas ahora, antes de
    -- que nada este mergeado, es gratis; agregarlas despues es OTRO par de migraciones con la
    -- misma secuencia obligatoria central-primero-filial-despues sobre una tabla BRANCH_TO_MAIN.
    ADD COLUMN IF NOT EXISTS reabierto_por_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS reabierto_en TIMESTAMP NULL;

COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_motivo IS
    'Por que este cobro quedo sin conciliar: CUPON_NO_IMPRESO | POS_FALLADO | CUPON_PERDIDO | OTRO. NULL = anterior a esta columna, o la fila nunca fue NO_COMPLETADO. Sin CHECK: central es subscriber de esta tabla.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_observacion IS
    'Lo que el cajero escribio cuando el motivo es OTRO, o el detalle que quiso dejar.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_por_id IS
    'Usuario que decidio cerrar sin conciliar este cobro. Sin FK: una FK en el subscriber puede abortar el apply.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_en IS
    'Cuando se marco. Junto con no_completado_por_id es lo que permite revisar despues.';

COMMENT ON COLUMN financiero.venta_tarjeta.reabierto_por_id IS
    'Usuario que devolvio este cobro de NO_COMPLETADO a PENDIENTE. Sin FK: central es subscriber.';
COMMENT ON COLUMN financiero.venta_tarjeta.reabierto_en IS
    'Cuando se reabrio. Las no_completado_* NO se limpian: se conserva por que se habia marcado.';
