package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.InventarioInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.reports.TicketReportService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class InventarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Inventario> inventario(Long id) {
        return service.findById(id);
    }

    public List<Inventario> inventarioList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

//    public List<Inventario> inventarioSearch(String texto){
//        return service.findByAll(texto);
//    }

    public List<Inventario> inventarioPorUsuario(Long id) {
        return service.findByUsuario(id);
    }


    public Inventario saveInventario(InventarioInput input) {
        ModelMapper m = new ModelMapper();
        Inventario e = m.map(input, Inventario.class);
        if (input.getFechaInicio() != null) e.setFechaInicio(stringToDate(input.getFechaInicio()));
        if (input.getFechaFin() != null) e.setFechaFin(stringToDate(input.getFechaFin()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.INVENTARIO, e.getSucursal().getId());
        multiTenantService.compartir("filial"+e.getSucursal().getId()+"_bkp", (Inventario s) -> service.save(s), e);
        return e;
    }

    public Boolean deleteInventario(Long id) {
        Boolean ok = false;
        Inventario i = service.findById(id).orElse(null);
        if (i != null) {
            ok = service.deleteById(id);
            multiTenantService.compartir("filial"+i.getSucursal().getId()+"_bkp", (Long s) -> service.deleteById(s), id);
        }
        return ok;
    }

    public Inventario finalizarInventario(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        if (inventario.getId() != null && inventario.getEstado() != InventarioEstado.CONCLUIDO) {
            inventario = multiTenantService.compartir("filial"+inventario.getSucursal().getId()+"_bkp", (Long s) -> finalizarInventarioEnSucursal(s), id);
            if(inventario!=null){
                return service.save(inventario);
            }
        }
        return null;
    }

    public Boolean cancelarInventario(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        if (inventario.getId() != null) {
            inventario.setEstado(InventarioEstado.CANCELADO);
            service.save(inventario);
            List<MovimientoStock> movimientoStockList = movimientoStockService.findListByTipoMovimientoAndReferencia(TipoMovimiento.AJUSTE, id);
            for (MovimientoStock ms : movimientoStockList) {
                ms.setEstado(false);
                movimientoStockService.save(ms);
            }
        }
        return true;
    }

    public Long countInventario() {
        return service.count();
    }

    public List<Inventario> inventarioPorFecha(String inicio, String fin) {
        return service.findByDate(inicio, fin);
    }

    public List<Inventario> inventarioAbiertoPorSucursal(Long sucId){
        return service.findInventarioAbiertoPorSucursal(sucId);
    }

    public Inventario finalizarInventarioEnSucursal(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        try {
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.CONCLUIDO);
                inventario.setFechaFin(LocalDateTime.now());
                inventario = service.save(inventario);
                List<InventarioProducto> inventarioProductoList = inventarioProductoService.findByInventarioId(id);
                List<MovimientoStock> movimientoStockList = new ArrayList<>();
                for (InventarioProducto ip : inventarioProductoList) {
                    List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService.findByInventarioProductoId(ip.getId());
                    for (InventarioProductoItem ipi : inventarioProductoItemList) {
                        MovimientoStock movimientoStockEncontrado = null;
                        for (MovimientoStock ms : movimientoStockList) {
                            if (ipi.getPresentacion().getProducto().getId() == ms.getProducto().getId()) {
                                ms.setCantidad(ms.getCantidad() + (ipi.getPresentacion().getCantidad() * ipi.getCantidad()));
                                movimientoStockEncontrado = ms;
                            }
                        }
                        if (movimientoStockEncontrado == null) {
                            movimientoStockEncontrado = new MovimientoStock();
                            movimientoStockEncontrado.setCantidad(ipi.getCantidad() * ipi.getPresentacion().getCantidad());
                            movimientoStockEncontrado.setTipoMovimiento(TipoMovimiento.AJUSTE);
                            movimientoStockEncontrado.setReferencia(ipi.getId());
                            movimientoStockEncontrado.setProducto(ipi.getPresentacion().getProducto());
                            movimientoStockEncontrado.setEstado(true);
                            movimientoStockList.add(movimientoStockEncontrado);
                        }
                    }
                }
                for (MovimientoStock ms : movimientoStockList) {
                    Double stockSistema = Double.valueOf(movimientoStockService.stockByProductoId(ms.getProducto().getId()));
                    Double stockFisico = ms.getCantidad();
                    Double diferencia = stockFisico - stockSistema; //9 - 10 = -1, 11 - 10 = 1
                    ms.setCantidad(diferencia);
                    movimientoStockService.saveAndSend(ms, false);
                }
            }
            return inventario;
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }

    }

}
