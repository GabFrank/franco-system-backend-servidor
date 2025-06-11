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
import java.util.Objects;

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
        
        // Map navigation properties
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
        if (input.getNotaRecepcionId() != null)
            e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        if (input.getAutorizadoPorRecepcionNotaId() != null)
            e.setAutorizadoPorRecepcionNota(usuarioService.findById(input.getAutorizadoPorRecepcionNotaId()).orElse(null));
        if (input.getAutorizadoPorRecepcionProductoId() != null)
            e.setAutorizadoPorRecepcionProducto(usuarioService.findById(input.getAutorizadoPorRecepcionProductoId()).orElse(null));

        // Handle state-based data copying
        if (e.getPedido() != null) {
            handleStateBasedDataCopying(e, e.getPedido().getEstado());
        }

        return service.save(e);
    }

    /**
     * Handles automatic data copying between steps based on Pedido state
     * @param pedidoItem The PedidoItem being saved
     * @param pedidoEstado The current state of the Pedido
     */
    private void handleStateBasedDataCopying(PedidoItem pedidoItem, PedidoEstado pedidoEstado) {
        if (pedidoEstado == null) return;

        // If this is a new item (no ID), only allow creation data
        if (pedidoItem.getId() == null) {
            // For new items, ensure only creation fields are populated regardless of state
            clearRecepcionNotaFields(pedidoItem);
            clearRecepcionProductoFields(pedidoItem);
            return;
        }

        // Load existing item to check current state
        PedidoItem existingItem = service.findById(pedidoItem.getId()).orElse(null);
        if (existingItem == null) {
            // If existing item not found, treat as new item
            clearRecepcionNotaFields(pedidoItem);
            clearRecepcionProductoFields(pedidoItem);
            return;
        }

        // CRITICAL: Preserve the managed notaRecepcion reference to avoid transient instance issues
        pedidoItem.setNotaRecepcion(existingItem.getNotaRecepcion());

        // Also preserve other managed entity references to prevent transient instance issues
        // Only preserve if the current item doesn't have a different reference
        if (pedidoItem.getPedido() == null || 
            (existingItem.getPedido() != null && 
             Objects.equals(pedidoItem.getPedido().getId(), existingItem.getPedido().getId()))) {
            pedidoItem.setPedido(existingItem.getPedido());
        }
        
        if (pedidoItem.getProducto() == null || 
            (existingItem.getProducto() != null && 
             Objects.equals(pedidoItem.getProducto().getId(), existingItem.getProducto().getId()))) {
            pedidoItem.setProducto(existingItem.getProducto());
        }
        
        // Preserve user references if they match
        if (pedidoItem.getUsuarioCreacion() == null) {
            pedidoItem.setUsuarioCreacion(existingItem.getUsuarioCreacion());
        }
        
        // Preserve creation timestamp if not set
        if (pedidoItem.getCreadoEn() == null) {
            pedidoItem.setCreadoEn(existingItem.getCreadoEn());
        }

        switch (pedidoEstado) {
            case ABIERTO:
            case ACTIVO:
                // For ABIERTO and ACTIVO states, only allow modifications to creation fields
                // Copy creation data to other steps and clear modification-specific fields
                if (hasCreationData(pedidoItem)) {
                    copyCreationToRecepcionNota(existingItem, pedidoItem);
                    copyCreationToRecepcionProducto(existingItem, pedidoItem);
                }
                break;

            case EN_RECEPCION_NOTA:
                // For EN_RECEPCION_NOTA state, handle RecepcionNota modifications
                if (hasRecepcionNotaData(pedidoItem)) {
                    // If this is the first time setting RecepcionNota data, copy from Creation
                    if (!hasRecepcionNotaData(existingItem)) {
                        copyCreationToRecepcionNota(existingItem, pedidoItem);
                    }
                    
                    // Check for modifications and set appropriate flags
                    detectAndHandleRecepcionNotaModifications(existingItem, pedidoItem);
                    
                    // Copy RecepcionNota data to RecepcionProducto
                    copyRecepcionNotaToRecepcionProducto(existingItem, pedidoItem);
                }
                break;

            case EN_RECEPCION_MERCADERIA:
                // For EN_RECEPCION_MERCADERIA state, handle RecepcionProducto modifications
                if (hasRecepcionProductoData(pedidoItem)) {
                    // If this is the first time setting RecepcionProducto data, copy from previous step
                    if (!hasRecepcionProductoData(existingItem)) {
                        if (hasRecepcionNotaData(existingItem)) {
                            copyRecepcionNotaToRecepcionProducto(existingItem, pedidoItem);
                        } else {
                            copyCreationToRecepcionProducto(existingItem, pedidoItem);
                        }
                    }
                    
                    // Check for modifications and set appropriate flags
                    detectAndHandleRecepcionProductoModifications(existingItem, pedidoItem);
                }
                break;

            case CONCLUIDO:
                // For CONCLUIDO state, no modifications should be allowed
                // Restore all data from existing item
                copyAllFieldsFromExisting(existingItem, pedidoItem);
                break;

            default:
                // For other states, preserve existing behavior
                break;
        }
    }

    private boolean hasCreationData(PedidoItem item) {
        return item.getPresentacionCreacion() != null || 
               item.getCantidadCreacion() != null || 
               item.getPrecioUnitarioCreacion() != null;
    }

    private boolean hasRecepcionNotaData(PedidoItem item) {
        return item.getPresentacionRecepcionNota() != null || 
               item.getCantidadRecepcionNota() != null || 
               item.getPrecioUnitarioRecepcionNota() != null;
    }

    private boolean hasRecepcionProductoData(PedidoItem item) {
        return item.getPresentacionRecepcionProducto() != null || 
               item.getCantidadRecepcionProducto() != null || 
               item.getPrecioUnitarioRecepcionProducto() != null;
    }

    private void copyCreationToRecepcionNota(PedidoItem existing, PedidoItem current) {
        current.setPresentacionRecepcionNota(current.getPresentacionCreacion());
        current.setCantidadRecepcionNota(current.getCantidadCreacion());
        current.setPrecioUnitarioRecepcionNota(current.getPrecioUnitarioCreacion());
        current.setDescuentoUnitarioRecepcionNota(current.getDescuentoUnitarioCreacion());
        current.setVencimientoRecepcionNota(current.getVencimientoCreacion());
        
        // Preserve existing RecepcionNota-specific fields if they exist
        if (existing.getObsRecepcionNota() != null) {
            current.setObsRecepcionNota(existing.getObsRecepcionNota());
        }
        if (existing.getUsuarioRecepcionNota() != null) {
            current.setUsuarioRecepcionNota(existing.getUsuarioRecepcionNota());
        }
        if (existing.getMotivoModificacionRecepcionNota() != null) {
            current.setMotivoModificacionRecepcionNota(existing.getMotivoModificacionRecepcionNota());
        }
        
        // Preserve managed presentacion references to avoid transient instance issues
        preservePresentacionReferences(existing, current);
    }

    private void copyCreationToRecepcionProducto(PedidoItem existing, PedidoItem current) {
        current.setPresentacionRecepcionProducto(current.getPresentacionCreacion());
        current.setCantidadRecepcionProducto(current.getCantidadCreacion());
        current.setPrecioUnitarioRecepcionProducto(current.getPrecioUnitarioCreacion());
        current.setDescuentoUnitarioRecepcionProducto(current.getDescuentoUnitarioCreacion());
        current.setVencimientoRecepcionProducto(current.getVencimientoCreacion());
        
        // Preserve existing RecepcionProducto-specific fields if they exist
        if (existing.getObsRecepcionProducto() != null) {
            current.setObsRecepcionProducto(existing.getObsRecepcionProducto());
        }
        if (existing.getUsuarioRecepcionProducto() != null) {
            current.setUsuarioRecepcionProducto(existing.getUsuarioRecepcionProducto());
        }
        if (existing.getMotivoModificacionRecepcionProducto() != null) {
            current.setMotivoModificacionRecepcionProducto(existing.getMotivoModificacionRecepcionProducto());
        }
        
        // Preserve managed presentacion references to avoid transient instance issues
        preservePresentacionReferences(existing, current);
    }

    private void copyRecepcionNotaToRecepcionProducto(PedidoItem existing, PedidoItem current) {
        current.setPresentacionRecepcionProducto(current.getPresentacionRecepcionNota());
        current.setCantidadRecepcionProducto(current.getCantidadRecepcionNota());
        current.setPrecioUnitarioRecepcionProducto(current.getPrecioUnitarioRecepcionNota());
        current.setDescuentoUnitarioRecepcionProducto(current.getDescuentoUnitarioRecepcionNota());
        current.setVencimientoRecepcionProducto(current.getVencimientoRecepcionNota());
        
        // Preserve existing RecepcionProducto-specific fields if they exist
        if (existing.getObsRecepcionProducto() != null) {
            current.setObsRecepcionProducto(existing.getObsRecepcionProducto());
        }
        if (existing.getUsuarioRecepcionProducto() != null) {
            current.setUsuarioRecepcionProducto(existing.getUsuarioRecepcionProducto());
        }
        if (existing.getMotivoModificacionRecepcionProducto() != null) {
            current.setMotivoModificacionRecepcionProducto(existing.getMotivoModificacionRecepcionProducto());
        }
        
        // Preserve managed presentacion references to avoid transient instance issues
        preservePresentacionReferences(existing, current);
    }

    /**
     * Helper method to preserve managed Presentacion references to avoid transient instance issues
     */
    private void preservePresentacionReferences(PedidoItem existing, PedidoItem current) {
        // Preserve presentacionCreacion if IDs match
        if (current.getPresentacionCreacion() != null && existing.getPresentacionCreacion() != null &&
            Objects.equals(current.getPresentacionCreacion().getId(), existing.getPresentacionCreacion().getId())) {
            current.setPresentacionCreacion(existing.getPresentacionCreacion());
        }
        
        // Preserve presentacionRecepcionNota if IDs match
        if (current.getPresentacionRecepcionNota() != null && existing.getPresentacionRecepcionNota() != null &&
            Objects.equals(current.getPresentacionRecepcionNota().getId(), existing.getPresentacionRecepcionNota().getId())) {
            current.setPresentacionRecepcionNota(existing.getPresentacionRecepcionNota());
        }
        
        // Preserve presentacionRecepcionProducto if IDs match
        if (current.getPresentacionRecepcionProducto() != null && existing.getPresentacionRecepcionProducto() != null &&
            Objects.equals(current.getPresentacionRecepcionProducto().getId(), existing.getPresentacionRecepcionProducto().getId())) {
            current.setPresentacionRecepcionProducto(existing.getPresentacionRecepcionProducto());
        }
    }

    private void detectAndHandleRecepcionNotaModifications(PedidoItem existing, PedidoItem current) {
        boolean hasModifications = false;
        StringBuilder motivoModificacion = new StringBuilder();

        // Check for presentation modification
        if (!Objects.equals(
                existing.getPresentacionCreacion() != null ? existing.getPresentacionCreacion().getId() : null,
                current.getPresentacionRecepcionNota() != null ? current.getPresentacionRecepcionNota().getId() : null)) {
            hasModifications = true;
            motivoModificacion.append("PRESENTACION_INCORRECTA,");
        }

        // Check for quantity modification
        if (!Objects.equals(existing.getCantidadCreacion(), current.getCantidadRecepcionNota())) {
            hasModifications = true;
            motivoModificacion.append("CANTIDAD_INCORRECTA,");
        }

        // Check for price modification
        if (!Objects.equals(existing.getPrecioUnitarioCreacion(), current.getPrecioUnitarioRecepcionNota())) {
            hasModifications = true;
            motivoModificacion.append("PRECIO_INCORRECTO,");
        }

        // Check for discount modification
        if (!Objects.equals(existing.getDescuentoUnitarioCreacion(), current.getDescuentoUnitarioRecepcionNota())) {
            hasModifications = true;
            motivoModificacion.append("DESCUENTO_APLICADO,");
        }

        // Check for observations
        if (current.getObsRecepcionNota() != null && !current.getObsRecepcionNota().trim().isEmpty()) {
            hasModifications = true;
            motivoModificacion.append("OBSERVACIONES_ADICIONALES,");
        }

        if (hasModifications) {
            // Remove trailing comma
            String motivo = motivoModificacion.toString();
            if (motivo.endsWith(",")) {
                motivo = motivo.substring(0, motivo.length() - 1);
            }
            current.setMotivoModificacionRecepcionNota(motivo);
        }
    }

    private void detectAndHandleRecepcionProductoModifications(PedidoItem existing, PedidoItem current) {
        boolean hasModifications = false;
        StringBuilder motivoModificacion = new StringBuilder();

        // Compare against RecepcionNota data if it exists, otherwise against Creation data
        Long previousPresentacionId = existing.getPresentacionRecepcionNota() != null ? 
            existing.getPresentacionRecepcionNota().getId() : 
            (existing.getPresentacionCreacion() != null ? existing.getPresentacionCreacion().getId() : null);
        Double previousCantidad = existing.getCantidadRecepcionNota() != null ? 
            existing.getCantidadRecepcionNota() : existing.getCantidadCreacion();
        Double previousPrecio = existing.getPrecioUnitarioRecepcionNota() != null ? 
            existing.getPrecioUnitarioRecepcionNota() : existing.getPrecioUnitarioCreacion();
        Double previousDescuento = existing.getDescuentoUnitarioRecepcionNota() != null ? 
            existing.getDescuentoUnitarioRecepcionNota() : existing.getDescuentoUnitarioCreacion();

        // Check for presentation modification
        if (!Objects.equals(previousPresentacionId,
                current.getPresentacionRecepcionProducto() != null ? current.getPresentacionRecepcionProducto().getId() : null)) {
            hasModifications = true;
            motivoModificacion.append("PRESENTACION_INCORRECTA,");
        }

        // Check for quantity modification
        if (!Objects.equals(previousCantidad, current.getCantidadRecepcionProducto())) {
            hasModifications = true;
            motivoModificacion.append("CANTIDAD_INCORRECTA,");
        }

        // Check for price modification
        if (!Objects.equals(previousPrecio, current.getPrecioUnitarioRecepcionProducto())) {
            hasModifications = true;
            motivoModificacion.append("PRECIO_INCORRECTO,");
        }

        // Check for discount modification
        if (!Objects.equals(previousDescuento, current.getDescuentoUnitarioRecepcionProducto())) {
            hasModifications = true;
            motivoModificacion.append("DESCUENTO_APLICADO,");
        }

        // Check for observations
        if (current.getObsRecepcionProducto() != null && !current.getObsRecepcionProducto().trim().isEmpty()) {
            hasModifications = true;
            motivoModificacion.append("OBSERVACIONES_ADICIONALES,");
        }

        if (hasModifications) {
            // Remove trailing comma
            String motivo = motivoModificacion.toString();
            if (motivo.endsWith(",")) {
                motivo = motivo.substring(0, motivo.length() - 1);
            }
            current.setMotivoModificacionRecepcionProducto(motivo);
        }
    }

    private void clearRecepcionNotaFields(PedidoItem item) {
        item.setPresentacionRecepcionNota(null);
        item.setCantidadRecepcionNota(null);
        item.setPrecioUnitarioRecepcionNota(null);
        item.setDescuentoUnitarioRecepcionNota(null);
        item.setVencimientoRecepcionNota(null);
        item.setObsRecepcionNota(null);
        item.setUsuarioRecepcionNota(null);
        item.setMotivoModificacionRecepcionNota(null);
        item.setMotivoRechazoRecepcionNota(null);
        item.setAutorizacionRecepcionNota(null);
        item.setAutorizadoPorRecepcionNota(null);
        item.setVerificadoRecepcionNota(false);
    }

    private void clearRecepcionProductoFields(PedidoItem item) {
        item.setPresentacionRecepcionProducto(null);
        item.setCantidadRecepcionProducto(null);
        item.setPrecioUnitarioRecepcionProducto(null);
        item.setDescuentoUnitarioRecepcionProducto(null);
        item.setVencimientoRecepcionProducto(null);
        item.setObsRecepcionProducto(null);
        item.setUsuarioRecepcionProducto(null);
        item.setMotivoModificacionRecepcionProducto(null);
        item.setMotivoRechazoRecepcionProducto(null);
        item.setAutorizacionRecepcionProducto(null);
        item.setAutorizadoPorRecepcionProducto(null);
        item.setVerificadoRecepcionProducto(false);
    }

    private void copyAllFieldsFromExisting(PedidoItem existing, PedidoItem current) {
        // Copy all creation fields
        current.setPresentacionCreacion(existing.getPresentacionCreacion());
        current.setCantidadCreacion(existing.getCantidadCreacion());
        current.setPrecioUnitarioCreacion(existing.getPrecioUnitarioCreacion());
        current.setDescuentoUnitarioCreacion(existing.getDescuentoUnitarioCreacion());
        current.setVencimientoCreacion(existing.getVencimientoCreacion());
        current.setObsCreacion(existing.getObsCreacion());

        // Copy all RecepcionNota fields
        current.setPresentacionRecepcionNota(existing.getPresentacionRecepcionNota());
        current.setCantidadRecepcionNota(existing.getCantidadRecepcionNota());
        current.setPrecioUnitarioRecepcionNota(existing.getPrecioUnitarioRecepcionNota());
        current.setDescuentoUnitarioRecepcionNota(existing.getDescuentoUnitarioRecepcionNota());
        current.setVencimientoRecepcionNota(existing.getVencimientoRecepcionNota());
        current.setObsRecepcionNota(existing.getObsRecepcionNota());
        current.setUsuarioRecepcionNota(existing.getUsuarioRecepcionNota());
        current.setMotivoModificacionRecepcionNota(existing.getMotivoModificacionRecepcionNota());
        current.setMotivoRechazoRecepcionNota(existing.getMotivoRechazoRecepcionNota());

        // Copy all RecepcionProducto fields
        current.setPresentacionRecepcionProducto(existing.getPresentacionRecepcionProducto());
        current.setCantidadRecepcionProducto(existing.getCantidadRecepcionProducto());
        current.setPrecioUnitarioRecepcionProducto(existing.getPrecioUnitarioRecepcionProducto());
        current.setDescuentoUnitarioRecepcionProducto(existing.getDescuentoUnitarioRecepcionProducto());
        current.setVencimientoRecepcionProducto(existing.getVencimientoRecepcionProducto());
        current.setObsRecepcionProducto(existing.getObsRecepcionProducto());
        current.setUsuarioRecepcionProducto(existing.getUsuarioRecepcionProducto());
        current.setMotivoModificacionRecepcionProducto(existing.getMotivoModificacionRecepcionProducto());
        current.setMotivoRechazoRecepcionProducto(existing.getMotivoRechazoRecepcionProducto());
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
                // Assigning to a nota recepcion - copy data from Creacion fields
                pi.setNotaRecepcion(notaRecepcion);
                
                // Copy data from Creacion fields to RecepcionNota fields
                pi.setPresentacionRecepcionNota(pi.getPresentacionCreacion());
                pi.setCantidadRecepcionNota(pi.getCantidadCreacion());
                pi.setDescuentoUnitarioRecepcionNota(pi.getDescuentoUnitarioCreacion());
                pi.setVencimientoRecepcionNota(pi.getVencimientoCreacion());
                pi.setPrecioUnitarioRecepcionNota(pi.getPrecioUnitarioCreacion());
                
                // Initialize RecepcionNota specific fields
                pi.setVerificadoRecepcionNota(true);
                // Note: Other fields like usuarioRecepcionNota, obsRecepcionNota, etc. 
                // will be set when the item is actually modified during the recepcion nota step
                
                return service.save(pi);
            } else {
                // Unassigning from nota recepcion - clear ALL RecepcionNota fields
                clearPedidoItemRecepcionNotaData(pi);
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
        item.setMotivoModificacionRecepcionNota(null);
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
