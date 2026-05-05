package com.franco.dev.graphql.operaciones.dto;

import java.time.LocalDateTime;

import com.franco.dev.utilitarios.DateUtils;

/**
 * DTO de una sucursal que participo en recepcion fisica.
 */
public class SucursalRecepcionFisicaDTO {

    private Long sucursalId;
    private String sucursalNombre;
    private Long cantidadItemsRecepcionados;
    private String fechaUltimaRecepcion;

    public SucursalRecepcionFisicaDTO() {
    }

    public SucursalRecepcionFisicaDTO(Long sucursalId, String sucursalNombre) {
        this.sucursalId = sucursalId;
        this.sucursalNombre = sucursalNombre;
    }

    /**
     * Constructor pensado para proyecciones JPQL/SQL.
     */
    public SucursalRecepcionFisicaDTO(
            Long sucursalId,
            String sucursalNombre,
            Long cantidadItemsRecepcionados,
            LocalDateTime fechaUltimaRecepcion) {
        this.sucursalId = sucursalId;
        this.sucursalNombre = sucursalNombre;
        this.cantidadItemsRecepcionados = cantidadItemsRecepcionados;
        this.fechaUltimaRecepcion = fechaUltimaRecepcion != null
                ? DateUtils.dateToString(fechaUltimaRecepcion)
                : null;
    }

    public Long getSucursalId() {
        return sucursalId;
    }

    public void setSucursalId(Long sucursalId) {
        this.sucursalId = sucursalId;
    }

    public String getSucursalNombre() {
        return sucursalNombre;
    }

    public void setSucursalNombre(String sucursalNombre) {
        this.sucursalNombre = sucursalNombre;
    }

    public Long getCantidadItemsRecepcionados() {
        return cantidadItemsRecepcionados;
    }

    public void setCantidadItemsRecepcionados(Long cantidadItemsRecepcionados) {
        this.cantidadItemsRecepcionados = cantidadItemsRecepcionados;
    }

    public String getFechaUltimaRecepcion() {
        return fechaUltimaRecepcion;
    }

    public void setFechaUltimaRecepcion(String fechaUltimaRecepcion) {
        this.fechaUltimaRecepcion = fechaUltimaRecepcion;
    }
}
