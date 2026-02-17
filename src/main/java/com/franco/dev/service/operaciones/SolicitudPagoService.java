package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.dto.DatosInicialesSolicitudPagoDTO;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SolicitudPagoService extends CrudService<SolicitudPago, SolicitudPagoRepository, Long> {
    private static final List<NotaRecepcionEstado> ESTADOS_ELEGIBLES_PAGO = Arrays.asList(
        NotaRecepcionEstado.CONCILIADA,
        NotaRecepcionEstado.RECEPCION_COMPLETA
    );
    private static final String MONEDA_GUARANI = "GUARANI";
    private static final String FORMA_PAGO_EFECTIVO = "EFECTIVO";

    private final SolicitudPagoRepository repository;
    private final SolicitudPagoNotaRecepcionService solicitudPagoNotaRecepcionService;
    private final NotaRecepcionRepository notaRecepcionRepository;
    private final ProcesoEtapaService procesoEtapaService;
    private final RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;
    private final RecepcionMercaderiaService recepcionMercaderiaService;
    private final MonedaRepository monedaRepository;
    private final FormaPagoRepository formaPagoRepository;

    @Override
    public SolicitudPagoRepository getRepository() {
        return repository;
    }

    @Override
    public SolicitudPago save(SolicitudPago entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setFechaSolicitud(LocalDateTime.now());
            entity.setEstado(SolicitudPagoEstado.PENDIENTE);
            
            // Generate unique numero_solicitud
            if (entity.getNumeroSolicitud() == null || entity.getNumeroSolicitud().isEmpty()) {
                entity.setNumeroSolicitud(generateNumeroSolicitud());
            }
        }
        return super.save(entity);
    }
    
    /**
     * Generate unique numero solicitud
     */
    private String generateNumeroSolicitud() {
        // Get current count of solicitudes for sequential numbering
        long count = repository.count();
        return "SP-" + String.format("%06d", count + 1);
    }
    
    /**
     * Find all SolicitudPago by pedido ID
     * @param pedidoId The ID of the pedido
     * @return List of SolicitudPago associated with the pedido
     */
    public List<SolicitudPago> getSolicitudesPorPedido(Long pedidoId) {
        return repository.findByPedidoId(pedidoId);
    }
    
    /**
     * Find all SolicitudPago by pago ID
     * @param pagoId The ID of the pago
     * @return List of SolicitudPago associated with the pago
     */
    public List<SolicitudPago> findByPagoId(Long pagoId) {
        return repository.findByPagoId(pagoId);
    }
    
    /**
     * Find all SolicitudPago by estado
     * @param estado The estado of the solicitud pago
     * @return List of SolicitudPago with the specified estado
     */
    public List<SolicitudPago> findByEstado(SolicitudPagoEstado estado) {
        return repository.findByEstado(estado);
    }
    
    /**
     * Find all SolicitudPago by proveedor ID
     * @param proveedorId The ID of the proveedor
     * @return List of SolicitudPago associated with the proveedor
     */
    public List<SolicitudPago> findByProveedorId(Long proveedorId) {
        return repository.findByProveedorId(proveedorId);
    }
    
    /**
     * Get a single NotaRecepcion eligible for payment by numero and proveedor.
     * Eligible: estado CONCILIADA or RECEPCION_COMPLETA, pagado null/false, not already in a solicitud.
     * @param numero Nota number
     * @param proveedorId Proveedor ID (nota must belong to a pedido of this proveedor)
     * @return First eligible NotaRecepcion or null
     */
    public NotaRecepcion getNotaDisponibleParaPagoPorNumero(Integer numero, Long proveedorId) {
        if (numero == null || proveedorId == null) {
            return null;
        }
        List<NotaRecepcionEstado> estados = Arrays.asList(
            NotaRecepcionEstado.CONCILIADA,
            NotaRecepcionEstado.RECEPCION_COMPLETA
        );
        List<NotaRecepcion> candidatas = notaRecepcionRepository.findDisponiblesParaPagoPorNumeroYProveedor(numero, proveedorId, estados);
        for (NotaRecepcion nota : candidatas) {
            if (!solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(nota.getId())) {
                return nota;
            }
        }
        return null;
    }

    /**
     * Obtiene datos iniciales para crear una solicitud de pago desde una recepcion de mercaderia.
     * Incluye notas elegibles (estado CONCILIADA/RECEPCION_COMPLETA, no pagadas, no incluidas en otra solicitud),
     * moneda sugerida (misma que notas; si difieren usar GUARANI), forma de pago (misma que pedidos; si difieren usar EFECTIVO),
     * y fecha de pago propuesta (hoy mas plazo mas largo entre los pedidos, si aplica).
     * Usado en: Desktop No; Mobile Si (pantalla nueva solicitud de pago desde recepcion).
     */
    @Transactional(readOnly = true)
    public DatosInicialesSolicitudPagoDTO getDatosInicialesSolicitudPagoPorRecepcion(Long recepcionMercaderiaId) {
        if (recepcionMercaderiaId == null) {
            return new DatosInicialesSolicitudPagoDTO(new ArrayList<>(), null, null, null);
        }
        RecepcionMercaderia recepcion = recepcionMercaderiaService.findById(recepcionMercaderiaId)
            .orElse(null);
        if (recepcion == null) {
            return new DatosInicialesSolicitudPagoDTO(new ArrayList<>(), null, null, null);
        }
        List<RecepcionMercaderiaNota> asociaciones = recepcionMercaderiaNotaService.findByRecepcionMercaderiaId(recepcionMercaderiaId);
        List<NotaRecepcion> notasElegibles = new ArrayList<>();
        for (RecepcionMercaderiaNota rmn : asociaciones) {
            NotaRecepcion nota = rmn.getNotaRecepcion();
            if (nota == null) continue;
            if (!ESTADOS_ELEGIBLES_PAGO.contains(nota.getEstado())) continue;
            if (Boolean.TRUE.equals(nota.getPagado())) continue;
            if (solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(nota.getId())) continue;
            notasElegibles.add(nota);
        }
        Long monedaId = resolverMonedaId(notasElegibles, recepcion);
        Long formaPagoId = resolverFormaPagoId(notasElegibles);
        String fechaPagoPropuesta = resolverFechaPagoPropuesta(notasElegibles);
        return new DatosInicialesSolicitudPagoDTO(notasElegibles, monedaId, formaPagoId, fechaPagoPropuesta);
    }

    private Long resolverMonedaId(List<NotaRecepcion> notas, RecepcionMercaderia recepcion) {
        Set<Long> monedaIds = notas.stream()
            .map(NotaRecepcion::getMoneda)
            .filter(m -> m != null && m.getId() != null)
            .map(Moneda::getId)
            .collect(Collectors.toSet());
        if (monedaIds.isEmpty()) {
            Moneda guaranies = monedaRepository.findByDenominacion(MONEDA_GUARANI);
            return guaranies != null ? guaranies.getId() : null;
        }
        if (monedaIds.size() == 1) {
            return monedaIds.iterator().next();
        }
        Moneda guaranies = monedaRepository.findByDenominacion(MONEDA_GUARANI);
        return guaranies != null ? guaranies.getId() : monedaIds.iterator().next();
    }

    private Long resolverFormaPagoId(List<NotaRecepcion> notas) {
        Set<Long> formaPagoIds = notas.stream()
            .map(NotaRecepcion::getPedido)
            .filter(p -> p != null && p.getFormaPago() != null)
            .map(p -> p.getFormaPago().getId())
            .collect(Collectors.toSet());
        if (formaPagoIds.isEmpty() || formaPagoIds.size() > 1) {
            return formaPagoRepository.findFirstByDescripcionIgnoreCase(FORMA_PAGO_EFECTIVO)
                .map(FormaPago::getId)
                .orElse(formaPagoIds.isEmpty() ? null : formaPagoIds.iterator().next());
        }
        return formaPagoIds.iterator().next();
    }

    private String resolverFechaPagoPropuesta(List<NotaRecepcion> notas) {
        int maxPlazo = notas.stream()
            .map(NotaRecepcion::getPedido)
            .filter(p -> p != null && p.getPlazoCredito() != null)
            .mapToInt(Pedido::getPlazoCredito)
            .max()
            .orElse(0);
        LocalDate fecha = maxPlazo > 0 ? LocalDate.now().plusDays(maxPlazo) : LocalDate.now();
        return fecha.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Get notas de recepcion available for payment for a specific pedido
     * @param pedidoId The ID of the pedido
     * @return List of NotaRecepcion available for payment
     */
    public List<NotaRecepcion> getNotasDisponiblesParaPago(Long pedidoId) {
        // Find notas with estado CONCILIADA that are not yet paid
        List<NotaRecepcion> todasLasNotas = notaRecepcionRepository.findByPedidoId(pedidoId);
        
        return todasLasNotas.stream()
            .filter(nota -> nota.getEstado() == NotaRecepcionEstado.CONCILIADA)
            .filter(nota -> nota.getPagado() == null || !nota.getPagado())
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate the total amount for a nota recepcion
     * @param notaRecepcion The nota recepcion
     * @return The total amount
     */
    public Double calcularMontoNota(NotaRecepcion notaRecepcion) {
        // Use the repository method that already calculates the total correctly
        return notaRecepcionRepository.valorTotal(notaRecepcion.getId());
    }

    /**
     * Create a solicitud de pago with multiple notas de recepcion
     * @param proveedor The proveedor
     * @param notaRecepcionIds List of nota recepcion IDs
     * @param moneda The moneda
     * @param formaPago The forma de pago
     * @param fechaPagoPropuesta The proposed payment date
     * @param observaciones Observations
     * @param usuario The user creating the solicitud
     * @return The created SolicitudPago
     */
    @Transactional
    public SolicitudPago crearSolicitudPago(Proveedor proveedor,
                                            List<Long> notaRecepcionIds,
                                            Moneda moneda,
                                            FormaPago formaPago,
                                            LocalDateTime fechaPagoPropuesta,
                                            String observaciones,
                                                              Usuario usuario) {
        
        // Validate that all notas belong to the same proveedor
        List<NotaRecepcion> notas = notaRecepcionRepository.findAllById(notaRecepcionIds);
        for (NotaRecepcion nota : notas) {
            if (!nota.getPedido().getProveedor().getId().equals(proveedor.getId())) {
                throw new IllegalArgumentException("Todas las notas deben pertenecer al mismo proveedor");
            }
        }
        
        // Calculate total amount
        Double montoTotal = notas.stream()
            .mapToDouble(this::calcularMontoNota)
            .sum();
        
        // Create solicitud pago
        SolicitudPago solicitud = new SolicitudPago();
        solicitud.setProveedor(proveedor);
        solicitud.setMontoTotal(montoTotal);
        solicitud.setMoneda(moneda);
        solicitud.setFormaPago(formaPago);
        solicitud.setFechaPagoPropuesta(fechaPagoPropuesta);
        solicitud.setObservaciones(observaciones);
        solicitud.setUsuario(usuario);
        solicitud.setEstado(SolicitudPagoEstado.PENDIENTE);
        
        SolicitudPago solicitudGuardada = save(solicitud);
        
        // Associate notas with solicitud
        for (NotaRecepcion nota : notas) {
            Double montoNota = calcularMontoNota(nota);
            solicitudPagoNotaRecepcionService.agregarNotaASolicitud(
                solicitudGuardada.getId(), nota.getId(), montoNota);
        }
        
        // Iniciar etapa SOLICITUD_PAGO si es la primera solicitud del pedido
        Long pedidoId = notas.get(0).getPedido().getId();
        try {
            procesoEtapaService.actualizarEtapaAEnProceso(pedidoId, ProcesoEtapaTipo.SOLICITUD_PAGO);
        } catch (Exception e) {
            // La etapa podría no existir aún, crear si es necesario
            // Esto ocurre si se salta directamente a solicitud de pago
            System.out.println("Etapa SOLICITUD_PAGO no encontrada, esto es normal si viene de recepción mercadería");
        }
        
        return solicitudGuardada;
    }

    /**
     * Update estado of solicitud pago
     * @param solicitudId The ID of the solicitud
     * @param nuevoEstado The new estado
     * @return The updated SolicitudPago
     */
    @Transactional
    public SolicitudPago actualizarEstado(Long solicitudId, SolicitudPagoEstado nuevoEstado) {
        SolicitudPago solicitud = findById(solicitudId).orElseThrow(
            () -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudId)
        );
        
        // Validate state transitions
        if (!isValidStateTransition(solicitud.getEstado(), nuevoEstado)) {
            throw new IllegalStateException("Transición de estado inválida de " + 
                solicitud.getEstado() + " a " + nuevoEstado);
        }
        
        solicitud.setEstado(nuevoEstado);
        
        // If changing to CONCLUIDO, mark notas as paid
        if (nuevoEstado == SolicitudPagoEstado.CONCLUIDO) {
            marcarNotasComoPagadas(solicitudId);
        }
        
        return save(solicitud);
    }
    
    /**
     * Validate state transitions
     */
    private boolean isValidStateTransition(SolicitudPagoEstado estadoActual, SolicitudPagoEstado nuevoEstado) {
        // Define valid transitions
        switch (estadoActual) {
            case PENDIENTE:
                return nuevoEstado == SolicitudPagoEstado.PARCIAL || 
                       nuevoEstado == SolicitudPagoEstado.CONCLUIDO ||
                       nuevoEstado == SolicitudPagoEstado.CANCELADO;
            case PARCIAL:
                return nuevoEstado == SolicitudPagoEstado.CONCLUIDO || 
                       nuevoEstado == SolicitudPagoEstado.CANCELADO;
            case CONCLUIDO:
            case CANCELADO:
                return false; // Final states
            default:
                return false;
        }
    }
    
    /**
     * Mark all notas as paid when solicitud is paid
     */
    private void marcarNotasComoPagadas(Long solicitudId) {
        List<SolicitudPagoNotaRecepcion> relaciones = 
            solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudId);
        
        for (SolicitudPagoNotaRecepcion relacion : relaciones) {
            NotaRecepcion nota = relacion.getNotaRecepcion();
            nota.setPagado(true);
            notaRecepcionRepository.save(nota);
        }
    }
    
    /**
     * Delete solicitud pago
     * @param solicitudId The ID of the solicitud to delete
     * @return true if deleted successfully
     */
    @Transactional
    public Boolean eliminarSolicitud(Long solicitudId) {
        SolicitudPago solicitud = findById(solicitudId).orElseThrow(
            () -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudId)
        );
        
        // Only allow deletion if in PENDIENTE state
        if (solicitud.getEstado() != SolicitudPagoEstado.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden eliminar solicitudes en estado PENDIENTE");
        }
        
        // Delete relationships first (cascade should handle this, but being explicit)
        solicitudPagoNotaRecepcionService.eliminarTodasRelaciones(solicitudId);
        
        // Delete the solicitud
        repository.deleteById(solicitudId);
        
        return true;
    }
    
    /**
     * Get all notas asociadas with a solicitud pago
     */
    public List<NotaRecepcion> getNotasAsociadas(Long solicitudPagoId) {
        List<SolicitudPagoNotaRecepcion> relaciones = 
            solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudPagoId);
        
        return relaciones.stream()
            .map(SolicitudPagoNotaRecepcion::getNotaRecepcion)
            .collect(Collectors.toList());
    }
    

}

