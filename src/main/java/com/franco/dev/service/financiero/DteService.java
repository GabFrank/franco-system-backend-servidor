package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.domain.financiero.DteEstado;
import com.franco.dev.domain.financiero.EventoTipo;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.repository.financiero.EventoDteRepository;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;

import java.util.ArrayList;
import java.util.List;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;

@Service
@AllArgsConstructor
public class DteService {

    private final DocumentoElectronicoRepository documentoElectronicoRepository;
    private final EventoDteRepository eventoDteRepository;
    private final FacturaLegalRepository facturaLegalRepository;
    private final DteNodeClient dteNodeClient;
    private final UsuarioService usuarioService;

    public UsuarioService getUsuarioService() {
        return usuarioService;
    }

    @Transactional
    public DocumentoElectronico iniciarGeneracionDte(Long ventaId, Long sucursalId, Long usuarioId) {
        FacturaLegal facturaLegal = facturaLegalRepository.findByVentaIdAndSucursalId(ventaId, sucursalId);
        if (facturaLegal == null) return null;

        // Validaciones previas, bloquea si hay errores
        List<String> errores = validarFacturaLegalParaDte(facturaLegal);
        if (!errores.isEmpty()) {
            throw new GraphQLException("Validación DTE: " + String.join("; ", errores));
        }

        DocumentoElectronico existente = documentoElectronicoRepository.findAll()
                .stream()
                .filter(d -> d.getFacturaLegal() != null
                        && d.getFacturaLegal().getId().equals(facturaLegal.getId())
                        && d.getFacturaLegal().getSucursalId().equals(facturaLegal.getSucursalId()))
                .findFirst()
                .orElse(null);
        if (existente != null) return existente;

        DocumentoElectronico dte = new DocumentoElectronico();
        dte.setEstadoSifen(DteEstado.PENDIENTE.name());
        dte.setFacturaLegal(facturaLegal);
        if (usuarioId != null) dte.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        dte = documentoElectronicoRepository.save(dte);
        generarYFirmarXmlConNode(dte.getId(), usuarioId);
        return dte;
    }

    public Page<DocumentoElectronico> findAll(int page, int size) {
        return documentoElectronicoRepository.findAll(PageRequest.of(page, size));
    }

    public Page<DocumentoElectronico> findFiltered(String estado, String fechaDesde, String fechaHasta, int page, int size, String cdc, Long sucursalId) {
        PageRequest pr = PageRequest.of(page, size);
        boolean hasEstado = estado != null;
        boolean hasDesde = fechaDesde != null;
        boolean hasHasta = fechaHasta != null;
        boolean hasCdc = cdc != null    ;
        boolean hasSuc = sucursalId != null;
        
        if (hasCdc) return documentoElectronicoRepository.findByCdcContainingIgnoreCase(cdc, pr);
        
        if (hasSuc && !hasDesde && !hasHasta && !hasEstado) {
            return documentoElectronicoRepository.findByFacturaLegal_SucursalId(sucursalId, pr);
        }
        if (hasSuc && !hasDesde && !hasHasta && hasEstado) {
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndEstadoSifen(sucursalId, estado, pr);
        }
        if (hasSuc && (hasDesde || hasHasta) && !hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndCreadoEnBetween(sucursalId, desde, hasta, pr);
        }
        if (hasSuc && (hasDesde || hasHasta) && hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndEstadoSifenAndCreadoEnBetween(sucursalId, estado, desde, hasta, pr);
        }
        
        if (!hasDesde && !hasHasta && hasEstado) {
            return documentoElectronicoRepository.findByEstadoSifen(estado, pr);
        }
        
        if ((hasDesde || hasHasta) && !hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByCreadoEnBetween(desde, hasta, pr);
        }
        
        if ((hasDesde || hasHasta) && hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByEstadoSifenAndCreadoEnBetween(estado, desde, hasta, pr);
        }
        
        return documentoElectronicoRepository.findAll(pr);
    }

    public DocumentoElectronico findById(Long id) {
        return documentoElectronicoRepository.findById(id).orElse(null);
    }

