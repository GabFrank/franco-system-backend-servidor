package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemVariacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.dto.operaciones.ValidacionFinalizacionRecepcion;
import com.franco.dev.dto.operaciones.ItemPendiente;
import com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaItemVariacionInput;

@Component
public class RecepcionMercaderiaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaItemService service;

    @Autowired
    private RecepcionMercaderiaItemVariacionService recepcionMercaderiaItemVariacionService;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    /**
     * Obtiene el ID de la recepción de mercadería
     */
    public Integer getRecepcionMercaderiaId(RecepcionMercaderiaItem item) {
        if (item.getRecepcionMercaderia() != null) {
            return item.getRecepcionMercaderia().getId().intValue();
        }
        return null;
    }

    /**
     * Obtiene un ítem de recepción por ID
     */
    public RecepcionMercaderiaItem recepcionMercaderiaItem(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }
        return service.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería con paginación y filtros
     */
    public Page<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorRecepcionPaginados(
            Long recepcionId, 
            Integer page, 
            Integer size, 
            String filtroTexto,
            List<EstadoVerificacion> estados) {
        
        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }

        // Crear pageable para paginación
        Pageable pageable = PageRequest.of(
            page != null ? page : 0, 
            size != null ? size : 20
        );

        // Obtener ítems paginados desde el servicio
        return service.findByRecepcionMercaderiaIdPaginados(recepcionId, filtroTexto, estados, pageable);
    }

    /**
     * Busca un item pendiente de recepción por producto en una recepción específica
     */
    public RecepcionMercaderiaItem findPendienteRecepcionItemPorProducto(Long recepcionId, Long productoId) {
        if (recepcionId == null || productoId == null) {
            throw new GraphQLException("ID de recepción y producto son requeridos");
        }
        
        return service.findPendienteRecepcionItemPorProducto(recepcionId, productoId);
    }

    /**
     * Obtiene ítems por producto y sucursal
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorProductoYSucursal(Long productoId, Long sucursalId) {
        if (productoId == null || sucursalId == null) {
            throw new GraphQLException("ID de producto y sucursal son requeridos");
        }
        return service.findByProductoIdAndSucursalEntregaId(productoId, sucursalId);
    }

    /**
     * Guarda un ítem de recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderiaItem saveRecepcionMercaderiaItem(RecepcionMercaderiaItemInput input) {
        if (input == null || input.getId() == null) {
            throw new GraphQLException("Input o ID de ítem de recepción es requerido para la actualización");
        }

        try {
            // 1. Obtener el RecepcionMercaderiaItem existente
            RecepcionMercaderiaItem item = service.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderiaItem no encontrado con ID: " + input.getId()));

            // 2. Mapear campos opcionales del item principal
            if(input.getUsuarioId() != null){
                item.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            item.setObservaciones(input.getObservaciones());
            item.setMetodoVerificacion(input.getMetodoVerificacion());
            item.setMotivoVerificacionManual(input.getMotivoVerificacionManual());

            // 3. Eliminar variaciones anteriores
            recepcionMercaderiaItemVariacionService.deleteByRecepcionMercaderiaItemId(item.getId());

            Double cantidadRecibidaTotal = 0.0;
            Double cantidadRechazadaTotal = 0.0;

            // 4. Procesar y crear las nuevas variaciones
            if (input.getVariaciones() != null && !input.getVariaciones().isEmpty()) {
                for (RecepcionMercaderiaItemVariacionInput varInput : input.getVariaciones()) {
                    RecepcionMercaderiaItemVariacion variacion = new RecepcionMercaderiaItemVariacion();
                    variacion.setRecepcionMercaderiaItem(item);

                    if (varInput.getPresentacionId() != null) {
                        variacion.setPresentacion(presentacionService.findById(varInput.getPresentacionId()).orElse(null));
                    }
                    if (varInput.getVencimiento() != null && !varInput.getVencimiento().isEmpty()) {
                        variacion.setVencimiento(stringToDate(varInput.getVencimiento()));
                    }

                    variacion.setCantidad(varInput.getCantidad());
                    variacion.setLote(varInput.getLote());
                    variacion.setRechazado(varInput.getRechazado() != null && varInput.getRechazado());
                    variacion.setMotivoRechazo(varInput.getMotivoRechazo());

                    recepcionMercaderiaItemVariacionService.save(variacion);

                    // 5. Sumarizar totales
                    if (variacion.getRechazado()) {
                        cantidadRechazadaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                    } else {
                        cantidadRecibidaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                    }
                }
            }

            // 6. Actualizar el item principal con los totales sumados
            item.setCantidadRecibida(cantidadRecibidaTotal);
            item.setCantidadRechazada(cantidadRechazadaTotal);

            // 7. Calcular y actualizar el estado de verificación
            actualizarEstadoVerificacion(item);

            // 8. Guardar el ítem principal actualizado
            return service.save(item);
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Error al guardar ítem de recepción: " + e.getMessage());
        }
        }

    /**
     * Cancela la verificación de un ítem de recepción
     */
    public Boolean cancelarVerificacion(Long notaRecepcionItemId, Long sucursalId) {
        System.out.println("=== CANCELAR VERIFICACIÓN ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("SucursalId: " + sucursalId);
        
        if (notaRecepcionItemId == null || sucursalId == null) {
            throw new GraphQLException("NotaRecepcionItemId y SucursalId son requeridos");
        }
        
        try {
            Boolean resultado = service.cancelarVerificacion(notaRecepcionItemId, sucursalId);
            System.out.println("Resultado de cancelación: " + resultado);
            return resultado;
        } catch (Exception e) {
            System.err.println("Error al cancelar verificación: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al cancelar verificación: " + e.getMessage());
        }
    }

    /**
     * Resetea la verificación de un ítem de recepción (elimina variaciones y resetea estado)
     */
    public Boolean resetearVerificacion(Long recepcionMercaderiaItemId) {
        System.out.println("=== RESETEAR VERIFICACIÓN ===");
        System.out.println("RecepcionMercaderiaItemId: " + recepcionMercaderiaItemId);
        
        if (recepcionMercaderiaItemId == null) {
            throw new GraphQLException("RecepcionMercaderiaItemId es requerido");
        }
        
        try {
            Boolean resultado = service.resetearVerificacion(recepcionMercaderiaItemId);
            System.out.println("Resultado de reseteo: " + resultado);
            return resultado;
        } catch (Exception e) {
            System.err.println("Error al resetear verificación: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al resetear verificación: " + e.getMessage());
        }
    }

    /**
     * Cancela el rechazo de un ítem de recepción
     */
    public Boolean cancelarRechazo(Long notaRecepcionItemId, Long sucursalId) {
        System.out.println("=== CANCELAR RECHAZO ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("SucursalId: " + sucursalId);
        
        if (notaRecepcionItemId == null || sucursalId == null) {
            throw new GraphQLException("NotaRecepcionItemId y SucursalId son requeridos");
        }
        
        try {
            Boolean resultado = service.cancelarRechazo(notaRecepcionItemId, sucursalId);
            System.out.println("Resultado de cancelación de rechazo: " + resultado);
            return resultado;
        } catch (Exception e) {
            System.err.println("Error al cancelar rechazo: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al cancelar rechazo: " + e.getMessage());
        }
    }

    /**
     * Rechaza un ítem de recepción con múltiples distribuciones
     */
    @Transactional
    public Boolean rechazarItem(Long notaRecepcionItemId, Long presentacionId, List<RechazoInput> rechazos, Long usuarioId) {
        System.out.println("=== RECHAZAR ÍTEM ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("PresentacionId: " + presentacionId);
        System.out.println("UsuarioId: " + usuarioId);
        System.out.println("Cantidad de rechazos: " + (rechazos != null ? rechazos.size() : 0));
        
        if (notaRecepcionItemId == null) {
            throw new GraphQLException("NotaRecepcionItemId es requerido");
        }
        
        if (rechazos == null || rechazos.isEmpty()) {
            throw new GraphQLException("Lista de rechazos es requerida");
        }
        
        if (usuarioId == null) {
            throw new GraphQLException("UsuarioId es requerido");
        }
        
        try {
            // 1. Obtener el NotaRecepcionItem
            NotaRecepcionItem notaItem = notaRecepcionItemService.findById(notaRecepcionItemId)
                .orElseThrow(() -> new RuntimeException("NotaRecepcionItem no encontrado: " + notaRecepcionItemId));
            
            // 2. Obtener la presentación si se proporciona
            Presentacion presentacion = null;
            if (presentacionId != null) {
                presentacion = presentacionService.findById(presentacionId)
                    .orElseThrow(() -> new RuntimeException("Presentación no encontrada: " + presentacionId));
            }
            
            // 3. Obtener el usuario
            Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + usuarioId));
            
            // 4. Procesar cada rechazo
            for (RechazoInput rechazo : rechazos) {
                System.out.println("Procesando rechazo para sucursal " + rechazo.getSucursalId() + 
                    ": cantidad=" + rechazo.getCantidadRechazada() + ", motivo=" + rechazo.getMotivoRechazo());
                
                // Crear RecepcionMercaderiaItem para el rechazo
                RecepcionMercaderiaItem itemRechazo = new RecepcionMercaderiaItem();
                
                // Obtener o crear RecepcionMercaderia
                RecepcionMercaderia recepcion = recepcionMercaderiaService.obtenerOcrearRecepcion(
                    notaItem.getNotaRecepcion().getPedido().getProveedor().getId(),
                    rechazo.getSucursalId(),
                    notaItem.getNotaRecepcion().getPedido().getMoneda().getId(),
                    1.0, // cotización por defecto
                    usuarioId
                );
                
                itemRechazo.setRecepcionMercaderia(recepcion);
                itemRechazo.setNotaRecepcionItem(notaItem);
                itemRechazo.setProducto(notaItem.getProducto());
                itemRechazo.setPresentacionRecibida(presentacion);
                itemRechazo.setSucursalEntrega(sucursalService.findById(rechazo.getSucursalId())
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada: " + rechazo.getSucursalId())));
                itemRechazo.setUsuario(usuario);
                itemRechazo.setCantidadRecibida(0.0); // No se recibe nada
                itemRechazo.setCantidadRechazada(rechazo.getCantidadRechazada());
                itemRechazo.setMotivoRechazo(rechazo.getMotivoRechazo());
                itemRechazo.setObservaciones(rechazo.getObservaciones());
                itemRechazo.setEsBonificacion(notaItem.getEsBonificacion());
                
                // Guardar el ítem de rechazo
                RecepcionMercaderiaItem itemGuardado = service.save(itemRechazo);
                System.out.println("Rechazo guardado con ID: " + itemGuardado.getId());
            }
            
            System.out.println("=== Rechazo de ítem completado exitosamente ===");
            return true;
            
        } catch (Exception e) {
            System.err.println("Error al rechazar ítem: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al rechazar ítem: " + e.getMessage());
        }
    }

    /**
     * Crea una recepción automática basada en el input
     */
    private RecepcionMercaderia crearRecepcionAutomatica(RecepcionMercaderiaItemInput input) {
        // Obtener datos del ítem de nota para crear la recepción
        NotaRecepcionItem notaItem = notaRecepcionItemService.findById(input.getNotaRecepcionItemId())
            .orElseThrow(() -> new GraphQLException("Ítem de nota no encontrado para crear recepción"));
        
        NotaRecepcion nota = notaItem.getNotaRecepcion();
        Pedido pedido = nota.getPedido();
        
        // Usar sucursal de entrega como sucursal de recepción
        Long sucursalRecepcionId = input.getSucursalEntregaId();
        
        // Obtener o crear recepción según las reglas de negocio
        RecepcionMercaderia recepcion = recepcionMercaderiaService.obtenerOcrearRecepcion(
            pedido.getProveedor().getId(),
            sucursalRecepcionId,
            pedido.getMoneda().getId(),
            1.0, // cotizacion por defecto
            input.getUsuarioId()
        );
        
        // Asociar la nota a la recepción si no está ya asociada
        if (!recepcionMercaderiaNotaService.existeAsociacion(recepcion.getId(), nota.getId())) {
            recepcionMercaderiaNotaService.asociarNotaARecepcion(recepcion, nota.getId());
        }
        
        return recepcion;
    }

    /**
     * Clase para representar un rechazo individual en GraphQL
     */
    public static class RechazoInput {
        private Long sucursalId;
        private Double cantidadRechazada;
        private MotivoRechazoFisico motivoRechazo;
        private String observaciones;
        
        // Constructores
        public RechazoInput() {}
        
        public RechazoInput(Long sucursalId, Double cantidadRechazada, MotivoRechazoFisico motivoRechazo, String observaciones) {
            this.sucursalId = sucursalId;
            this.cantidadRechazada = cantidadRechazada;
            this.motivoRechazo = motivoRechazo;
            this.observaciones = observaciones;
        }
        
        // Getters y Setters
        public Long getSucursalId() { return sucursalId; }
        public void setSucursalId(Long sucursalId) { this.sucursalId = sucursalId; }
        
        public Double getCantidadRechazada() { return cantidadRechazada; }
        public void setCantidadRechazada(Double cantidadRechazada) { this.cantidadRechazada = cantidadRechazada; }
        
        public MotivoRechazoFisico getMotivoRechazo() { return motivoRechazo; }
        public void setMotivoRechazo(MotivoRechazoFisico motivoRechazo) { this.motivoRechazo = motivoRechazo; }
        
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }

    /**
     * Valida si se puede finalizar la recepción física por pedido
     */
    @Transactional
    public ValidacionFinalizacionRecepcion validarFinalizacionRecepcionPorPedido(Long pedidoId, List<Long> sucursalesIds) {
        System.out.println("=== VALIDACIÓN FINALIZACIÓN RECEPCIÓN POR PEDIDO ===");
        System.out.println("PedidoId: " + pedidoId);
        System.out.println("SucursalesIds: " + sucursalesIds);
        
        if (pedidoId == null) {
            throw new GraphQLException("PedidoId es requerido");
        }
        
        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            throw new GraphQLException("Lista de sucursales es requerida");
        }
        
        try {
            // 1. Obtener todas las notas de recepción del pedido
            List<NotaRecepcion> notasPedido = notaRecepcionService.findByPedidoId(pedidoId);
            System.out.println("Notas de recepción encontradas: " + notasPedido.size());
            
            List<ItemPendiente> itemsPendientes = new ArrayList<>();
            
            for (NotaRecepcion nota : notasPedido) {
                System.out.println("Procesando nota: " + nota.getNumero());
                
                // 2. Obtener todos los ítems de la nota
                List<NotaRecepcionItem> itemsNota = notaRecepcionItemService.findByNotaRecepcionId(nota.getId());
                System.out.println("Ítems de nota encontrados: " + itemsNota.size());
                
                for (NotaRecepcionItem item : itemsNota) {
                    // 3. Verificar si el ítem tiene distribuciones en las sucursales seleccionadas
                    List<NotaRecepcionItemDistribucion> distribuciones = 
                        notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(item.getId());
                    
                    // Filtrar solo distribuciones de las sucursales seleccionadas
                    List<NotaRecepcionItemDistribucion> distribucionesFiltradas = distribuciones.stream()
                        .filter(dist -> sucursalesIds.contains(dist.getSucursalEntrega().getId()))
                        .collect(Collectors.toList());
                    
                    if (distribucionesFiltradas.isEmpty()) {
                        System.out.println("No hay distribuciones para las sucursales seleccionadas en ítem: " + item.getId());
                        continue; // No hay distribuciones para las sucursales seleccionadas
                    }
                    
                    // 4. Calcular cantidad esperada total para las sucursales seleccionadas
                    double cantidadEsperadaTotal = distribucionesFiltradas.stream()
                        .mapToDouble(NotaRecepcionItemDistribucion::getCantidad)
                        .sum();
                    
                    System.out.println("Cantidad esperada total para ítem " + item.getId() + ": " + cantidadEsperadaTotal);
                    
                    // 5. Buscar todas las recepciones de mercadería asociadas a esta nota
                    List<RecepcionMercaderiaNota> recepcionesNota = recepcionMercaderiaNotaService.findByNotaRecepcionId(nota.getId());
                    System.out.println("Recepciones de mercadería encontradas: " + recepcionesNota.size());
                    
                    double totalRecibido = 0.0;
                    double totalRechazado = 0.0;
                    
                    for (RecepcionMercaderiaNota recepcionNota : recepcionesNota) {
                        // 6. Obtener ítems de recepción para las sucursales seleccionadas
                        List<RecepcionMercaderiaItem> itemsRecepcion = service.findByRecepcionMercaderiaIdAndSucursales(
                            recepcionNota.getRecepcionMercaderia().getId(), 
                            sucursalesIds
                        );
                        
                        // Sumar cantidades recibidas y rechazadas
                        for (RecepcionMercaderiaItem itemRecepcion : itemsRecepcion) {
                            if (itemRecepcion.getNotaRecepcionItem().getId().equals(item.getId())) {
                                totalRecibido += (itemRecepcion.getCantidadRecibida() != null ? 
                                    itemRecepcion.getCantidadRecibida() : 0.0);
                                totalRechazado += (itemRecepcion.getCantidadRechazada() != null ? 
                                    itemRecepcion.getCantidadRechazada() : 0.0);
                            }
                        }
                    }
                    
                    System.out.println("Total recibido: " + totalRecibido + ", Total rechazado: " + totalRechazado);
                    
                    // 7. Verificar si está completo
                    if (totalRecibido + totalRechazado < cantidadEsperadaTotal) {
                        ItemPendiente itemPendiente = new ItemPendiente(
                            item.getId(),
                            item.getProducto().getDescripcion(),
                            nota.getNumero().toString(),
                            "Cantidad incompleta",
                            cantidadEsperadaTotal,
                            totalRecibido,
                            totalRechazado
                        );
                        itemsPendientes.add(itemPendiente);
                        System.out.println("Ítem pendiente agregado: " + item.getProducto().getDescripcion());
                    }
                }
            }
            
            boolean puedeFinalizar = itemsPendientes.isEmpty();
            String mensaje = puedeFinalizar ? 
                "Se puede finalizar la recepción física" : 
                "No se puede finalizar. Hay " + itemsPendientes.size() + " ítem(s) pendiente(s)";
            
            System.out.println("=== RESULTADO VALIDACIÓN ===");
            System.out.println("Puede finalizar: " + puedeFinalizar);
            System.out.println("Ítems pendientes: " + itemsPendientes.size());
            System.out.println("Mensaje: " + mensaje);
            
            return new ValidacionFinalizacionRecepcion(
                puedeFinalizar,
                itemsPendientes,
                itemsPendientes.size(),
                mensaje
            );
            
        } catch (Exception e) {
            System.err.println("Error en validación de finalización: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error en validación de finalización: " + e.getMessage());
        }
    }

    /**
     * Finaliza la recepción física por pedido
     */
    @Transactional
    public Boolean finalizarRecepcionFisicaPorPedido(Long pedidoId, List<Long> sucursalesIds) {
        System.out.println("=== FINALIZAR RECEPCIÓN FÍSICA POR PEDIDO ===");
        System.out.println("PedidoId: " + pedidoId);
        System.out.println("SucursalesIds: " + sucursalesIds);
        
        if (pedidoId == null) {
            throw new GraphQLException("PedidoId es requerido");
        }
        
        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            throw new GraphQLException("Lista de sucursales es requerida");
        }
        
        try {
            // 1. Validar que se puede finalizar
            ValidacionFinalizacionRecepcion validacion = validarFinalizacionRecepcionPorPedido(pedidoId, sucursalesIds);
            
            if (!validacion.getPuedeFinalizar()) {
                throw new GraphQLException("No se puede finalizar la recepción física: " + validacion.getMensaje());
            }
            
            // 2. Obtener el pedido para verificar que existe
            Pedido pedido = pedidoService.findById(pedidoId)
                .orElseThrow(() -> new GraphQLException("Pedido no encontrado: " + pedidoId));
            
            // 3. Verificar que todos los items estén completos
            System.out.println("=== VERIFICANDO COMPLETITUD DE ITEMS ===");
            // Buscar todas las recepciones del pedido y obtener sus items
            List<RecepcionMercaderia> recepciones = recepcionMercaderiaService.findByPedidoId(pedidoId);
            List<RecepcionMercaderiaItem> itemsVerificados = new ArrayList<>();
            
            for (RecepcionMercaderia recepcion : recepciones) {
                List<RecepcionMercaderiaItem> itemsRecepcion = service.findByRecepcionMercaderiaId(recepcion.getId());
                // Filtrar por sucursales seleccionadas
                itemsRecepcion = itemsRecepcion.stream()
                    .filter(item -> sucursalesIds.contains(item.getSucursalEntrega().getId()))
                    .collect(Collectors.toList());
                itemsVerificados.addAll(itemsRecepcion);
            }
            
            if (itemsVerificados.isEmpty()) {
                throw new GraphQLException("No se encontraron items verificados para finalizar la recepción física");
            }
            
            System.out.println("Items verificados encontrados: " + itemsVerificados.size());
            
            // 4. Generar MovimientoStock para todos los items verificados
            System.out.println("=== GENERANDO MOVIMIENTOS DE STOCK ===");
            
            for (RecepcionMercaderiaItem item : itemsVerificados) {
                if (item.getCantidadRecibida() > 0) {
                    // Crear movimiento de entrada para cantidad recibida
                    try {
                        // Crear MovimientoStock de entrada
                        MovimientoStock movimiento = new MovimientoStock();
                        movimiento.setId(item.getId()); // Usar el ID del item como ID del movimiento
                        movimiento.setSucursalId(item.getSucursalEntrega().getId());
                        movimiento.setProducto(item.getProducto());
                        movimiento.setCantidad(item.getCantidadRecibida());
                        movimiento.setTipoMovimiento(TipoMovimiento.COMPRA);
                        movimiento.setReferencia(item.getId());
                        movimiento.setEstado(true);
                        movimiento.setUsuario(item.getUsuario());
                        movimiento.setCreadoEn(LocalDateTime.now());
                        
                        // Guardar el movimiento
                        MovimientoStock movimientoGuardado = movimientoStockService.save(movimiento);
                        
                        System.out.println("Movimiento creado: " + item.getProducto().getDescripcion() + 
                                         " - Cantidad: " + item.getCantidadRecibida() + 
                                         " - Sucursal ID: " + item.getSucursalEntrega().getId() +
                                         " - ID: " + movimientoGuardado.getId());
                    } catch (Exception e) {
                        System.err.println("Error al crear MovimientoStock para item " + item.getId() + ": " + e.getMessage());
                        throw new GraphQLException("Error al crear MovimientoStock: " + e.getMessage());
                    }
                }
            }
            
            // 5. Recalcular costos basados en cantidades realmente recibidas
            System.out.println("=== RECALCULANDO COSTOS ===");
            double costoTotalRecibido = 0.0;
            for (RecepcionMercaderiaItem item : itemsVerificados) {
                if (item.getCantidadRecibida() > 0) {
                    // Obtener precio del NotaRecepcionItem asociado
                    double precioUnitario = 0.0;
                    if (item.getNotaRecepcionItem() != null && item.getNotaRecepcionItem().getPrecioUnitarioEnNota() != null) {
                        precioUnitario = item.getNotaRecepcionItem().getPrecioUnitarioEnNota();
                    }
                    
                    double costoItem = item.getCantidadRecibida() * precioUnitario;
                    costoTotalRecibido += costoItem;
                    System.out.println("Costo item " + item.getProducto().getDescripcion() + ": " + costoItem + " (precio: " + precioUnitario + ")");
                }
            }
            System.out.println("Costo total recibido: " + costoTotalRecibido);
            
            // 6. Finalizar etapa de recepción física usando ProcesoEtapa
            procesoEtapaService.finalizarEtapa(pedidoId, ProcesoEtapaTipo.RECEPCION_MERCADERIA);
            
            System.out.println("=== RECEPCIÓN FÍSICA FINALIZADA EXITOSAMENTE ===");
            System.out.println("Pedido ID: " + pedidoId);
            System.out.println("Etapa RECEPCION_MERCADERIA finalizada");
            System.out.println("ProcesoEtapa actualizado a COMPLETADA");
            System.out.println("Movimientos de stock generados: " + itemsVerificados.size());
            System.out.println("Costo total recalculado: " + costoTotalRecibido);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error al finalizar recepción física: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al finalizar recepción física: " + e.getMessage());
        }
    }

    /**
     * Actualiza el estado de verificación de un ítem basado en las cantidades
     * Implementa lógica inteligente para casos de recepción parcial + rechazo parcial
     */
    private void actualizarEstadoVerificacion(RecepcionMercaderiaItem item) {
        if (item == null) return;
        
        try {
            // Obtener la cantidad esperada de la distribución
            Double cantidadEsperada = 0.0;
            if (item.getNotaRecepcionItemDistribucion() != null) {
                cantidadEsperada = item.getNotaRecepcionItemDistribucion().getCantidad();
            }
            
            Double cantidadRecibida = item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0;
            Double cantidadRechazada = item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0;
            
            // Calcular el estado con lógica inteligente
            EstadoVerificacion nuevoEstado;
            
            if (cantidadRecibida == 0 && cantidadRechazada == 0) {
                // No se ha procesado nada
                nuevoEstado = EstadoVerificacion.PENDIENTE;
            } else if (cantidadRecibida > 0 && cantidadRechazada > 0) {
                // Caso especial: recepción parcial + rechazo parcial
                if (cantidadRecibida >= cantidadRechazada) {
                    // Se recibió más o igual que lo rechazado -> VERIFICADO_CON_DIFERENCIA
                    nuevoEstado = EstadoVerificacion.VERIFICADO_CON_DIFERENCIA;
                } else {
                    // Se rechazó más que lo recibido -> RECHAZADO
                    nuevoEstado = EstadoVerificacion.RECHAZADO;
                }
            } else if (cantidadRecibida > 0) {
                // Solo hay recepción
                if (Math.abs(cantidadRecibida - cantidadEsperada) < 0.001) {
                    // Cantidad exacta recibida
                    nuevoEstado = EstadoVerificacion.VERIFICADO;
                } else {
                    // Cantidad diferente a la esperada
                    nuevoEstado = EstadoVerificacion.VERIFICADO_CON_DIFERENCIA;
                }
            } else if (cantidadRechazada > 0) {
                // Solo hay rechazo
                nuevoEstado = EstadoVerificacion.RECHAZADO;
            } else {
                // Caso edge (no debería ocurrir)
                nuevoEstado = EstadoVerificacion.PENDIENTE;
            }
            
            // Log del estado calculado para debugging
            System.out.println("=== ACTUALIZACIÓN DE ESTADO DE VERIFICACIÓN ===");
            System.out.println("Item ID: " + item.getId());
            System.out.println("Producto: " + (item.getNotaRecepcionItem() != null ? item.getNotaRecepcionItem().getProducto().getDescripcion() : "N/A"));
            System.out.println("Cantidad Esperada: " + cantidadEsperada);
            System.out.println("Cantidad Recibida: " + cantidadRecibida);
            System.out.println("Cantidad Rechazada: " + cantidadRechazada);
            System.out.println("Estado Anterior: " + item.getEstadoVerificacion());
            System.out.println("Estado Nuevo: " + nuevoEstado);
            System.out.println("Razón: " + obtenerRazonEstado(nuevoEstado, cantidadRecibida, cantidadRechazada, cantidadEsperada));
            
            // Actualizar el estado si ha cambiado
            if (!nuevoEstado.equals(item.getEstadoVerificacion())) {
                item.setEstadoVerificacion(nuevoEstado);
                service.save(item); // Guardar el cambio de estado
                System.out.println("✅ Estado actualizado exitosamente");
            } else {
                System.out.println("ℹ️ Estado sin cambios");
            }
            
        } catch (Exception e) {
            // Log del error pero no fallar la operación principal
            System.err.println("Error al actualizar estado de verificación: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtiene la razón del estado calculado para debugging
     */
    private String obtenerRazonEstado(EstadoVerificacion estado, Double cantidadRecibida, Double cantidadRechazada, Double cantidadEsperada) {
        switch (estado) {
            case PENDIENTE:
                return "No se ha procesado nada (recibido: " + cantidadRecibida + ", rechazado: " + cantidadRechazada + ")";
            case VERIFICADO:
                return "Cantidad exacta recibida (" + cantidadRecibida + " = " + cantidadEsperada + ")";
            case VERIFICADO_CON_DIFERENCIA:
                if (cantidadRecibida > 0 && cantidadRechazada > 0) {
                    return "Recepción parcial + rechazo parcial (recibido: " + cantidadRecibida + ", rechazado: " + cantidadRechazada + ")";
                } else {
                    return "Cantidad diferente a la esperada (recibido: " + cantidadRecibida + ", esperado: " + cantidadEsperada + ")";
                }
            case RECHAZADO:
                if (cantidadRecibida > 0) {
                    return "Más rechazado que recibido (recibido: " + cantidadRecibida + ", rechazado: " + cantidadRechazada + ")";
                } else {
                    return "Todo rechazado (rechazado: " + cantidadRechazada + ")";
                }
            default:
                return "Estado desconocido";
        }
    }

    /**
     * Obtiene el sumario de una recepción de mercadería
     */
    public RecepcionSumarioDTO obtenerSumarioRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new IllegalArgumentException("ID de recepción es requerido");
        }
        
        return service.obtenerSumarioRecepcion(recepcionId);
    }
} 