-- Sucursales adicionales que cubre una caja virtual.
--
-- Un gerente suele estar a cargo de todas las sucursales de una ciudad. Hasta ahora el rol acotado
-- (TESORERIA CAJA) ingresaba a su caja solo los retiros del PDV de la sucursal de esa caja, asi que hacia
-- falta una caja por sucursal. Con esta lista una sola caja recibe los retiros de varias: los de su
-- sucursal (caja_virtual.sucursal_id, que no se repite aca) mas los de las que figuren en esta tabla.
--
-- Aditiva: tabla nueva y vacia; sin filas todo se comporta como antes. Central-only: no se agrega a
-- ninguna publicacion.

CREATE TABLE IF NOT EXISTS financiero.caja_virtual_sucursal (
    id                bigserial PRIMARY KEY,
    caja_virtual_id   bigint NOT NULL REFERENCES financiero.caja_virtual(id),
    sucursal_id       bigint NOT NULL REFERENCES empresarial.sucursal(id),
    agregado_por_id   bigint REFERENCES personas.usuario(id),
    creado_en         timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_caja_virtual_sucursal UNIQUE (caja_virtual_id, sucursal_id)
);

CREATE INDEX IF NOT EXISTS ix_caja_virtual_sucursal_sucursal ON financiero.caja_virtual_sucursal (sucursal_id);
