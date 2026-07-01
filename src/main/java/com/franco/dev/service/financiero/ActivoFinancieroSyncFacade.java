package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import com.franco.dev.service.personas.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ActivoFinancieroSyncFacade {

    private final ActivoFinancieroSyncService syncService;
    private final ProveedorService proveedorService;

    public void sync(
            TipoEnte tipoEnte,
            Long referenciaId,
            String situacionPago,
            Long proveedorId,
            Long monedaId,
            BigDecimal montoTotal,
            BigDecimal montoYaPagado,
            Integer cantidadCuotas,
            Integer cantidadCuotasPagadas,
            Integer diaVencimiento,
            List<CuotaDetalleInput> cuotasDetalle,
            Long usuarioId
    ) {
        Long proveedorPersonaId = resolveProveedorPersonaId(proveedorId);
        syncService.syncFromAsset(
                tipoEnte,
                referenciaId,
                situacionPago,
                proveedorPersonaId,
                monedaId,
                montoTotal,
                montoYaPagado,
                cantidadCuotas,
                cantidadCuotasPagadas,
                diaVencimiento,
                cuotasDetalle,
                usuarioId
        );
    }

    private Long resolveProveedorPersonaId(Long proveedorId) {
        if (proveedorId == null) {
            return null;
        }
        Optional<Proveedor> proveedor = proveedorService.findById(proveedorId);
        return proveedor.map(p -> p.getPersona() != null ? p.getPersona().getId() : null).orElse(null);
    }
}
