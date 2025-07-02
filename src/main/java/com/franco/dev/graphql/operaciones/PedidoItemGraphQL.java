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
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
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

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private CompraItemService compraItemService;

    @Autowired
    private NotaPedidoService notaPedidoService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

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
        logger.info("=== Manual Mapping: Starting PedidoItem mapping ===");
        logger.info("Input vencimientoRecepcionProducto: {}", input.getVencimientoRecepcionProducto());
        
        try {
            // Manual mapping instead of ModelMapper to avoid date->boolean conversion issues
            PedidoItem e = new PedidoItem();
            
            // Map simple fields directly
            e.setId(input.getId());
            e.setPrecioUnitarioCreacion(input.getPrecioUnitarioCreacion());
            e.setDescuentoUnitarioCreacion(input.getDescuentoUnitarioCreacion());
            e.setBonificacion(input.getBonificacion());
            e.setBonificacionDetalle(input.getBonificacionDetalle());
            e.setObservacion(input.getObservacion());
            e.setFrio(input.getFrio());
            e.setEstado(input.getEstado());
            e.setCantidadCreacion(input.getCantidadCreacion());
            e.setPrecioUnitarioRecepcionNota(input.getPrecioUnitarioRecepcionNota());
            e.setDescuentoUnitarioRecepcionNota(input.getDescuentoUnitarioRecepcionNota());
            e.setCantidadRecepcionNota(input.getCantidadRecepcionNota());
            e.setPrecioUnitarioRecepcionProducto(input.getPrecioUnitarioRecepcionProducto());
            e.setDescuentoUnitarioRecepcionProducto(input.getDescuentoUnitarioRecepcionProducto());
            e.setCantidadRecepcionProducto(input.getCantidadRecepcionProducto());
            e.setObsCreacion(input.getObsCreacion());
            e.setObsRecepcionNota(input.getObsRecepcionNota());
            e.setObsRecepcionProducto(input.getObsRecepcionProducto());
            e.setAutorizacionRecepcionNota(input.getAutorizacionRecepcionNota());
            e.setAutorizacionRecepcionProducto(input.getAutorizacionRecepcionProducto());
            e.setMotivoModificacionRecepcionNota(input.getMotivoModificacionRecepcionNota());
            e.setMotivoModificacionRecepcionProducto(input.getMotivoModificacionRecepcionProducto());
            e.setCancelado(input.getCancelado());
            e.setVerificadoRecepcionNota(input.getVerificadoRecepcionNota());
            e.setVerificadoRecepcionProducto(input.getVerificadoRecepcionProducto());
            e.setMotivoRechazoRecepcionNota(input.getMotivoRechazoRecepcionNota());
            e.setMotivoRechazoRecepcionProducto(input.getMotivoRechazoRecepcionProducto());
            
            logger.info("Manual mapping completed successfully");
        
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
                
            // Manually handle date field conversions 
            logger.info("Starting manual date conversions...");
            if (input.getCreadoEn() != null) {
                logger.debug("Converting creadoEn: {}", input.getCreadoEn());
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
            }
            if (input.getVencimientoCreacion() != null) {
                logger.debug("Converting vencimientoCreacion: {}", input.getVencimientoCreacion());
            e.setVencimientoCreacion(stringToDate(input.getVencimientoCreacion()));
            }
            if (input.getVencimientoRecepcionNota() != null) {
                logger.debug("Converting vencimientoRecepcionNota: {}", input.getVencimientoRecepcionNota());
            e.setVencimientoRecepcionNota(stringToDate(input.getVencimientoRecepcionNota()));
            }
            if (input.getVencimientoRecepcionProducto() != null) {
                logger.debug("Converting vencimientoRecepcionProducto: {}", input.getVencimientoRecepcionProducto());
            e.setVencimientoRecepcionProducto(stringToDate(input.getVencimientoRecepcionProducto()));
            }
            logger.info("Manual date conversions completed");
            
        if (input.getNotaRecepcionId() != null){ 
            e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        } else {
            e.setNotaRecepcion(null);
        }
        if (input.getAutorizadoPorRecepcionNotaId() != null)
            e.setAutorizadoPorRecepcionNota(usuarioService.findById(input.getAutorizadoPorRecepcionNotaId()).orElse(null));
        if (input.getAutorizadoPorRecepcionProductoId() != null)
            e.setAutorizadoPorRecepcionProducto(usuarioService.findById(input.getAutorizadoPorRecepcionProductoId()).orElse(null));

            logger.info("=== Manual Mapping: PedidoItem mapping completed successfully ===");
            
            // **NEW: Check if verificadoRecepcionProducto changed and update PedidoItemSucursal accordingly**
            boolean wasVerifiedBefore = false;
            if (e.getId() != null) {
                // This is an update, check previous verification status
                PedidoItem existingItem = service.findById(e.getId()).orElse(null);
                if (existingItem != null) {
                    wasVerifiedBefore = existingItem.getVerificadoRecepcionProducto() != null && existingItem.getVerificadoRecepcionProducto();
                }
            }
            
            // Save the pedido item first
            PedidoItem savedItem = service.save(e);
            
            // Update PedidoItemSucursal if verification status changed
            boolean isVerifiedNow = savedItem.getVerificadoRecepcionProducto() != null && savedItem.getVerificadoRecepcionProducto();
            if (wasVerifiedBefore != isVerifiedNow) {
                logger.info("Verification status changed for PedidoItem ID: {}, from {} to {}", 
                    savedItem.getId(), wasVerifiedBefore, isVerifiedNow);
                updatePedidoItemSucursalCantidadRecibida(savedItem, isVerifiedNow);
            }
            
            return savedItem;
            
        } catch (Exception ex) {
            logger.error("=== Manual Mapping: ERROR during mapping ===");
            logger.error("Exception type: {}", ex.getClass().getSimpleName());
            logger.error("Exception message: {}", ex.getMessage());
            logger.error("Full exception: ", ex);
            throw ex; // Re-throw the exception
        }
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

    public Page<PedidoItem> pedidoItemPorNotaRecepcion(Long id, int page, int size, String texto, Boolean verificado, Long pedidoId) {
        Pageable pageable = PageRequest.of(page, size);
        
        // If nota recepcion ID is provided, filter by nota recepcion (existing behavior)
        if (id != null) {
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
        // If no nota recepcion ID but pedidoId is provided, return all items from that pedido
        else if (pedidoId != null) {
            if (texto != null) {
                texto = "%" + texto.replace(" ", "%").toUpperCase() + "%";
                // Use the available method from repository for pedido + texto filtering
                return service.getRepository().findByPedidoIdAndProductoDescripcionLikeOrderByProductoDescripcionDesc(pedidoId, texto, pageable);
            } else {
                // Use service method for pedido filtering
                return service.findByPedidoId(pedidoId, pageable);
            }
        }
        // If neither nota recepcion ID nor pedido ID is provided, return empty page
        else {
            return Page.empty(pageable);
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
            
            // **NEW: Update PedidoItemSucursal cantidadPorUnidadRecibida when verifying**
            updatePedidoItemSucursalCantidadRecibida(pi, true);
            
            return service.save(pi);
        } else {
            // Clear all RecepcionProducto fields
            clearPedidoItemRecepcionProductoData(pi);
            
            // **NEW: Clear PedidoItemSucursal cantidadPorUnidadRecibida when unverifying**
            updatePedidoItemSucursalCantidadRecibida(pi, false);
            
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

    /**
     * Update cantidadPorUnidadRecibida in all PedidoItemSucursal records related to this PedidoItem
     * @param pedidoItem The PedidoItem being verified/unverified
     * @param isVerifying true if verifying (set cantidadPorUnidadRecibida), false if unverifying (clear it)
     */
    private void updatePedidoItemSucursalCantidadRecibida(PedidoItem pedidoItem, boolean isVerifying) {
        try {
            // Get all PedidoItemSucursal records for this PedidoItem
            List<PedidoItemSucursal> sucursalDistributions = pedidoItemSucursalService.findByPedidoItemId(pedidoItem.getId());
            
            if (sucursalDistributions != null && !sucursalDistributions.isEmpty()) {
                for (PedidoItemSucursal sucursalDist : sucursalDistributions) {
                    if (isVerifying) {
                        // When verifying: set cantidadPorUnidadRecibida = cantidadPorUnidad
                        // This indicates that the expected quantity was actually received in this sucursal
                        sucursalDist.setCantidadPorUnidadRecibida(sucursalDist.getCantidadPorUnidad());
                    } else {
                        // When unverifying: clear cantidadPorUnidadRecibida (set to null or 0)
                        sucursalDist.setCantidadPorUnidadRecibida(null);
                    }
                    
                    // Save the updated PedidoItemSucursal
                    pedidoItemSucursalService.save(sucursalDist);
                }
                
                logger.info("Updated {} PedidoItemSucursal records for PedidoItem ID: {}, verified: {}", 
                    sucursalDistributions.size(), pedidoItem.getId(), isVerifying);
            }
        } catch (Exception e) {
            logger.error("Error updating PedidoItemSucursal cantidadPorUnidadRecibida for PedidoItem ID: {}", 
                pedidoItem.getId(), e);
            // Don't throw exception here to avoid breaking the main verification flow
            // The PedidoItem verification should still succeed even if PedidoItemSucursal update fails
        }
    }
}
