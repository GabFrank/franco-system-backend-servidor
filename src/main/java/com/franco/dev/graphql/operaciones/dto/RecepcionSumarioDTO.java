package com.franco.dev.graphql.operaciones.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.utilitarios.DateUtils;
/**
 * DTO que contiene el sumario de una recepción de mercadería
 */
public class RecepcionSumarioDTO {
    
    private Long recepcionId;
    private String estadoRecepcion;
    private String nombreProveedor;
    private String nombreSucursal;
    
    // Contadores de items
    private Long totalItems;
    private Long itemsVerificados;
    private Long itemsPendientes;
    private Long itemsConDiferencia;
    private Long itemsRechazados;
    
    // Progreso
    private BigDecimal progresoPorcentaje;
    private String estadoProgreso; // "INICIADA", "EN_PROGRESO", "COMPLETADA"
    
    // Información adicional
    private Long totalProductosUnicos;
    private Long productosVerificados;
    private Long productosPendientes;
    
    // Fechas
    private String fechaCreacion;
    private String fechaUltimaVerificacion;
    
    // Constructor
    public RecepcionSumarioDTO() {}
    
    /**
     * Constructor para JPQL query con tipos exactos que espera Hibernate
     */
    public RecepcionSumarioDTO(
            long recepcionId,
            RecepcionMercaderiaEstado estadoRecepcion,
            String nombreProveedor,
            String nombreSucursal,
            long totalItems,
            long itemsVerificados,
            long itemsPendientes,
            long itemsConDiscrepancia,
            long itemsRechazados,
            long totalProductosUnicos,
            long productosVerificados,
            long productosPendientes,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaUltimaVerificacion) {
        
        this.recepcionId = recepcionId;
        this.estadoRecepcion = estadoRecepcion != null ? estadoRecepcion.toString() : null;
        this.nombreProveedor = nombreProveedor;
        this.nombreSucursal = nombreSucursal;
        this.totalItems = totalItems;
        this.itemsVerificados = itemsVerificados;
        this.itemsPendientes = itemsPendientes;
        this.itemsConDiferencia = itemsConDiferencia;
        this.itemsRechazados = itemsRechazados;
        this.totalProductosUnicos = totalProductosUnicos;
        this.productosVerificados = productosVerificados;
        this.productosPendientes = productosPendientes;
        this.fechaCreacion = DateUtils.dateToString(fechaCreacion);
        this.fechaUltimaVerificacion = fechaUltimaVerificacion != null ? DateUtils.dateToString(fechaUltimaVerificacion) : null;
        
        // Calcular progreso
        if (this.totalItems != null && this.totalItems > 0) {
            double progreso = (double) this.itemsVerificados / this.totalItems * 100;
            this.progresoPorcentaje = BigDecimal.valueOf(progreso);
            
            // Determinar estado del progreso
            if (progreso == 0) {
                this.estadoProgreso = "INICIADA";
            } else if (progreso == 100) {
                this.estadoProgreso = "COMPLETADA";
            } else {
                this.estadoProgreso = "EN_PROGRESO";
            }
        } else {
            this.progresoPorcentaje = BigDecimal.ZERO;
            this.estadoProgreso = "INICIADA";
        }
    }
    
    // Getters y Setters
    public Long getRecepcionId() {
        return recepcionId;
    }
    
    public void setRecepcionId(Long recepcionId) {
        this.recepcionId = recepcionId;
    }
    
    public String getEstadoRecepcion() {
        return estadoRecepcion;
    }
    
    public void setEstadoRecepcion(String estadoRecepcion) {
        this.estadoRecepcion = estadoRecepcion;
    }
    
    public String getNombreProveedor() {
        return nombreProveedor;
    }
    
    public void setNombreProveedor(String nombreProveedor) {
        this.nombreProveedor = nombreProveedor;
    }
    
    public String getNombreSucursal() {
        return nombreSucursal;
    }
    
    public void setNombreSucursal(String nombreSucursal) {
        this.nombreSucursal = nombreSucursal;
    }
    
    public Long getTotalItems() {
        return totalItems;
    }
    
    public void setTotalItems(Long totalItems) {
        this.totalItems = totalItems;
    }
    
    public Long getItemsVerificados() {
        return itemsVerificados;
    }
    
    public void setItemsVerificados(Long itemsVerificados) {
        this.itemsVerificados = itemsVerificados;
    }
    
    public Long getItemsPendientes() {
        return itemsPendientes;
    }
    
    public void setItemsPendientes(Long itemsPendientes) {
        this.itemsPendientes = itemsPendientes;
    }
    
    public Long getItemsConDiferencia() {
        return itemsConDiferencia;
    }
    
    public void setItemsConDiferencia(Long itemsConDiferencia) {
        this.itemsConDiferencia = itemsConDiferencia;
    }
    
    public Long getItemsRechazados() {
        return itemsRechazados;
    }
    
    public void setItemsRechazados(Long itemsRechazados) {
        this.itemsRechazados = itemsRechazados;
    }
    
    public BigDecimal getProgresoPorcentaje() {
        return progresoPorcentaje;
    }
    
    public void setProgresoPorcentaje(BigDecimal progresoPorcentaje) {
        this.progresoPorcentaje = progresoPorcentaje;
    }
    
    public String getEstadoProgreso() {
        return estadoProgreso;
    }
    
    public void setEstadoProgreso(String estadoProgreso) {
        this.estadoProgreso = estadoProgreso;
    }
    
    public Long getTotalProductosUnicos() {
        return totalProductosUnicos;
    }
    
    public void setTotalProductosUnicos(Long totalProductosUnicos) {
        this.totalProductosUnicos = totalProductosUnicos;
    }
    
    public Long getProductosVerificados() {
        return productosVerificados;
    }
    
    public void setProductosVerificados(Long productosVerificados) {
        this.productosVerificados = productosVerificados;
    }
    
    public Long getProductosPendientes() {
        return productosPendientes;
    }
    
    public void setProductosPendientes(Long productosPendientes) {
        this.productosPendientes = productosPendientes;
    }
    
    public String getFechaCreacion() {
        return fechaCreacion;
    }
    
    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
    
    public String getFechaUltimaVerificacion() {
        return fechaUltimaVerificacion;
    }
    
    public void setFechaUltimaVerificacion(String fechaUltimaVerificacion) {
        this.fechaUltimaVerificacion = fechaUltimaVerificacion;
    }
}
