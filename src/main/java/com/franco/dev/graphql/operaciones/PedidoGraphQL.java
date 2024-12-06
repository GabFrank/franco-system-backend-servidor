package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoFechaEntrega;
import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private PedidoItemGraphQL pedidoItemGraphQL;

    @Autowired
    private PedidoFechaEntregaService pedidoFechaEntregaService;

    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<Pedido> pedido(Long id) {return service.findById(id);}

    public List<Pedido> pedidos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Pedido> filterPedidos(PedidoEstado estado, Long sucursalId, String inicio, String fin, Long proveedorId, Long vendedorId, Long formaPago, Long productoId){
        String auxEstado = null;
        String auxFormaPago = null;
        if(estado!=null){
            auxEstado = estado.name();
        }
        if(fin==null && inicio!=null){
            fin = inicio;
        }
        return service.filterPedidos(auxEstado, sucursalId, inicio, fin, proveedorId, vendedorId, auxFormaPago, productoId);
    }

    public Pedido savePedido(PedidoInput input){
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getProveedorId()!=null) e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if(input.getVendedorId()!=null) e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        Pedido pedido = service.save(e);
        return pedido;
    }

    public Pedido savePedidoFull(PedidoInput input, List<String> fechaEntregaList, List<Long> sucursalEntregaList, List<Long> sucursalInfluenciaList, Long usuarioId){
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getProveedorId()!=null) e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if(input.getVendedorId()!=null) e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        Pedido pedido = service.save(e);
        if(fechaEntregaList != null){
            updatePedidoFechaEntrega(pedido, fechaEntregaList, usuarioId);
        }
        if(sucursalEntregaList != null){
            updatePedidoSucursalEntrega(pedido, sucursalEntregaList, usuarioId);
        }
        if(sucursalEntregaList != null){
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

    public Boolean deletePedido(Long id){
        return service.deleteById(id);
    }

    public Long countPedido(){
        return service.count();
    }

    public Pedido finalizarPedido(Long id, PedidoEstado estado){
        Pedido pedido = service.findById(id).orElse(null);
        if(pedido == null){
            throw new GraphQLException("No se puedo encontrar el pedido");
        }
        pedido.setEstado(estado);
        return service.save(pedido);
    }

}
