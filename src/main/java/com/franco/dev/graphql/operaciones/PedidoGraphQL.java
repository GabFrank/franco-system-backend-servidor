package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.*;
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
    private Integer totalNotas;

    public PedidoRecepcionNotaSummary(Integer totalItems, Integer assignedItems, Integer pendingItems, Integer totalNotas) {
        this.totalItems = totalItems;
        this.assignedItems = assignedItems;
        this.pendingItems = pendingItems;
        this.totalNotas = totalNotas;
    }

    // Getters
    public Integer getTotalItems() { return totalItems; }
    public Integer getAssignedItems() { return assignedItems; }
    public Integer getPendingItems() { return pendingItems; }
    public Integer getTotalNotas() { return totalNotas; }
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
        if (estado == PedidoEstado.EN_RECEPCION_MERCADERIA) {
            List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(id);
            procesarPedidoItems(pedido, pedidoItemList);
        } else if (estado == PedidoEstado.CONCLUIDO) {
            List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(id);
            for (PedidoItem pi : pedidoItemList) {

            }
        }
        pedido.setEstado(estado);
        return service.save(pedido);
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

            // Crear precio de costo
            Double precioCosto = pi.getPrecioUnitarioRecepcionNota();
            Double costoMedio = costosPorProductoService.calcularCostoMedio(
                    pi.getProducto().getId(),
                    pi.getCantidadRecepcionNota(),
                    precioCosto);

            CostoPorProducto costoPorProducto = new CostoPorProducto();
            costoPorProducto.setProducto(pi.getProducto());
            costoPorProducto.setUltimoPrecioCompra(precioCosto);
            costoPorProducto.setCostoMedio(costoMedio);
            costoPorProducto.setCreadoEn(LocalDateTime.now());
            costoPorProducto.setMoneda(pi.getPedido().getMoneda());
            costoPorProducto.setCotizacion(
                    cambioService.findLastByMonedaId(costoPorProducto.getMoneda().getId()).getValorEnGs());
            costoPorProducto.setUsuario(pedido.getUsuario());
            costosPorProductoService.save(costoPorProducto);
        }

    }

    public Boolean verificarDistribucionSucursales(Long id){
        return pedidoItemService.getRepository().findDistribucionSucursalRecepcionByPedidoId(id);
    }

    public PedidoRecepcionNotaSummary pedidoRecepcionNotaSummary(Long id) {
        List<PedidoItem> allItems = pedidoItemService.findByPedidoId(id);
        List<PedidoItem> assignedItems = allItems.stream()
                .filter(item -> item.getNotaRecepcion() != null)
                .collect(Collectors.toList());

        List<NotaRecepcion> notas = notaRecepcionService.findByPedidoId(id);

        return new PedidoRecepcionNotaSummary(
                allItems.size(),
                assignedItems.size(),
                allItems.size() - assignedItems.size(),
                notas.size()
        );
    }

    /**
     * Handles automatic data copying when Pedido estado changes
     * This ensures that when a Pedido transitions to a new state, all its items
     * have the appropriate step data copied from the previous step
     */
    @Transactional
    private void handlePedidoEstadoChange(Pedido pedido, PedidoEstado previousEstado, PedidoEstado newEstado) {
        List<PedidoItem> pedidoItems = pedidoItemService.findByPedidoId(pedido.getId());
        
        for (PedidoItem item : pedidoItems) {
            copyDataForEstadoTransition(item, previousEstado, newEstado);
            // Items from findByPedidoId are already managed entities, so we can save them directly
            pedidoItemService.save(item);
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

}
