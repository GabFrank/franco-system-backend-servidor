package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class PedidoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private CompraItemService compraItemService;

    public Optional<PedidoItem> pedidoItem(Long id) {return service.findById(id);}

    public Page<PedidoItem> pedidoItemPorPedidoPage(Long id, int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findByPedidoId(id, pageable);
    }

    public List<PedidoItem> pedidoItemPorPedido(Long id){
        return service.findByPedidoId(id);
    }

    public PedidoItem savePedidoItem(PedidoItemInput input){
        ModelMapper m = new ModelMapper();
        PedidoItem e = m.map(input, PedidoItem.class);
        if(input.getUsuarioCreacionId()!=null) e.setUsuarioCreacion(usuarioService.findById(input.getUsuarioCreacionId()).orElse(null));
        if(input.getUsuarioRecepcionNotaId()!=null) e.setUsuarioRecepcionNota(usuarioService.findById(input.getUsuarioRecepcionNotaId()).orElse(null));
        if(input.getUsuarioRecepcionProductoId()!=null) e.setUsuarioRecepcionProducto(usuarioService.findById(input.getUsuarioRecepcionProductoId()).orElse(null));
        if(input.getProductoId()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(input.getPedidoId()!=null) e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if(input.getPresentacionCreacionId()!=null) e.setPresentacionCreacion(presentacionService.findById(input.getPresentacionCreacionId()).orElse(null));
        if(input.getPresentacionRecepcionNotaId()!=null) e.setPresentacionRecepcionNota(presentacionService.findById(input.getPresentacionRecepcionNotaId()).orElse(null));
        if(input.getPresentacionRecepcionProductoId()!=null) e.setPresentacionRecepcionProducto(presentacionService.findById(input.getPresentacionRecepcionProductoId()).orElse(null));
        if(input.getCreadoEn()!=null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        if(input.getVencimientoCreacion()!=null) e.setVencimientoCreacion(stringToDate(input.getVencimientoCreacion()));
        if(input.getVencimientoRecepcionNota()!=null) e.setVencimientoRecepcionNota(stringToDate(input.getVencimientoRecepcionNota()));
        if(input.getVencimientoRecepcionProducto()!=null) e.setVencimientoRecepcionProducto(stringToDate(input.getVencimientoRecepcionProducto()));
        if(input.getCreadoEn()!=null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        if(input.getNotaRecepcionId()!=null) e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        if(input.getAutorizadoPorRecepcionNotaId()!=null) e.setAutorizadoPorRecepcionNota(usuarioService.findById(input.getAutorizadoPorRecepcionNotaId()).orElse(null));
        if(input.getAutorizadoPorRecepcionProductoId()!=null) e.setAutorizadoPorRecepcionProducto(usuarioService.findById(input.getAutorizadoPorRecepcionProductoId()).orElse(null));

        return service.save(e);
    }

    public Boolean deletePedidoItem(Long id){
        return service.deleteById(id);
    }

    public Long countPedidoItem(){
        return service.count();
    }

    public Page<PedidoItem> pedidoItemPorPedidoIdSobrante(Long id, int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        return service.findByPedidoIdSobrantes(id, pageable);
    }

    public Page<PedidoItem> pedidoItemPorNotaRecepcion(Long id, int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        return service.findByNotaRecepcionId(id, pageable);
    }

    public PedidoItem updateNotaRecepcion(Long pedidoItemId, Long notaRecepcionId){
        PedidoItem pedidoItem = service.findById(pedidoItemId).orElse(null);
        if(pedidoItem!=null){
            if(notaRecepcionId!=null){
                pedidoItem.setNotaRecepcion(notaRecepcionService.findById(notaRecepcionId).orElse(null));
            } else {
                pedidoItem.setNotaRecepcion(null);
//                CompraItem compraItem = compraItemService.findByPedidoItemId(pedidoItemId);
//                if(compraItem!=null){
//                    compraItem.setEstado(CompraItemEstado.SIN_MODIFICACION);
//                    compraItem.setPrecioUnitario(pedidoItem.getPrecioUnitario());
//                    compraItem.setDescuentoUnitario(pedidoItem.getDescuentoUnitario());
//                    compraItem.setCantidad(pedidoItem.getCantidad());
//                    compraItemService.save(compraItem);
//                }
            }
            pedidoItem = service.save(pedidoItem);
        }
        return pedidoItem;
    }

    public Boolean addPedidoItemListToNotaRecepcion(Long notaRecepcionId, List<Long> pedidoItemIdList){
        NotaRecepcion notaRecepcion = notaRecepcionId != null ? notaRecepcionService.findById(notaRecepcionId).orElse(null) : null;
        List<PedidoItem> pedidoItemList = service.getRepository().findByIdIn(pedidoItemIdList);
        if(notaRecepcion != null && pedidoItemIdList.size() > 0){
            for(PedidoItem pi : pedidoItemList){
                pi.setNotaRecepcion(notaRecepcion);
                pi.setPresentacionRecepcionNota(pi.getPresentacionCreacion());
                pi.setCantidadRecepcionNota(pi.getCantidadRecepcionNota());
                pi.setDescuentoUnitarioRecepcionNota(pi.getDescuentoUnitarioCreacion());
                pi.setVencimientoRecepcionNota(pi.getVencimientoCreacion());
                pi.setPrecioUnitarioRecepcionNota(pi.getPrecioUnitarioCreacion());
                pi.setVerificadoRecepcionNota(true);
                service.save(pi);
            }
            return true;
        } else if(notaRecepcionId == null && pedidoItemIdList.size() > 0) {
            for(PedidoItem pi : pedidoItemList){
                pi.setPresentacionRecepcionNota(null);
                pi.setCantidadRecepcionNota(null);
                pi.setDescuentoUnitarioRecepcionNota(null);
                pi.setVencimientoRecepcionNota(null);
                pi.setPrecioUnitarioRecepcionNota(null);
                pi.setVerificadoRecepcionNota(false);
                pi.setNotaRecepcion(null);
                service.save(pi);
            }
            return true;
        } else {
            return false;
        }
    }


}
