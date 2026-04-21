package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.service.financiero.PreGastoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.service.financiero.PreGastoDetalleFinanzasService;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PreGastoResolver implements GraphQLResolver<PreGasto> {
    
    @Autowired
    private PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;
    @Autowired
    private PreGastoService preGastoService;

    public List<PreGastoDetalleFinanzas> finanzas(PreGasto preGasto) {
        if (preGasto.getId() == null || preGasto.getSucursalId() == null) {
            return null;
        }
        return preGastoDetalleFinanzasService.findByPreGastoIdAndSucursalId(preGasto.getId(), preGasto.getSucursalId());
    }

    public BigDecimal montoPendienteRetiro(PreGasto preGasto) {
        BigDecimal solicitado = preGasto.getMontoSolicitado() != null ? preGasto.getMontoSolicitado() : BigDecimal.ZERO;
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        return solicitado.subtract(retirado);
    }

    public BigDecimal montoNoRendido(PreGasto preGasto) {
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        return retirado.subtract(gastado);
    }

    public Double porcentajeRendicion(PreGasto preGasto) {
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        if (retirado.compareTo(BigDecimal.ZERO) <= 0) {
            return 0d;
        }
        return gastado.multiply(new BigDecimal(100)).divide(retirado, 2, RoundingMode.HALF_UP).doubleValue();
    }

    public BigDecimal desvioVsSolicitado(PreGasto preGasto) {
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        BigDecimal solicitado = preGasto.getMontoSolicitado() != null ? preGasto.getMontoSolicitado() : BigDecimal.ZERO;
        return gastado.subtract(solicitado);
    }

    public String estadoEtiqueta(PreGasto preGasto) {
        return preGastoService.getStatusMetadataList().stream()
                .filter(m -> m.getEstado().equals(preGasto.getEstado().name()))
                .map(m -> m.getEtiqueta())
                .findFirst().orElse(preGasto.getEstado().name());
    }

    public String estadoIcono(PreGasto preGasto) {
        return preGastoService.getStatusMetadataList().stream()
                .filter(m -> m.getEstado().equals(preGasto.getEstado().name()))
                .map(m -> m.getIcono())
                .findFirst().orElse("help_outline");
    }

    public String estadoColor(PreGasto preGasto) {
        return preGastoService.getStatusMetadataList().stream()
                .filter(m -> m.getEstado().equals(preGasto.getEstado().name()))
                .map(m -> m.getColor())
                .findFirst().orElse("#9e9e9e");
    }
}
