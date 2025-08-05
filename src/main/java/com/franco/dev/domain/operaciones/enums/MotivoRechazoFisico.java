package com.franco.dev.domain.operaciones.enums;

public enum MotivoRechazoFisico {
    PRODUCTO_DANADO("Producto Dañado"),
    PRODUCTO_VENCIDO("Producto Vencido"),
    CANTIDAD_INCORRECTA("Cantidad Incorrecta"),
    PRODUCTO_DIFERENTE("Producto Diferente"),
    EMBALAJE_DANADO("Embalaje Dañado"),
    OTRO("Otro");

    private final String descripcion;

    MotivoRechazoFisico(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
} 