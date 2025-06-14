package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.operaciones.NotaPedidoService;
import graphql.GraphQLException;
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

    @Autowired
    private NotaPedidoService notaPedidoService;

    public Optional<PedidoItem> pedidoItem(Long id) {
        return service.findById(id);
    }

    public Page<PedidoItem> pedidoItemPorPedidoPage(Long id, int page, int size, String texto) {
        Pageable pageable = PageRequest.of(page, size);
        if (texto != null) {
            texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
            return service.findByPedidoIdAndTexto(id, texto, pageable);
        } else {
            return service.findByPedidoId(id, pageable);
        }
    }

    public List<PedidoItem> pedidoItemPorPedido(Long id) {
        return service.findByPedidoId(id);
    }

    public PedidoItem savePedidoItem(PedidoItemInput input) {
        ModelMapper m = new ModelMapper();
        PedidoItem e = m.map(input, PedidoItem.class);
        
        // Map navigation properties - always fetch fresh managed entities from database
        if (input.getUsuarioCreacionId() != null)
            e.setUsuarioCreacion(usuarioService.findById(input.getUsuarioCreacionId()).orElse(null));
        if (input.getUsuarioRecepcionNotaId() != null)
            e.setUsuarioRecepcionNota(usuarioService.findById(input.getUsuarioRecepcionNotaId()).orElse(null));
        if (input.getUsuarioRecepcionProductoId() != null)
            e.setUsuarioRecepcionProducto(usuarioService.findById(input.getUsuarioRecepcionProductoId()).orElse(null));
        if (input.getProductoId() != null) 
            e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if (input.getPedidoId() != null) 
            e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if (input.getPresentacionCreacionId() != null)
            e.setPresentacionCreacion(presentacionService.findById(input.getPresentacionCreacionId()).orElse(null));
        if (input.getPresentacionRecepcionNotaId() != null)
            e.setPresentacionRecepcionNota(presentacionService.findById(input.getPresentacionRecepcionNotaId()).orElse(null));
        if (input.getPresentacionRecepcionProductoId() != null)
            e.setPresentacionRecepcionProducto(presentacionService.findById(input.getPresentacionRecepcionProductoId()).orElse(null));
        if (input.getCreadoEn() != null) 
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        if (input.getVencimientoCreacion() != null)
            e.setVencimientoCreacion(stringToDate(input.getVencimientoCreacion()));
        if (input.getVencimientoRecepcionNota() != null)
            e.setVencimientoRecepcionNota(stringToDate(input.getVencimientoRecepcionNota()));
        if (input.getVencimientoRecepcionProducto() != null)
            e.setVencimientoRecepcionProducto(stringToDate(input.getVencimientoRecepcionProducto()));
        if (input.getNotaRecepcionId() != null){ 
            e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        } else {
            e.setNotaRecepcion(null);
        }
        if (input.getAutorizadoPorRecepcionNotaId() != null)
            e.setAutorizadoPorRecepcionNota(usuarioService.findById(input.getAutorizadoPorRecepcionNotaId()).orElse(null));
        if (input.getAutorizadoPorRecepcionProductoId() != null)
            e.setAutorizadoPorRecepcionProducto(usuarioService.findById(input.getAutorizadoPorRecepcionProductoId()).orElse(null));

        return service.save(e);
    }

    public Boolean deletePedidoItem(Long id) {
        return service.deleteById(id);
    }

    public Long countPedidoItem() {
        return service.count();
    }

    public Page<PedidoItem> pedidoItemPorPedidoIdSobrante(Long id, int page, int size, String texto) {
        Pageable pageable = PageRequest.of(page, size);
        if (texto != null) {
            texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
            return service.findByPedidoIdAndDescripcionSobrantes(id, texto, pageable);
        } else {
            return service.findByPedidoIdSobrantes(id, pageable);
        }
    }

    public Page<PedidoItem> pedidoItemPorNotaRecepcion(Long id, int page, int size, String texto, Boolean verificado) {
        Pageable pageable = PageRequest.of(page, size);
        if (texto != null) {
            texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
            if (verificado != null) {
                return service.getRepository().findByNotaRecepcionIdAndProductoDescripcionLikeAndVerificadoRecepcionProductoOrderByProductoDescripcionDesc(id, texto, verificado, pageable);
            } else {
                return service.findByNotaRecepcionIdAndDescripcion(id, texto, pageable);
            }
        } else {
            if (verificado != null) {
                return service.getRepository().findByNotaRecepcionIdAndVerificadoRecepcionProducto(id, verificado, pageable);
            } else {
                return service.findByNotaRecepcionId(id, pageable);
            }
        }
    }

    public PedidoItem updateNotaRecepcion(Long pedidoItemId, Long notaRecepcionId) {
        PedidoItem pedidoItem = service.findById(pedidoItemId).orElse(null);
        if (pedidoItem != null) {
            if (notaRecepcionId != null) {
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

    public PedidoItem addPedidoItemToNotaRecepcion(Long notaRecepcionId, Long pedidoItemId) {
        NotaRecepcion notaRecepcion = notaRecepcionId != null ? notaRecepcionService.findById(notaRecepcionId).orElse(null) : null;
        PedidoItem pi = service.getRepository().findById(pedidoItemId).orElse(null);
        
        if (pi == null) {
            throw new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId);
        }
        
        try {
            if (notaRecepcion != null) {
                // Assigning to a nota recepcion
                pi.setNotaRecepcion(notaRecepcion);
                
                // **FIX ISSUE 2**: Only copy from Creacion fields if RecepcionNota fields are empty
                // This prevents overwriting existing RecepcionNota data
                if (pi.getPresentacionRecepcionNota() == null) {
                    pi.setPresentacionRecepcionNota(pi.getPresentacionCreacion());
                }
                if (pi.getCantidadRecepcionNota() == null) {
                    pi.setCantidadRecepcionNota(pi.getCantidadCreacion());
                }
                if (pi.getDescuentoUnitarioRecepcionNota() == null) {
                    pi.setDescuentoUnitarioRecepcionNota(pi.getDescuentoUnitarioCreacion());
                }
                if (pi.getVencimientoRecepcionNota() == null) {
                    pi.setVencimientoRecepcionNota(pi.getVencimientoCreacion());
                }
                if (pi.getPrecioUnitarioRecepcionNota() == null) {
                    pi.setPrecioUnitarioRecepcionNota(pi.getPrecioUnitarioCreacion());
                }
                
                // Initialize RecepcionNota specific fields only if not already set
                if (pi.getVerificadoRecepcionNota() == null) {
                    pi.setVerificadoRecepcionNota(true);
                }
                // Note: Other fields like usuarioRecepcionNota, obsRecepcionNota, etc. 
                // will be set when the item is actually modified during the recepcion nota step
                
                return service.save(pi);
            } else {
                // Unassigning from nota recepcion - ONLY clear the nota recepcion reference
                // Preserve all estado-related RecepcionNota data fields
                pi.setNotaRecepcion(null);
                return service.save(pi);
            }
        } catch (Exception e) {
            throw new GraphQLException("Error al asignar/desasignar item a nota de recepción: " + e.getMessage());
        }
    }
    
    /**
     * Clear all RecepcionNota-related fields from a PedidoItem
     * @param item PedidoItem to clear
     */
    private void clearPedidoItemRecepcionNotaData(PedidoItem item) {
        // Clear all RecepcionNota fields
        item.setPrecioUnitarioRecepcionNota(null);
        item.setDescuentoUnitarioRecepcionNota(null);
        item.setVencimientoRecepcionNota(null);
        item.setPresentacionRecepcionNota(null);
        item.setCantidadRecepcionNota(null);
        item.setUsuarioRecepcionNota(null);
        item.setObsRecepcionNota(null);
        item.setAutorizacionRecepcionNota(null);
        item.setAutorizadoPorRecepcionNota(null);
        item.setVerificadoRecepcionNota(false);
        item.setMotivoRechazoRecepcionNota(null);
        
        // Clear the nota recepcion reference
        item.setNotaRecepcion(null);
    }

    public PedidoItem verificarRecepcionProducto(Long pedidoItemId, Boolean verificar) {
        PedidoItem pi = service.findById(pedidoItemId).orElse(null);
        if (pi == null) throw new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId);
        
        if (verificar) {
            // Copy data from RecepcionNota fields to RecepcionProducto fields
            pi.setPresentacionRecepcionProducto(pi.getPresentacionRecepcionNota());
            pi.setCantidadRecepcionProducto(pi.getCantidadRecepcionNota());
            pi.setDescuentoUnitarioRecepcionProducto(pi.getDescuentoUnitarioRecepcionNota());
            pi.setVencimientoRecepcionProducto(pi.getVencimientoRecepcionNota());
            pi.setPrecioUnitarioRecepcionProducto(pi.getPrecioUnitarioRecepcionNota());
            pi.setVerificadoRecepcionProducto(true);
            return service.save(pi);
        } else {
            // Clear all RecepcionProducto fields
            clearPedidoItemRecepcionProductoData(pi);
            return service.save(pi);
        }
    }

    public Integer cantidadItensFaltaVerificarNota(Long id){
        return service.getRepository().countByPedidoIdAndVerificadoRecepcionNotaFalse(id);
    }

    public Integer cantidadItensFaltaVerificarProducto(Long id){
        return service.getRepository().countByPedidoIdAndVerificadoRecepcionProductoFalse(id);
    }

    public Page<PedidoItem> findHistoricoCompras(Long productoId, int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        return service.getRepository().findByProductoIdAndPedidoEstado(productoId, PedidoEstado.CONCLUIDO, pageable);
    }

    /**
     * Clear all RecepcionProducto-related fields from a PedidoItem
     * @param item PedidoItem to clear
     */
    private void clearPedidoItemRecepcionProductoData(PedidoItem item) {
        // Clear all RecepcionProducto fields
        item.setPrecioUnitarioRecepcionProducto(null);
        item.setDescuentoUnitarioRecepcionProducto(null);
        item.setVencimientoRecepcionProducto(null);
        item.setPresentacionRecepcionProducto(null);
        item.setCantidadRecepcionProducto(null);
        item.setUsuarioRecepcionProducto(null);
        item.setObsRecepcionProducto(null);
        item.setAutorizacionRecepcionProducto(null);
        item.setAutorizadoPorRecepcionProducto(null);
        item.setMotivoModificacionRecepcionProducto(null);
        item.setVerificadoRecepcionProducto(false);
        item.setMotivoRechazoRecepcionProducto(null);
    }
}
