package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
import com.franco.dev.service.productos.ProductoProveedorService;
import graphql.GraphQLException;
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
    private PedidoFechaEntregaService pedidoFechaEntregaService;

    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProductoProveedorService productoProveedorService;

    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    // ===== BASIC CRUD OPERATIONS =====

    public Optional<Pedido> pedido(Long id) {
        return service.findById(id);
    }

    public List<Pedido> pedidos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<Pedido> filterPedidos(Long idPedido, Integer numeroNotaRecepcion, PedidoEstado estado, 
                                      Long sucursalId, String inicio, String fin, Long proveedorId, 
                                      Long vendedorId, Long formaPagoId, Long productoId, 
                                      Integer page, Integer size) {
        return service.filterPedidos(idPedido, numeroNotaRecepcion, estado, sucursalId, inicio, fin, 
                                   proveedorId, vendedorId, formaPagoId, productoId, page, size);
    }

    public Long countPedido() {
        return service.count();
    }

    public Boolean deletePedido(Long id) {
        return service.deleteById(id);
    }

    // ===== SAVE OPERATIONS =====

    public Pedido savePedido(PedidoInput input) {
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getMonedaId() != null) 
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getVendedorId() != null) 
            e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        
        return service.save(e);
    }

    public Pedido savePedidoFull(PedidoInput input, List<String> fechaEntregaList, 
                                List<Long> sucursalEntregaList, List<Long> sucursalInfluenciaList, 
                                Long usuarioId) {
        ModelMapper m = new ModelMapper();
        Boolean isNew = input.getId() == null;
        Pedido e = m.map(input, Pedido.class);
        
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getMonedaId() != null) 
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getVendedorId() != null) 
            e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
            
        Pedido pedido = service.save(e);
        
        if (fechaEntregaList != null) {
            updatePedidoFechaEntrega(pedido, fechaEntregaList, usuarioId);
        }
        if (sucursalEntregaList != null) {
            updatePedidoSucursalEntrega(pedido, sucursalEntregaList, usuarioId);
        }
        if (sucursalInfluenciaList != null) {
            updatePedidoSucursalInfluencia(pedido, sucursalInfluenciaList, usuarioId);
        }

        if(isNew){
            inicializarEtapas(pedido);
        }
        
        return service.findById(pedido.getId()).orElse(null);
    }

    // ===== RELATED ENTITY UPDATES =====

    @Transactional
    public void updatePedidoFechaEntrega(Pedido pedido, List<String> newDates, Long usuarioId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        Set<LocalDateTime> newDatesSet = newDates.stream()
                .map(dateStr -> stringToDate(dateStr))
                .collect(Collectors.toSet());

        List<PedidoFechaEntrega> currentEntries = pedidoFechaEntregaService.findByPedido(pedido.getId());

        List<PedidoFechaEntrega> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getFechaEntrega()))
                .collect(Collectors.toList());

        pedidoFechaEntregaService.deleteAll(toDelete);

        currentEntries.forEach(entry -> newDatesSet.remove(entry.getFechaEntrega()));

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
        List<Long> newDatesSet = newSucursalesList;

        List<PedidoSucursalEntrega> currentEntries = pedidoSucursalEntregaService.findByPedidoId(pedido.getId());

        List<PedidoSucursalEntrega> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getSucursal().getId()))
                .collect(Collectors.toList());

        pedidoSucursalEntregaService.deleteAll(toDelete);

        currentEntries.forEach(entry -> newDatesSet.remove(entry.getSucursal().getId()));

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
        List<Long> newDatesSet = newSucursalesList;

        List<PedidoSucursalInfluencia> currentEntries = pedidoSucursalInfluenciaService.findByPedidoId(pedido.getId());

        List<PedidoSucursalInfluencia> toDelete = currentEntries.stream()
                .filter(entry -> !newDatesSet.contains(entry.getSucursal().getId()))
                .collect(Collectors.toList());

        pedidoSucursalInfluenciaService.deleteAll(toDelete);

        currentEntries.forEach(entry -> newDatesSet.remove(entry.getSucursal().getId()));

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

    // ===== WORKFLOW OPERATIONS =====

    public Pedido finalizarPedido(Long id, PedidoEstado estado) {
        Pedido pedido = service.findById(id).orElse(null);
        if (pedido == null) {
            throw new GraphQLException("No se puedo encontrar el pedido");
        }

        if (estado == PedidoEstado.EN_RECEPCION_MERCADERIA) {
            List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(id);
            procesarPedidoItems(pedido, pedidoItemList);
        }

        pedido.setEstado(estado);
        return service.save(pedido);
    }

    @Async
    public void procesarPedidoItems(Pedido pedido, List<PedidoItem> pedidoItemList) {
        for (PedidoItem pi : pedidoItemList) {
            ProductoProveedor productoProveedor = new ProductoProveedor();
            productoProveedor.setProveedor(pedido.getProveedor());
            productoProveedor.setPedido(pedido);
            productoProveedor.setUsuario(pedido.getUsuario());
            productoProveedor.setProducto(pi.getProducto());
            productoProveedor.setCreadoEn(LocalDateTime.now());
            productoProveedorService.save(productoProveedor);
        }
    }

    // ===== NEW STEP TRACKING FUNCTIONALITY =====

    /**
     * Get current stage of the pedido
     */
    public String etapaActualDelPedido(Long pedidoId) {
        try {
            if (pedidoId == null) {
                throw new GraphQLException("ID del pedido es requerido");
            }
            
            Object etapaActual = procesoEtapaService.getEtapaActual(pedidoId);
            return etapaActual != null ? etapaActual.toString() : "CREACION";
            
        } catch (Exception e) {
            System.err.println("Error obteniendo etapa actual para pedido " + pedidoId + ": " + e.getMessage());
            return "CREACION";
        }
    }

    /**
     * Finalize pedido creation and update stages
     */
    @Transactional
    public Pedido finalizarCreacionPedido(Long pedidoId) {
        try {
            if (pedidoId == null) {
                throw new GraphQLException("ID del pedido es requerido");
            }

            Pedido pedido = service.finalizarCreacion(pedidoId);
            return pedido;

        } catch (Exception e) {
            System.err.println("Error finalizando creación del pedido " + pedidoId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al finalizar creación del pedido: " + e.getMessage());
        }
    }

    @Transactional
    private void inicializarEtapas(Pedido pedido) {
        List<ProcesoEtapa> etapas = new ArrayList<>();

        // Etapa de Creación
        ProcesoEtapa etapaCreacion = new ProcesoEtapa();
        etapaCreacion.setPedido(pedido);
        etapaCreacion.setTipoEtapa(ProcesoEtapaTipo.CREACION);
        etapaCreacion.setEstadoEtapa(ProcesoEtapaEstado.EN_PROCESO);
        etapaCreacion.setFechaInicio(LocalDateTime.now());
        etapaCreacion.setUsuarioInicio(pedido.getUsuario());
        etapaCreacion.setCreadoEn(LocalDateTime.now());
        etapas.add(etapaCreacion);

        // Etapa de Recepción de Nota
        ProcesoEtapa etapaRecepcionNota = new ProcesoEtapa();
        etapaRecepcionNota.setPedido(pedido);
        etapaRecepcionNota.setTipoEtapa(ProcesoEtapaTipo.RECEPCION_NOTA);
        etapaRecepcionNota.setEstadoEtapa(ProcesoEtapaEstado.PENDIENTE);
        etapaRecepcionNota.setCreadoEn(LocalDateTime.now());
        etapas.add(etapaRecepcionNota);

        // Etapa de Recepción de Mercadería
        ProcesoEtapa etapaRecepcionMercaderia = new ProcesoEtapa();
        etapaRecepcionMercaderia.setPedido(pedido);
        etapaRecepcionMercaderia.setTipoEtapa(ProcesoEtapaTipo.RECEPCION_MERCADERIA);
        etapaRecepcionMercaderia.setEstadoEtapa(ProcesoEtapaEstado.PENDIENTE);
        etapaRecepcionMercaderia.setCreadoEn(LocalDateTime.now());
        etapas.add(etapaRecepcionMercaderia);

        // Etapa de Solicitud de Pago
        ProcesoEtapa etapaSolicitudPago = new ProcesoEtapa();
        etapaSolicitudPago.setPedido(pedido);
        etapaSolicitudPago.setTipoEtapa(ProcesoEtapaTipo.SOLICITUD_PAGO);
        etapaSolicitudPago.setEstadoEtapa(ProcesoEtapaEstado.PENDIENTE);
        etapaSolicitudPago.setCreadoEn(LocalDateTime.now());
        etapas.add(etapaSolicitudPago);
        
        procesoEtapaService.saveAll(etapas);
    }


    /**
     * Get overall pedido progress (percentage of completed stages)
     */
    public Integer progresoPedido(Long pedidoId) {
        try {
            if (pedidoId == null) {
                throw new GraphQLException("ID del pedido es requerido");
            }

            java.util.List<com.franco.dev.domain.operaciones.ProcesoEtapa> etapas = 
                procesoEtapaService.getEtapasByPedidoId(pedidoId);

            if (etapas.isEmpty()) {
                return 0;
            }

            long etapasCompletadas = etapas.stream()
                .filter(etapa -> etapa.getEstadoEtapa() == com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado.FINALIZADA)
                .count();

            return (int) ((etapasCompletadas * 100) / etapas.size());

        } catch (Exception e) {
            System.err.println("Error calculando progreso para pedido " + pedidoId + ": " + e.getMessage());
            return 0;
        }
    }
}
