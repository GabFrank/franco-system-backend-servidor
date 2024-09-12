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
import java.util.*;

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
        return e;
    }

    public Boolean deleteInventario(Long id) {
        Boolean ok = false;
        Inventario i = service.findById(id).orElse(null);
        if (i != null) {
            ok = service.deleteById(id);
        }
        return ok;
    }

    public Inventario finalizarInventario(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        if (inventario.getId() != null && inventario.getEstado() != InventarioEstado.CONCLUIDO) {
            inventario = finalizarInventarioEnSucursal(id);
            if (inventario != null) {
                return service.save(inventario);
            }
        }
        return null;
    }

    public Boolean cancelarInventario(Long id) {
        try {
            Inventario inventario = service.findById(id).orElse(null);
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.CANCELADO);
                List<MovimientoStock> movimientoStockList = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.AJUSTE, inventario.getId(), inventario.getSucursal().getId());
                for (MovimientoStock ms : movimientoStockList) {
                    if (ms != null) {
                        ms.setEstado(false);
                    }
                    movimientoStockService.save(ms);

                }
                service.save(inventario);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo reabrir el inventario. Comunicarse con RRHH");
        }
    }

    public Boolean reabrirInventario(Long id) {
        try {
            Inventario inventario = service.findById(id).orElse(null);
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.ABIERTO);
                inventario.setAbierto(true);
                List<MovimientoStock> movimientoStockList = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.AJUSTE, inventario.getId(), inventario.getSucursal().getId());
                for (MovimientoStock ms : movimientoStockList) {
                    if (ms != null) {
                        ms.setEstado(true);
                    }
                    movimientoStockService.save(ms);

                }
                inventario = service.save(inventario);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo reabrir el inventario. Comunicarse con RRHH");
        }
    }

    public Long countInventario() {
        return service.count();
    }

    public List<Inventario> inventarioPorFecha(String inicio, String fin) {
        return service.findByDate(inicio, fin);
    }

    public List<Inventario> inventarioAbiertoPorSucursal(Long sucId) {
        return service.findInventarioAbiertoPorSucursal(sucId);
    }

    public Inventario finalizarInventarioEnSucursal(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        Map<Long, Double> cantidadesPorProducto = new HashMap<>();
        Producto selectedProducto = null;
        try {
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.CONCLUIDO);
                inventario.setAbierto(false);
                inventario.setFechaFin(LocalDateTime.now());
                List<InventarioProducto> inventarioProductoList = inventarioProductoService.findByInventarioId(id);
                List<MovimientoStock> movimientoStockList = new ArrayList<>();
                for (InventarioProducto ip : inventarioProductoList) {
                    List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService.findByInventarioProductoId(ip.getId());
                    for (InventarioProductoItem ipi : inventarioProductoItemList) {
                        selectedProducto = ipi.getPresentacion().getProducto();
                        cantidadesPorProducto.merge(
                                ipi.getPresentacion().getProducto().getId(),
                                ipi.getCantidad() * ipi.getPresentacion().getCantidad(),
                                Double::sum
                        );
                    }

                    for (Map.Entry<Long, Double> entry : cantidadesPorProducto.entrySet()) {
                        Long productoId = entry.getKey();
                        Double cantidadTotal = entry.getValue();
                        Double stockSistema = 0.0;
                        MovimientoStock movimientoStockEncontrado = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.AJUSTE, inventario.getId(), inventario.getSucursal().getId(), productoId);
                        if (movimientoStockEncontrado != null) {
                            stockSistema = movimientoStockService.stockByProductoIdExecptMovStockId(movimientoStockEncontrado.getProducto().getId(), movimientoStockEncontrado.getId());
                        } else {
                            stockSistema = movimientoStockService.stockByProductoId(productoId);
                        }
                        if (movimientoStockEncontrado == null) {
                            movimientoStockEncontrado = new MovimientoStock();
                            movimientoStockEncontrado.setTipoMovimiento(TipoMovimiento.AJUSTE);
                            movimientoStockEncontrado.setSucursalId(inventario.getSucursal().getId());
                            movimientoStockEncontrado.setReferencia(inventario.getId());
                            movimientoStockEncontrado.setProducto(selectedProducto);
                            movimientoStockEncontrado.setUsuario(inventario.getUsuario());
                            movimientoStockEncontrado.setEstado(true);
                        }
                        Double diferencia = cantidadTotal - stockSistema; //9 - 10 = -1, 11 - 10 = 1
                        movimientoStockEncontrado.setCantidad(diferencia);
                        movimientoStockEncontrado = movimientoStockService.save(movimientoStockEncontrado);
                    }
                }
            }
            return inventario;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }
}
