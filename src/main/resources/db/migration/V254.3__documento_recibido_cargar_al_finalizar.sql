-- =====================================================================
-- Compras en el celular: la factura que crea la compra se carga sola al
-- finalizar la planificación
-- =====================================================================
-- La compra creada desde el KuDE queda en «Ítems del pedido» para que el
-- comprador la revise con el papel en la mano. La factura todavía no es nota:
-- queda vinculada al pedido (pedido_id) con esta marca, y el central crea la
-- nota cuando se finaliza la planificación.
--
-- La marca distingue esa factura de una escaneada a un pedido y no confirmada,
-- que no se carga sola.
--
-- Aditivo e idempotente. Central-only, como la tabla (V253.3).
-- =====================================================================

ALTER TABLE operaciones.documento_recibido
    ADD COLUMN IF NOT EXISTS cargar_al_finalizar BOOLEAN NOT NULL DEFAULT false;
