package com.franco.dev.graphql.operaciones.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO resumen de recepcion fisica por pedido.
 */
public class PedidoRecepcionFisicaResumenDTO {

    private Long pedidoId;
    private List<SucursalRecepcionFisicaDTO> sucursalesRecepcionFisica = new ArrayList<>();
    private Long totalSucursalesRecepcionFisica;

    public PedidoRecepcionFisicaResumenDTO() {
    }

    public PedidoRecepcionFisicaResumenDTO(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public PedidoRecepcionFisicaResumenDTO(
            Long pedidoId,
            List<SucursalRecepcionFisicaDTO> sucursalesRecepcionFisica) {
        this.pedidoId = pedidoId;
        this.sucursalesRecepcionFisica = sucursalesRecepcionFisica != null
                ? sucursalesRecepcionFisica
                : new ArrayList<>();
        recalculateTotal();
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public List<SucursalRecepcionFisicaDTO> getSucursalesRecepcionFisica() {
        return sucursalesRecepcionFisica;
    }

    public void setSucursalesRecepcionFisica(List<SucursalRecepcionFisicaDTO> sucursalesRecepcionFisica) {
        this.sucursalesRecepcionFisica = sucursalesRecepcionFisica != null
                ? sucursalesRecepcionFisica
                : new ArrayList<>();
        recalculateTotal();
    }

    public Long getTotalSucursalesRecepcionFisica() {
        return totalSucursalesRecepcionFisica;
    }

    public void setTotalSucursalesRecepcionFisica(Long totalSucursalesRecepcionFisica) {
        this.totalSucursalesRecepcionFisica = totalSucursalesRecepcionFisica;
    }

    public void recalculateTotal() {
        this.totalSucursalesRecepcionFisica = (long) this.sucursalesRecepcionFisica.size();
    }
}
