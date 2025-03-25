package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionAgrupadaEstado;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionAgrupadaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class NotaRecepcionAgrupadaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionAgrupadaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    public NotaRecepcionAgrupada notaRecepcionAgrupadaPorId(Long id) {
        return service.getRepository().findById(id).orElse(null);
    }

    public Page<NotaRecepcionAgrupada> notaRecepcionListPorUsuarioId(Long id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.getRepository().findByUsuarioIdOrderByIdDesc(id, pageable);
    }

    public Page<NotaRecepcionAgrupada> notaRecepcionListPorProveedorId(Long id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.getRepository().findByProveedorId(id, pageable);
    }

    public NotaRecepcionAgrupada saveNotaRecepcionAgrupada(NotaRecepcionAgrupadaInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcionAgrupada e = m.map(input, NotaRecepcionAgrupada.class);
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getCreadoEn() != null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        return service.save(e);
    }

    public Page<PedidoRecepcionProductoDto> pedidoRecepcionProductoPorNotaRecepcionAgrupada(Long id, PedidoRecepcionProductoEstado estado, Integer page, Integer size) {
        return service.findRecepcionProductoByNotaRecepcionAgrupada(id, estado, PageRequest.of(page, size));
    }

    public PedidoRecepcionProductoDto pedidoRecepcionProductoPorNotaRecepcionAgrupadaAndProducto(Long notaRecepcionAgrupadaId, Long productoId, PedidoRecepcionProductoEstado estado) {
        return service.findRecepcionProductoByNotaRecepcionAgrupadaAndProducto(notaRecepcionAgrupadaId, productoId, estado);
    }

    public Boolean recepcionProductoNotaRecepcionAgrupada(Long notaRecepcionAgrupadaId, Long productoId, Long sucursalId, Double cantidad) {
        try {
            List<NotaRecepcion> notas = notaRecepcionService.findByNotaRecepcionAgrupadaId(notaRecepcionAgrupadaId);
            for(NotaRecepcion nr: notas){
                List<PedidoItem> pedidoItemList = pedidoItemService.findByNotaRecepcionId(nr.getId());
                for(PedidoItem pi: pedidoItemList){
                    List<PedidoItemSucursal> pedidoItemSucursalList = pedidoItemSucursalService.getRepository().findByPedidoItemIdAndSucursalEntregaId(pi.getId(), sucursalId);
                    for(PedidoItemSucursal pis: pedidoItemSucursalList){
                        if(pis.getPedidoItem().getProducto().getId().equals(productoId)){
                            Double cantRecibida = pis.getCantidadPorUnidadRecibida() != null ? pis.getCantidadPorUnidadRecibida() : 0.0;
                            Double cantFaltante = pis.getCantidadPorUnidad() - cantRecibida;
                            if(cantidad > 0.0 && cantFaltante <= cantidad){
                                pis.setCantidadPorUnidadRecibida(cantFaltante);
                                cantidad = cantidad - cantFaltante;
                            }
                            pedidoItemSucursalService.save(pis);
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public NotaRecepcionAgrupada finalizarRecepcion(Long id){
        try {
            NotaRecepcionAgrupada nra = service.findById(id).orElse(null);
            List<NotaRecepcion> notas = notaRecepcionService.findByNotaRecepcionAgrupadaId(nra.getId());
            for(NotaRecepcion nr: notas){
                List<PedidoItem> pedidoItemList = pedidoItemService.findByNotaRecepcionId(nr.getId());
                for(PedidoItem pi: pedidoItemList){
                    List<PedidoItemSucursal> pedidoItemSucursalList = pedidoItemSucursalService.getRepository().findByPedidoItemIdAndSucursalEntregaId(pi.getId(), nra.getSucursal().getId());
                    for(PedidoItemSucursal pis: pedidoItemSucursalList){
                        MovimientoStock ms = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.COMPRA, pis.getId(), nra.getSucursal().getId(), pi.getProducto().getId());
                        if(ms == null) {
                            ms = new MovimientoStock();
                            ms.setEstado(true);
                            ms.setCantidad(pis.getCantidadPorUnidadRecibida());
                            ms.setProducto(pi.getProducto());
                            ms.setUsuario(pis.getUsuario());
                            ms.setCreadoEn(LocalDateTime.now());
                            ms.setReferencia(pis.getId());
                            ms.setTipoMovimiento(TipoMovimiento.COMPRA);
                            ms.setSucursalId(nra.getSucursal().getId());
                        } else {
                            ms.setCantidad(pis.getCantidadPorUnidadRecibida());
                            ms.setCreadoEn(LocalDateTime.now());
                        }

                        movimientoStockService.save(ms);
                    }
                }
            }
            nra.setEstado(NotaRecepcionAgrupadaEstado.CONCLUIDO);
            return service.save(nra);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public NotaRecepcionAgrupada reabrirRecepcion(Long id){
        try {
            NotaRecepcionAgrupada nra = service.findById(id).orElse(null);
            nra.setEstado(NotaRecepcionAgrupadaEstado.EN_RECEPCION);
            return service.save(nra);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SolicitudPago solicitarPagoNotaRecepcionAgrupada(Long id){
        try {
            NotaRecepcionAgrupada nra = service.findById(id).orElse(null);
            //crear una solicitud de pago
            SolicitudPago sp = new SolicitudPago();
            sp.setEstado(SolicitudPagoEstado.PENDIENTE);
            sp.setCreadoEn(LocalDateTime.now());
            sp.setUsuario(nra.getUsuario());
            sp.setTipo(TipoSolicitudPago.COMPRA);
            sp.setReferenciaId(nra.getId());
            sp = solicitudPagoService.save(sp);
            return sp;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
