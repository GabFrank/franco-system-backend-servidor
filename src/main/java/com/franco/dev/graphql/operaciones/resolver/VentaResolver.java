package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.repository.operaciones.VentaCancelacionRepository;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import com.franco.dev.domain.financiero.FormaPago;

@Component
public class VentaResolver implements GraphQLResolver<Venta> {

    @Autowired
    private VentaService service;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private VentaObservacionService ventaObservacionService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private VentaCancelacionRepository ventaCancelacionRepository;

    public FormaPago formaPago(Venta v) {
        if (v.getFormaPago() != null) {
            return v.getFormaPago();
        } else {
            if (v.getCobro() != null) {
                List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(v.getCobro().getId(), v.getSucursalId());
                if (cobroDetalleList != null && !cobroDetalleList.isEmpty()) {
                    return cobroDetalleList.get(0).getFormaPago();
                }
            }
        }
        return null;
    }

    /** Quien cancelo la venta, si esta cancelada y quedo registro (V274.1); si no, null. */
    public VentaCancelacion cancelacion(Venta v) {
        if (v.getEstado() != VentaEstado.CANCELADA) return null;
        return cancelacionVigente(v,
                ventaCancelacionRepository.findFirstByVentaIdAndSucursalIdOrderByIdDesc(v.getId(), v.getSucursalId()));
    }

    /** La ultima fila vale solo si coincide con el estado de la venta: una cancelada por otra via no se atribuye. */
    static VentaCancelacion cancelacionVigente(Venta v, VentaCancelacion ultima) {
        return v.getEstado() == VentaEstado.CANCELADA && ultima != null && ultima.getEstado() == VentaEstado.CANCELADA
                ? ultima : null;
    }

    public List<VentaObservacion> ventaObservacionList(Venta v) {
        return ventaObservacionService.findByVentaIdAndSucursalId(v.getId(), v.getSucursalId());
    }

    public List<VentaItem> ventaItemList(Venta v){
        return ventaItemService.findByVentaId(v.getId(), v.getSucursalId());
    }

    public Double valorDescuento(Venta v){
        Double descuento = 0.0;
        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId(), v.getSucursalId());
        for (VentaItem vi : ventaItemList){
            if(vi.getValorDescuento()!=null){
                descuento += vi.getValorDescuento() * vi.getCantidad();
            }
        }
        return descuento;
    }

    public Double valorTotal(Venta v){
        Double precio = 0.0;
//        Integer cantidad = 1;
//        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId(), v.getSucursalId());
//        for (VentaItem vi : ventaItemList){
//            if(vi.getUnidadMedida() == UnidadMedida.CAJA){
//                cantidad = vi.getProducto().getUnidadPorCaja();
//            }
//            precio += vi.getPrecioVenta().getPrecio() * (vi.getCantidad() * cantidad);
//            cantidad = 1;
//        }
//        return precio;
        precio = ventaItemService.getRepository().totalByVentaIdAndSucId(v.getId(), v.getSucursalId());
        if(precio != null) {
            return precio;
        } else {
            return 0.0;
        }
    }
}
