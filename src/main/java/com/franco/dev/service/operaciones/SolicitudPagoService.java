package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.service.financiero.CambioService;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SolicitudPagoService extends CrudService<SolicitudPago, SolicitudPagoRepository, Long> {
    /**
     * Estados en los que una nota puede incluirse en una solicitud de pago.
     * Solo RECEPCION_COMPLETA: la recepción física debe estar finalizada.
     * CONCILIADA = ítems verificados y listos para recepción física, no implica que ya se haya recepcionado.
     */
    private static final List<NotaRecepcionEstado> ESTADOS_ELEGIBLES_PAGO = Arrays.asList(
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
    private final CambioService cambioService;

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
     * Eligible: estado RECEPCION_COMPLETA (recepción física finalizada), pagado null/false, not already in a solicitud.
     */
    public NotaRecepcion getNotaDisponibleParaPagoPorNumero(Integer numero, Long proveedorId) {
        if (numero == null || proveedorId == null) {
            return null;
        }
        List<NotaRecepcion> candidatas = notaRecepcionRepository.findDisponiblesParaPagoPorNumeroYProveedor(numero, proveedorId, ESTADOS_ELEGIBLES_PAGO);
        for (NotaRecepcion nota : candidatas) {
            if (!solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(nota.getId())) {
                return nota;
            }
        }
        return null;
    }

    /**
     * Get all NotaRecepcion eligible for payment for a proveedor (not yet in any solicitud).
     * Usado en: Desktop Sí (diálogo Adicionar nota en nueva solicitud de pago).
     */
    @Transactional(readOnly = true)
    public List<NotaRecepcion> getNotasDisponiblesParaPagoPorProveedor(Long proveedorId) {
        if (proveedorId == null) {
            return new ArrayList<>();
        }
        List<NotaRecepcion> candidatas = notaRecepcionRepository.findDisponiblesParaPagoPorProveedor(
            proveedorId, ESTADOS_ELEGIBLES_PAGO);
        List<NotaRecepcion> resultado = new ArrayList<>();
        for (NotaRecepcion nota : candidatas) {
            if (!solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(nota.getId())) {
                resultado.add(nota);
            }
        }
        return resultado;
    }

    /**
     * Paginated: NotaRecepcion eligible for payment for a proveedor (not in any solicitud). Filter in SQL for scale.
     * filtroTexto: optional; filters by numero (LIKE). Empty/null = no filter.
     * Usado en: Desktop Sí (diálogo Adicionar nota, tabla paginada).
     */
    @Transactional(readOnly = true)
    public Page<NotaRecepcion> getNotasDisponiblesParaPagoPorProveedorPaginated(
            Long proveedorId, int page, int size, String filtroTexto) {
        if (proveedorId == null) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(0, Math.max(1, size)), 0);
        }
        String filtroNumeroPattern = (filtroTexto != null && !filtroTexto.trim().isEmpty())
            ? "%" + filtroTexto.trim() + "%" : null;
        return notaRecepcionRepository.findDisponiblesParaPagoPorProveedorPage(
            proveedorId, ESTADOS_ELEGIBLES_PAGO, filtroNumeroPattern, PageRequest.of(page, size));
    }

    /**
     * Obtiene datos iniciales para crear una solicitud de pago desde una recepcion de mercaderia.
     * Incluye notas elegibles (estado RECEPCION_COMPLETA, no pagadas, no incluidas en otra solicitud),
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
     * Get notas de recepción disponibles para pago para un pedido.
     * Solo notas con recepción física finalizada (RECEPCION_COMPLETA), no pagadas.
     */
    public List<NotaRecepcion> getNotasDisponiblesParaPago(Long pedidoId) {
        List<NotaRecepcion> todasLasNotas = notaRecepcionRepository.findByPedidoId(pedidoId);
        return todasLasNotas.stream()
            .filter(nota -> ESTADOS_ELEGIBLES_PAGO.contains(nota.getEstado()))
            .filter(nota -> nota.getPagado() == null || !nota.getPagado())
            .collect(Collectors.toList());
    }
    
    /**
     * Monto a pagar de la nota: valor descontando rechazos (RecepcionMercaderiaItem.cantidadRechazada).
     * Respeta atomicidad por etapa: la nota no se modifica en recepción; el rechazo se refleja aquí.
     */
    public Double calcularMontoNota(NotaRecepcion notaRecepcion) {
        Double total = notaRecepcionRepository.valorTotalConRechazos(notaRecepcion.getId());
        return total != null ? total : notaRecepcionRepository.valorTotal(notaRecepcion.getId());
    }

    /**
     * Igual que {@link #calcularMontoNota} pero convertido a la moneda cabecera de la solicitud,
     * usando la cotización propia de la nota (fallback último Cambio de la moneda).
     * Si moneda cabecera == moneda nota o cualquiera es null → retorna valor raw.
     */
    public Double calcularMontoNotaEnMoneda(NotaRecepcion nota, Moneda monedaCabecera) {
        Double valorRaw = calcularMontoNota(nota);
        if (valorRaw == null || valorRaw == 0.0) return 0.0;
        Moneda monedaNota = nota.getMoneda();
        if (monedaCabecera == null || monedaNota == null
                || monedaCabecera.getId() == null
                || monedaCabecera.getId().equals(monedaNota.getId())) {
            return valorRaw;
        }
        boolean monedaNotaEsGs = MONEDA_GUARANI.equalsIgnoreCase(monedaNota.getDenominacion());
        boolean monedaCabEsGs = MONEDA_GUARANI.equalsIgnoreCase(monedaCabecera.getDenominacion());
        Double cotNota = (nota.getCotizacion() != null && nota.getCotizacion() > 0)
                ? nota.getCotizacion()
                : ultimoCambioEnGs(monedaNota);
        Double valorEnGs = monedaNotaEsGs ? valorRaw : valorRaw * cotNota;
        if (monedaCabEsGs) return valorEnGs;
        // Caso defensivo: cabecera no-Gs distinta de la nota — convertir Gs → cabecera vía último Cambio.
        Double cambioCab = ultimoCambioEnGs(monedaCabecera);
        return cambioCab > 0 ? valorEnGs / cambioCab : valorEnGs;
    }

    /** Última cotización conocida (Cambio.valorEnGs) de la moneda; 1.0 si no se encuentra. */
    private Double ultimoCambioEnGs(Moneda moneda) {
        if (moneda == null || moneda.getId() == null) return 1.0;
        try {
            Cambio cambio = cambioService.findLastByMonedaId(moneda.getId());
            if (cambio != null && cambio.getValorEnGs() != null && cambio.getValorEnGs() > 0) {
                return cambio.getValorEnGs();
            }
        } catch (Exception ignored) {
            // Sin cambio registrado → fallback 1.0 (suma raw, mejor que romper).
        }
        return 1.0;
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
        List<NotaRecepcion> notas = (notaRecepcionIds != null && !notaRecepcionIds.isEmpty()) 
            ? notaRecepcionRepository.findAllById(notaRecepcionIds) 
            : new ArrayList<>();
            
        for (NotaRecepcion nota : notas) {
            if (nota.getPedido() != null && nota.getPedido().getProveedor() != null) {
                if (!nota.getPedido().getProveedor().getId().equals(proveedor.getId())) {
                    throw new IllegalArgumentException("Todas las notas deben pertenecer al mismo proveedor");
                }
            }
        }
        
        // Calculate total amount — converte a la moneda cabecera de la solicitud cuando difiere de la nota.
        Double montoTotal = notas.stream()
            .mapToDouble(n -> calcularMontoNotaEnMoneda(n, moneda))
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
        solicitud.setTipo(com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.COMPRA);

        SolicitudPago solicitudGuardada = save(solicitud);

        // Associate notas with solicitud — montoIncluido también en moneda cabecera para que recálculos sumen consistente.
        for (NotaRecepcion nota : notas) {
            Double montoNota = calcularMontoNotaEnMoneda(nota, moneda);
            solicitudPagoNotaRecepcionService.agregarNotaASolicitud(
                solicitudGuardada.getId(), nota.getId(), montoNota);
        }
        
        // Iniciar etapa SOLICITUD_PAGO si hay notas vinculadas a pedidos
        if (!notas.isEmpty() && notas.get(0).getPedido() != null) {
            Long pedidoId = notas.get(0).getPedido().getId();
            try {
                procesoEtapaService.actualizarEtapaAEnProceso(pedidoId, ProcesoEtapaTipo.SOLICITUD_PAGO);
            } catch (Exception e) {
                System.out.println("Etapa SOLICITUD_PAGO no encontrada, esto es normal si viene de recepción mercadería o pre-gasto");
            }
        }
        
        return solicitudGuardada;
    }

    /**
     * Crea una SolicitudPago de tipo GASTO, lista para pagar (estado SOLICITADO). El proveedor
     * (beneficiario) es opcional. Es la obligación de pago de un gasto (el documento del gasto
     * vive en PreGasto). Reutiliza el mismo motor de pago que las compras.
     */
    @Transactional
    public SolicitudPago crearSolicitudGasto(com.franco.dev.domain.personas.Proveedor beneficiario,
                                             com.franco.dev.domain.financiero.TipoGasto categoria,
                                             Moneda moneda, Double montoTotal, String observaciones,
                                             java.time.LocalDateTime fechaVencimiento,
                                             com.franco.dev.domain.personas.Usuario usuario) {
        SolicitudPago solicitud = new SolicitudPago();
        solicitud.setTipo(com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO);
        solicitud.setTipoGasto(categoria);
        solicitud.setProveedor(beneficiario); // puede ser null
        solicitud.setMoneda(moneda);
        solicitud.setMontoTotal(montoTotal);
        solicitud.setObservaciones(observaciones);
        solicitud.setFechaPagoPropuesta(fechaVencimiento);
        solicitud.setUsuario(usuario);
        // save() fuerza PENDIENTE en el alta; se re-marca SOLICITADO (lista para pagar directo).
        SolicitudPago guardada = save(solicitud);
        guardada.setEstado(SolicitudPagoEstado.SOLICITADO);
        return save(guardada);
    }

    /**
     * Crea la obligacion de pago de un vale de RRHH (tipo RRHH), lista para pagar
     * (estado SOLICITADO). No tiene proveedor ni notas: el documento es el vale.
     */
    public SolicitudPago crearSolicitudVale(Moneda moneda, Double montoTotal, String observaciones,
                                            com.franco.dev.domain.personas.Usuario usuario) {
        SolicitudPago solicitud = new SolicitudPago();
        solicitud.setTipo(com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.RRHH);
        solicitud.setMoneda(moneda);
        solicitud.setMontoTotal(montoTotal);
        solicitud.setObservaciones(observaciones);
        solicitud.setUsuario(usuario);
        // save() fuerza PENDIENTE en el alta; se re-marca SOLICITADO (lista para pagar directo).
        SolicitudPago guardada = save(solicitud);
        guardada.setEstado(SolicitudPagoEstado.SOLICITADO);
        return save(guardada);
    }

    /**
     * Update solicitud pago (solo cuando estado es PENDIENTE).
     * Actualiza moneda, formaPago, fechaPagoPropuesta, observaciones y sincroniza notas.
     * Usado en: Desktop Sí (editar pago desde lista).
     */
    @Transactional
    public SolicitudPago actualizarSolicitudPago(Long solicitudId, Long monedaId, Long formaPagoId,
            LocalDateTime fechaPagoPropuesta, String observaciones, List<Long> nuevaListaNotaIds) {
        SolicitudPago solicitud = findById(solicitudId).orElseThrow(
            () -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudId)
        );
        if (solicitud.getEstado() != SolicitudPagoEstado.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden editar solicitudes en estado PENDIENTE");
        }
        Moneda moneda = monedaRepository.findById(monedaId)
            .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada"));
        FormaPago formaPago = formaPagoId != null
            ? formaPagoRepository.findById(formaPagoId)
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada"))
            : null;
        solicitud.setMoneda(moneda);
        solicitud.setFormaPago(formaPago);
        solicitud.setFechaPagoPropuesta(fechaPagoPropuesta);
        solicitud.setObservaciones(observaciones);
        List<SolicitudPagoNotaRecepcion> actuales = solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudId);
        Set<Long> idsActuales = actuales.stream()
            .map(r -> r.getNotaRecepcion().getId())
            .collect(Collectors.toSet());
        Set<Long> idsNuevos = nuevaListaNotaIds != null
            ? new java.util.HashSet<>(nuevaListaNotaIds)
            : new java.util.HashSet<>();
        for (SolicitudPagoNotaRecepcion rel : actuales) {
            if (!idsNuevos.contains(rel.getNotaRecepcion().getId())) {
                solicitudPagoNotaRecepcionService.removerNotaDeSolicitud(solicitudId, rel.getNotaRecepcion().getId());
            }
        }
        for (Long notaId : idsNuevos) {
            if (!idsActuales.contains(notaId)) {
                NotaRecepcion nota = notaRecepcionRepository.findById(notaId)
                    .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada: " + notaId));
                // Solicitudes de GASTO/RRHH no tienen proveedor: solo validar la coincidencia
                // cuando ambos lados lo tienen (evita NPE con proveedor nullable).
                if (solicitud.getProveedor() != null && nota.getPedido() != null
                        && nota.getPedido().getProveedor() != null
                        && !nota.getPedido().getProveedor().getId().equals(solicitud.getProveedor().getId())) {
                    throw new IllegalArgumentException("La nota debe pertenecer al mismo proveedor");
                }
                Double monto = calcularMontoNotaEnMoneda(nota, solicitud.getMoneda());
                solicitudPagoNotaRecepcionService.agregarNotaASolicitud(solicitudId, notaId, monto);
            }
        }
        solicitudPagoNotaRecepcionService.recalcularMontoTotalSolicitud(solicitudId);
        return findById(solicitudId).orElse(solicitud);
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
        if (solicitud.getEstado() == SolicitudPagoEstado.CANCELADO) {
            throw new IllegalStateException("Una solicitud cancelada no puede cambiar de estado");
        }
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
        
        // If changing to CANCELADO, free the notas and record which ones were linked
        if (nuevoEstado == SolicitudPagoEstado.CANCELADO) {
            liberarNotasYRegistrarEnObservaciones(solicitud);
        }
        
        return save(solicitud);
    }
    
    /**
     * Validate state transitions
     */
    private boolean isValidStateTransition(SolicitudPagoEstado estadoActual, SolicitudPagoEstado nuevoEstado) {
        // Define valid transitions.
        // PENDIENTE (borrador) → SOLICITADO (solicitar) o CANCELADO.
        // SOLICITADO (validada) → PENDIENTE (reabrir), o pagos/cancelación.
        switch (estadoActual) {
            case PENDIENTE:
                return nuevoEstado == SolicitudPagoEstado.SOLICITADO ||
                       nuevoEstado == SolicitudPagoEstado.PARCIAL ||
                       nuevoEstado == SolicitudPagoEstado.CONCLUIDO ||
                       nuevoEstado == SolicitudPagoEstado.CANCELADO;
            case SOLICITADO:
                return nuevoEstado == SolicitudPagoEstado.PENDIENTE ||
                       nuevoEstado == SolicitudPagoEstado.PARCIAL ||
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
     * When solicitud is canceled: record linked notas in observaciones, then free them
     * so they can be re-linked to a new solicitud.
     */
    private void liberarNotasYRegistrarEnObservaciones(SolicitudPago solicitud) {
        Long solicitudId = solicitud.getId();
        List<SolicitudPagoNotaRecepcion> relaciones = solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudId);
        if (!relaciones.isEmpty()) {
            String numerosNotas = relaciones.stream()
                .map(r -> r.getNotaRecepcion())
                .filter(n -> n != null)
                .map(n -> "Nº" + (n.getNumero() != null ? n.getNumero() : n.getId()))
                .collect(Collectors.joining(", "));
            String observacionCancelado = "CANCELADO - Notas vinculadas al cancelar (liberadas): " + numerosNotas;
            String obsActual = solicitud.getObservaciones();
            solicitud.setObservaciones(
                (obsActual != null && !obsActual.trim().isEmpty())
                    ? obsActual.trim() + " | " + observacionCancelado
                    : observacionCancelado
            );
        }
        solicitudPagoNotaRecepcionService.eliminarTodasRelaciones(solicitudId);
        solicitud.setMontoTotal(0.0);
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
     * Inverso de {@link #marcarNotasComoPagadas}: al anular un pago y reabrir la solicitud
     * (CONCLUIDO → SOLICITADO/PARCIAL), las notas de recepción vuelven a estado no pagado.
     * Idempotente: si la nota ya está en false, es un no-op. Público porque lo invoca
     * PagoProveedorService desde el flujo de anulación de pago CPP.
     */
    @Transactional
    public void desmarcarNotasComoPagadas(Long solicitudId) {
        List<SolicitudPagoNotaRecepcion> relaciones =
            solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudId);

        for (SolicitudPagoNotaRecepcion relacion : relaciones) {
            NotaRecepcion nota = relacion.getNotaRecepcion();
            if (nota != null) {
                nota.setPagado(false);
                notaRecepcionRepository.save(nota);
            }
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

