package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class TransferenciaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TransferenciaItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<TransferenciaItem> transferenciaItem(Long id) {
        return service.findById(id);
    }

    public List<TransferenciaItem> transferenciaItems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Page<TransferenciaItem> transferenciaItensPorTransferenciaId(Long id, Integer page, Integer size) {
        Page<TransferenciaItem> res = service.findByTransferenciaItemId(id, page, size);
        return res;
    }

    public Page<TransferenciaItem> transferenciaItensPorTransferenciaIdWithFilter(Long id, String name, Integer page, Integer size) {
        Page<TransferenciaItem> res = service.findByTransferenciaItemIdWithFilter(id, name, page, size);
        return res;
    }

    public TransferenciaItem saveTransferenciaItem(TransferenciaItemInput input, Double precioCosto) {
        ModelMapper m = new ModelMapper();
        TransferenciaItem e = m.map(input, TransferenciaItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setTransferencia(transferenciaService.findById(input.getTransferenciaId()).orElse(null));
        if (input.getVencimientoPreTransferencia() != null)
            e.setVencimientoPreTransferencia(stringToDate(input.getVencimientoPreTransferencia()));
        if (input.getVencimientoPreparacion() != null)
            e.setVencimientoPreparacion(stringToDate(input.getVencimientoPreparacion()));
        if (input.getVencimientoTransporte() != null)
            e.setVencimientoTransporte(stringToDate(input.getVencimientoTransporte()));
        if (input.getVencimientoRecepcion() != null)
            e.setVencimientoRecepcion(stringToDate(input.getVencimientoRecepcion()));
        if (input.getPresentacionPreTransferenciaId() != null)
            e.setPresentacionPreTransferencia(presentacionService.findById(input.getPresentacionPreTransferenciaId()).orElse(null));
        if (input.getPresentacionPreparacionId() != null)
            e.setPresentacionPreparacion(presentacionService.findById(input.getPresentacionPreparacionId()).orElse(null));
        if (input.getPresentacionTransporteId() != null)
            e.setPresentacionTransporte(presentacionService.findById(input.getPresentacionTransporteId()).orElse(null));
        if (input.getPresentacionRecepcionId() != null)
            e.setPresentacionRecepcion(presentacionService.findById(input.getPresentacionRecepcionId()).orElse(null));

        e = service.save(e);

        multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen().getId() + "_bkp", (TransferenciaItem s) -> service.save(s), e);
        multiTenantService.compartir("filial" + e.getTransferencia().getSucursalDestino().getId() + "_bkp", (TransferenciaItem s) -> service.save(s), e);

        movimientoStockService.createMovimientoFromTransferenciaItem(e);

        if (e != null && precioCosto != null) {
            Producto producto = e.getPresentacionPreTransferencia().getProducto();
            CostoPorProducto costoPorProducto = new CostoPorProducto();
            CostoPorProducto lastCostoPorProducto = costosPorProductoService.findLastByProductoId(producto.getId()).orElse(null);
            if (lastCostoPorProducto.getUltimoPrecioCompra() != precioCosto) {
                if (lastCostoPorProducto != null && lastCostoPorProducto.getCostoMedio() == null) {
                    costoPorProducto.setCostoMedio((lastCostoPorProducto.getUltimoPrecioCompra() + precioCosto) / 2);
                } else {
                    costoPorProducto.setCostoMedio(precioCosto);
                }
                costoPorProducto.setProducto(producto);
                costoPorProducto.setCotizacion(1.0);
                costoPorProducto.setUltimoPrecioCompra(precioCosto);
                costoPorProducto.setUsuario(e.getUsuario());
                costoPorProducto.setMoneda(monedaService.findByDescripcion("GUARANI"));
                costoPorProducto.setCreadoEn(e.getCreadoEn());
                costoPorProducto = costosPorProductoService.save(costoPorProducto);
                if (costoPorProducto != null)
                    multiTenantService.compartir("filial" + e.getTransferencia().getSucursalOrigen() + "_bkp", (CostoPorProducto s) -> costosPorProductoService.save(s), costoPorProducto);
            }

        }
        return e;
    }

    public Boolean deleteTransferenciaItem(Long id) {
        TransferenciaItem ti = service.findById(id).orElse(null);
        Boolean ok = service.deleteById(id);
        if(ok && ti!=null) {
            multiTenantService.compartir("filial"+ti.getTransferencia().getSucursalOrigen().getId()+"_bkp", (Long s) -> service.deleteById(s), id);
            multiTenantService.compartir("filial"+ti.getTransferencia().getSucursalDestino().getId()+"_bkp", (Long s) -> service.deleteById(s), id);
        }
        return ok;
    }
}

