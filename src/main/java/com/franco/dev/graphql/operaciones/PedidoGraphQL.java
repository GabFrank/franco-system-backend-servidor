package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionAgrupadaEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.ProductoProveedorService;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

// DTO class for PedidoRecepcionNotaSummary
class PedidoRecepcionNotaSummary {
    private Integer totalItems;
    private Integer assignedItems;
    private Integer pendingItems;
    private Integer cancelledItems;
    private Integer totalNotas;
    private Integer itemsNeedingDistribution;

    public PedidoRecepcionNotaSummary(Integer totalItems, Integer assignedItems, Integer pendingItems, Integer cancelledItems, Integer totalNotas, Integer itemsNeedingDistribution) {
        this.totalItems = totalItems;
        this.assignedItems = assignedItems;
        this.pendingItems = pendingItems;
        this.cancelledItems = cancelledItems;
        this.totalNotas = totalNotas;
        this.itemsNeedingDistribution = itemsNeedingDistribution;
    }

    // Getters
    public Integer getTotalItems() { return totalItems; }
    public Integer getAssignedItems() { return assignedItems; }
    public Integer getPendingItems() { return pendingItems; }
    public Integer getCancelledItems() { return cancelledItems; }
    public Integer getTotalNotas() { return totalNotas; }
    public Integer getItemsNeedingDistribution() { return itemsNeedingDistribution; }
}

// DTO class for PedidoRecepcionMercaderiaSummary
class PedidoRecepcionMercaderiaSummary {
    private Integer totalItems;
    private Integer verificados;
    private Integer pendientes;
    private Integer sucursales;

    public PedidoRecepcionMercaderiaSummary(Integer totalItems, Integer verificados, Integer pendientes, Integer sucursales) {
        this.totalItems = totalItems;
        this.verificados = verificados;
        this.pendientes = pendientes;
        this.sucursales = sucursales;
    }

    // Getters
    public Integer getTotalItems() { return totalItems; }
    public Integer getVerificados() { return verificados; }
    public Integer getPendientes() { return pendientes; }
    public Integer getSucursales() { return sucursales; }
}

// DTO class for comprehensive PedidoSummary
class PedidoSummary {
    private Integer totalItems;
    private Integer cancelledItems;
    private Integer activeItems;
    private Double totalSinDescuento;
    private Double totalDescuento;
    private Double totalConDescuento;
    private PedidoEstado estado;

    public PedidoSummary(Integer totalItems, Integer cancelledItems, Integer activeItems, 
                        Double totalSinDescuento, Double totalDescuento, Double totalConDescuento, 
                        PedidoEstado estado) {
        this.totalItems = totalItems;
        this.cancelledItems = cancelledItems;
        this.activeItems = activeItems;
        this.totalSinDescuento = totalSinDescuento;
        this.totalDescuento = totalDescuento;
        this.totalConDescuento = totalConDescuento;
        this.estado = estado;
    }

    // Getters
    public Integer getTotalItems() { return totalItems; }
    public Integer getCancelledItems() { return cancelledItems; }
    public Integer getActiveItems() { return activeItems; }
    public Double getTotalSinDescuento() { return totalSinDescuento; }
    public Double getTotalDescuento() { return totalDescuento; }
    public Double getTotalConDescuento() { return totalConDescuento; }
    public PedidoEstado getEstado() { return estado; }
}

// DTO class for SolicitudPagoSummary
class SolicitudPagoSummary {
    private Integer totalNotas;
    private Integer notasAgrupadas;
    private Integer notasSinAgrupar;
    private Integer totalGrupos;
    private Double valorTotalNotas;
    private Double valorTotalAgrupado;
    private Boolean puedeProgresar;

    public SolicitudPagoSummary(Integer totalNotas, Integer notasAgrupadas, Integer notasSinAgrupar, 
                               Integer totalGrupos, Double valorTotalNotas, Double valorTotalAgrupado, 
                               Boolean puedeProgresar) {
        this.totalNotas = totalNotas;
        this.notasAgrupadas = notasAgrupadas;
        this.notasSinAgrupar = notasSinAgrupar;
        this.totalGrupos = totalGrupos;
        this.valorTotalNotas = valorTotalNotas;
        this.valorTotalAgrupado = valorTotalAgrupado;
        this.puedeProgresar = puedeProgresar;
    }

    // Getters
    public Integer getTotalNotas() { return totalNotas; }
    public Integer getNotasAgrupadas() { return notasAgrupadas; }
    public Integer getNotasSinAgrupar() { return notasSinAgrupar; }
    public Integer getTotalGrupos() { return totalGrupos; }
    public Double getValorTotalNotas() { return valorTotalNotas; }
    public Double getValorTotalAgrupado() { return valorTotalAgrupado; }
    public Boolean getPuedeProgresar() { return puedeProgresar; }
}

// DTO class for GrupoConInfo (matches frontend expectations)
class GrupoConInfo {
    private NotaRecepcionAgrupada grupo;
    private List<NotaRecepcion> notasAsignadas;
    private Double valorTotal;
    private Boolean puedeAgregarNotas;
    private Boolean puedeEliminar;
    private Boolean esGrupoExterno;

    public GrupoConInfo(NotaRecepcionAgrupada grupo, List<NotaRecepcion> notasAsignadas, 
                       Double valorTotal, Boolean puedeAgregarNotas, 
                       Boolean puedeEliminar, Boolean esGrupoExterno) {
        this.grupo = grupo;
        this.notasAsignadas = notasAsignadas;
        this.valorTotal = valorTotal;
        this.puedeAgregarNotas = puedeAgregarNotas;
        this.puedeEliminar = puedeEliminar;
        this.esGrupoExterno = esGrupoExterno;
    }

