package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;

@Component
public class RecepcionMercaderiaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaService service;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private com.franco.dev.service.operaciones.ConstanciaDeRecepcionService constanciaDeRecepcionService;

    @Autowired
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;

    /**
     * Obtiene una recepción de mercadería por ID
     */
    public RecepcionMercaderia recepcionMercaderia(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Busca recepciones de mercadería con filtros avanzados
     */
    public Page<RecepcionMercaderia> recepcionMercaderiaConFiltros(
            Long proveedorId,
            Long sucursalId,
            RecepcionMercaderiaEstado estado,
            List<RecepcionMercaderiaEstado> estados,
            Long usuarioId,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {

        if (page == null) page = 0;
        if (size == null) size = 20;

        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime fechaInicioDate = fechaInicio != null ? stringToDate(fechaInicio) : null;
        LocalDateTime fechaFinDate = fechaFin != null ? stringToDate(fechaFin) : null;

        return service.findByFilters(proveedorId, sucursalId, estado, estados, usuarioId, fechaInicioDate, fechaFinDate, pageable);
    }

    /**
     * Obtiene recepciones por proveedor
     */
    public List<RecepcionMercaderia> recepcionesPorProveedor(Long proveedorId) {
        if (proveedorId == null) {
            throw new GraphQLException("ID del proveedor es requerido");
        }
        return service.findByProveedorId(proveedorId);
    }

    /**
     * Obtiene recepciones por estado
     */
    public List<RecepcionMercaderia> recepcionesPorEstado(RecepcionMercaderiaEstado estado) {
        if (estado == null) {
            throw new GraphQLException("Estado es requerido");
        }
        return service.findByEstado(estado);
    }

    /**
     * Guarda una nueva recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderia saveRecepcionMercaderia(RecepcionMercaderiaInput input) {
        ModelMapper mapper = new ModelMapper();
        RecepcionMercaderia entity = mapper.map(input, RecepcionMercaderia.class);

        // Mapear relaciones
        if (input.getProveedorId() != null) {
            entity.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        }

        if (input.getSucursalRecepcionId() != null) {
            entity.setSucursalRecepcion(sucursalService.findById(input.getSucursalRecepcionId()).orElse(null));
        }

        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }

        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getFecha() != null) {
            entity.setFecha(stringToDate(input.getFecha()));
        }

        // TODO: Uncomment when creadoEn is added to RecepcionMercaderia entity
        // if (input.getCreadoEn() != null) {
        //     entity.setCreadoEn(stringToDate(input.getCreadoEn()));
        // }

        RecepcionMercaderia recepcionGuardada = service.save(entity);

        // Asociar notas de recepción si están especificadas
        if (input.getNotaRecepcionIds() != null && !input.getNotaRecepcionIds().isEmpty()) {
            service.asociarNotasRecepcion(recepcionGuardada.getId(), input.getNotaRecepcionIds());
        }

        return recepcionGuardada;
    }

    /**
     * FUNCIÓN CRÍTICA: Finaliza una recepción de mercadería
     * Genera movimientos de stock y actualiza costos
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcionMercaderia(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de la recepción es requerido");
        }

        try {
            System.out.println("=== FINALIZANDO RECEPCIÓN DE MERCADERÍA ===");
            System.out.println("Recepción ID: " + recepcionId);

            RecepcionMercaderia recepcion = service.finalizarRecepcion(recepcionId);

            // Generar constancia automáticamente
            List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcionId);

            // Persistir constancia
            constanciaDeRecepcionService.generarConstancia(recepcion, items);

            System.out.println("=== RECEPCIÓN FINALIZADA EXITOSAMENTE ===");
            System.out.println("Estado final: " + recepcion.getEstado());

            return recepcion;

        } catch (Exception e) {
            System.err.println("Error al finalizar recepción " + recepcionId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al finalizar recepción: " + e.getMessage());
        }
    }

    /**
     * Inicia recepción móvil (crea la recepción, asocia notas y pre-crea todos los ítems)
     */
    @Transactional
    public RecepcionMercaderia iniciarRecepcion(Long sucursalId, List<Long> notaRecepcionIds, Long proveedorId, Long monedaId, Long usuarioId, Double cotizacion) {
        if (sucursalId == null || proveedorId == null || monedaId == null || usuarioId == null || notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("Parámetros inválidos para iniciar recepción");
        }

        // Crear recepción básica
        RecepcionMercaderia recepcion = crearRecepcionMercaderia(proveedorId, sucursalId, monedaId, cotizacion, usuarioId);

        // Asociar notas
        asociarNotasARecepcion(recepcion.getId(), notaRecepcionIds);

        // Pre-crear todos los ítems de recepción para cada nota
        preCrearItemsRecepcion(recepcion.getId(), notaRecepcionIds, usuarioId);

        return recepcion;
    }

    /**
     * Crea una nueva recepción de mercadería con datos básicos
     */
    @Transactional
    public RecepcionMercaderia crearRecepcionMercaderia(
            Long proveedorId,
            Long sucursalId,
            Long monedaId,
            Double cotizacion,
            Long usuarioId) {

        if (proveedorId == null || sucursalId == null || usuarioId == null) {
            throw new GraphQLException("Proveedor, sucursal y usuario son requeridos");
        }

        try {
            com.franco.dev.domain.personas.Proveedor proveedor = proveedorService.findById(proveedorId)
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + proveedorId));

            com.franco.dev.domain.empresarial.Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new GraphQLException("Sucursal no encontrada: " + sucursalId));

            com.franco.dev.domain.financiero.Moneda moneda = monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId));

            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

            return service.crearRecepcion(proveedor, sucursal, moneda, 
                                        cotizacion != null ? cotizacion : 1.0, usuario);

        } catch (Exception e) {
            throw new GraphQLException("Error al crear recepción: " + e.getMessage());
        }
    }

    /**
     * Asocia notas de recepción a una recepción de mercadería
     */
    @Transactional
    public Boolean asociarNotasARecepcion(Long recepcionId, List<Long> notaRecepcionIds) {
        if (recepcionId == null || notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("ID de recepción y lista de notas son requeridos");
        }

        try {
            service.asociarNotasRecepcion(recepcionId, notaRecepcionIds);
            return true;
        } catch (Exception e) {
            throw new GraphQLException("Error al asociar notas: " + e.getMessage());
        }
    }

    /**
     * Pre-crea todos los ítems de recepción para las notas asociadas
     * Cada ítem se crea con estado PENDIENTE y cantidadRecibida = 0
     */
    @Transactional
    public void preCrearItemsRecepcion(Long recepcionId, List<Long> notaRecepcionIds, Long usuarioId) {
        if (recepcionId == null || notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("ID de recepción, lista de notas y usuario son requeridos");
        }

        try {
            // Obtener la recepción
            RecepcionMercaderia recepcion = service.findById(recepcionId)
                .orElseThrow(() -> new GraphQLException("Recepción no encontrada: " + recepcionId));

            // Obtener el usuario
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

            // Para cada nota, obtener sus distribuciones y crear los ítems
            for (Long notaId : notaRecepcionIds) {
                List<NotaRecepcionItemDistribucion> distribuciones = 
                    notaRecepcionItemDistribucionService.findByNotaRecepcionId(notaId);
                
                for (NotaRecepcionItemDistribucion distribucion : distribuciones) {
                    // Crear el ítem de recepción
                    RecepcionMercaderiaItem item = new RecepcionMercaderiaItem();
                    item.setRecepcionMercaderia(recepcion);
                    item.setNotaRecepcionItem(distribucion.getNotaRecepcionItem());
                    item.setNotaRecepcionItemDistribucion(distribucion);
                    item.setProducto(distribucion.getNotaRecepcionItem().getProducto());
                    item.setPresentacionRecibida(distribucion.getNotaRecepcionItem().getPresentacionEnNota());
                    item.setSucursalEntrega(recepcion.getSucursalRecepcion());
                    item.setUsuario(usuario);
                    item.setCantidadRecibida(0.0); // Inicialmente 0
                    item.setCantidadRechazada(0.0); // Inicialmente 0
                    item.setEsBonificacion(false);
                    item.setEstadoVerificacion(EstadoVerificacion.PENDIENTE); // Estado inicial
                    
                    // Guardar el ítem
                    recepcionMercaderiaItemService.save(item);
                }
            }
        } catch (Exception e) {
            throw new GraphQLException("Error al pre-crear ítems de recepción: " + e.getMessage());
        }
    }

    /**
     * Genera PDF de constancia de recepción y retorna como base64
     */
    public com.franco.dev.graphql.operaciones.dto.ConstanciaRecepcionPDFDTO generarConstanciaRecepcionPDF(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de la recepción es requerido");
        }

        try {
            // Generar PDF usando el servicio de impresión
            byte[] pdfBytes = constanciaDeRecepcionService.generarConstanciaRecepcionPDF(recepcionId);
            
            // Convertir a base64
            String pdfBase64 = java.util.Base64.getEncoder().encodeToString(pdfBytes);
            
            // Crear respuesta
            return new com.franco.dev.graphql.operaciones.dto.ConstanciaRecepcionPDFDTO(
                pdfBase64,
                "constancia-recepcion-" + recepcionId + ".pdf",
                (long) pdfBytes.length,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            throw new GraphQLException("Error al generar PDF: " + e.getMessage());
        }
    }
} 