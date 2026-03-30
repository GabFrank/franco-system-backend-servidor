package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.util.List;
import java.util.Optional;


@Component
public class PedidoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(PedidoItemGraphQL.class);

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

    // ===== BASIC CRUD OPERATIONS =====

    public Optional<PedidoItem> pedidoItem(Long id) {
        return service.findById(id);
    }

    public Long countPedidoItem() {
        return service.count();
    }

    public Boolean deletePedidoItem(Long id) {
        return service.deleteById(id);
    }

    // ===== QUERY OPERATIONS =====

    public Page<PedidoItem> pedidoItemPorPedidoPage(Long id, int page, int size, String texto, Boolean soloPendientes) {
        Pageable pageable = PageRequest.of(page, size);
        
        // Si soloPendientes es true, usar los métodos que filtran por cantidad pendiente > 0
        if (Boolean.TRUE.equals(soloPendientes)) {
            if (texto != null && !texto.trim().isEmpty()) {
                texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
                return service.findByPedidoIdConCantidadPendienteAndTexto(id, texto, pageable);
            } else {
                return service.findByPedidoIdConCantidadPendiente(id, pageable);
            }
        } else {
            // Comportamiento original: devolver todos los items
            if (texto != null && !texto.trim().isEmpty()) {
                texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
                return service.findByPedidoIdAndTexto(id, texto, pageable);
            } else {
                return service.findByPedidoId(id, pageable);
            }
        }
    }

    public List<PedidoItem> pedidoItemPorPedido(Long id) {
        return service.findByPedidoId(id);
    }

    // public Page<PedidoItem> findHistoricoCompras(Long productoId, int page, int size){
    //     Pageable pageable = PageRequest.of(page, size);
    //     return service.getRepository().findByProductoIdAndPedidoEstado(productoId, PedidoEstado.CONCLUIDO, pageable);
    // }

    // ===== SAVE OPERATIONS =====

    public PedidoItem savePedidoItem(PedidoItemInput input) {
        logger.info("=== Starting PedidoItem save operation ===");
        
        try {
            PedidoItem e = new PedidoItem();
            
            // Map only the fields that exist in the refactored PedidoItem
            if(input.getId() != null)
                e.setId(input.getId()); // id is ia nor null
            e.setObservacion(input.getObservacion());
            e.setEsBonificacion(input.getEsBonificacion());
            e.setEstado(input.getEstado());
            e.setVencimientoEsperado(stringToDate(input.getVencimientoEsperado()));
            e.setCantidadSolicitada(input.getCantidadSolicitada());
            e.setPrecioUnitarioSolicitado(input.getPrecioUnitarioSolicitado());
            
            // Map navigation properties
            if (input.getUsuarioCreacionId() != null)
                e.setUsuarioCreacion(usuarioService.findById(input.getUsuarioCreacionId()).orElse(null));
            
            if (input.getProductoId() != null) 
                e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
                
            if (input.getPedidoId() != null) 
                e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
                
            if (input.getPresentacionCreacionId() != null)
                e.setPresentacionCreacion(presentacionService.findById(input.getPresentacionCreacionId()).orElse(null));
                
            // Handle date field conversions
            if (input.getCreadoEn() != null) {
                e.setCreadoEn(stringToDate(input.getCreadoEn()));
            }
            
            logger.info("=== PedidoItem mapping completed successfully ===");
            
            PedidoItem savedItem = service.save(e);
            return savedItem;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during PedidoItem save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw ex;
        }
    }

    // ===== WORKFLOW OPERATIONS (SIMPLIFIED) =====
    // These methods are simplified stubs - full functionality moved to new entities

    public PedidoItem updateNotaRecepcion(Long pedidoItemId, Long notaRecepcionId) {
        // TODO: Implement using NotaRecepcionItem entity
        PedidoItem pedidoItem = service.findById(pedidoItemId).orElse(null);
        return pedidoItem;
    }

    public PedidoItem addPedidoItemToNotaRecepcion(Long notaRecepcionId, Long pedidoItemId) {
        // TODO: Implement using NotaRecepcionItem entity
        PedidoItem pi = service.getRepository().findById(pedidoItemId).orElse(null);
        if (pi == null) {
            throw new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId);
        }
        return pi;
    }

    public PedidoItem verificarRecepcionProducto(Long pedidoItemId, Boolean verificar) {
        // TODO: Implement using RecepcionMercaderiaItem entity
        PedidoItem pi = service.findById(pedidoItemId).orElse(null);
        if (pi == null) throw new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId);
        return pi;
    }

    public Integer cantidadItensFaltaVerificarNota(Long id){
        // TODO: Implement using NotaRecepcionItem entity
        return 0;
    }

    public Integer cantidadItensFaltaVerificarProducto(Long id){
        // TODO: Implement using RecepcionMercaderiaItem entity
        return 0;
    }
}