    // Getters
    public NotaRecepcionAgrupada getGrupo() { return grupo; }
    public List<NotaRecepcion> getNotasAsignadas() { return notasAsignadas; }
    public Double getValorTotal() { return valorTotal; }
    public Boolean getPuedeAgregarNotas() { return puedeAgregarNotas; }
    public Boolean getPuedeEliminar() { return puedeEliminar; }
    public Boolean getEsGrupoExterno() { return esGrupoExterno; }
}

// DTO class for SolicitudPagoStepResult
class SolicitudPagoStepResult {
    private List<SolicitudPago> solicitudesCreadas;
    private List<NotaRecepcionAgrupada> gruposFinalizados;
    private Pedido pedidoActualizado;
    private Boolean success;
    private String mensaje;

    public SolicitudPagoStepResult(List<SolicitudPago> solicitudesCreadas, List<NotaRecepcionAgrupada> gruposFinalizados, 
                                 Pedido pedidoActualizado, Boolean success, String mensaje) {
        this.solicitudesCreadas = solicitudesCreadas;
        this.gruposFinalizados = gruposFinalizados;
        this.pedidoActualizado = pedidoActualizado;
        this.success = success;
        this.mensaje = mensaje;
    }

    // Getters
    public List<SolicitudPago> getSolicitudesCreadas() { return solicitudesCreadas; }
    public List<NotaRecepcionAgrupada> getGruposFinalizados() { return gruposFinalizados; }
    public Pedido getPedidoActualizado() { return pedidoActualizado; }
    public Boolean getSuccess() { return success; }
    public String getMensaje() { return mensaje; }
}

