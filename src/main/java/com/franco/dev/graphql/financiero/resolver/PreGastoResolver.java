package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.financiero.PreGastoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.service.financiero.PreGastoDetalleFinanzasService;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class PreGastoResolver implements GraphQLResolver<PreGasto> {
    
    @Autowired
    private PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;
    @Autowired
    private PreGastoService preGastoService;
    @Autowired
    private GastoRepository gastoRepository;

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
        BigDecimal solicitado = preGasto.getMontoSolicitado() != null ? preGasto.getMontoSolicitado() : BigDecimal.ZERO;
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal baseRendicion = retirado.compareTo(BigDecimal.ZERO) > 0 ? retirado : solicitado;
        if (preGasto.getEstado() == null || !"COMPLETADO".equals(preGasto.getEstado().name())) {
            return baseRendicion.compareTo(BigDecimal.ZERO) > 0 ? baseRendicion : BigDecimal.ZERO;
        }
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        BigDecimal pendiente = baseRendicion.subtract(gastado);
        return pendiente.compareTo(BigDecimal.ZERO) > 0 ? pendiente : BigDecimal.ZERO;
    }

    public Double porcentajeRendicion(PreGasto preGasto) {
        if (preGasto.getEstado() == null || !"COMPLETADO".equals(preGasto.getEstado().name())) {
            return 0d;
        }
        BigDecimal solicitado = preGasto.getMontoSolicitado() != null ? preGasto.getMontoSolicitado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        if (solicitado.compareTo(BigDecimal.ZERO) <= 0) {
            return 0d;
        }
        return gastado.multiply(new BigDecimal(100)).divide(solicitado, 2, RoundingMode.HALF_UP).doubleValue();
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

    public Gasto gasto(PreGasto preGasto) {
        if (preGasto.getId() == null || preGasto.getSucursalId() == null) {
            return null;
        }
        return gastoRepository.findFirstByPreGastoIdAndPreGastoSucursalIdOrderByCreadoEnDescIdDesc(
                preGasto.getId(), preGasto.getSucursalId());
    }

    public String estadoRendicion(PreGasto preGasto) {
        if (preGasto.getEstado() == null || !"COMPLETADO".equals(preGasto.getEstado().name())) {
            return "NO_RENDIDO";
        }
        if (preGasto.getEstadoRendicion() != null && !preGasto.getEstadoRendicion().trim().isEmpty()) {
            return preGasto.getEstadoRendicion();
        }
        BigDecimal retirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal gastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        if (retirado.compareTo(BigDecimal.ZERO) <= 0 || gastado.compareTo(BigDecimal.ZERO) <= 0) {
            return "NO_RENDIDO";
        }
        if (gastado.compareTo(retirado) >= 0) {
            return "RENDIDO_COMPLETO";
        }
        return "RENDIDO_PARCIAL";
    }

    public Boolean rindioGasto(PreGasto preGasto) {
        if (preGasto.getEstado() == null || !"COMPLETADO".equals(preGasto.getEstado().name())) {
            return false;
        }
        return Boolean.TRUE.equals(preGasto.getRindioGasto());
    }

    public LocalDateTime fechaRendicion(PreGasto preGasto) {
        if (preGasto.getEstado() == null || !"COMPLETADO".equals(preGasto.getEstado().name())) {
            return null;
        }
        return preGasto.getFechaRendicion();
    }
}
