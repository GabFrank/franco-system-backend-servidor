package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.graphql.operaciones.input.RechazoPendientesInput;
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
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import com.franco.dev.domain.productos.Producto;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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

    @Autowired
    private RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;

    @Autowired
    private com.franco.dev.service.operaciones.NotaRecepcionItemService notaRecepcionItemService;

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
     * Verifica si existe una recepción activa para una nota en una sucursal específica
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (validación al buscar nota por número antes de agregarla)
     * 
     * @param notaRecepcionId ID de la nota de recepción
     * @param sucursalRecepcionId ID de la sucursal de recepción
     * @return RecepcionMercaderia activa si existe, null si no existe
     */
    public RecepcionMercaderia verificarRecepcionActivaPorNotaYSucursal(Long notaRecepcionId, Long sucursalRecepcionId) {
        if (notaRecepcionId == null || sucursalRecepcionId == null) {
            return null;
        }
        return service.encontrarRecepcionActivaPorNotaYSucursal(notaRecepcionId, sucursalRecepcionId);
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
     * Si rechazoPendientes se proporciona, los items pendientes se marcan como rechazados antes de finalizar.
     *
     * Usado en:
     * - Desktop: Sí (opcional, sin rechazoPendientes)
     * - Mobile: Sí (con rechazoPendientes cuando hay items pendientes)
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcionMercaderia(Long recepcionId, RechazoPendientesInput rechazoPendientes) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de la recepción es requerido");
        }

        try {
            System.out.println("=== FINALIZANDO RECEPCIÓN DE MERCADERÍA ===");
            System.out.println("Recepción ID: " + recepcionId);

            com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico motivoRechazoPendientes = 
                (rechazoPendientes != null && rechazoPendientes.getMotivoRechazo() != null) 
                    ? rechazoPendientes.getMotivoRechazo() : null;
            RecepcionMercaderia recepcion = service.finalizarRecepcion(recepcionId, motivoRechazoPendientes);

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
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (inicio de recepción desde mobile)
     */
    @Transactional
    public RecepcionMercaderia iniciarRecepcion(Long sucursalId, List<Long> notaRecepcionIds, Long proveedorId, Long monedaId, Long usuarioId, Double cotizacion) {
        if (sucursalId == null || proveedorId == null || monedaId == null || usuarioId == null || notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("Parámetros inválidos para iniciar recepción");
        }

        // VALIDACIÓN: Verificar si ya existe una recepción para alguna nota en esta sucursal
        // Bloquea EN_PROCESO, PENDIENTE y FINALIZADA para evitar duplicación de stock y costos
        for (Long notaId : notaRecepcionIds) {
            RecepcionMercaderia recepcionExistente = service.encontrarRecepcionActivaPorNotaYSucursal(notaId, sucursalId);
            if (recepcionExistente != null) {
                String mensaje;
                if (recepcionExistente.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
                    mensaje = String.format(
                        "Esta nota ya fue recibida y finalizada (Recepción ID: %d) en la sucursal seleccionada. " +
                        "Si necesita hacer correcciones, use la opción 'Reabrir recepción' en lugar de crear una nueva. " +
                        "Crear una nueva recepción duplicaría movimientos de stock y costos.",
                        recepcionExistente.getId()
                    );
                } else {
                    mensaje = String.format(
                        "Ya existe una recepción en proceso (ID: %d, Estado: %s) para la nota %d en la sucursal seleccionada. " +
                        "Debe finalizar o cancelar la recepción existente antes de crear una nueva.",
                        recepcionExistente.getId(), 
                        recepcionExistente.getEstado(),
                        notaId
                    );
                }
                throw new GraphQLException(mensaje);
            }
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
                    // FILTRAR: Solo crear items para distribuciones de la sucursal de recepción
                    // Esto evita crear items innecesarios con cantidad 0 para otras sucursales
                    if (distribucion.getSucursalEntrega() == null || 
                        !distribucion.getSucursalEntrega().getId().equals(recepcion.getSucursalRecepcion().getId())) {
                        continue; // Saltar distribuciones de otras sucursales
                    }
                    
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

            // Después de crear items para distribuciones, también crear items para NotaRecepcionItem sin distribución
            // Esto asegura que todos los items de nota estén disponibles para recepción, incluso si no tienen distribución
            for (Long notaId : notaRecepcionIds) {
                List<com.franco.dev.domain.operaciones.NotaRecepcionItem> itemsNota = 
                    notaRecepcionItemService.findByNotaRecepcionId(notaId);
                
                for (com.franco.dev.domain.operaciones.NotaRecepcionItem notaItem : itemsNota) {
                    // Verificar si ya tiene distribución
                    List<NotaRecepcionItemDistribucion> distribucionesItem = 
                        notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(notaItem.getId());
                    
                    // Si no tiene distribución, crear item de recepción
                    if (distribucionesItem.isEmpty()) {
                        // Verificar si ya existe un item de recepción para este notaItem en esta recepción
                        List<RecepcionMercaderiaItem> itemsExistentes = 
                            recepcionMercaderiaItemService.getRepository()
                                .findByNotaRecepcionItemIdAndRecepcionMercaderiaId(
                                    notaItem.getId(), recepcionId);
                        
                        if (itemsExistentes.isEmpty()) {
                            RecepcionMercaderiaItem item = new RecepcionMercaderiaItem();
                            item.setRecepcionMercaderia(recepcion);
                            item.setNotaRecepcionItem(notaItem);
                            item.setNotaRecepcionItemDistribucion(null); // Sin distribución
                            item.setProducto(notaItem.getProducto());
                            item.setPresentacionRecibida(notaItem.getPresentacionEnNota());
                            item.setSucursalEntrega(recepcion.getSucursalRecepcion());
                            item.setUsuario(usuario);
                            item.setCantidadRecibida(0.0);
                            item.setCantidadRechazada(0.0);
                            item.setEsBonificacion(notaItem.getEsBonificacion() != null ? notaItem.getEsBonificacion() : false);
                            item.setEstadoVerificacion(EstadoVerificacion.PENDIENTE);
                            
                            recepcionMercaderiaItemService.save(item);
                        }
                    }
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

    /**
     * Obtiene productos agrupados por RecepcionMercaderia
     * Agrupa productos de todas las notas asociadas y calcula cantidades totales
     * Filtra las distribuciones por la sucursal de recepción (solo muestra distribuciones
     * donde sucursal_entrega_id coincide con sucursal_recepcion_id)
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (componente recepcion-producto para mostrar lista agrupada)
     */
    public Page<PedidoRecepcionProductoDto> pedidoRecepcionProductoPorRecepcionMercaderia(
            Long recepcionMercaderiaId,
            PedidoRecepcionProductoEstado estado,
            Integer page,
            Integer size) {
        
        if (recepcionMercaderiaId == null) {
            throw new GraphQLException("ID de recepción de mercadería es requerido");
        }

        if (page == null) page = 0;
        if (size == null) size = 20;

        // 1. Verificar que la recepción existe y obtenerla
        Optional<RecepcionMercaderia> recepcionOpt = service.findById(recepcionMercaderiaId);
        if (!recepcionOpt.isPresent()) {
            throw new GraphQLException("Recepción de mercadería no encontrada: " + recepcionMercaderiaId);
        }
        
        RecepcionMercaderia recepcion = recepcionOpt.get();
        Long sucursalRecepcionId = recepcion.getSucursalRecepcion() != null ? 
            recepcion.getSucursalRecepcion().getId() : null;

        // 2. Obtener todas las NotaRecepcion asociadas (RecepcionMercaderiaNota)
        List<com.franco.dev.domain.operaciones.RecepcionMercaderiaNota> asociaciones = 
            recepcionMercaderiaNotaService.findByRecepcionMercaderiaId(recepcionMercaderiaId);
        
        if (asociaciones.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(
                new ArrayList<>(), 
                PageRequest.of(page, size), 
                0
            );
        }

        List<Long> notaRecepcionIds = asociaciones.stream()
            .map(assoc -> assoc.getNotaRecepcion().getId())
            .collect(Collectors.toList());

        // 3. Obtener todas las NotaRecepcionItemDistribucion de esas notas
        List<NotaRecepcionItemDistribucion> distribuciones = 
            notaRecepcionItemDistribucionService.findByNotaRecepcionIds(notaRecepcionIds);
        
        // 3.1. Filtrar distribuciones por sucursal de recepción (solo mostrar distribuciones de la sucursal donde se recepciona)
        if (sucursalRecepcionId != null) {
            distribuciones = distribuciones.stream()
                .filter(dist -> dist.getSucursalEntrega() != null && 
                               dist.getSucursalEntrega().getId().equals(sucursalRecepcionId))
                .collect(Collectors.toList());
        }

        // 4. Obtener todos los RecepcionMercaderiaItem de la recepción
        List<RecepcionMercaderiaItem> itemsRecepcion = 
            recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcionMercaderiaId);

        // 5. Agrupar por Producto (usando ID como clave) y calcular totales
        Map<Long, PedidoRecepcionProductoDto> productosMap = new HashMap<>();
        Map<Long, Producto> productosById = new HashMap<>();

        for (NotaRecepcionItemDistribucion distribucion : distribuciones) {
            if (distribucion == null || distribucion.getNotaRecepcionItem() == null) {
                continue;
            }
            
            Producto producto = distribucion.getNotaRecepcionItem().getProducto();
            if (producto == null || producto.getId() == null) {
                continue;
            }
            
            Long productoId = producto.getId();
            productosById.put(productoId, producto);
            
            PedidoRecepcionProductoDto dto = productosMap.get(productoId);
            if (dto == null) {
                dto = new PedidoRecepcionProductoDto(producto, 0.0, 0.0, 0.0);
                productosMap.put(productoId, dto);
            }
            
            // Sumar cantidad a recibir
            Double cantidadDistribucion = distribucion.getCantidad() != null ? distribucion.getCantidad() : 0.0;
            Double cantidadActual = dto.getTotalCantidadARecibirPorUnidad() != null ? dto.getTotalCantidadARecibirPorUnidad() : 0.0;
            dto.setTotalCantidadARecibirPorUnidad(cantidadActual + cantidadDistribucion);
        }

        // 6. Calcular cantidad recibida por producto desde RecepcionMercaderiaItem
        for (RecepcionMercaderiaItem item : itemsRecepcion) {
            if (item == null || item.getProducto() == null || item.getProducto().getId() == null) {
                continue;
            }
            
            Long productoId = item.getProducto().getId();
            Producto producto = item.getProducto();
            productosById.put(productoId, producto);
            
            PedidoRecepcionProductoDto dto = productosMap.get(productoId);
            if (dto == null) {
                // Si el producto no estaba en distribuciones, crear DTO nuevo
                dto = new PedidoRecepcionProductoDto(producto, 0.0, 0.0, 0.0);
                productosMap.put(productoId, dto);
            }
            
            Double cantidadRecibida = item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0;
            Double cantidadActual = dto.getTotalCantidadRecibidaPorUnidad() != null ? dto.getTotalCantidadRecibidaPorUnidad() : 0.0;
            dto.setTotalCantidadRecibidaPorUnidad(cantidadActual + cantidadRecibida);
            
            // Sumar cantidad rechazada
            Double cantidadRechazada = item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0;
            Double cantidadRechazadaActual = dto.getTotalCantidadRechazadaPorUnidad() != null ? dto.getTotalCantidadRechazadaPorUnidad() : 0.0;
            dto.setTotalCantidadRechazadaPorUnidad(cantidadRechazadaActual + cantidadRechazada);
        }

        // 7. Convertir a lista, asegurar valores no null y aplicar filtro por estado si se proporciona
        List<PedidoRecepcionProductoDto> productosList = new ArrayList<>();
        for (PedidoRecepcionProductoDto dto : productosMap.values()) {
            // Asegurar que los valores no sean null
            if (dto.getTotalCantidadARecibirPorUnidad() == null) {
                dto.setTotalCantidadARecibirPorUnidad(0.0);
            }
            if (dto.getTotalCantidadRecibidaPorUnidad() == null) {
                dto.setTotalCantidadRecibidaPorUnidad(0.0);
            }
            if (dto.getTotalCantidadRechazadaPorUnidad() == null) {
                dto.setTotalCantidadRechazadaPorUnidad(0.0);
            }
            productosList.add(dto);
        }
        
        if (estado != null) {
            productosList = productosList.stream()
                .filter(dto -> {
                    try {
                        PedidoRecepcionProductoEstado dtoEstado = calcularEstado(dto);
                        return dtoEstado == estado;
                    } catch (Exception e) {
                        // Si hay error calculando el estado, excluir el item
                        return false;
                    }
                })
                .collect(Collectors.toList());
        }

        // 8. Aplicar paginación manual
        int start = page * size;
        int end = Math.min(start + size, productosList.size());
        List<PedidoRecepcionProductoDto> productosPaginados = 
            start < productosList.size() ? productosList.subList(start, end) : new ArrayList<>();

        return new org.springframework.data.domain.PageImpl<>(
            productosPaginados,
            PageRequest.of(page, size),
            productosList.size()
        );
    }

    /**
     * Busca un PedidoRecepcionProductoDto específico por RecepcionMercaderia y Producto
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (búsqueda por código de barras)
     */
    public PedidoRecepcionProductoDto pedidoRecepcionProductoPorRecepcionMercaderiaAndProducto(
            Long recepcionMercaderiaId,
            Long productoId,
            PedidoRecepcionProductoEstado estado) {
        
        if (recepcionMercaderiaId == null || productoId == null) {
            throw new GraphQLException("ID de recepción y producto son requeridos");
        }

        // Obtener todos los productos agrupados
        Page<PedidoRecepcionProductoDto> productosPage = pedidoRecepcionProductoPorRecepcionMercaderia(
            recepcionMercaderiaId, null, 0, Integer.MAX_VALUE);

        // Buscar el producto específico
        PedidoRecepcionProductoDto productoDto = productosPage.getContent().stream()
            .filter(dto -> dto.getProducto() != null && dto.getProducto().getId().equals(productoId))
            .findFirst()
            .orElse(null);

        if (productoDto == null) {
            throw new GraphQLException("Producto no encontrado en la recepción");
        }

        // Aplicar filtro por estado si se proporciona
        if (estado != null) {
            PedidoRecepcionProductoEstado dtoEstado = calcularEstado(productoDto);
            if (dtoEstado != estado) {
                throw new GraphQLException("Producto no encontrado con el estado especificado");
            }
        }

        return productoDto;
    }

    /**
     * Calcula el estado de un PedidoRecepcionProductoDto
     */
    private PedidoRecepcionProductoEstado calcularEstado(PedidoRecepcionProductoDto dto) {
        if (dto == null) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        Double cantidadRecibida = dto.getTotalCantidadRecibidaPorUnidad();
        Double cantidadARecibir = dto.getTotalCantidadARecibirPorUnidad();
        
        if (cantidadRecibida == null || cantidadRecibida == 0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        if (cantidadARecibir == null || cantidadARecibir == 0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        if (cantidadRecibida >= cantidadARecibir) {
            return PedidoRecepcionProductoEstado.RECIBIDO;
        } else {
            return PedidoRecepcionProductoEstado.RECIBIDO_PARCIALMENTE;
        }
    }

    /**
     * Reabre una recepción de mercadería finalizada
     * Cambia el estado de FINALIZADA a EN_PROCESO
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (permitir reabrir recepción para correcciones)
     * 
     * NOTA: No revierte movimientos de stock ni costos. Solo cambia el estado.
     * Para revertir completamente, se debe eliminar la recepción y crear una nueva.
     */
    @Transactional
    public RecepcionMercaderia reabrirRecepcionMercaderia(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }
        
        RecepcionMercaderia recepcion = service.findById(recepcionId)
            .orElseThrow(() -> new GraphQLException("Recepción no encontrada: " + recepcionId));
        
        if (recepcion.getEstado() != RecepcionMercaderiaEstado.FINALIZADA) {
            throw new GraphQLException("Solo se pueden reabrir recepciones finalizadas. Estado actual: " + recepcion.getEstado());
        }
        
        recepcion.setEstado(RecepcionMercaderiaEstado.EN_PROCESO);
        return service.save(recepcion);
    }
} 