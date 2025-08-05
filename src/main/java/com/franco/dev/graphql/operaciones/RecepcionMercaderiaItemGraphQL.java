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
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class RecepcionMercaderiaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaItemService service;

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
        if (input == null) {
            throw new GraphQLException("Input de ítem de recepción es requerido");
        }

        try {
            RecepcionMercaderiaItem item = new RecepcionMercaderiaItem();
            
            // Mapear campos básicos
            if (input.getId() != null) {
                item.setId(input.getId());
            }

        // Mapear relaciones
            RecepcionMercaderia recepcion = null;
        if (input.getRecepcionMercaderiaId() != null) {
                recepcion = recepcionMercaderiaService.findById(input.getRecepcionMercaderiaId())
                    .orElseThrow(() -> new GraphQLException("Recepción no encontrada: " + input.getRecepcionMercaderiaId()));
                item.setRecepcionMercaderia(recepcion);
            } else {
                // Crear recepción automáticamente si no se proporciona
                recepcion = crearRecepcionAutomatica(input);
                item.setRecepcionMercaderia(recepcion);
        }

        if (input.getNotaRecepcionItemId() != null) {
                NotaRecepcionItem notaItem = notaRecepcionItemService.findById(input.getNotaRecepcionItemId())
                    .orElseThrow(() -> new GraphQLException("Ítem de nota no encontrado: " + input.getNotaRecepcionItemId()));
                item.setNotaRecepcionItem(notaItem);
                
                // Vincular NotaRecepcionItemDistribucion
                if (input.getNotaRecepcionItemDistribucionId() != null) {
                    // Si se proporciona directamente el ID de la distribución
                    NotaRecepcionItemDistribucion distribucion = notaRecepcionItemDistribucionService.findById(input.getNotaRecepcionItemDistribucionId())
                        .orElseThrow(() -> new GraphQLException("Distribución no encontrada: " + input.getNotaRecepcionItemDistribucionId()));
                    item.setNotaRecepcionItemDistribucion(distribucion);
                    System.out.println("=== DISTRIBUCIÓN VINCULADA POR ID ===");
                    System.out.println("NotaRecepcionItemDistribucion ID: " + distribucion.getId());
                    System.out.println("Sucursal: " + distribucion.getSucursalEntrega().getNombre());
                    System.out.println("Cantidad: " + distribucion.getCantidad());
                } else if (input.getSucursalEntregaId() != null) {
                    // Buscar distribución por sucursal y nota item
                    List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(notaItem.getId());
                    NotaRecepcionItemDistribucion distribucionEncontrada = distribuciones.stream()
                        .filter(dist -> dist.getSucursalEntrega().getId().equals(input.getSucursalEntregaId()))
                        .findFirst()
                        .orElse(null);
                    
                    if (distribucionEncontrada != null) {
                        item.setNotaRecepcionItemDistribucion(distribucionEncontrada);
                        System.out.println("=== DISTRIBUCIÓN VINCULADA POR SUCURSAL ===");
                        System.out.println("NotaRecepcionItemDistribucion ID: " + distribucionEncontrada.getId());
                        System.out.println("Sucursal: " + distribucionEncontrada.getSucursalEntrega().getNombre());
                        System.out.println("Cantidad: " + distribucionEncontrada.getCantidad());
                    } else {
                        System.out.println("=== ADVERTENCIA: No se encontró distribución para la sucursal ===");
                        System.out.println("NotaRecepcionItemId: " + notaItem.getId());
                        System.out.println("SucursalId: " + input.getSucursalEntregaId());
                        System.out.println("Distribuciones disponibles: " + distribuciones.size());
                    }
                }
            }
            
            // Obtener producto del NotaRecepcionItem si no se proporciona productoId
            if (input.getProductoId() != null) {
                Producto producto = productoService.findById(input.getProductoId())
                    .orElseThrow(() -> new GraphQLException("Producto no encontrado: " + input.getProductoId()));
                item.setProducto(producto);
            } else {
                // Obtener producto del NotaRecepcionItem
                NotaRecepcionItem notaItem = item.getNotaRecepcionItem();
                if (notaItem != null && notaItem.getProducto() != null) {
                    item.setProducto(notaItem.getProducto());
                } else {
                    throw new GraphQLException("No se puede determinar el producto para el ítem");
                }
        }

            if (input.getPresentacionRecibidaId() != null) {
                Presentacion presentacion = presentacionService.findById(input.getPresentacionRecibidaId())
                    .orElseThrow(() -> new GraphQLException("Presentación no encontrada: " + input.getPresentacionRecibidaId()));
                item.setPresentacionRecibida(presentacion);
        }

        if (input.getSucursalEntregaId() != null) {
                Sucursal sucursal = sucursalService.findById(input.getSucursalEntregaId())
                    .orElseThrow(() -> new GraphQLException("Sucursal no encontrada: " + input.getSucursalEntregaId()));
                item.setSucursalEntrega(sucursal);
            }

            if(input.getUsuarioId() != null){
                Usuario usuario = usuarioService.findById(input.getUsuarioId())
                    .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + input.getUsuarioId()));
                item.setUsuario(usuario);
            }
            
            // Mapear campos de cantidad
            item.setCantidadRecibida(input.getCantidadRecibida());
            item.setCantidadRechazada(input.getCantidadRechazada());
            item.setEsBonificacion(input.getEsBonificacion());
            
            // Mapear campos adicionales
        if (input.getVencimientoRecibido() != null) {
                item.setVencimientoRecibido(LocalDate.parse(input.getVencimientoRecibido()));
            }
            
            item.setLote(input.getLote());
            item.setMotivoRechazo(input.getMotivoRechazo());
            item.setObservaciones(input.getObservaciones());
            
            // Guardar usando el método simple
            RecepcionMercaderiaItem itemGuardado = service.save(item);
            
            System.out.println("=== ÍTEM DE RECEPCIÓN GUARDADO ===");
            System.out.println("ID: " + itemGuardado.getId());
            System.out.println("Recepción ID: " + itemGuardado.getRecepcionMercaderia().getId());
            System.out.println("Cantidad Recibida: " + itemGuardado.getCantidadRecibida());
            
            return itemGuardado;
            
        } catch (Exception e) {
            System.err.println("Error al guardar ítem de recepción: " + e.getMessage());
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
} 