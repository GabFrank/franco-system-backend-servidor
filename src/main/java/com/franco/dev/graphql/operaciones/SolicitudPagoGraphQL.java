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
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.SolicitudPagoDetalleService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private TesoreriaSecurityService tesoreriaSecurityService;

    @Autowired
    private RrhhSecurityService rrhhSecurityService;

    // ========== QUERIES ==========

    /**
     * Get single solicitud pago by ID
     */
    public SolicitudPago solicitudPago(Long id) {
        exigirLectura(id);
        return solicitudPagoService.findById(id).orElse(null);
    }

    /**
     * Get all solicitudes pago for a specific pedido
     */
    public List<SolicitudPago> solicitudesPagoPorPedido(Long pedidoId) {
        exigirRolCompras();
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
            String fechaHasta) {
        exigirRolCompras();
        PageRequest pageRequest = PageRequest.of(page, size);
        Specification<SolicitudPago> spec = (root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            // Esta lista es la vista de Compras: excluir GASTO/RRHH (tienen su propia query
            // gastosPendientes). Sin esto, las solicitudes de tesorería se filtran en Compras.
            predicates.add(cb.equal(root.get("tipo"), com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.COMPRA));
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
        exigirRolCompras();
        return solicitudPagoService.getNotasDisponiblesParaPago(pedidoId);
    }

    /**
     * Get all notas asociadas with a solicitud pago
     */
    public List<NotaRecepcion> notasAsociadasASolicitud(Long solicitudPagoId) {
        exigirLectura(solicitudPagoId);
        return solicitudPagoService.getNotasAsociadas(solicitudPagoId);
    }

    /**
     * Check if a nota is already included in any solicitud
     */
    public Boolean isNotaIncludedInSolicitud(Long notaId) {
        exigirRolCompras();
        return solicitudPagoNotaRecepcionService.isNotaIncludedInSolicitud(notaId);
    }

    /**
     * Get a single NotaRecepcion eligible for payment by numero and proveedor
     * (independent of pedido).
     * Returns null if none found or already included in a solicitud.
     */
    public NotaRecepcion notaRecepcionDisponibleParaPagoPorNumero(Integer numero, Long proveedorId) {
        exigirRolCompras();
        return solicitudPagoService.getNotaDisponibleParaPagoPorNumero(numero, proveedorId);
    }

    /**
     * Get all NotaRecepcion eligible for payment for a proveedor (for desktop
     * dialog searchable select).
     */
    public List<NotaRecepcion> notasDisponiblesParaPagoPorProveedor(Long proveedorId) {
        exigirRolCompras();
        return solicitudPagoService.getNotasDisponiblesParaPagoPorProveedor(proveedorId);
    }

    /**
     * Paginated: notas disponibles para pago por proveedor; filtro en BD por numero
     * (filtroTexto).
     */
    public Page<NotaRecepcion> notasDisponiblesParaPagoPorProveedorPaginated(
            Long proveedorId, Integer page, Integer size, String filtroTexto) {
        exigirRolCompras();
        int p = page != null ? page : 0;
        int s = size != null && size > 0 ? size : 20;
        return solicitudPagoService.getNotasDisponiblesParaPagoPorProveedorPaginated(
                proveedorId, p, s, filtroTexto);
    }

    /**
     * Datos iniciales para crear solicitud de pago desde una recepción (mobile).
     */
    public DatosInicialesSolicitudPagoDTO datosInicialesSolicitudPagoPorRecepcion(Long recepcionMercaderiaId) {
        exigirRolCompras();
        return solicitudPagoService.getDatosInicialesSolicitudPagoPorRecepcion(recepcionMercaderiaId);
    }

    // ========== MUTATIONS ==========

    /**
     * Save/create solicitud pago
     */
    @Transactional // la cabecera, las notas y las formas de pago se guardan juntas o nada (#435)
    public SolicitudPago saveSolicitudPago(SolicitudPagoInput input) {
        exigirRolCompras();
        try {
            Proveedor proveedor = proveedorService.findById(Long.valueOf(input.getProveedorId()))
                    .orElseThrow(
                            () -> new IllegalArgumentException("Proveedor no encontrado: " + input.getProveedorId()));

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
                        .orElseThrow(
                                () -> new IllegalArgumentException("Usuario no encontrado: " + input.getUsuarioId()));
            }

            SolicitudPago solicitudGuardada = solicitudPagoService.crearSolicitudPago(
                    proveedor,
                    input.getNotaRecepcionIds(),
                    moneda,
                    formaPago,
                    fechaPagoPropuesta,
                    input.getObservaciones(),
                    usuario);
            if (detalleInputs.isEmpty() && formaPago != null) {
                SolicitudPagoDetalleService.DetalleInput uno = new SolicitudPagoDetalleService.DetalleInput();
                uno.setMonedaId(moneda.getId());
                uno.setFormaPagoId(formaPago.getId());
                uno.setValor(input.getMontoTotal());
                uno.setFechaPago(input.getFechaPagoPropuesta());
                detalleInputs = Collections.singletonList(uno);
            }
            solicitudPagoDetalleService.sincronizarDetalles(solicitudGuardada.getId(), solicitudGuardada,
                    detalleInputs);
            // Sin el flush, un error de la base saltaría en el commit, fuera de este catch y sin su mensaje
            solicitudPagoService.getRepository().flush();
            return solicitudGuardada;

        } catch (Exception e) {
            throw new RuntimeException("Error al crear solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Update solicitud pago (only when estado is PENDIENTE).
     * Usado en: Desktop Sí (editar pago desde lista).
     */
    @Transactional // cabecera, notas y formas de pago juntas o nada (#435)
    public SolicitudPago actualizarSolicitudPago(SolicitudPagoInput input) {
        exigirRolCompras();
        exigirSolicitudDeCompra(input.getId());
        try {
            if (input.getId() == null) {
                throw new IllegalArgumentException("Se requiere id para actualizar");
            }
            // Cargarla ya bloqueada: un findById previo la dejaría en el contexto y el lock del servicio devolvería
            // esa instancia sin refrescar, con el estado viejo si tesorería la cambió en el medio.
            SolicitudPago existente = solicitudPagoService.exigirEditable(input.getId(),
                    SolicitudPagoService.ESTADOS_EDITABLES, "editar la solicitud");
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
                    input.getNotaRecepcionIds());
            solicitudPagoDetalleService.sincronizarDetalles(actualizada.getId(), actualizada,
                    detalleInputs != null ? detalleInputs : Collections.emptyList());
            solicitudPagoService.getRepository().flush(); // ver saveSolicitudPago
            return solicitudPagoService.findById(actualizada.getId()).orElse(actualizada);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Delete solicitud pago (only if in PENDIENTE state)
     */
    public Boolean deleteSolicitudPago(Long id) {
        exigirRolCompras();
        exigirSolicitudDeCompra(id);
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
        exigirRolCompras();
        exigirSolicitudDeCompra(id);
        // PARCIAL / CONCLUIDO los fija el pago de la caja mayor (PagoProveedorService, que llama al
        // servicio directo). Marcarlos a mano dejaba las notas pagadas sin que saliera plata.
        if (estado == SolicitudPagoEstado.PARCIAL || estado == SolicitudPagoEstado.CONCLUIDO) {
            throw errorParaMostrar(new IllegalStateException("Los pagos se registran desde la caja mayor"
                    + " (Pagar Compras): una solicitud no se marca como pagada a mano."));
        }
        // DEVUELTO pide motivo y rol de tesorería: solo entra por devolverSolicitudPago.
        if (estado == SolicitudPagoEstado.DEVUELTO) {
            throw errorParaMostrar(new IllegalStateException(
                    "Para devolver una solicitud a compras usá \"Devolver a compras\" en Pagar Compras."));
        }
        try {
            return solicitudPagoService.actualizarEstado(id, estado);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar estado de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Compras cancela su solicitud, con motivo. Las reglas (sin pagos, solo compras) viven en
     * {@link SolicitudPagoService#cancelar}. Pide los roles de compras, como el resto del resolver.
     */
    public SolicitudPago cancelarSolicitudPago(Long id, String motivo) {
        exigirRolCompras();
        try {
            return solicitudPagoService.cancelar(id, motivo, tesoreriaSecurityService.currentUsuario());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw errorParaMostrar(e);
        }
    }

    /** Tesorería devuelve a compras una solicitud que no va a pagar (SOLICITADO → PENDIENTE), con motivo. */
    public SolicitudPago devolverSolicitudPago(Long id, String motivo) {
        tesoreriaSecurityService.requirePagarCpp();
        try {
            return solicitudPagoService.devolverACompras(id, motivo, tesoreriaSecurityService.currentUsuario());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw errorParaMostrar(e);
        }
    }

    /**
     * Error de negocio listo para mostrar: GraphqlExceptionHandler desanida los GraphQLError, así
     * que el mensaje llega al cliente sin el prefijo "Exception while fetching data".
     */
    private static graphql.GraphqlErrorException errorParaMostrar(RuntimeException e) {
        return graphql.GraphqlErrorException.newErrorException().message(e.getMessage()).build();
    }

    /**
     * Roles que ven y gestionan las solicitudes de compra: los mismos del menú «Solicitud de pago» del desktop y del
     * área solicitudPago de la PWA. ADMIN entra por el bypass de {@link TesoreriaSecurityService#hasAnyRole}.
     */
    static final String[] ROLES_COMPRAS = {
            "ANALISIS DE CAJA", "ANALISIS FINANCIERO", "ANALISIS CONTABLE", "VER PRECIO COSTO", "RECIBIR PEDIDOS",
            TesoreriaSecurityService.VER, TesoreriaSecurityService.GESTIONAR};

    /** Va antes que exigirSolicitudDeCompra: sin rol no se puede usar un id para averiguar de qué tipo es. */
    private void exigirRolCompras() {
        if (!tesoreriaSecurityService.hasAnyRole(ROLES_COMPRAS)) {
            throw errorParaMostrar(new IllegalStateException(
                    "No autorizado: las solicitudes de pago de compras piden un rol de compras, análisis o tesorería."));
        }
    }

    /**
     * Una obligacion de pago de RRHH (vale, liquidacion, finiquito, aguinaldo) solo se lee con rol de tesoreria o de
     * RRHH (issue #306); una de compras, con los roles de compras. Las de gasto siguen sin rol (las imprime caja
     * chica). Id inexistente o sin tipo: sigue el flujo de siempre.
     */
    private void exigirLectura(Long solicitudPagoId) {
        if (solicitudPagoId == null) return;
        TipoSolicitudPago tipo = solicitudPagoService.getRepository().findTipoById(solicitudPagoId).orElse(null);
        boolean permitido = tipo == TipoSolicitudPago.RRHH ? puedeVerObligacionesRrhh()
                : tipo != TipoSolicitudPago.COMPRA || tesoreriaSecurityService.hasAnyRole(ROLES_COMPRAS);
        if (!permitido) {
            // Sin nombrar el tipo: el mensaje no debe revelar de qué tipo es el id.
            throw errorParaMostrar(new IllegalStateException(
                    "No autorizado para ver la solicitud #" + solicitudPagoId + "."));
        }
    }

    /** Mismo criterio que PagoResolver y cajaVirtualesActivas: rol de tesoreria o de RRHH (ambos con bypass ADMIN). */
    private boolean puedeVerObligacionesRrhh() {
        return tesoreriaSecurityService.hasAnyRole(TesoreriaSecurityService.TODOS)
                || rrhhSecurityService.hasAnyRole(RrhhSecurityService.TODOS);
    }

    /**
     * Las mutations de compras solo tocan solicitudes de compra: las de gasto y RRHH se gestionan desde su modulo
     * (issue #306). Va en el resolver y no en el servicio: el motor de pago usa el servicio para concluirlas.
     */
    /** Rechaza con el motivo a la vista cuando la solicitud no está en un estado que permita el cambio (#435). */
    private SolicitudPago exigirEditable(Long solicitudPagoId, java.util.Set<SolicitudPagoEstado> permitidos, String accion) {
        try {
            return solicitudPagoService.exigirEditable(solicitudPagoId, permitidos, accion);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw errorParaMostrar(e);
        }
    }

    private void exigirSolicitudDeCompra(Long solicitudPagoId) {
        if (solicitudPagoId == null) return;
        TipoSolicitudPago tipo = solicitudPagoService.getRepository().findTipoById(solicitudPagoId).orElse(null);
        if (tipo != null && tipo != TipoSolicitudPago.COMPRA) {
            throw errorParaMostrar(new IllegalStateException("La solicitud #" + solicitudPagoId
                    + " no es de compras: se gestiona desde su propio módulo."));
        }
    }

    /**
     * Add nota recepcion to solicitud pago
     */
    public SolicitudPagoNotaRecepcion agregarNotaASolicitudPago(Long solicitudPagoId, Long notaRecepcionId,
            Double montoIncluido) {
        exigirRolCompras();
        exigirSolicitudDeCompra(solicitudPagoId);
        try {
            // montoIncluido queda en el esquema por compatibilidad: el monto lo calcula el servidor (#435)
            return solicitudPagoService.agregarNota(solicitudPagoId, notaRecepcionId);
        } catch (Exception e) {
            throw new RuntimeException("Error al agregar nota a solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Remove nota recepcion from solicitud pago
     */
    @Transactional // el lock de exigirEditable dura hasta el commit
    public Boolean removerNotaDeSolicitudPago(Long solicitudPagoId, Long notaRecepcionId) {
        exigirRolCompras();
        exigirSolicitudDeCompra(solicitudPagoId);
        exigirEditable(solicitudPagoId, SolicitudPagoService.ESTADOS_EDITABLES, "quitar notas");
        try {
            Boolean removida = solicitudPagoNotaRecepcionService.removerNotaDeSolicitud(solicitudPagoId, notaRecepcionId);
            solicitudPagoService.getRepository().flush(); // ver saveSolicitudPago
            return removida;
        } catch (Exception e) {
            throw new RuntimeException("Error al remover nota de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a single solicitud pago detalle (forma de pago) by id.
     * Usado en: Desktop (diálogo create/edit, al confirmar eliminación de forma de
     * pago).
     */
    @Transactional // el lock de exigirEditable dura hasta el commit
    public Boolean eliminarSolicitudPagoDetalle(Long id) {
        exigirRolCompras();
        if (id != null) {
            solicitudPagoDetalleService.getRepository().findSolicitudIdById(id).ifPresent(solicitudId -> {
                exigirSolicitudDeCompra(solicitudId);
                exigirEditable(solicitudId, SolicitudPagoService.ESTADOS_EDITABLES, "cambiar las formas de pago");
            });
        }
        try {
            Boolean eliminado = solicitudPagoDetalleService.eliminarDetalle(id);
            solicitudPagoService.getRepository().flush(); // ver saveSolicitudPago
            return eliminado;
        } catch (Exception e) {
            throw new RuntimeException("Error al eliminar detalle de solicitud de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Add a single solicitud pago detalle (forma de pago) to an existing solicitud.
     * Usado en: Desktop (diálogo adicionar forma de pago al confirmar guardado).
     */
    @Transactional // el lock de exigirEditable dura hasta el commit
    public SolicitudPagoDetalle agregarSolicitudPagoDetalle(Long solicitudPagoId,
            SolicitudPagoDetalleInput detalleInput) {
        exigirRolCompras();
        exigirSolicitudDeCompra(solicitudPagoId);
        SolicitudPago solicitud = exigirEditable(solicitudPagoId, SolicitudPagoService.ESTADOS_EDITABLES,
                "cambiar las formas de pago");
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
        SolicitudPagoDetalle detalle = solicitudPagoDetalleService.agregarDetalle(solicitud, in);
        solicitudPagoService.getRepository().flush(); // sin try: el error de la base sale tal cual, como antes
        return detalle;
    }

    /**
     * Print solicitud pago PDF
     */
    public String imprimirSolicitudPagoPDF(Long solicitudPagoId) {
        exigirLectura(solicitudPagoId);
        try {
            SolicitudPago solicitudPago = solicitudPagoService.getRepository()
                    .findByIdWithUsuarioAndMoneda(solicitudPagoId)
                    .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));

            // Obtener información del proveedor
            String proveedorNombre = solicitudPago.getProveedor() != null
                    && solicitudPago.getProveedor().getPersona() != null
                            ? solicitudPago.getProveedor().getPersona().getNombre()
                            : "";

            // Detalles (formas de pago): cargar desde servicio para PDF (with
            // moneda/formaPago fetched)
            List<SolicitudPagoDetalle> detalles = solicitudPagoDetalleService
                    .findBySolicitudPagoIdForPrint(solicitudPagoId);
            java.time.format.DateTimeFormatter dateFmtPago = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.format.DateTimeFormatter dateFmtShort = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
            List<ImpresionService.SolicitudPagoFormaPagoDetalleDto> detalleFormasPago = new ArrayList<>();
            for (SolicitudPagoDetalle d : detalles) {
                ImpresionService.SolicitudPagoFormaPagoDetalleDto row = new ImpresionService.SolicitudPagoFormaPagoDetalleDto();
                row.setMoneda(d.getMoneda() != null && d.getMoneda().getDenominacion() != null
                        ? d.getMoneda().getDenominacion().toUpperCase()
                        : "—");
                row.setMonedaSimbolo(
                        d.getMoneda() != null && d.getMoneda().getSimbolo() != null ? d.getMoneda().getSimbolo() : "");
                row.setFormaPago(d.getFormaPago() != null && d.getFormaPago().getDescripcion() != null
                        ? d.getFormaPago().getDescripcion()
                        : "—");
                row.setValor(d.getValor() != null ? d.getValor() : 0.0);
                row.setFechaPago(d.getFechaPago() != null ? d.getFechaPago().format(dateFmtPago) : "—");
                boolean esCheque = d.getFormaPago() != null && d.getFormaPago().getDescripcion() != null
                        && d.getFormaPago().getDescripcion().toUpperCase().contains("CHEQUE");
                row.setEsCheque(esCheque);
                row.setFechaEmisionCheque(
                        esCheque && d.getFechaEmisionCheque() != null ? d.getFechaEmisionCheque().format(dateFmtShort)
                                : "");
                if (esCheque) {
                    row.setNominal(Boolean.TRUE.equals(d.getNominal()) ? "Sí" : "No");
                    row.setDiferido(Boolean.TRUE.equals(d.getDiferido()) ? "Sí" : "No");
                } else {
                    row.setNominal("—");
                    row.setDiferido("—");
                }
                detalleFormasPago.add(row);
            }

            // Notas con NotaRecepcion cargada (para PDF y para numerosFactura/valorTotal)
            List<SolicitudPagoNotaRecepcion> solicitudNotas = solicitudPagoNotaRecepcionService
                    .getNotasDeSolicitudWithNotaRecepcion(solicitudPagoId);
            String numerosFactura = "";
            Double valorTotal = solicitudPago.getMontoTotal() != null ? solicitudPago.getMontoTotal() : 0.0;
            java.util.List<ImpresionService.SolicitudPagoNotaRowDto> notasRows = new java.util.ArrayList<>();
            if (!solicitudNotas.isEmpty()) {
                valorTotal = 0.0;
                java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
                for (SolicitudPagoNotaRecepcion spnr : solicitudNotas) {
                    if (spnr == null)
                        continue;
                    String nro = "?";
                    String fechaStr = "---";
                    if (spnr.getNotaRecepcion() != null) {
                        nro = spnr.getNotaRecepcion().getNumero() != null
                                ? String.valueOf(spnr.getNotaRecepcion().getNumero())
                                : "?";
                        if (spnr.getNotaRecepcion().getFecha() != null) {
                            fechaStr = spnr.getNotaRecepcion().getFecha().toLocalDate().format(dateFmt);
                        }
                    }
                    Double monto = spnr.getMontoIncluido() != null ? spnr.getMontoIncluido() : 0.0;
                    valorTotal += monto;
                    String montoStr;
                    try {
                        montoStr = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN)
                                .format((long) Math.round(monto));
                    } catch (Exception ignored) {
                        montoStr = String.valueOf((int) Math.round(monto));
                    }
                    notasRows.add(new ImpresionService.SolicitudPagoNotaRowDto(nro, fechaStr, montoStr));
                }
                numerosFactura = solicitudNotas.stream()
                        .filter(spnr -> spnr.getNotaRecepcion() != null && spnr.getNotaRecepcion().getNumero() != null)
                        .map(spnr -> String.valueOf(spnr.getNotaRecepcion().getNumero()))
                        .distinct()
                        .collect(Collectors.joining(", "));
            } else {
                if (SolicitudPagoEstado.CANCELADO.equals(solicitudPago.getEstado())) {
                    notasRows.add(new ImpresionService.SolicitudPagoNotaRowDto("—", "—", "CANCELADA - sin notas"));
                } else {
                    notasRows.add(new ImpresionService.SolicitudPagoNotaRowDto("—", "—", "—"));
                }
                numerosFactura = "---";
            }

            String observaciones = (solicitudPago.getObservaciones() != null
                    && !solicitudPago.getObservaciones().trim().isEmpty())
                            ? solicitudPago.getObservaciones().trim().toUpperCase()
                            : "";

            log.info(
                    "[imprimirSolicitudPagoPDF] solicitudPagoId={} | proveedorNombre={} | numerosFactura={} | valorTotal={} | detalleFormasPago.size()={} | notasRows.size()={}",
                    solicitudPagoId, proveedorNombre, numerosFactura, valorTotal, detalleFormasPago.size(),
                    notasRows.size());

            return impresionService.imprimirSolicitudPagoPDF(
                    solicitudPago,
                    proveedorNombre,
                    observaciones,
                    numerosFactura,
                    valorTotal,
                    notasRows,
                    detalleFormasPago);

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
    public Boolean imprimirSolicitudPagoTicket(java.lang.Number solicitudPagoId, String printerName,
            DataFetchingEnvironment env) {
        exigirLectura(solicitudPagoId != null ? solicitudPagoId.longValue() : null);
        try {
            Long id = solicitudPagoId != null ? solicitudPagoId.longValue() : null;
            if (id == null) {
                throw new RuntimeException("solicitudPagoId es requerido");
            }
            SolicitudPago solicitudPago = solicitudPagoService.getRepository()
                    .findByIdWithUsuarioAndMoneda(id)
                    .orElseThrow(() -> new RuntimeException("Solicitud de pago no encontrada: " + solicitudPagoId));

            String proveedorNombre = solicitudPago.getProveedor() != null
                    && solicitudPago.getProveedor().getPersona() != null
                            ? solicitudPago.getProveedor().getPersona().getNombre()
                            : "";

            List<SolicitudPagoDetalle> detallesTicket = solicitudPagoDetalleService.findBySolicitudPagoIdForPrint(id);
            java.time.format.DateTimeFormatter dateFmtTicket = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
            String monedaSimboloPrincipal = solicitudPago.getMoneda() != null
                    && solicitudPago.getMoneda().getSimbolo() != null
                            ? solicitudPago.getMoneda().getSimbolo()
                            : "";

            // Formas de pago: FormaPagoTicketDto (formaPago, fecha, valorFormateado =
            // simbolo + valor)
            java.util.List<ImpresionService.FormaPagoTicketDto> formasPago = new java.util.ArrayList<>();
            for (SolicitudPagoDetalle d : detallesTicket) {
                String fp = d.getFormaPago() != null && d.getFormaPago().getDescripcion() != null
                        ? d.getFormaPago().getDescripcion()
                        : "—";
                String fe = d.getFechaPago() != null ? d.getFechaPago().format(dateFmtTicket) : "—";
                String simbolo = d.getMoneda() != null && d.getMoneda().getSimbolo() != null
                        ? d.getMoneda().getSimbolo()
                        : "";
                Double val = d.getValor() != null ? d.getValor() : 0.0;
                String valorFormateado = (simbolo.isEmpty() ? "" : simbolo + " ")
                        + java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(val);
                formasPago.add(new ImpresionService.FormaPagoTicketDto(fp, fe, valorFormateado));
            }

            // Notas: NotaTicketDto (numero, fecha, valorFormateado = simbolo + valor)
            List<SolicitudPagoNotaRecepcion> solicitudNotas = solicitudPagoNotaRecepcionService
                    .getNotasDeSolicitudWithNotaRecepcion(id);
            java.util.List<ImpresionService.NotaTicketDto> notas = new java.util.ArrayList<>();
            java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy");
            if (!solicitudNotas.isEmpty()) {
                for (SolicitudPagoNotaRecepcion spnr : solicitudNotas) {
                    if (spnr == null)
                        continue;
                    String nro = "?";
                    String fechaStr = "---";
                    if (spnr.getNotaRecepcion() != null) {
                        nro = spnr.getNotaRecepcion().getNumero() != null
                                ? String.valueOf(spnr.getNotaRecepcion().getNumero())
                                : "?";
                        if (spnr.getNotaRecepcion().getFecha() != null) {
                            fechaStr = spnr.getNotaRecepcion().getFecha().toLocalDate().format(dateFmt);
                        }
                    }
                    Double monto = spnr.getMontoIncluido() != null ? spnr.getMontoIncluido() : 0.0;
                    String valorFormateado = (monedaSimboloPrincipal.isEmpty() ? "" : monedaSimboloPrincipal + " ")
                            + java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(monto);
                    notas.add(new ImpresionService.NotaTicketDto(nro, fechaStr, valorFormateado));
                }
            } else if (SolicitudPagoEstado.CANCELADO.equals(solicitudPago.getEstado())) {
                notas.add(new ImpresionService.NotaTicketDto("—", "—", "CANCELADA - sin notas"));
            }

            String usuario = "";
            if (solicitudPago.getUsuario() != null) {
                if (solicitudPago.getUsuario().getPersona() != null
                        && solicitudPago.getUsuario().getPersona().getNombre() != null) {
                    usuario = solicitudPago.getUsuario().getPersona().getNombre();
                } else if (solicitudPago.getUsuario().getNickname() != null) {
                    usuario = solicitudPago.getUsuario().getNickname();
                }
            }

            impresionService.printSolicitudPagoTicket(
                    solicitudPago, proveedorNombre, usuario, printerName, notas, formasPago);
            return true;
        } catch (GraphQLException e) {
            throw e; // ya trae el motivo («No se pudo imprimir: ...» o la impresora que falta)
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
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getProveedorId() {
            return proveedorId;
        }

        public void setProveedorId(Integer proveedorId) {
            this.proveedorId = proveedorId;
        }

        public String getNumeroSolicitud() {
            return numeroSolicitud;
        }

        public void setNumeroSolicitud(String numeroSolicitud) {
            this.numeroSolicitud = numeroSolicitud;
        }

        public String getFechaSolicitud() {
            return fechaSolicitud;
        }

        public void setFechaSolicitud(String fechaSolicitud) {
            this.fechaSolicitud = fechaSolicitud;
        }

        public String getFechaPagoPropuesta() {
            return fechaPagoPropuesta;
        }

        public void setFechaPagoPropuesta(String fechaPagoPropuesta) {
            this.fechaPagoPropuesta = fechaPagoPropuesta;
        }

        public Double getMontoTotal() {
            return montoTotal;
        }

        public void setMontoTotal(Double montoTotal) {
            this.montoTotal = montoTotal;
        }

        public Integer getMonedaId() {
            return monedaId;
        }

        public void setMonedaId(Integer monedaId) {
            this.monedaId = monedaId;
        }

        public Integer getFormaPagoId() {
            return formaPagoId;
        }

        public void setFormaPagoId(Integer formaPagoId) {
            this.formaPagoId = formaPagoId;
        }

        public SolicitudPagoEstado getEstado() {
            return estado;
        }

        public void setEstado(SolicitudPagoEstado estado) {
            this.estado = estado;
        }

        public String getObservaciones() {
            return observaciones;
        }

        public void setObservaciones(String observaciones) {
            this.observaciones = observaciones;
        }

        public List<Long> getNotaRecepcionIds() {
            return notaRecepcionIds;
        }

        public void setNotaRecepcionIds(List<Long> notaRecepcionIds) {
            this.notaRecepcionIds = notaRecepcionIds;
        }

        public List<SolicitudPagoDetalleInput> getDetalles() {
            return detalles;
        }

        public void setDetalles(List<SolicitudPagoDetalleInput> detalles) {
            this.detalles = detalles;
        }

        public Long getUsuarioId() {
            return usuarioId;
        }

        public void setUsuarioId(Long usuarioId) {
            this.usuarioId = usuarioId;
        }
    }

    private List<SolicitudPagoDetalleService.DetalleInput> mapToDetalleInputs(List<SolicitudPagoDetalleInput> list) {
        if (list == null || list.isEmpty())
            return new ArrayList<>();
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
    private Moneda resolveMoneda(SolicitudPagoInput input,
            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs) {
        if (input.getMonedaId() != null) {
            return monedaService.findById(Long.valueOf(input.getMonedaId()))
                    .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + input.getMonedaId()));
        }
        if (detalleInputs != null && !detalleInputs.isEmpty()) {
            SolicitudPagoDetalleService.DetalleInput first = detalleInputs.get(0);
            if (first.getMonedaId() != null) {
                return monedaService.findById(Long.valueOf(first.getMonedaId()))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Moneda no encontrada en detalle: " + first.getMonedaId()));
            }
        }
        Moneda defaultMoneda = monedaService.findByDescripcion("GUARANI");
        if (defaultMoneda == null) {
            throw new IllegalArgumentException("Moneda por defecto GUARANI no encontrada");
        }
        return defaultMoneda;
    }

    /**
     * Resolves FormaPago from input or first detalle. May return null (cabecera
     * acepta formaPago null).
     */
    private FormaPago resolveFormaPago(SolicitudPagoInput input,
            List<SolicitudPagoDetalleService.DetalleInput> detalleInputs) {
        final Long formaPagoId;
        if (input.getFormaPagoId() != null) {
            formaPagoId = Long.valueOf(input.getFormaPagoId());
        } else if (detalleInputs != null && !detalleInputs.isEmpty() && detalleInputs.get(0).getFormaPagoId() != null) {
            formaPagoId = Long.valueOf(detalleInputs.get(0).getFormaPagoId());
        } else {
            formaPagoId = null;
        }
        if (formaPagoId == null)
            return null;
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