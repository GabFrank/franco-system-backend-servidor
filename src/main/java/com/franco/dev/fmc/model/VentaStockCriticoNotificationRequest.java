package com.franco.dev.fmc.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaStockCriticoNotificationRequest {
    private Long ventaId;
    private Long sucursalId;
    private String usuarioNombre;
    private String sucursalNombre;
    private List<VentaStockCriticoItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaStockCriticoItem {
        private Long productoId;
        private String productoDescripcion;
        private Double stockActual;
        private Double cantidadVendida;
        private Double stockResultante;
    }
}
