package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.graphql.financiero.input.PreGastoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;
import com.franco.dev.service.financiero.dto.PreGastoStatusMetadataDTO;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.graphql.financiero.input.PreGastoDetalleFinanzasInput;
import com.franco.dev.service.financiero.PreGastoDetalleFinanzasService;
import com.franco.dev.service.personas.ProveedorService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class PreGastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PreGastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;

    @Autowired
    private com.franco.dev.service.impresion.ImpresionService impresionService;

    public PreGasto preGasto(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public List<PreGasto> preGastos(String estado, Long sucId) {
        if (estado != null && !estado.isEmpty()) {
            if (sucId != null && sucId > 0) {
                return service.buscarPorEstadoYSucursal(EstadoPreGasto.valueOf(estado), sucId);
            } else {
                return service.buscarPorEstado(EstadoPreGasto.valueOf(estado));
            }
        }
        return service.findAll2();
    }

    public List<PreGasto> preGastosPorSucursal(String estado, Long sucursalId) {
        return service.buscarPorEstadoYSucursal(EstadoPreGasto.valueOf(estado), sucursalId);
    }

    public List<PreGasto> preGastosPorFuncionario(Long funcionarioId) {
        return service.buscarPorFuncionario(funcionarioId);
    }

    public List<PreGasto> preGastosSearch(String texto, Long sucId) {
        return service.buscarPorTexto(texto, sucId);
    }

    public Page<PreGasto> filterPreGastos(Long id, String estado, String inicio, String fin, Integer page,
            Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 15);
        return service.filterPreGastos(id, estado, inicio, fin, pageable);
    }

    public String imprimirPreGasto(Long id, Long sucId) {
        PreGasto preGasto = service.findByIdAndSucursalId(id, sucId);
        if (preGasto != null) {
            return impresionService.imprimirPreGasto(preGasto);
        }
        return "";
    }

    public EnteFinancialSummaryDTO getEnteFinancialSummary(Long enteId) {
        return service.getFinancialSummary(enteId);
    }

    public List<PreGastoStatusMetadataDTO> getPreGastoStatusMetadataList() {
        return service.getStatusMetadataList();
    }

    public PreGasto savePreGasto(PreGastoInput input) {
        ModelMapper m = new ModelMapper();
        PreGasto e = m.map(input, PreGasto.class);
        String desc = input.getDescripcion() != null ? input.getDescripcion() : "";
        StringBuilder extras = new StringBuilder();
        if (input.getNivelUrgencia() != null && !input.getNivelUrgencia().equals("NORMAL")) {
            extras.append("[URGENCIA: ").append(input.getNivelUrgencia()).append("] ");
        }
        if (input.getObservaciones() != null && !input.getObservaciones().isEmpty()) {
            extras.append("[OBS: ").append(input.getObservaciones()).append("] ");
        }

        if (extras.length() > 0) {
            e.setDescripcion(desc + (desc.isEmpty() ? "" : " | ") + extras.toString().trim());
        }

        if (input.getFechaVencimiento() != null && !input.getFechaVencimiento().isEmpty()) {
            try {
                e.setFechaVencimiento(
                        LocalDateTime.parse(input.getFechaVencimiento(), DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception ex) {
                // Ignore parse errors or use default
            }
        }

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getFuncionarioId() != null) {
            e.setFuncionario(personaService.findById(input.getFuncionarioId()).orElse(null));
        }
        if (input.getTipoGastoId() != null) {
            e.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getSucursalCajaId() != null) {
            e.setSucursalCaja(sucursalService.findById(input.getSucursalCajaId()).orElse(null));
        }
        if (input.getAutorizadoPorId() != null) {
            e.setAutorizadoPor(personaService.findById(input.getAutorizadoPorId()).orElse(null));
        }
        if (input.getDelegadoAId() != null) {
            e.setDelegadoA(personaService.findById(input.getDelegadoAId()).orElse(null));
        }
        if (input.getBeneficiarioPersonaId() != null) {
            e.setBeneficiarioPersona(personaService.findById(input.getBeneficiarioPersonaId()).orElse(null));
        }
        if (input.getBeneficiarioProveedorId() != null) {
            e.setBeneficiarioProveedor(proveedorService.findById(input.getBeneficiarioProveedorId()).orElse(null));
        }

        e = service.save(e);

        // Save details finanzas
        if (input.getFinanzas() != null && !input.getFinanzas().isEmpty() && e.getId() != null) {
            // First delete old entries if taking full list update
            List<PreGastoDetalleFinanzas> oldList = preGastoDetalleFinanzasService
                    .findByPreGastoIdAndSucursalId(e.getId(), e.getSucursalId());
            for (PreGastoDetalleFinanzas old : oldList) {
                preGastoDetalleFinanzasService.delete(old);
            }

            // Calcular montoSolicitado como suma de todos los detalles de finanzas
            BigDecimal totalFinanzas = BigDecimal.ZERO;

            for (PreGastoDetalleFinanzasInput fInput : input.getFinanzas()) {
                PreGastoDetalleFinanzas det = new PreGastoDetalleFinanzas();
                det.setPreGasto(e);
                det.setFormaPago(fInput.getFormaPago());
                det.setMonto(fInput.getMonto());
                if (fInput.getMonedaId() != null) {
                    det.setMoneda(monedaService.findById(fInput.getMonedaId()).orElse(null));
                }
                preGastoDetalleFinanzasService.save(det);

                if (fInput.getMonto() != null) {
                    totalFinanzas = totalFinanzas.add(fInput.getMonto());
                }
            }
            e.setMontoSolicitado(totalFinanzas);

            PreGastoDetalleFinanzasInput primerDetalle = input.getFinanzas().get(0);
            if (primerDetalle.getMonedaId() != null) {
                e.setMoneda(monedaService.findById(primerDetalle.getMonedaId()).orElse(null));
            }
            e = service.save(e);
        }

        return e;
    }

    public PreGasto autorizarPreGasto(Long id, Long autorizadorId, Long sucId) {
        PreGasto preGasto = service.autorizar(id, autorizadorId, sucId);
        if (preGasto != null && autorizadorId != null) {
            preGasto.setAutorizadoPor(personaService.findById(autorizadorId).orElse(null));
            preGasto = service.save(preGasto);
        }
        return preGasto;
    }

    public PreGasto rechazarPreGasto(Long id, String motivo, Long sucId) {
        return service.rechazar(id, motivo, sucId);
    }

    public PreGasto tramitarPreGasto(Long id, Long sucId) {
        return service.enviarATramite(id, sucId);
    }

    public PreGasto completarPreGasto(Long id, Long sucId) {
        return service.completar(id, sucId);
    }

    public PreGasto enviarPreGastoATesoreria(Long id, Long sucId, Long usuarioId) {
        return service.enviarATesoreria(id, sucId, usuarioId);
    }

    public Boolean deletePreGasto(Long id, Long sucId) {
        PreGasto e = service.findByIdAndSucursalId(id, sucId);
        return service.delete(e);
    }
}
