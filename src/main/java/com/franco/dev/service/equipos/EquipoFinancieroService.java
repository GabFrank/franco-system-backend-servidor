package com.franco.dev.service.equipos;

import com.franco.dev.domain.equipos.Equipo;
import com.franco.dev.domain.equipos.EquipoFinanciero;
import com.franco.dev.graphql.equipos.dto.EquipoFinancieroOutput;
import com.franco.dev.graphql.equipos.input.EquipoFinancieroInput;
import com.franco.dev.graphql.equipos.input.EquipoInput;
import com.franco.dev.repository.equipos.EquipoFinancieroRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class EquipoFinancieroService extends CrudService<EquipoFinanciero, EquipoFinancieroRepository, Long> {

    private final EquipoFinancieroRepository repository;
    private final ProveedorService proveedorService;
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;

    public EquipoFinancieroService(
            EquipoFinancieroRepository repository,
            ProveedorService proveedorService,
            MonedaService monedaService,
            UsuarioService usuarioService) {
        this.repository = repository;
        this.proveedorService = proveedorService;
        this.monedaService = monedaService;
        this.usuarioService = usuarioService;
    }

    @Override
    public EquipoFinancieroRepository getRepository() {
        return repository;
    }

    public Optional<EquipoFinanciero> buscarPorEquipoId(Long equipoId) {
        return repository.findByEquipoId(equipoId);
    }

    public EquipoFinancieroOutput aOutput(EquipoFinanciero entity) {
        if (entity == null) {
            return null;
        }
        EquipoFinancieroOutput output = new EquipoFinancieroOutput();
        output.setId(entity.getId());
        output.setCosto(entity.getCosto());
        output.setValorTasacion(entity.getValorTasacion());
        output.setValorTasacionPyg(entity.getValorTasacionPyg());
        output.setValorTasacionBrl(entity.getValorTasacionBrl());
        output.setSituacionPago(entity.getSituacionPago());
        output.setProveedor(entity.getProveedor());
        output.setMoneda(entity.getMoneda());
        output.setMontoTotal(entity.getMontoTotal());
        output.setMontoYaPagado(entity.getMontoYaPagado());
        output.setCantidadCuotas(entity.getCantidadCuotas());
        output.setCantidadCuotasPagadas(entity.getCantidadCuotasPagadas());
        output.setDiaVencimiento(entity.getDiaVencimiento());
        output.setUsuario(entity.getUsuario());
        output.setCreadoEn(entity.getCreadoEn());
        return output;
    }

    public EquipoFinanciero aplicarDesdeInput(EquipoFinanciero entity, EquipoFinancieroInput input) {
        if (input == null) {
            return entity;
        }
        entity.setCosto(input.getCosto());
        entity.setValorTasacion(input.getValorTasacion());
        entity.setValorTasacionPyg(input.getValorTasacionPyg());
        entity.setValorTasacionBrl(input.getValorTasacionBrl());
        entity.setSituacionPago(aMayusculas(input.getSituacionPago()));
        entity.setMontoTotal(input.getMontoTotal());
        entity.setMontoYaPagado(input.getMontoYaPagado());
        entity.setCantidadCuotas(input.getCantidadCuotas());
        entity.setCantidadCuotasPagadas(input.getCantidadCuotasPagadas());
        entity.setDiaVencimiento(input.getDiaVencimiento());

        aplicarLogicaSituacionPago(entity, input);

        if (input.getProveedorId() != null) {
            entity.setProveedor(proveedorService.findById(input.getProveedorId())
                    .orElseThrow(() -> new GraphQLException("El proveedor seleccionado no es valido.")));
        }
        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        Long usuarioId = input.getUsuarioId();
        if (usuarioId != null) {
            entity.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        }
        return entity;
    }

    public EquipoFinancieroInput extraerInput(EquipoInput input) {
        EquipoFinancieroInput financieroInput = new EquipoFinancieroInput();
        financieroInput.setCosto(input.getCosto());
        financieroInput.setValorTasacion(input.getValorTasacion());
        financieroInput.setValorTasacionPyg(input.getValorTasacionPyg());
        financieroInput.setValorTasacionBrl(input.getValorTasacionBrl());
        financieroInput.setSituacionPago(input.getSituacionPago());
        financieroInput.setProveedorId(input.getProveedorId());
        financieroInput.setMonedaId(input.getMonedaId());
        financieroInput.setMontoTotal(input.getMontoTotal());
        financieroInput.setMontoYaPagado(input.getMontoYaPagado());
        financieroInput.setCantidadCuotas(input.getCantidadCuotas());
        financieroInput.setCantidadCuotasPagadas(input.getCantidadCuotasPagadas());
        financieroInput.setDiaVencimiento(input.getDiaVencimiento());
        financieroInput.setUsuarioId(input.getUsuarioId());
        return financieroInput;
    }

    public boolean tieneDatosFinancieros(EquipoFinancieroInput input) {
        if (input == null) {
            return false;
        }
        return input.getCosto() != null
                || input.getValorTasacion() != null
                || input.getValorTasacionPyg() != null
                || input.getValorTasacionBrl() != null
                || input.getSituacionPago() != null
                || input.getProveedorId() != null
                || input.getMonedaId() != null
                || input.getMontoTotal() != null
                || input.getMontoYaPagado() != null
                || input.getCantidadCuotas() != null
                || input.getCantidadCuotasPagadas() != null
                || input.getDiaVencimiento() != null;
    }

    public EquipoFinanciero vincularAEquipo(Equipo equipo, EquipoFinancieroInput input) {
        if (equipo == null || !tieneDatosFinancieros(input)) {
            return null;
        }

        EquipoFinanciero financiero = equipo.getFinanciero();
        if (financiero == null) {
            financiero = repository.findByEquipoId(equipo.getId()).orElse(new EquipoFinanciero());
            financiero.setEquipo(equipo);
            equipo.setFinanciero(financiero);
        }

        aplicarDesdeInput(financiero, input);
        normalizarDatosFinancieros(financiero);
        if (financiero.getId() == null) {
            financiero.setCreadoEn(LocalDateTime.now());
        }
        return financiero;
    }

    @Override
    public EquipoFinanciero save(EquipoFinanciero entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        normalizarDatosFinancieros(entity);
        return super.save(entity);
    }

    private void aplicarLogicaSituacionPago(EquipoFinanciero entity, EquipoFinancieroInput input) {
        if ("PAGADO".equalsIgnoreCase(input.getSituacionPago())) {
            entity.setCantidadCuotasPagadas(input.getCantidadCuotas() != null ? input.getCantidadCuotas() : 0);
            entity.setMontoYaPagado(input.getMontoTotal() != null ? input.getMontoTotal() : BigDecimal.ZERO);
        } else if ("PAGANDO".equalsIgnoreCase(input.getSituacionPago())) {
            boolean tieneMonto = input.getMontoTotal() != null && input.getMontoTotal().compareTo(BigDecimal.ZERO) > 0;
            boolean tieneCuotas = input.getCantidadCuotas() != null && input.getCantidadCuotas() > 0;

            boolean montoPagado = tieneMonto && input.getMontoYaPagado() != null
                    && input.getMontoYaPagado().compareTo(input.getMontoTotal()) >= 0;
            boolean cuotasPagadas = tieneCuotas && input.getCantidadCuotasPagadas() != null
                    && input.getCantidadCuotasPagadas() >= input.getCantidadCuotas();

            boolean totalmentePagado = false;
            if (tieneMonto && tieneCuotas) {
                totalmentePagado = montoPagado && cuotasPagadas;
            } else if (tieneMonto) {
                totalmentePagado = montoPagado;
            } else if (tieneCuotas) {
                totalmentePagado = cuotasPagadas;
            }

            if (totalmentePagado) {
                entity.setSituacionPago("PAGADO");
                if (tieneMonto) {
                    entity.setMontoYaPagado(input.getMontoTotal());
                }
                if (tieneCuotas) {
                    entity.setCantidadCuotasPagadas(input.getCantidadCuotas());
                }
            }
        }
    }

    private void normalizarDatosFinancieros(EquipoFinanciero entity) {
        if (entity.getSituacionPago() == null) {
            return;
        }
        String situacion = entity.getSituacionPago().toUpperCase();
        if (situacion.equals("PAGADO") || situacion.equals("DADO")
                || situacion.equals("GANADO") || situacion.equals("COMODATO")) {
            if (entity.getMontoTotal() != null) {
                entity.setMontoYaPagado(entity.getMontoTotal());
            }
            if (entity.getCantidadCuotas() != null) {
                entity.setCantidadCuotasPagadas(entity.getCantidadCuotas());
            } else {
                entity.setCantidadCuotas(0);
                entity.setCantidadCuotasPagadas(0);
            }
        } else if (situacion.equals("PAGANDO")) {
            BigDecimal total = entity.getMontoTotal() != null ? entity.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal pagado = entity.getMontoYaPagado() != null ? entity.getMontoYaPagado() : BigDecimal.ZERO;
            Integer cuotasTotal = entity.getCantidadCuotas() != null ? entity.getCantidadCuotas() : 0;
            Integer cuotasPagadas = entity.getCantidadCuotasPagadas() != null ? entity.getCantidadCuotasPagadas() : 0;

            if (total.compareTo(BigDecimal.ZERO) > 0 && pagado.compareTo(total) >= 0 && cuotasPagadas >= cuotasTotal) {
                entity.setSituacionPago("PAGADO");
            }
        }
    }

    private String aMayusculas(String value) {
        return value != null ? value.toUpperCase() : null;
    }
}
