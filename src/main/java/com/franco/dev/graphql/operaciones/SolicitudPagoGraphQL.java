package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoDetalleInput;
import com.franco.dev.graphql.operaciones.dto.DatosInicialesSolicitudPagoDTO;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.SolicitudPagoDetalleService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.impresion.ImpresionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SolicitudPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(SolicitudPagoGraphQL.class);

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    @Autowired
    private SolicitudPagoNotaRecepcionService solicitudPagoNotaRecepcionService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private FormaPagoService formaPagoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private SolicitudPagoDetalleService solicitudPagoDetalleService;

    // ========== QUERIES ==========

    /**
     * Get single solicitud pago by ID
     */
    public SolicitudPago solicitudPago(Long id) {
        return solicitudPagoService.findById(id).orElse(null);
    }

    /**
     * Get all solicitudes pago for a specific pedido
     */
    public List<SolicitudPago> solicitudesPagoPorPedido(Long pedidoId) {
        return solicitudPagoService.getSolicitudesPorPedido(pedidoId);
    }

    /**
     * Get solicitudes pago with pagination.
     * Usado en:
     * - Desktop: Sí (lista de solicitudes de pago)
     * - Mobile: No
     */
    public Page<SolicitudPago> solicitudesPagoPaginated(
            int page, int size,
            Long proveedorId,
            SolicitudPagoEstado estado,
            String numero,
            String fechaDesde,
            String fechaHasta
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Specification<SolicitudPago> spec = (root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            if (proveedorId != null) {
                predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
            }
            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }
            if (numero != null && !numero.trim().isEmpty()) {
                predicates.add(cb.like(cb.upper(root.get("numeroSolicitud")), "%" + numero.trim().toUpperCase() + "%"));
            }
            if (fechaDesde != null && !fechaDesde.trim().isEmpty()) {
                LocalDateTime desde = stringToDate(fechaDesde.trim() + " 00:00");
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaSolicitud"), desde));
            }
            if (fechaHasta != null && !fechaHasta.trim().isEmpty()) {
                LocalDateTime hasta = stringToDate(fechaHasta.trim() + " 23:59");
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaSolicitud"), hasta));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return solicitudPagoService.getRepository().findAll(spec, pageRequest);
    }

    /**
     * Get notas de recepcion available for payment for a specific pedido
     */
    public List<NotaRecepcion> notasDisponiblesParaPago(Long pedidoId) {
        return solicitudPagoService.getNotasDisponiblesParaPago(pedidoId);
    }

    /**
     * Get all notas asociadas with a solicitud pago
     */
    public List<NotaRecepcion> notasAsociadasASolicitud(Long solicitudPagoId) {
        return solicitudPagoService.getNotasAsociadas(solicitudPagoId);
    }

    /**
     * Check if a nota is already included in any solicitud
     */
    public Boolean isNotaIncludedInSolicitud(Long notaId) {
        return solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(notaId);
    }

    /**
     * Get a single NotaRecepcion eligible for payment by numero and proveedor (independent of pedido).
     * Returns null if none found or already included in a solicitud.
     */
    public NotaRecepcion notaRecepcionDisponibleParaPagoPorNumero(Integer numero, Long proveedorId) {
        return solicitudPagoService.getNotaDisponibleParaPagoPorNumero(numero, proveedorId);
    }

    /**
     * Get all NotaRecepcion eligible for payment for a proveedor (for desktop dialog searchable select).
     */
    public List<NotaRecepcion> notasDisponiblesParaPagoPorProveedor(Long proveedorId) {
        return solicitudPagoService.getNotasDisponiblesParaPagoPorProveedor(proveedorId);
    }

    /**
     * Paginated: notas disponibles para pago por proveedor; filtro en BD por numero (filtroTexto).
     */
    public Page<NotaRecepcion> notasDisponiblesParaPagoPorProveedorPaginated(
            Long proveedorId, Integer page, Integer size, String filtroTexto) {
        int p = page != null ? page : 0;
        int s = size != null && size > 0 ? size : 20;
        return solicitudPagoService.getNotasDisponiblesParaPagoPorProveedorPaginated(
                proveedorId, p, s, filtroTexto);
    }

    /**
     * Datos iniciales para crear solicitud de pago desde una recepción (mobile).
     */
    public DatosInicialesSolicitudPagoDTO datosInicialesSolicitudPagoPorRecepcion(Long recepcionMercaderiaId) {
        return solicitudPagoService.getDatosInicialesSolicitudPagoPorRecepcion(recepcionMercaderiaId);
    }

    // ========== MUTATIONS ==========

    /**
     * Save/create solicitud pago
     */
    public SolicitudPago saveSolicitudPago(SolicitudPagoInput input) {
        try {
            Proveedor proveedor = proveedorService.findById(Long.valueOf(input.getProveedorId()))
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + input.getProveedorId()));

            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs = mapToDetalleInputs(input.getDetalles());
            Moneda moneda = resolveMoneda(input, detalleInputs);
            FormaPago formaPago = resolveFormaPago(input, detalleInputs);

            LocalDateTime fechaPagoPropuesta = null;
            if (input.getFechaPagoPropuesta() != null && !input.getFechaPagoPropuesta().isEmpty()) {
                fechaPagoPropuesta = stringToDate(input.getFechaPagoPropuesta());
            }

            Usuario usuario = null;
            if (input.getUsuarioId() != null) {
                usuario = usuarioService.findById(Long.valueOf(input.getUsuarioId()))
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + input.getUsuarioId()));
            }

            SolicitudPago solicitudGuardada = solicitudPagoService.crearSolicitudPago(
                proveedor,
                input.getNotaRecepcionIds(),
                moneda,
                formaPago,
                fechaPagoPropuesta,
                input.getObservaciones(),
                usuario
            );
            if (detalleInputs.isEmpty() && formaPago != null) {
                SolicitudPagoDetalleService.DetalleInput uno = new SolicitudPagoDetalleService.DetalleInput();
                uno.setMonedaId(moneda.getId());
                uno.setFormaPagoId(formaPago.getId());
                uno.setValor(input.getMontoTotal());
                uno.setFechaPago(input.getFechaPagoPropuesta());
                detalleInputs = Collections.singletonList(uno);
            }
            solicitudPagoDetalleService.sincronizarDetalles(solicitudGuardada.getId(), solicitudGuardada, detalleInputs);
            return solicitudGuardada;

        } catch (Exception e) {
            throw new RuntimeException("Error al crear solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Update solicitud pago (only when estado is PENDIENTE).
     * Usado en: Desktop Sí (editar pago desde lista).
     */
    public SolicitudPago actualizarSolicitudPago(SolicitudPagoInput input) {
        try {
            if (input.getId() == null) {
                throw new IllegalArgumentException("Se requiere id para actualizar");
            }
            SolicitudPago existente = solicitudPagoService.findById(input.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de pago no encontrada: " + input.getId()));
            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs = mapToDetalleInputs(input.getDetalles());
            Moneda moneda = resolveMonedaForUpdate(input, detalleInputs, existente.getMoneda());
            FormaPago formaPago = resolveFormaPagoForUpdate(input, detalleInputs, existente.getFormaPago());
            LocalDateTime fechaPagoPropuesta = null;
            if (input.getFechaPagoPropuesta() != null && !input.getFechaPagoPropuesta().isEmpty()) {
                fechaPagoPropuesta = stringToDate(input.getFechaPagoPropuesta());
            }
            Long formaPagoIdForUpdate = formaPago != null ? formaPago.getId() : null;
            SolicitudPago actualizada = solicitudPagoService.actualizarSolicitudPago(
                input.getId(),
                moneda.getId(),
                formaPagoIdForUpdate,
                fechaPagoPropuesta,
                input.getObservaciones(),
                input.getNotaRecepcionIds()
            );
            solicitudPagoDetalleService.sincronizarDetalles(actualizada.getId(), actualizada, detalleInputs != null ? detalleInputs : Collections.emptyList());
            return solicitudPagoService.findById(actualizada.getId()).orElse(actualizada);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Delete solicitud pago (only if in PENDIENTE state)
     */
    public Boolean deleteSolicitudPago(Long id) {
        try {
            return solicitudPagoService.eliminarSolicitud(id);
        } catch (Exception e) {
            throw new RuntimeException("Error al eliminar solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Update estado of solicitud pago
     */
    public SolicitudPago actualizarEstadoSolicitudPago(Long id, SolicitudPagoEstado estado) {
        try {
            return solicitudPagoService.actualizarEstado(id, estado);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar estado de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Add nota recepcion to solicitud pago
     */
    public SolicitudPagoNotaRecepcion agregarNotaASolicitudPago(Long solicitudPagoId, Long notaRecepcionId, Double montoIncluido) {
        try {
            return solicitudPagoNotaRecepcionService.agregarNotaASolicitud(solicitudPagoId, notaRecepcionId, montoIncluido);
        } catch (Exception e) {
            throw new RuntimeException("Error al agregar nota a solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Remove nota recepcion from solicitud pago
     */
    public Boolean removerNotaDeSolicitudPago(Long solicitudPagoId, Long notaRecepcionId) {
        try {
            return solicitudPagoNotaRecepcionService.removerNotaDeSolicitud(solicitudPagoId, notaRecepcionId);
        } catch (Exception e) {
            throw new RuntimeException("Error al remover nota de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a single solicitud pago detalle (forma de pago) by id.
     * Usado en: Desktop (diálogo create/edit, al confirmar eliminación de forma de pago).
     */
    public Boolean eliminarSolicitudPagoDetalle(Long id) {
        try {
            Boolean deleted = solicitudPagoDetalleService.deleteById(id);
            return deleted != null ? deleted : false;
        } catch (Exception e) {
            throw new RuntimeException("Error al eliminar detalle de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Add a single solicitud pago detalle (forma de pago) to an existing solicitud.
     * Usado en: Desktop (diálogo adicionar forma de pago al confirmar guardado).
     */
    public SolicitudPagoDetalle agregarSolicitudPagoDetalle(Long solicitudPagoId, SolicitudPagoDetalleInput detalleInput) {
        SolicitudPago solicitud = solicitudPagoService.findById(solicitudPagoId)
            .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));
        SolicitudPagoDetalleService.DetalleInput in = new SolicitudPagoDetalleService.DetalleInput();
        in.setMonedaId(detalleInput.getMonedaId());
        in.setFormaPagoId(detalleInput.getFormaPagoId());
        in.setValor(detalleInput.getValor());
        in.setFechaPago(detalleInput.getFechaPago());
        in.setObservacion(detalleInput.getObservacion());
        in.setCotizacion(detalleInput.getCotizacion());
        in.setFechaEmisionCheque(detalleInput.getFechaEmisionCheque());
        in.setPortador(detalleInput.getPortador());
        in.setNominal(detalleInput.getNominal());
        in.setDiferido(detalleInput.getDiferido());
        return solicitudPagoDetalleService.agregarDetalle(solicitud, in);
    }

    /**
     * Print solicitud pago PDF
     */
    public String imprimirSolicitudPagoPDF(Long solicitudPagoId) {
        try {
            SolicitudPago solicitudPago = solicitudPagoService.getRepository()
                .findByIdWithUsuarioAndMoneda(solicitudPagoId)
                .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));
            
            // Obtener información del proveedor
            String proveedorNombre = solicitudPago.getProveedor() != null && solicitudPago.getProveedor().getPersona() != null
                ? solicitudPago.getProveedor().getPersona().getNombre() : "";
            
            // Detalles (formas de pago): cargar desde servicio para PDF
            List<com.franco.dev.domain.operaciones.SolicitudPagoDetalle> detalles = solicitudPagoDetalleService.findBySolicitudPagoId(solicitudPagoId);
            StringBuilder formasPagoSb = new StringBuilder();
            java.time.format.DateTimeFormatter dateFmtPago = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            for (com.franco.dev.domain.operaciones.SolicitudPagoDetalle d : detalles) {
                if (d.getFormaPago() != null) formasPagoSb.append(d.getFormaPago().getDescripcion() != null ? d.getFormaPago().getDescripcion() : "");
                formasPagoSb.append(" - ").append(d.getValor() != null ? java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(d.getValor().longValue()) : "0");
                if (d.getFechaPago() != null) formasPagoSb.append(" - ").append(d.getFechaPago().format(dateFmtPago));
                if (d.getMoneda() != null && d.getMoneda().getDenominacion() != null) formasPagoSb.append(" ").append(d.getMoneda().getDenominacion());
                if (d.getPortador() != null && !d.getPortador().isEmpty()) formasPagoSb.append(" - Portador: ").append(d.getPortador());
                if (Boolean.TRUE.equals(d.getDiferido())) formasPagoSb.append(" - Diferido");
                formasPagoSb.append("\n");
            }
            String formasPagoTexto = formasPagoSb.length() > 0 ? formasPagoSb.toString().trim() : "";

            // Fecha / moneda / forma: desde primer detalle si hay detalles, sino desde entidad
            String fechaDePago = "";
            String moneda = "";
            String monedaSimbolo = "";
            String formaPago = "";
            if (!detalles.isEmpty()) {
                com.franco.dev.domain.operaciones.SolicitudPagoDetalle prim = detalles.get(0);
                if (prim.getFechaPago() != null) fechaDePago = prim.getFechaPago().format(dateFmtPago);
                if (prim.getMoneda() != null) {
                    moneda = prim.getMoneda().getDenominacion() != null ? prim.getMoneda().getDenominacion() : "";
                    monedaSimbolo = prim.getMoneda().getSimbolo() != null ? prim.getMoneda().getSimbolo() : moneda;
                }
                if (prim.getFormaPago() != null) formaPago = prim.getFormaPago().getDescripcion() != null ? prim.getFormaPago().getDescripcion() : "";
            }
            if (fechaDePago.isEmpty() && solicitudPago.getFechaPagoPropuesta() != null) {
                fechaDePago = solicitudPago.getFechaPagoPropuesta().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            if (moneda.isEmpty() && solicitudPago.getMoneda() != null) {
                moneda = solicitudPago.getMoneda().getDenominacion() != null ? solicitudPago.getMoneda().getDenominacion() : "";
                monedaSimbolo = solicitudPago.getMoneda().getSimbolo() != null ? solicitudPago.getMoneda().getSimbolo() : moneda;
            }
            if (formaPago.isEmpty() && solicitudPago.getFormaPago() != null) {
                formaPago = solicitudPago.getFormaPago().getDescripcion() != null ? solicitudPago.getFormaPago().getDescripcion() : "";
            }
            
            // Notas con NotaRecepcion cargada (para PDF y para numerosFactura/valorTotal)
            List<SolicitudPagoNotaRecepcion> solicitudNotas = solicitudPagoNotaRecepcionService.getNotasDeSolicitudWithNotaRecepcion(solicitudPagoId);
            String numerosFactura = "";
            Double valorTotal = solicitudPago.getMontoTotal() != null ? solicitudPago.getMontoTotal() : 0.0;
            final int COL_NRO = 6, COL_FECHA = 10, COL_TOTAL = 16;
            java.util.function.Function<String, String> padR = s -> {
                String safe = s != null ? s : "";
                StringBuilder sb = new StringBuilder(safe);
                for (int i = safe.length(); i < COL_NRO; i++) sb.append(' ');
                return sb.toString();
            };
            java.util.function.Function<String, String> padRFecha = s -> {
                String safe = s != null ? s : "";
                StringBuilder sb = new StringBuilder(safe);
                for (int i = safe.length(); i < COL_FECHA; i++) sb.append(' ');
                return sb.toString();
            };
            java.util.function.Function<String, String> padLTotal = s -> {
                String safe = s != null ? s : "";
                int pad = Math.max(0, COL_TOTAL - safe.length());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pad; i++) sb.append(' ');
                return sb.append(safe).toString();
            };

            java.util.List<String> lineasNotas = new java.util.ArrayList<>();
            if (!solicitudNotas.isEmpty()) {
                valorTotal = 0.0;
                lineasNotas.add(padR.apply("Nro.") + padRFecha.apply("Fecha") + padLTotal.apply("Total"));
                java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
                for (SolicitudPagoNotaRecepcion spnr : solicitudNotas) {
                    if (spnr == null) continue;
                    String nro = "?";
                    String fechaStr = "---";
                    if (spnr.getNotaRecepcion() != null) {
                        nro = spnr.getNotaRecepcion().getNumero() != null
                            ? String.valueOf(spnr.getNotaRecepcion().getNumero()) : "?";
                        if (spnr.getNotaRecepcion().getFecha() != null) {
                            fechaStr = spnr.getNotaRecepcion().getFecha().toLocalDate().format(dateFmt);
                        }
                    }
                    Double monto = spnr.getMontoIncluido() != null ? spnr.getMontoIncluido() : 0.0;
                    valorTotal += monto;
                    String montoStr;
                    try {
                        montoStr = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format((long) Math.round(monto));
                    } catch (Exception ignored) {
                        montoStr = String.valueOf((int) Math.round(monto));
                    }
                    lineasNotas.add(padR.apply(nro) + padRFecha.apply(fechaStr) + padLTotal.apply(montoStr));
                }
                numerosFactura = solicitudNotas.stream()
                    .filter(spnr -> spnr.getNotaRecepcion() != null && spnr.getNotaRecepcion().getNumero() != null)
                    .map(spnr -> String.valueOf(spnr.getNotaRecepcion().getNumero()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            } else {
                lineasNotas.add(padR.apply("Nro.") + padRFecha.apply("Fecha") + padLTotal.apply("Total"));
                lineasNotas.add(SolicitudPagoEstado.CANCELADO.equals(solicitudPago.getEstado())
                    ? "CANCELADA - sin notas" : padR.apply("-") + padRFecha.apply("-") + padLTotal.apply("-"));
                numerosFactura = "---";
            }
            String notasTexto = String.join("\n", lineasNotas);

            String observaciones = (solicitudPago.getObservaciones() != null && !solicitudPago.getObservaciones().trim().isEmpty())
                ? solicitudPago.getObservaciones().trim().toUpperCase() : "";

            log.info("[imprimirSolicitudPagoPDF] solicitudPagoId={} | proveedorNombre={} (null={}) | fechaDePago={} (null={}) | formaPago={} (null={}) | moneda={} (null={}) | monedaSimbolo={} (null={}) | numerosFactura={} (null={}) | valorTotal={} (null={}) | observaciones={}",
                solicitudPagoId, proveedorNombre, (proveedorNombre == null), fechaDePago, (fechaDePago == null), formaPago, (formaPago == null), moneda, (moneda == null), monedaSimbolo, (monedaSimbolo == null), numerosFactura, (numerosFactura == null), valorTotal, (valorTotal == null), observaciones);
            if (log.isDebugEnabled()) {
                log.debug("[imprimirSolicitudPagoPDF] notasTexto length={}, preview={}", notasTexto != null ? notasTexto.length() : 0, notasTexto != null && notasTexto.length() > 80 ? notasTexto.substring(0, 80) + "..." : notasTexto);
            }

            return impresionService.imprimirSolicitudPagoPDF(
                solicitudPago,
                proveedorNombre,
                fechaDePago,
                formaPago,
                moneda,
                monedaSimbolo,
                observaciones,
                numerosFactura,
                valorTotal,
                notasTexto,
                formasPagoTexto
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Error al imprimir solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Imprime solicitud de pago en ticket 58mm.
     * 
     * Usado en:
     * - Desktop: Sí (lista y dashboard de solicitudes de pago)
     * - Mobile: No
     */
    public Boolean imprimirSolicitudPagoTicket(java.lang.Number solicitudPagoId, String printerName, DataFetchingEnvironment env) {
        try {
            Long id = solicitudPagoId != null ? solicitudPagoId.longValue() : null;
            if (id == null) {
                throw new RuntimeException("solicitudPagoId es requerido");
            }
            SolicitudPago solicitudPago = solicitudPagoService.getRepository()
                .findByIdWithUsuarioAndMoneda(id)
                .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));

            String proveedorNombre = solicitudPago.getProveedor() != null && solicitudPago.getProveedor().getPersona() != null
                ? solicitudPago.getProveedor().getPersona().getNombre() : "";

            List<com.franco.dev.domain.operaciones.SolicitudPagoDetalle> detallesTicket = solicitudPagoDetalleService.findBySolicitudPagoId(id);
            java.util.List<String> lineasFormasPago = new java.util.ArrayList<>();
            java.time.format.DateTimeFormatter dateFmtTicket = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
            for (com.franco.dev.domain.operaciones.SolicitudPagoDetalle d : detallesTicket) {
                String fp = d.getFormaPago() != null && d.getFormaPago().getDescripcion() != null ? d.getFormaPago().getDescripcion() : "";
                String val = d.getValor() != null ? java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(d.getValor().longValue()) : "0";
                String fe = d.getFechaPago() != null ? d.getFechaPago().format(dateFmtTicket) : "";
                lineasFormasPago.add(fp + " " + val + (fe.isEmpty() ? "" : " " + fe));
            }

            String fechaDePago = "";
            String moneda = "";
            String monedaSimbolo = "";
            String formaPago = "";
            if (!detallesTicket.isEmpty()) {
                com.franco.dev.domain.operaciones.SolicitudPagoDetalle prim = detallesTicket.get(0);
                if (prim.getFechaPago() != null) fechaDePago = prim.getFechaPago().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                if (prim.getMoneda() != null) {
                    moneda = prim.getMoneda().getDenominacion() != null ? prim.getMoneda().getDenominacion() : "";
                    monedaSimbolo = prim.getMoneda().getSimbolo() != null ? prim.getMoneda().getSimbolo() : moneda;
                }
                if (prim.getFormaPago() != null) formaPago = prim.getFormaPago().getDescripcion() != null ? prim.getFormaPago().getDescripcion() : "";
            }
            if (fechaDePago.isEmpty() && solicitudPago.getFechaPagoPropuesta() != null) {
                fechaDePago = solicitudPago.getFechaPagoPropuesta().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            if (moneda.isEmpty() && solicitudPago.getMoneda() != null) {
                moneda = solicitudPago.getMoneda().getDenominacion() != null ? solicitudPago.getMoneda().getDenominacion() : "";
                monedaSimbolo = solicitudPago.getMoneda().getSimbolo() != null ? solicitudPago.getMoneda().getSimbolo() : moneda;
            }
            if (formaPago.isEmpty() && solicitudPago.getFormaPago() != null) {
                formaPago = solicitudPago.getFormaPago().getDescripcion() != null ? solicitudPago.getFormaPago().getDescripcion() : "";
            }

            String estado = solicitudPago.getEstado() != null ? solicitudPago.getEstado().toString() : "";

            List<SolicitudPagoNotaRecepcion> solicitudNotas = solicitudPagoNotaRecepcionService.getNotasDeSolicitudWithNotaRecepcion(id);
            List<String> lineasNotas = new java.util.ArrayList<>();
            Double valorTotal = solicitudPago.getMontoTotal() != null ? solicitudPago.getMontoTotal() : 0.0;
            final int COL_NRO = 6, COL_FECHA = 10, COL_TOTAL = 16;
            java.util.function.Function<String, String> padRight = s -> {
                String safe = s != null ? s : "";
                StringBuilder sb = new StringBuilder(safe);
                for (int i = safe.length(); i < COL_NRO; i++) sb.append(' ');
                return sb.toString();
            };
            java.util.function.Function<String, String> padRightFecha = s -> {
                String safe = s != null ? s : "";
                StringBuilder sb = new StringBuilder(safe);
                for (int i = safe.length(); i < COL_FECHA; i++) sb.append(' ');
                return sb.toString();
            };
            java.util.function.Function<String, String> padLeftTotal = s -> {
                String safe = s != null ? s : "";
                int pad = Math.max(0, COL_TOTAL - safe.length());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pad; i++) sb.append(' ');
                return sb.append(safe).toString();
            };

            if (!solicitudNotas.isEmpty()) {
                valorTotal = 0.0;
                lineasNotas.add(padRight.apply("Nro.") + padRightFecha.apply("Fecha") + padLeftTotal.apply("Total"));
                java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
                for (SolicitudPagoNotaRecepcion spnr : solicitudNotas) {
                    if (spnr == null) continue;
                    String nro = "?";
                    String fechaStr = "---";
                    if (spnr.getNotaRecepcion() != null) {
                        nro = spnr.getNotaRecepcion().getNumero() != null
                            ? String.valueOf(spnr.getNotaRecepcion().getNumero()) : "?";
                        if (spnr.getNotaRecepcion().getFecha() != null) {
                            fechaStr = spnr.getNotaRecepcion().getFecha().toLocalDate().format(dateFmt);
                        }
                    }
                    Double monto = spnr.getMontoIncluido() != null ? spnr.getMontoIncluido() : 0.0;
                    valorTotal += monto;
                    String montoStr;
                    try {
                        long montoLong = (long) Math.round(monto);
                        montoStr = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(montoLong);
                    } catch (Exception ignored) {
                        montoStr = String.valueOf((int) Math.round(monto));
                    }
                    lineasNotas.add(padRight.apply(nro) + padRightFecha.apply(fechaStr) + padLeftTotal.apply(montoStr));
                }
            } else {
                lineasNotas.add(padRight.apply("Nro.") + padRightFecha.apply("Fecha") + padLeftTotal.apply("Total"));
                if (SolicitudPagoEstado.CANCELADO.equals(solicitudPago.getEstado())) {
                    lineasNotas.add("CANCELADA - sin notas");
                } else {
                    lineasNotas.add(padRight.apply("-") + padRightFecha.apply("-") + padLeftTotal.apply("-"));
                }
            }

            String usuario = "";
            if (solicitudPago.getUsuario() != null) {
                if (solicitudPago.getUsuario().getPersona() != null && solicitudPago.getUsuario().getPersona().getNombre() != null) {
                    usuario = solicitudPago.getUsuario().getPersona().getNombre();
                } else if (solicitudPago.getUsuario().getNickname() != null) {
                    usuario = solicitudPago.getUsuario().getNickname();
                }
            }

            impresionService.printSolicitudPagoTicket(
                solicitudPago, proveedorNombre, fechaDePago, formaPago,
                moneda, monedaSimbolo, lineasNotas, valorTotal, estado, usuario, printerName,
                lineasFormasPago
            );
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error al imprimir ticket de solicitud de pago: " + e.getMessage(), e);
        }
    }

    // ========== INPUT CLASSES ==========

    public static class SolicitudPagoInput {
        private Long id;
        private Integer proveedorId;
        private String numeroSolicitud;
        private String fechaSolicitud;
        private String fechaPagoPropuesta;
        private Double montoTotal;
        private Integer monedaId;
        private Integer formaPagoId;
        private SolicitudPagoEstado estado;
        private String observaciones;
        private List<Long> notaRecepcionIds;
        private Long usuarioId;
        private List<SolicitudPagoDetalleInput> detalles;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Integer getProveedorId() { return proveedorId; }
        public void setProveedorId(Integer proveedorId) { this.proveedorId = proveedorId; }

        public String getNumeroSolicitud() { return numeroSolicitud; }
        public void setNumeroSolicitud(String numeroSolicitud) { this.numeroSolicitud = numeroSolicitud; }

        public String getFechaSolicitud() { return fechaSolicitud; }
        public void setFechaSolicitud(String fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

        public String getFechaPagoPropuesta() { return fechaPagoPropuesta; }
        public void setFechaPagoPropuesta(String fechaPagoPropuesta) { this.fechaPagoPropuesta = fechaPagoPropuesta; }

        public Double getMontoTotal() { return montoTotal; }
        public void setMontoTotal(Double montoTotal) { this.montoTotal = montoTotal; }

        public Integer getMonedaId() { return monedaId; }
        public void setMonedaId(Integer monedaId) { this.monedaId = monedaId; }

        public Integer getFormaPagoId() { return formaPagoId; }
        public void setFormaPagoId(Integer formaPagoId) { this.formaPagoId = formaPagoId; }

        public SolicitudPagoEstado getEstado() { return estado; }
        public void setEstado(SolicitudPagoEstado estado) { this.estado = estado; }

        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

        public List<Long> getNotaRecepcionIds() { return notaRecepcionIds; }
        public void setNotaRecepcionIds(List<Long> notaRecepcionIds) { this.notaRecepcionIds = notaRecepcionIds; }

        public List<SolicitudPagoDetalleInput> getDetalles() { return detalles; }
        public void setDetalles(List<SolicitudPagoDetalleInput> detalles) { this.detalles = detalles; }
        
        public Long getUsuarioId() { return usuarioId; }
        public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    }

    private List<SolicitudPagoDetalleService.DetalleInput> mapToDetalleInputs(List<SolicitudPagoDetalleInput> list) {
        if (list == null || list.isEmpty()) return new ArrayList<>();
        List<SolicitudPagoDetalleService.DetalleInput> out = new ArrayList<>();
        for (SolicitudPagoDetalleInput in : list) {
            SolicitudPagoDetalleService.DetalleInput d = new SolicitudPagoDetalleService.DetalleInput();
            d.setMonedaId(in.getMonedaId() != null ? in.getMonedaId() : null);
            d.setFormaPagoId(in.getFormaPagoId() != null ? in.getFormaPagoId() : null);
            d.setValor(in.getValor());
            d.setFechaPago(in.getFechaPago());
            d.setObservacion(in.getObservacion());
            d.setCotizacion(in.getCotizacion());
            d.setOrden(in.getOrden());
            d.setFechaEmisionCheque(in.getFechaEmisionCheque());
            d.setPortador(in.getPortador());
            d.setNominal(in.getNominal());
            d.setDiferido(in.getDiferido());
            out.add(d);
        }
        return out;
    }

    /**
     * Resolves Moneda from input, first detalle, or default (GUARANI).
     * Usado en: save/update cuando monedaId ya no va en cabecera (va en detalle).
     */
    private Moneda resolveMoneda(SolicitudPagoInput input, List<SolicitudPagoDetalleService.DetalleInput> detalleInputs) {
        if (input.getMonedaId() != null) {
            return monedaService.findById(Long.valueOf(input.getMonedaId()))
                .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + input.getMonedaId()));
        }
        if (detalleInputs != null && !detalleInputs.isEmpty()) {
            SolicitudPagoDetalleService.DetalleInput first = detalleInputs.get(0);
            if (first.getMonedaId() != null) {
                return monedaService.findById(Long.valueOf(first.getMonedaId()))
                    .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada en detalle: " + first.getMonedaId()));
            }
        }
        Moneda defaultMoneda = monedaService.findByDescripcion("GUARANI");
        if (defaultMoneda == null) {
            throw new IllegalArgumentException("Moneda por defecto GUARANI no encontrada");
        }
        return defaultMoneda;
    }

    /**
     * Resolves FormaPago from input or first detalle. May return null (cabecera acepta formaPago null).
     */
    private FormaPago resolveFormaPago(SolicitudPagoInput input, List<SolicitudPagoDetalleService.DetalleInput> detalleInputs) {
        final Long formaPagoId;
        if (input.getFormaPagoId() != null) {
            formaPagoId = Long.valueOf(input.getFormaPagoId());
        } else if (detalleInputs != null && !detalleInputs.isEmpty() && detalleInputs.get(0).getFormaPagoId() != null) {
            formaPagoId = Long.valueOf(detalleInputs.get(0).getFormaPagoId());
        } else {
            formaPagoId = null;
        }
        if (formaPagoId == null) return null;
        return formaPagoService.findById(formaPagoId)
            .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + formaPagoId));
    }

    private Moneda resolveMonedaForUpdate(SolicitudPagoInput input,
            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs, Moneda existingMoneda) {
        if (input.getMonedaId() != null) {
            return monedaService.findById(Long.valueOf(input.getMonedaId()))
                .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + input.getMonedaId()));
        }
        if (detalleInputs != null && !detalleInputs.isEmpty() && detalleInputs.get(0).getMonedaId() != null) {
            return monedaService.findById(Long.valueOf(detalleInputs.get(0).getMonedaId()))
                .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada en detalle"));
        }
        return existingMoneda != null ? existingMoneda : resolveMoneda(input, detalleInputs);
    }

    private FormaPago resolveFormaPagoForUpdate(SolicitudPagoInput input,
            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs, FormaPago existingFormaPago) {
        FormaPago fromInputOrDetalle = resolveFormaPago(input, detalleInputs);
        return fromInputOrDetalle != null ? fromInputOrDetalle : existingFormaPago;
    }
}