@Component
public class PedidoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemGraphQL pedidoItemGraphQL;

    @Autowired
    private PedidoFechaEntregaService pedidoFechaEntregaService;

    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProductoProveedorService productoProveedorService;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private NotaRecepcionAgrupadaService notaRecepcionAgrupadaService;

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    public Optional<Pedido> pedido(Long id) {
        return service.findById(id);
    }

    public List<Pedido> pedidos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<Pedido> filterPedidos(Long idPedido,
                                      Integer numeroNotaRecepcion, PedidoEstado estado, Long sucursalId, String inicio, String fin, Long proveedorId, Long vendedorId, Long formaPagoId, Long productoId, Integer page, Integer size) {
        return service.filterPedidos(idPedido,
                numeroNotaRecepcion, estado, sucursalId, inicio, fin, proveedorId, vendedorId, formaPagoId, productoId, page, size);
    }

    public Pedido savePedido(PedidoInput input) {
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        
        // Get existing pedido to detect estado changes
        Pedido existingPedido = null;
        PedidoEstado previousEstado = null;
        if (input.getId() != null) {
            existingPedido = service.findById(input.getId()).orElse(null);
            if (existingPedido != null) {
                previousEstado = existingPedido.getEstado();
            }
        }
        
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getMonedaId() != null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getVendedorId() != null) e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        
        Pedido pedido = service.save(e);
        
        // Handle estado change: automatically copy data from previous step to current step
        if (existingPedido != null && previousEstado != null && 
            previousEstado != pedido.getEstado()) {
            handlePedidoEstadoChange(pedido, previousEstado, pedido.getEstado());
        }
        
        return pedido;
    }

    public Pedido savePedidoFull(PedidoInput input, List<String> fechaEntregaList, List<Long> sucursalEntregaList, List<Long> sucursalInfluenciaList, Long usuarioId) {
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getMonedaId() != null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getVendedorId() != null) e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        Pedido pedido = service.save(e);
        if (fechaEntregaList != null) {
            updatePedidoFechaEntrega(pedido, fechaEntregaList, usuarioId);
        }
        if (sucursalEntregaList != null) {
            updatePedidoSucursalEntrega(pedido, sucursalEntregaList, usuarioId);
        }
        if (sucursalEntregaList != null) {
            updatePedidoSucursalInfluencia(pedido, sucursalInfluenciaList, usuarioId);
        }
        return pedido;
    }

    @Transactional
    public void updatePedidoFechaEntrega(Pedido pedido, List<String> newDates, Long usuarioId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        // Convert newDates to a Set of LocalDateTime for easier comparison
        Set<LocalDateTime> newDatesSet = newDates.stream()
                .map(dateStr -> stringToDate(dateStr))
                .collect(Collectors.toSet());

        // Retrieve current PedidoFechaEntrega entries from the database
        List<PedidoFechaEntrega> currentEntries = pedidoFechaEntregaService.findByPedido(pedido.getId());

        // Determine entries to delete (existing in database but not in newDatesSet)
        List<PedidoFechaEntrega> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getFechaEntrega()))
                .collect(Collectors.toList());

        // Delete entries
        pedidoFechaEntregaService.deleteAll(toDelete);

        // Find which dates are new by removing all dates already present
        currentEntries.forEach(entry -> newDatesSet.remove(entry.getFechaEntrega()));

        // Create new PedidoFechaEntrega entries for remaining new dates
        newDatesSet.forEach(date -> {
            PedidoFechaEntrega newEntry = new PedidoFechaEntrega();
            newEntry.setPedido(pedido);
            newEntry.setFechaEntrega(date);
            newEntry.setCreadoEn(LocalDateTime.now());
            if (usuario != null) {
                newEntry.setUsuario(usuario);
            } else {
                newEntry.setUsuario(pedido.getUsuario());
            }
            pedidoFechaEntregaService.save(newEntry);
        });
    }

    @Transactional
    public void updatePedidoSucursalEntrega(Pedido pedido, List<Long> newSucursalesList, Long usuarioId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        // Convert newDates to a Set of LocalDateTime for easier comparison
        List<Long> newDatesSet = newSucursalesList;

        // Retrieve current PedidoFechaEntrega entries from the database
        List<PedidoSucursalEntrega> currentEntries = pedidoSucursalEntregaService.findByPedidoId(pedido.getId());

        // Determine entries to delete (existing in database but not in newDatesSet)
        List<PedidoSucursalEntrega> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getSucursal().getId()))
                .collect(Collectors.toList());

        // Delete entries
        pedidoSucursalEntregaService.deleteAll(toDelete);

        // Find which dates are new by removing all dates already present
        currentEntries.forEach(entry -> newDatesSet.remove(entry.getSucursal().getId()));

        // Create new PedidoFechaEntrega entries for remaining new dates
        newDatesSet.forEach(data -> {
            PedidoSucursalEntrega newEntry = new PedidoSucursalEntrega();
            Sucursal sucursal = sucursalService.findById(data).orElse(null);
            newEntry.setPedido(pedido);
            newEntry.setSucursal(sucursal);
            newEntry.setCreadoEn(LocalDateTime.now());
            if (usuario != null) {
                newEntry.setUsuario(usuario);
            } else {
                newEntry.setUsuario(pedido.getUsuario());
            }
            pedidoSucursalEntregaService.save(newEntry);
        });
    }

    @Transactional
    public void updatePedidoSucursalInfluencia(Pedido pedido, List<Long> newSucursalesList, Long usuarioId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        // Convert newDates to a Set of LocalDateTime for easier comparison
        List<Long> newDatesSet = newSucursalesList;

        // Retrieve current PedidoFechaEntrega entries from the database
        List<PedidoSucursalInfluencia> currentEntries = pedidoSucursalInfluenciaService.findByPedidoId(pedido.getId());

        // Determine entries to delete (existing in database but not in newDatesSet)
        List<PedidoSucursalInfluencia> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getSucursal().getId()))
                .collect(Collectors.toList());

        // Delete entries
        pedidoSucursalInfluenciaService.deleteAll(toDelete);

        // Find which dates are new by removing all dates already present
        currentEntries.forEach(entry -> newDatesSet.remove(entry.getSucursal().getId()));

        // Create new PedidoFechaEntrega entries for remaining new dates
        newDatesSet.forEach(data -> {
            PedidoSucursalInfluencia newEntry = new PedidoSucursalInfluencia();
            Sucursal sucursal = sucursalService.findById(data).orElse(null);
            newEntry.setPedido(pedido);
            newEntry.setSucursal(sucursal);
            newEntry.setCreadoEn(LocalDateTime.now());
            if (usuario != null) {
                newEntry.setUsuario(usuario);
            } else {
                newEntry.setUsuario(pedido.getUsuario());
            }
            pedidoSucursalInfluenciaService.save(newEntry);
        });
    }

    public Boolean deletePedido(Long id) {
        return service.deleteById(id);
    }

    public Long countPedido() {
        return service.count();
    }

    public Pedido finalizarPedido(Long id, PedidoEstado estado) {
        Pedido pedido = service.findById(id).orElse(null);
        if (pedido == null) {
            throw new GraphQLException("No se puedo encontrar el pedido");
        }
        
        // Store the previous estado for step tracking
        PedidoEstado previousEstado = pedido.getEstado();
        
        if (estado == PedidoEstado.EN_RECEPCION_MERCADERIA) {
            List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(id);
            procesarPedidoItems(pedido, pedidoItemList);
        } else if (estado == PedidoEstado.CONCLUIDO) {
            List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(id);
            for (PedidoItem pi : pedidoItemList) {
                // Skip cancelled items
                if (pi.getCancelado() != null && pi.getCancelado()) {
                    continue;
                }

                // Skip items that haven't been verified in recepcion mercaderia
                if (pi.getVerificadoRecepcionProducto() == null || !pi.getVerificadoRecepcionProducto()) {
                    continue;
                }

                // Get the final cost price from recepcion producto
                Double nuevoPrecioCosto = pi.getPrecioUnitarioRecepcionProducto();
                if (nuevoPrecioCosto == null) {
                    // Fallback to recepcion nota price
                    nuevoPrecioCosto = pi.getPrecioUnitarioRecepcionNota();
                }
                if (nuevoPrecioCosto == null) {
                    // Skip if no price available
                    continue;
                }

                // Check if we need to create a new CostoPorProducto entry
                CostoPorProducto ultimoCosto = costosPorProductoService.findLastByProductoId(pi.getProducto().getId());
                boolean needsNewCostEntry = false;

                if (ultimoCosto == null) {
                    // No previous cost entry exists
                    needsNewCostEntry = true;
                } else if (!nuevoPrecioCosto.equals(ultimoCosto.getUltimoPrecioCompra())) {
                    // Price has changed
                    needsNewCostEntry = true;
                }

                if (needsNewCostEntry) {
                    // Calculate new average cost
                    Double cantidadRecibida = pi.getCantidadRecepcionProducto();
                    if (cantidadRecibida == null) {
                        cantidadRecibida = pi.getCantidadRecepcionNota();
                    }
                    if (cantidadRecibida == null) {
                        cantidadRecibida = pi.getCantidadCreacion();
                    }

                    Double costoMedio = costosPorProductoService.calcularCostoMedio(
                            pi.getProducto().getId(),
                            cantidadRecibida,
                            nuevoPrecioCosto);

                    // Create new CostoPorProducto entry
                    CostoPorProducto costoPorProducto = new CostoPorProducto();
                    costoPorProducto.setProducto(pi.getProducto());
                    costoPorProducto.setUltimoPrecioCompra(nuevoPrecioCosto);
                    costoPorProducto.setCostoMedio(costoMedio);
                    costoPorProducto.setCreadoEn(LocalDateTime.now());
                    costoPorProducto.setMoneda(pi.getPedido().getMoneda());
                    
                    // Safe handling of cotizacion
                    if (costoPorProducto.getMoneda() != null) {
                        try {
                            Double cotizacion = cambioService.findLastByMonedaId(costoPorProducto.getMoneda().getId()).getValorEnGs();
                            costoPorProducto.setCotizacion(cotizacion);
                        } catch (Exception e) {
                            // Fallback to 1.0 if no exchange rate found
                            costoPorProducto.setCotizacion(1.0);
                        }
                    } else {
                        costoPorProducto.setCotizacion(1.0);
                    }
                    
                    costoPorProducto.setUsuario(pedido.getUsuario());
                    costosPorProductoService.save(costoPorProducto);
                }

                // Create stock movements for each sucursal distribution
                List<PedidoItemSucursal> sucursalDistributions = pedidoItemSucursalService.findByPedidoItemId(pi.getId());
                for (PedidoItemSucursal sucursalDist : sucursalDistributions) {
                    if (sucursalDist.getCantidadPorUnidad() != null && sucursalDist.getCantidadPorUnidad() > 0) {
                        // Create MovimientoStock for this sucursal
                        MovimientoStock movimiento = new MovimientoStock();
                        movimiento.setProducto(pi.getProducto());
                        movimiento.setSucursalId(sucursalDist.getSucursalEntrega().getId());
                        movimiento.setTipoMovimiento(TipoMovimiento.COMPRA);
                        movimiento.setCantidad(sucursalDist.getCantidadPorUnidad());
                        movimiento.setReferencia(sucursalDist.getId()); // Reference to pedido item sucursal ID
                        movimiento.setUsuario(pedido.getUsuarioRecepcionMercaderia());
                        movimiento.setCreadoEn(LocalDateTime.now());
                        movimiento.setEstado(true); // Active movement
                        
                        // Generate unique ID for the movement
                        movimiento.setId(System.currentTimeMillis() + sucursalDist.getId());

                        movimientoStockService.save(movimiento);
                    }
                }
            }
        }
        
        // Update estado and handle step tracking
        pedido.setEstado(estado);
        handlePedidoEstadoChange(pedido, previousEstado, estado);
        
        return pedido; // handlePedidoEstadoChange already saves the pedido
    }

    @Async
    public void procesarPedidoItems(Pedido pedido, List<PedidoItem> pedidoItemList) {
        for (PedidoItem pi : pedidoItemList) {
            // Crear producto proveedor
            ProductoProveedor productoProveedor = new ProductoProveedor();
            productoProveedor.setProveedor(pedido.getProveedor());
            productoProveedor.setPedido(pedido);
            productoProveedor.setUsuario(pedido.getUsuario());
            productoProveedor.setProducto(pi.getProducto());
            productoProveedor.setCreadoEn(LocalDateTime.now());
            productoProveedorService.save(productoProveedor);

            // NOTE: CostoPorProducto creation removed from here to avoid duplicates
            // Cost records will be created only when pedido reaches CONCLUIDO status
            // with the final verified prices from recepcion producto step
        }

    }

    public Boolean verificarDistribucionSucursales(Long id){
        // Use estado-aware logic instead of the flawed repository method
        List<PedidoItem> allItems = pedidoItemService.findByPedidoId(id);
        
        for (PedidoItem item : allItems) {
            // Skip cancelled items
            if (item.getCancelado() != null && item.getCancelado()) {
                continue;
            }
            
            // If any item needs distribution, return false
            if (itemNeedsDistribution(item)) {
                return false;
            }
        }
        
        // All items have complete distribution
        return true;
    }

    public PedidoRecepcionNotaSummary pedidoRecepcionNotaSummary(Long id) {
        List<PedidoItem> allItems = pedidoItemService.findByPedidoId(id);
        
        // **FIX ISSUE 1**: Include cancelled items in assigned count if they have notaRecepcion
        // This allows cancelled items to still be assigned to notas for tracking purposes
        List<PedidoItem> assignedItems = allItems.stream()
                .filter(item -> item.getNotaRecepcion() != null)
                .collect(Collectors.toList());
        
        List<PedidoItem> cancelledItems = allItems.stream()
                .filter(item -> item.getCancelado() != null && item.getCancelado())
                .collect(Collectors.toList());

        List<NotaRecepcion> notas = notaRecepcionService.findByPedidoId(id);

        // **FIX ISSUE 1**: Calculate pending items correctly
        // Pending = items that are NOT assigned to any nota AND are NOT cancelled
        // This means: total - assigned (regardless of cancelled status)
        int pendingItemsCount = allItems.size() - assignedItems.size();
        
        // **FIX ISSUE 2**: Calculate items needing distribution
        // Count assigned items that don't have complete distribution using estado-aware logic
        int itemsNeedingDistribution = 0;
        for (PedidoItem item : assignedItems) {
            // Skip cancelled items
            if (item.getCancelado() != null && item.getCancelado()) {
                continue;
            }
            
            // Use the same estado-aware logic as PedidoItemResolver.needsDistribucion
            if (itemNeedsDistribution(item)) {
                itemsNeedingDistribution++;
            }
        }

        return new PedidoRecepcionNotaSummary(
                allItems.size(),
                assignedItems.size(),
                pendingItemsCount,
                cancelledItems.size(),
                notas.size(),
                itemsNeedingDistribution
        );
    }

    /**
     * Calculate comprehensive pedido summary with financial totals based on estado
     * Excludes cancelled items and uses appropriate step fields for calculations
     */
    public PedidoSummary pedidoSummary(Long id) {
        Pedido pedido = service.findById(id).orElse(null);
        if (pedido == null) {
            throw new GraphQLException("Pedido no encontrado con ID: " + id);
        }

        List<PedidoItem> allItems = pedidoItemService.findByPedidoId(id);
        
        // Separate cancelled and active items
        List<PedidoItem> cancelledItems = allItems.stream()
                .filter(item -> item.getCancelado() != null && item.getCancelado())
                .collect(Collectors.toList());
        
        List<PedidoItem> activeItems = allItems.stream()
                .filter(item -> item.getCancelado() == null || !item.getCancelado())
                .collect(Collectors.toList());

        // Calculate financial totals based on pedido estado
        double totalSinDescuento = 0.0;
        double totalDescuento = 0.0;

        for (PedidoItem item : activeItems) {
            // Get values based on current pedido estado
            Double precioUnitario = getPrecioUnitarioForEstado(item, pedido.getEstado());
            Double descuentoUnitario = getDescuentoUnitarioForEstado(item, pedido.getEstado());
            Double cantidad = getCantidadForEstado(item, pedido.getEstado());
            Double cantidadPresentacion = getCantidadPresentacionForEstado(item, pedido.getEstado());

            if (precioUnitario != null && cantidad != null && cantidadPresentacion != null) {
                double itemTotalSinDescuento = cantidad * cantidadPresentacion.doubleValue() * precioUnitario;
                double itemDescuento = (descuentoUnitario != null) ? 
                    cantidad * cantidadPresentacion.doubleValue() * descuentoUnitario : 0.0;

                totalSinDescuento += itemTotalSinDescuento;
                totalDescuento += itemDescuento;
            }
        }

        double totalConDescuento = totalSinDescuento - totalDescuento;

        return new PedidoSummary(
                Integer.valueOf(allItems.size()),
                Integer.valueOf(cancelledItems.size()),
                Integer.valueOf(activeItems.size()),
                Double.valueOf(totalSinDescuento),
                Double.valueOf(totalDescuento),
                Double.valueOf(totalConDescuento),
                pedido.getEstado()
        );
    }

    /**
     * Calculate summary data for Recepcion Mercaderia step
     * Following user requirements:
     * - Total Items: quantity of items available for reception
     * - Verificados: items that have been verified (verificadoRecepcionProducto = true)
     * - Pendientes: difference between total and verified 
     * - Sucursales: quantity of sucursales from pedido-item-sucursal linked to available items
     */
    public PedidoRecepcionMercaderiaSummary pedidoRecepcionMercaderiaSummary(Long id) {
        // Get all pedido items that are available for reception
        // (have notaRecepcion assigned and are not cancelled)
        List<PedidoItem> allItems = pedidoItemService.findByPedidoId(id);
        
        List<PedidoItem> availableItems = allItems.stream()
                .filter(item -> item.getNotaRecepcion() != null) // Must have nota assigned
                .filter(item -> item.getCancelado() == null || !item.getCancelado()) // Not cancelled
                .collect(Collectors.toList());

        // Count verified items (verificadoRecepcionProducto = true)
        long verificados = availableItems.stream()
                .filter(item -> item.getVerificadoRecepcionProducto() != null && item.getVerificadoRecepcionProducto())
                .count();

        // Calculate pendientes (total - verified)
        int totalItems = availableItems.size();
        int pendientes = totalItems - (int) verificados;

        // Count unique sucursales from pedidoItemSucursalList
        Set<Long> uniqueSucursales = new java.util.HashSet<>();
        for (PedidoItem item : availableItems) {
            List<com.franco.dev.domain.operaciones.PedidoItemSucursal> sucursalList = 
                pedidoItemSucursalService.findByPedidoItemId(item.getId());
            
            for (com.franco.dev.domain.operaciones.PedidoItemSucursal sucursalItem : sucursalList) {
                if (sucursalItem.getSucursalEntrega() != null) {
                    uniqueSucursales.add(sucursalItem.getSucursalEntrega().getId());
                }
            }
        }

        return new PedidoRecepcionMercaderiaSummary(
                Integer.valueOf(totalItems),
                Integer.valueOf((int) verificados),
                Integer.valueOf(pendientes),
                Integer.valueOf(uniqueSucursales.size())
        );
    }

    /**
     * Get precio unitario based on pedido estado
     */
    private Double getPrecioUnitarioForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getPrecioUnitarioCreacion();
            case EN_RECEPCION_NOTA:
                return item.getPrecioUnitarioRecepcionNota() != null ? 
                    item.getPrecioUnitarioRecepcionNota() : item.getPrecioUnitarioCreacion();
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getPrecioUnitarioRecepcionProducto() != null ? 
                    item.getPrecioUnitarioRecepcionProducto() : 
                    (item.getPrecioUnitarioRecepcionNota() != null ? 
                        item.getPrecioUnitarioRecepcionNota() : item.getPrecioUnitarioCreacion());
            default:
                return item.getPrecioUnitarioCreacion();
        }
    }

    /**
     * Get descuento unitario based on pedido estado
     */
    private Double getDescuentoUnitarioForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getDescuentoUnitarioCreacion();
            case EN_RECEPCION_NOTA:
                return item.getDescuentoUnitarioRecepcionNota() != null ? 
                    item.getDescuentoUnitarioRecepcionNota() : item.getDescuentoUnitarioCreacion();
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getDescuentoUnitarioRecepcionProducto() != null ? 
                    item.getDescuentoUnitarioRecepcionProducto() : 
                    (item.getDescuentoUnitarioRecepcionNota() != null ? 
                        item.getDescuentoUnitarioRecepcionNota() : item.getDescuentoUnitarioCreacion());
            default:
                return item.getDescuentoUnitarioCreacion();
        }
    }

    /**
     * Get cantidad based on pedido estado
     */
    private Double getCantidadForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getCantidadCreacion();
            case EN_RECEPCION_NOTA:
                return item.getCantidadRecepcionNota() != null ? 
                    item.getCantidadRecepcionNota() : item.getCantidadCreacion();
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getCantidadRecepcionProducto() != null ? 
                    item.getCantidadRecepcionProducto() : 
                    (item.getCantidadRecepcionNota() != null ? 
                        item.getCantidadRecepcionNota() : item.getCantidadCreacion());
            default:
                return item.getCantidadCreacion();
        }
    }

    /**
     * Get cantidad presentacion based on pedido estado
     */
    private Double getCantidadPresentacionForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getPresentacionCreacion() != null ? 
                    item.getPresentacionCreacion().getCantidad() : 1;
            case EN_RECEPCION_NOTA:
                return item.getPresentacionRecepcionNota() != null ? 
                    item.getPresentacionRecepcionNota().getCantidad() : 
                    (item.getPresentacionCreacion() != null ? 
                        item.getPresentacionCreacion().getCantidad() : 1);
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getPresentacionRecepcionProducto() != null ? 
                    item.getPresentacionRecepcionProducto().getCantidad() : 
                    (item.getPresentacionRecepcionNota() != null ? 
                        item.getPresentacionRecepcionNota().getCantidad() : 
                        (item.getPresentacionCreacion() != null ? 
                            item.getPresentacionCreacion().getCantidad() : 1));
            default:
                return item.getPresentacionCreacion() != null ? 
                    item.getPresentacionCreacion().getCantidad() : 1;
        }
    }

    /**
     * Check if a PedidoItem needs distribution using estado-aware logic
     * This mirrors the logic in PedidoItemResolver.needsDistribucion
     */
    private boolean itemNeedsDistribution(PedidoItem pedidoItem) {
        try {
            // Skip cancelled items
            if (pedidoItem.getCancelado() != null && pedidoItem.getCancelado()) {
                return false;
            }

            // Get expected quantity based on pedido estado
            Double expectedQuantity = getCantidadForEstado(pedidoItem, pedidoItem.getPedido().getEstado());
            Double expectedPresentacionCantidad = getCantidadPresentacionForEstado(pedidoItem, pedidoItem.getPedido().getEstado());
            
            if (expectedQuantity == null || expectedQuantity <= 0 || expectedPresentacionCantidad == null) {
                return false;
            }

            // Calculate total expected quantity (cantidad * presentacion.cantidad)
            Double totalExpectedQuantity = expectedQuantity * expectedPresentacionCantidad;

            // Get total distributed quantity from database
            Double totalDistributedQuantity = pedidoItemService.getRepository().getTotalDistributedQuantityByPedidoItemId(pedidoItem.getId());
            
            if (totalDistributedQuantity == null) {
                totalDistributedQuantity = 0.0;
            }

            // Item needs distribution if distributed quantity is not equal to expected, so return false if not equal
            return !totalDistributedQuantity.equals(totalExpectedQuantity);
            
        } catch (Exception e) {
            // Log error and return false as fallback
            System.err.println("Error calculating itemNeedsDistribution for PedidoItem " + pedidoItem.getId() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles automatic data copying when Pedido estado changes
     * This ensures that when a Pedido transitions to a new state, all its items
     * have the appropriate step data copied from the previous step.
     * Also handles step tracking for the Pedido itself.
     */
    @Transactional
    private void handlePedidoEstadoChange(Pedido pedido, PedidoEstado previousEstado, PedidoEstado newEstado) {
        // Handle step tracking for the Pedido
        handlePedidoStepTracking(pedido, previousEstado, newEstado);
        
        // Handle PedidoItem data copying
        List<PedidoItem> pedidoItems = pedidoItemService.findByPedidoId(pedido.getId());
        
        for (PedidoItem item : pedidoItems) {
            copyDataForEstadoTransition(item, previousEstado, newEstado);
            // Items from findByPedidoId are already managed entities, so we can save them directly
            pedidoItemService.save(item);
        }
        
        // Save the updated pedido with step tracking changes
        service.save(pedido);
    }
    
    /**
     * Handles step tracking when Pedido estado changes
     * This method manages completing previous steps and preparing next steps
     */
    private void handlePedidoStepTracking(Pedido pedido, PedidoEstado previousEstado, PedidoEstado newEstado) {
        LocalDateTime now = LocalDateTime.now();
        
        // Complete previous step based on estado transition
        switch (newEstado) {
            case EN_RECEPCION_NOTA:
                // Complete creation step when moving to recepcion nota
                if (previousEstado == PedidoEstado.ABIERTO || previousEstado == PedidoEstado.ACTIVO) {
                    pedido.setFechaFinCreacion(now);
                    pedido.setProgresoCreacion(100); // Complete
                    
                    // Note: Do NOT set usuarioRecepcionNota or fechaInicioRecepcionNota yet
                    // This will be set when user explicitly begins the step
                }
                break;
                
            case EN_RECEPCION_MERCADERIA:
                // Complete recepcion nota step when moving to recepcion mercaderia
                if (previousEstado == PedidoEstado.EN_RECEPCION_NOTA) {
                    pedido.setFechaFinRecepcionNota(now);
                    pedido.setProgresoRecepcionNota(100); // Complete
                    
                    // Note: Do NOT set usuarioRecepcionMercaderia or fechaInicioRecepcionMercaderia yet
                    // This will be set when user explicitly begins the step
                }
                break;
                
            case EN_SOLICITUD_PAGO:
                // Complete recepcion mercaderia step when moving to solicitud pago
                if (previousEstado == PedidoEstado.EN_RECEPCION_MERCADERIA) {
                    pedido.setFechaFinRecepcionMercaderia(now);
                    pedido.setProgresoRecepcionMercaderia(100); // Complete
                    
                    // Note: Do NOT set usuarioSolicitudPago or fechaInicioSolicitudPago yet
                    // This will be set when user explicitly begins the step
                }
                break;
                
            case CONCLUIDO:
                // Complete solicitud pago step when moving to concluded
                if (previousEstado == PedidoEstado.EN_SOLICITUD_PAGO) {
                    pedido.setFechaFinSolicitudPago(now);
                    pedido.setProgresoSolicitudPago(100); // Complete
                }
                break;
        }
    }
    
    /**
     * Copies data between step fields based on estado transition
     */
    private void copyDataForEstadoTransition(PedidoItem item, PedidoEstado previousEstado, PedidoEstado newEstado) {
        switch (newEstado) {
            case EN_RECEPCION_NOTA:
                // Copy from Creacion to RecepcionNota fields
                if (previousEstado == PedidoEstado.ABIERTO || previousEstado == PedidoEstado.ACTIVO) {
                    copyCreacionToRecepcionNotaFields(item);
                }
                break;
                
            case EN_RECEPCION_MERCADERIA:
                // Copy from RecepcionNota to RecepcionProducto fields
                if (previousEstado == PedidoEstado.EN_RECEPCION_NOTA) {
                    copyRecepcionNotaToRecepcionProductoFields(item);
                } else if (previousEstado == PedidoEstado.ABIERTO || previousEstado == PedidoEstado.ACTIVO) {
                    // Direct transition from creation to product reception
                    copyCreacionToRecepcionProductoFields(item);
                }
                break;
                
            // Add other transitions as needed
        }
    }
    
    /**
     * Copy data from Creacion fields to RecepcionNota fields
     */
    private void copyCreacionToRecepcionNotaFields(PedidoItem item) {
        if (item.getPresentacionCreacion() != null) {
            item.setPresentacionRecepcionNota(item.getPresentacionCreacion());
        }
        if (item.getCantidadCreacion() != null) {
            item.setCantidadRecepcionNota(item.getCantidadCreacion());
        }
        if (item.getPrecioUnitarioCreacion() != null) {
            item.setPrecioUnitarioRecepcionNota(item.getPrecioUnitarioCreacion());
        }
        if (item.getDescuentoUnitarioCreacion() != null) {
            item.setDescuentoUnitarioRecepcionNota(item.getDescuentoUnitarioCreacion());
        }
        if (item.getVencimientoCreacion() != null) {
            item.setVencimientoRecepcionNota(item.getVencimientoCreacion());
        }
        // Don't copy obs automatically - let user decide
    }
    
    /**
     * Copy data from RecepcionNota fields to RecepcionProducto fields
     */
    private void copyRecepcionNotaToRecepcionProductoFields(PedidoItem item) {
        if (item.getPresentacionRecepcionNota() != null) {
            item.setPresentacionRecepcionProducto(item.getPresentacionRecepcionNota());
        }
        if (item.getCantidadRecepcionNota() != null) {
            item.setCantidadRecepcionProducto(item.getCantidadRecepcionNota());
        }
        if (item.getPrecioUnitarioRecepcionNota() != null) {
            item.setPrecioUnitarioRecepcionProducto(item.getPrecioUnitarioRecepcionNota());
        }
        if (item.getDescuentoUnitarioRecepcionNota() != null) {
            item.setDescuentoUnitarioRecepcionProducto(item.getDescuentoUnitarioRecepcionNota());
        }
        if (item.getVencimientoRecepcionNota() != null) {
            item.setVencimientoRecepcionProducto(item.getVencimientoRecepcionNota());
        }
        // Don't copy obs automatically - let user decide
    }
    
    /**
     * Copy data directly from Creacion fields to RecepcionProducto fields (skip RecepcionNota)
     */
    private void copyCreacionToRecepcionProductoFields(PedidoItem item) {
        if (item.getPresentacionCreacion() != null) {
            item.setPresentacionRecepcionProducto(item.getPresentacionCreacion());
        }
        if (item.getCantidadCreacion() != null) {
            item.setCantidadRecepcionProducto(item.getCantidadCreacion());
        }
        if (item.getPrecioUnitarioCreacion() != null) {
            item.setPrecioUnitarioRecepcionProducto(item.getPrecioUnitarioCreacion());
        }
        if (item.getDescuentoUnitarioCreacion() != null) {
            item.setDescuentoUnitarioRecepcionProducto(item.getDescuentoUnitarioCreacion());
        }
        if (item.getVencimientoCreacion() != null) {
            item.setVencimientoRecepcionProducto(item.getVencimientoCreacion());
        }
        // Don't copy obs automatically - let user decide
    }

    // ==================== SOLICITUD PAGO METHODS ====================

    /**
     * Get solicitud pago summary for a pedido
     */
    public SolicitudPagoSummary pedidoSolicitudPagoSummary(Long pedidoId) {
        try {
            Pedido pedido = service.findById(pedidoId).orElse(null);
            if (pedido == null) {
                return new SolicitudPagoSummary(0, 0, 0, 0, 0.0, 0.0, false);
            }

            // Get all nota recepcions for this pedido
            List<NotaRecepcion> allNotas = notaRecepcionService.findByPedidoId(pedidoId);
            
            // Count notas by status
            int totalNotas = allNotas.size();
            int notasAgrupadas = 0;
            int notasSinAgrupar = 0;
            double valorTotalNotas = 0.0;
            double valorTotalAgrupado = 0.0;

            for (NotaRecepcion nota : allNotas) {
                // Calculate nota value based on its items
                double notaValue = calculateNotaValue(nota);
                valorTotalNotas += notaValue;

                if (nota.getNotaRecepcionAgrupada() != null) {
                    notasAgrupadas++;
                    valorTotalAgrupado += notaValue;
                } else {
                    notasSinAgrupar++;
                }
            }

            // Count unique groups for this pedido's notas
            Set<Long> grupoIds = allNotas.stream()
                .filter(nota -> nota.getNotaRecepcionAgrupada() != null)
                .map(nota -> nota.getNotaRecepcionAgrupada().getId())
                .collect(Collectors.toSet());
            int totalGrupos = grupoIds.size();

            // Can progress if all notas are grouped
            boolean puedeProgresar = notasSinAgrupar == 0 && totalNotas > 0;

            return new SolicitudPagoSummary(
                totalNotas,
                notasAgrupadas,
                notasSinAgrupar,
                totalGrupos,
                valorTotalNotas,
                valorTotalAgrupado,
                puedeProgresar
            );

        } catch (Exception e) {
            System.err.println("Error calculating pedidoSolicitudPagoSummary for pedido " + pedidoId + ": " + e.getMessage());
            return new SolicitudPagoSummary(0, 0, 0, 0, 0.0, 0.0, false);
        }
    }

    /**
     * Get nota recepcions that are not yet assigned to any group for a pedido
     */
    public List<NotaRecepcion> notaRecepcionesSinAgrupar(Long pedidoId) {
        try {
            return notaRecepcionService.findByPedidoId(pedidoId).stream()
                .filter(nota -> nota.getNotaRecepcionAgrupada() == null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting notaRecepcionesSinAgrupar for pedido " + pedidoId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }



    /**
     * Get groups created for a specific pedido with their assigned notas
     */
    public List<GrupoConInfo> gruposCreadosParaPedido(Long pedidoId) {
        try {
            // Get all notas for this pedido
            List<NotaRecepcion> notasPedido = notaRecepcionService.findByPedidoId(pedidoId);
            
            // Get unique groups from these notas
            Set<NotaRecepcionAgrupada> grupos = notasPedido.stream()
                .filter(nota -> nota.getNotaRecepcionAgrupada() != null)
                .map(NotaRecepcion::getNotaRecepcionAgrupada)
                .collect(Collectors.toSet());

            return grupos.stream().map(grupo -> {
                // Get notas assigned to this group from this pedido
                List<NotaRecepcion> notasAsignadas = notasPedido.stream()
                    .filter(nota -> nota.getNotaRecepcionAgrupada() != null && 
                                   nota.getNotaRecepcionAgrupada().getId().equals(grupo.getId()))
                    .collect(Collectors.toList());
                
                // Calculate total value
                double valorTotal = notasAsignadas.stream()
                    .mapToDouble(this::calculateNotaValue)
                    .sum();
                
                // Check if grupo can accept more notas
                boolean puedeAgregarNotas = grupo.getEstado() != NotaRecepcionAgrupadaEstado.CONCLUIDO;
                
                // Check if it's an external group (created outside this pedido)
                boolean esGrupoExterno = false; // For now, assume all groups are created for this pedido
                
                // Check if can be deleted
                boolean puedeEliminar = grupo.getEstado() != NotaRecepcionAgrupadaEstado.CONCLUIDO;

                return new GrupoConInfo(
                    grupo,
                    notasAsignadas,
                    valorTotal,
                    puedeAgregarNotas,
                    puedeEliminar,
                    esGrupoExterno
                );
            }).collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error getting gruposCreadosParaPedido for pedido " + pedidoId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    

    /**
     * Calculate the total value of a NotaRecepcion based on its PedidoItems
     */
    private Double calculateNotaValue(NotaRecepcion nota) {
        try {
            List<PedidoItem> items = pedidoItemService.findByNotaRecepcionId(nota.getId());
            return items.stream()
                .filter(item -> item.getCancelado() == null || !item.getCancelado()) // Exclude cancelled items
                .mapToDouble(item -> {
                    // Use recepcion nota values if available, otherwise use creation values
                    Double precio = item.getPrecioUnitarioRecepcionNota() != null ? 
                        item.getPrecioUnitarioRecepcionNota() : item.getPrecioUnitarioCreacion();
                    Double cantidadDouble = item.getCantidadRecepcionNota() != null ? 
                        item.getCantidadRecepcionNota() : item.getCantidadCreacion();
                    Double descuento = item.getDescuentoUnitarioRecepcionNota() != null ? 
                        item.getDescuentoUnitarioRecepcionNota() : item.getDescuentoUnitarioCreacion();
                    
                    if (precio == null || cantidadDouble == null) return 0.0;
                    if (descuento == null) descuento = 0.0;
                    
                    return (precio - descuento) * cantidadDouble;
                })
                .sum();
        } catch (Exception e) {
            System.err.println("Error calculating nota value for nota " + nota.getId() + ": " + e.getMessage());
            return 0.0;
        }
    }


}
