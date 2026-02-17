package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.graphql.operaciones.dto.DatosInicialesSolicitudPagoDTO;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.impresion.ImpresionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SolicitudPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

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
     * Get solicitudes pago with pagination
     */
    public Page<SolicitudPago> solicitudesPagoPaginated(int page, int size, Long proveedorId, SolicitudPagoEstado estado) {
        PageRequest pageRequest = PageRequest.of(page, size);
        
        if (proveedorId != null && estado != null) {
            return solicitudPagoService.getRepository().findByProveedorIdAndEstado(proveedorId, estado, pageRequest);
        } else if (proveedorId != null) {
            return solicitudPagoService.getRepository().findAll(
                (root, query, cb) -> cb.equal(root.get("proveedor").get("id"), proveedorId),
                pageRequest
            );
        } else if (estado != null) {
            return solicitudPagoService.getRepository().findAll(
                (root, query, cb) -> cb.equal(root.get("estado"), estado),
                pageRequest
            );
        } else {
            return solicitudPagoService.getRepository().findAll(pageRequest);
        }
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
            // Load required entities
            Proveedor proveedor = proveedorService.findById(Long.valueOf(input.getProveedorId()))
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + input.getProveedorId()));

            Moneda moneda = monedaService.findById(Long.valueOf(input.getMonedaId()))
                .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + input.getMonedaId()));

            FormaPago formaPago = formaPagoService.findById(Long.valueOf(input.getFormaPagoId()))
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + input.getFormaPagoId()));

            // Parse dates
            LocalDateTime fechaPagoPropuesta = null;
            if (input.getFechaPagoPropuesta() != null && !input.getFechaPagoPropuesta().isEmpty()) {
                fechaPagoPropuesta = stringToDate(input.getFechaPagoPropuesta());
            }

            // Load usuario if provided
            Usuario usuario = null;
            if(input.getUsuarioId() != null){
                usuario = usuarioService.findById(Long.valueOf(input.getUsuarioId()))
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + input.getUsuarioId()));
            }

            // Create solicitud pago
            return solicitudPagoService.crearSolicitudPago(
                proveedor,
                input.getNotaRecepcionIds(),
                moneda,
                formaPago,
                fechaPagoPropuesta,
                input.getObservaciones(),
                usuario
            );

        } catch (Exception e) {
            throw new RuntimeException("Error al crear solicitud de pago: " + e.getMessage(), e);
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
     * Print solicitud pago PDF
     */
    public String imprimirSolicitudPagoPDF(Long solicitudPagoId) {
        try {
            SolicitudPago solicitudPago = solicitudPagoService.findById(solicitudPagoId)
                .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));
            
            // Obtener información del proveedor
            String proveedorNombre = solicitudPago.getProveedor().getPersona().getNombre();
            
            // Formatear fecha de pago propuesta
            String fechaDePago = solicitudPago.getFechaPagoPropuesta() != null ? 
                solicitudPago.getFechaPagoPropuesta().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : 
                "No especificada";
            
            // Obtener forma de pago
            String formaPago = solicitudPago.getFormaPago() != null ? 
                solicitudPago.getFormaPago().getDescripcion() : "No especificada";
            
            // Obtener números de factura de las notas de recepción asociadas
            List<SolicitudPagoNotaRecepcion> solicitudNotas = solicitudPagoNotaRecepcionService.getNotasDeSolicitud(solicitudPagoId);
            String numerosFactura = "";
            Double valorTotal = 0.0;
            
            if (!solicitudNotas.isEmpty()) {
                List<String> numeros = solicitudNotas.stream()
                    .map(spnr -> spnr.getNotaRecepcion())
                    .filter(nota -> nota.getNumero() != null)
                    .map(nota -> String.valueOf(nota.getNumero()))
                    .distinct()
                    .collect(Collectors.toList());
                numerosFactura = String.join(", ", numeros);
                
                // Calcular valor total
                valorTotal = solicitudNotas.stream()
                    .mapToDouble(spnr -> spnr.getMontoIncluido() != null ? spnr.getMontoIncluido() : 0.0)
                    .sum();
            }
            
            if (numerosFactura.isEmpty()) {
                numerosFactura = "---";
            }
            
            // Por ahora, nominal es false por defecto
            Boolean nominal = false;
            
            return impresionService.imprimirSolicitudPagoPDF(
                solicitudPago,
                proveedorNombre,
                fechaDePago,
                formaPago,
                nominal,
                numerosFactura,
                valorTotal
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Error al imprimir solicitud de pago: " + e.getMessage(), e);
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
        
        public Long getUsuarioId() { return usuarioId; }
        public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    }
}