    @Transactional
    public DocumentoElectronico generarDesdeFacturaLegalSiNoExiste(Long ventaId, Long sucursalId, Long usuarioId) {
        FacturaLegal facturaLegal = facturaLegalRepository.findByVentaIdAndSucursalId(ventaId, sucursalId);
        if (facturaLegal == null) return null;
        DocumentoElectronico ya = documentoElectronicoRepository.findFirstByFacturaLegal_IdAndFacturaLegal_SucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());
        if (ya != null) return ya;
        return iniciarGeneracionDte(ventaId, sucursalId, usuarioId);
    }

    @Transactional
    public EventoDte registrarEvento(Long dteId, Integer tipoEvento, Long usuarioId, String motivo, String observacion) {
        DocumentoElectronico dte = documentoElectronicoRepository.findById(dteId).orElse(null);
        if (dte == null) return null;
        EventoDte evento = new EventoDte();
        evento.setDocumentoElectronico(dte);
        evento.setTipoEvento(tipoEvento);
        evento.setFechaEvento(java.time.LocalDateTime.now());
        if (usuarioId != null) {
            evento.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        }
        if (motivo != null) evento.setMotivo(motivo);
        if (observacion != null) evento.setObservacion(observacion);
        evento.setCreadoEn(java.time.LocalDateTime.now());
        // Llamada al Node (mock o real) para registrar el evento
        try {
            DteNodeClient.RegistrarEventoResponse resp = dteNodeClient.registrarEvento(dte.getCdc(), tipoEvento, motivo, observacion);
            if (resp != null) {
                evento.setCdcEvento(resp.getCdcEvento());
                evento.setMensajeRespuestaSifen(resp.getMensaje());
            }
        } catch (Exception ignored) {}
        evento = eventoDteRepository.save(evento);
        // Actualización de estado del DTE según tipo de evento (p. ej., 1 = Cancelación)
        EventoTipo et = EventoTipo.fromCode(tipoEvento);
        if (et == EventoTipo.CANCELACION) {
            dte.setEstadoSifen(DteEstado.CANCELADO.name());
            documentoElectronicoRepository.save(dte);
        }
        return evento;
    }

    public java.util.List<EventoDte> listarEventosPorDte(Long dteId) {
        return eventoDteRepository.findByDocumentoElectronicoIdOrderByIdAsc(dteId);
    }

    @Transactional
    public void generarYFirmarXmlConNode(Long dteId, Long usuarioId) {
        DocumentoElectronico dte = documentoElectronicoRepository.findById(dteId).orElse(null);
        if (dte == null) return;
        if (usuarioId != null) dte.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        Long facturaId = dte.getFacturaLegal() != null ? dte.getFacturaLegal().getId() : null;
        Long sucursalId = dte.getFacturaLegal() != null ? dte.getFacturaLegal().getSucursalId() : null;

        // Validaciones previas a firma/generación
        if (dte.getFacturaLegal() != null) {
            List<String> errores = validarFacturaLegalParaDte(dte.getFacturaLegal());
            if (!errores.isEmpty()) {
                dte.setMensajeSifen("Validación DTE: " + String.join("; ", errores));
                documentoElectronicoRepository.save(dte);
                return;
            }
        }
        DteNodeClient.GenerarDocumentoResponse res = dteNodeClient.generarDocumentoDesdeFactura(facturaId, sucursalId);
        if (res != null) {
            dte.setXmlFirmado(res.getXmlFirmado());
            dte.setCdc(res.getCdc());
            dte.setUrlQr(res.getUrlQr());
            dte.setEstadoSifen(DteEstado.GENERADO.name());
            documentoElectronicoRepository.save(dte);
        }
    }

    // Validaciones mínimas previas según Manual (se ampliarán en real mode)
    private List<String> validarFacturaLegalParaDte(FacturaLegal f) {
        List<String> errores = new ArrayList<>();
        if (f == null) {
            errores.add("Factura no encontrada");
            return errores;
        }
        // Timbrado presente y vigente por fechas
        if (f.getTimbradoDetalle() == null || f.getTimbradoDetalle().getTimbrado() == null) {
            errores.add("Timbrado no asignado");
        } else {
            if (f.getTimbradoDetalle().getTimbrado().getFechaInicio() != null && java.time.LocalDate.now().isBefore(f.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate())) {
                errores.add("Timbrado aún no vigente");
            }
            if (f.getTimbradoDetalle().getTimbrado().getFechaFin() != null && java.time.LocalDate.now().isAfter(f.getTimbradoDetalle().getTimbrado().getFechaFin().toLocalDate())) {
                errores.add("Timbrado vencido");
            }
        }
        // Receptor
        if (f.getNombre() == null || f.getNombre().trim().isEmpty()) errores.add("Nombre del receptor requerido");
        if (f.getRuc() == null || f.getRuc().trim().isEmpty()) {
            errores.add("RUC/CI del receptor requerido");
        } else if (f.getRuc().contains("-")) {
            try {
                String[] parts = f.getRuc().split("-");
                String base = parts[0];
                String dv = parts[1];
                String dvCalc = getDigitoVerificadorString(base);
                if (!dv.equals(dvCalc)) errores.add("RUC con dígito verificador inválido");
            } catch (Exception ignored) {}
        }
        // Totales
        if (f.getTotalFinal() == null || f.getTotalFinal() <= 0) errores.add("Total final inválido");
        return errores;
    }
}


