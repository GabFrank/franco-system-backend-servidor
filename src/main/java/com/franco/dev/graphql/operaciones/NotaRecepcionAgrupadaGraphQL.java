package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionAgrupadaEstado;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Usuario;
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

// Unified result class for all grupo operations
class GrupoOperacionResult {
    private NotaRecepcionAgrupada grupo;
    private List<NotaRecepcion> notasAfectadas;
    private String mensaje;
    private Boolean success;

    public GrupoOperacionResult(NotaRecepcionAgrupada grupo, List<NotaRecepcion> notasAfectadas,
                               String mensaje, Boolean success) {
        this.grupo = grupo;
        this.notasAfectadas = notasAfectadas;
        this.mensaje = mensaje;
        this.success = success;
    }

    // Getters
    public NotaRecepcionAgrupada getGrupo() { return grupo; }
    public List<NotaRecepcion> getNotasAfectadas() { return notasAfectadas; }
    public String getMensaje() { return mensaje; }
    public Boolean getSuccess() { return success; }
}

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

    @Autowired
    private PedidoService pedidoService;

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

    /**
     * GraphQL Query: Get available grupos for solicitud pago with business logic filtering
     * Only returns grupos that can receive new notas for payment request
     * @param proveedorId ID of the proveedor
     * @param page Page number (0-based)
     * @param size Page size
     * @return Page of available NotaRecepcionAgrupada entities
     */
    public Page<NotaRecepcionAgrupada> getGruposDisponiblesParaSolicitudPago(Long proveedorId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findGruposDisponiblesParaSolicitudPago(proveedorId, pageable);
    }

    /**
     * GraphQL Query: Get all grupos created for a specific pedido
     * Shows grupos regardless of SolicitudPago status - for solicitud-pago step visualization
     * @param pedidoId ID of the pedido
     * @param page Page number (0-based)
     * @param size Page size
     * @return Page of NotaRecepcionAgrupada entities for this pedido
     */
    public Page<NotaRecepcionAgrupada> getGruposPorPedido(Long pedidoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findGruposByPedidoIdPaginated(pedidoId, pageable);
    }

    public NotaRecepcionAgrupada saveNotaRecepcionAgrupada(NotaRecepcionAgrupadaInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcionAgrupada e = m.map(input, NotaRecepcionAgrupada.class);
        if (input.getPedidoId() != null)
            e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
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
                            if(pis.getCantidadPorUnidad().equals(pis.getCantidadPorUnidadRecibida())){
                                pis.setVerificado(true);
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
            
            // Only process stock movements if sucursal is defined
            if (nra.getSucursal() != null) {
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

    public GrupoOperacionResult crearGrupoYAsignarNotas(Long proveedorId, Long sucursalId, List<Long> notaRecepcionIds, String descripcion) {
        try {
            // Get the first nota to determine the pedido
            if (notaRecepcionIds.isEmpty()) {
                throw new RuntimeException("Debe proporcionar al menos una nota para crear el grupo");
            }
            
            NotaRecepcion primeraNota = notaRecepcionService.findById(notaRecepcionIds.get(0)).orElse(null);
            if (primeraNota == null || primeraNota.getPedido() == null) {
                throw new RuntimeException("No se pudo determinar el pedido de las notas");
            }
            
            // Create new group
            NotaRecepcionAgrupada grupo = new NotaRecepcionAgrupada();
            grupo.setPedido(primeraNota.getPedido()); // Set pedido relationship
            grupo.setProveedor(proveedorService.findById(proveedorId).orElse(null));
            // Set sucursal only if sucursalId is provided
            if (sucursalId != null) {
                grupo.setSucursal(sucursalService.findById(sucursalId).orElse(null));
            } else {
                grupo.setSucursal(null);
            }
            grupo.setUsuario(usuarioService.findById(1L).orElse(null)); // TODO: Get current user from context
            grupo.setCreadoEn(LocalDateTime.now());
            grupo.setEstado(NotaRecepcionAgrupadaEstado.EN_RECEPCION);
            // cantNotas is a computed field in GraphQL, not a database field
            
            // Save the group
            grupo = service.save(grupo);
            
            // Assign notas and calculate total
            List<NotaRecepcion> notasAsignadas = new ArrayList<>();
            double valorTotal = 0.0;
            
            for (Long notaId : notaRecepcionIds) {
                NotaRecepcion nota = notaRecepcionService.findById(notaId).orElse(null);
                if (nota != null && nota.getNotaRecepcionAgrupada() == null) {
                    nota.setNotaRecepcionAgrupada(grupo);
                    nota = notaRecepcionService.save(nota);
                    notasAsignadas.add(nota);
                    
                    // Calculate value using the repository method (same as resolver)
                    Double notaValor = notaRecepcionService.getRepository().valor(nota.getId());
                    valorTotal += notaValor != null ? notaValor : 0.0;
                }
            }
            
            return new GrupoOperacionResult(
                grupo,
                notasAsignadas,
                "Grupo creado exitosamente",
                true
            );
            
        } catch (Exception e) {
            return new GrupoOperacionResult(
                null,
                new ArrayList<>(),
                "Error al crear grupo: " + e.getMessage(),
                false
            );
        }
    }

    public GrupoOperacionResult asignarNotasAGrupoExistente(Long grupoId, List<Long> notaRecepcionIds) {
        try {
            // Get existing group
            NotaRecepcionAgrupada grupo = service.findById(grupoId).orElse(null);
            if (grupo == null) {
                throw new RuntimeException("Grupo no encontrado: " + grupoId);
            }
            
            // Check if group can accept more notas
            if (grupo.getEstado() == NotaRecepcionAgrupadaEstado.CONCLUIDO) {
                throw new RuntimeException("El grupo ya está concluido y no puede recibir más notas");
            }
            
            // Assign notas
            List<NotaRecepcion> notasAsignadas = new ArrayList<>();
            
            for (Long notaId : notaRecepcionIds) {
                NotaRecepcion nota = notaRecepcionService.findById(notaId).orElse(null);
                if (nota != null && nota.getNotaRecepcionAgrupada() == null) {
                    nota.setNotaRecepcionAgrupada(grupo);
                    nota = notaRecepcionService.save(nota);
                    notasAsignadas.add(nota);
                }
            }
            
            return new GrupoOperacionResult(
                grupo,
                notasAsignadas,
                "Notas asignadas exitosamente",
                true
            );
            
        } catch (Exception e) {
            return new GrupoOperacionResult(
                null,
                new ArrayList<>(),
                "Error al asignar notas: " + e.getMessage(),
                false
            );
        }
    }

    public GrupoOperacionResult eliminarNotaRecepcionAgrupada(Long grupoId) {
        try {
            // Get the group to be deleted
            NotaRecepcionAgrupada grupo = service.findById(grupoId).orElse(null);
            if (grupo == null) {
                return new GrupoOperacionResult(
                    null,
                    new ArrayList<>(),
                    "Grupo no encontrado con ID: " + grupoId,
                    false
                );
            }

            // Business logic validation: Only allow deletion if group is not concluded
            if (grupo.getEstado() == NotaRecepcionAgrupadaEstado.CONCLUIDO) {
                return new GrupoOperacionResult(
                    null,
                    new ArrayList<>(),
                    "No se puede eliminar un grupo en estado CONCLUIDO",
                    false
                );
            }

            // Check if group has pending payments that prevent deletion
            SolicitudPago solicitudPago = solicitudPagoService.findByTipoAndReferenciaId(
                TipoSolicitudPago.COMPRA, 
                grupo.getId()
            );
            
            if (solicitudPago != null) {
                return new GrupoOperacionResult(
                    null,
                    new ArrayList<>(),
                    "No se puede eliminar un grupo que tiene solicitudes de pago asociadas",
                    false
                );
            }

            // Get all notas assigned to this group before deletion
            List<NotaRecepcion> notasAsignadas = notaRecepcionService.findByNotaRecepcionAgrupadaId(grupoId);
            
            // Create copies of the notas for the result (before they're modified)
            List<NotaRecepcion> notasLiberadas = new ArrayList<>();
            for (NotaRecepcion nota : notasAsignadas) {
                NotaRecepcion notaCopy = new NotaRecepcion();
                notaCopy.setId(nota.getId());
                notaCopy.setNumero(nota.getNumero());
                notaCopy.setFecha(nota.getFecha());
                notaCopy.setPedido(nota.getPedido());
                notasLiberadas.add(notaCopy);
            }

            // Unassign all notas from the group (set notaRecepcionAgrupada to null)
            for (NotaRecepcion nota : notasAsignadas) {
                nota.setNotaRecepcionAgrupada(null);
                notaRecepcionService.save(nota);
            }

            // Delete the group itself
            service.delete(grupo);

            return new GrupoOperacionResult(
                grupo,
                notasLiberadas,
                "Grupo eliminado exitosamente. " + notasLiberadas.size() + " notas liberadas.",
                true
            );

        } catch (Exception e) {
            return new GrupoOperacionResult(
                null,
                new ArrayList<>(),
                "Error al eliminar grupo: " + e.getMessage(),
                false
            );
        }
    }

    /**
     * GraphQL Mutation: Reject a product from NotaRecepcionAgrupada
     * When a product is rejected, it won't be received, won't enter stock, and won't be paid for
     * 
     * @param notaRecepcionAgrupadaId ID of the NotaRecepcionAgrupada
     * @param productoId ID of the product to reject
     * @param sucursalId ID of the sucursal where the rejection occurs
     * @param motivoRechazo Rejection reason from PedidoItemMotivoRechazo enum
     * @param usuarioId ID of the user performing the rejection
     * @return Boolean indicating if the rejection was successful
     */
    public Boolean rechazarProductoNotaRecepcionAgrupada(Long notaRecepcionAgrupadaId, Long productoId, Long sucursalId, String motivoRechazo, Long usuarioId) {
        if(notaRecepcionAgrupadaId > 0){
            return true;
        }
        try {
            // Validate input parameters
            if (notaRecepcionAgrupadaId == null || productoId == null || sucursalId == null || motivoRechazo == null || usuarioId == null) {
                throw new RuntimeException("Todos los parámetros son requeridos para rechazar un producto");
            }

            // Get the NotaRecepcionAgrupada
            NotaRecepcionAgrupada nra = service.findById(notaRecepcionAgrupadaId).orElse(null);
            if (nra == null) {
                throw new RuntimeException("NotaRecepcionAgrupada no encontrada con ID: " + notaRecepcionAgrupadaId);
            }

            // Get the user performing the rejection
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            if (usuario == null) {
                throw new RuntimeException("Usuario no encontrado con ID: " + usuarioId);
            }

            // Get all NotaRecepcion entities for this NotaRecepcionAgrupada
            List<NotaRecepcion> notas = notaRecepcionService.findByNotaRecepcionAgrupadaId(notaRecepcionAgrupadaId);
            
            boolean productFound = false;
            int itemsRejected = 0;
            
            for (NotaRecepcion nr : notas) {
                // Get all PedidoItem entities for each NotaRecepcion
                List<PedidoItem> pedidoItemList = pedidoItemService.findByNotaRecepcionId(nr.getId());
                
                for (PedidoItem pi : pedidoItemList) {
                    // Check if this PedidoItem corresponds to the product we want to reject
                    if (pi.getProducto() != null && pi.getProducto().getId().equals(productoId)) {
                        productFound = true;
                        
                        // Update PedidoItem with rejection information
                        pi.setMotivoRechazoRecepcionProducto(motivoRechazo);
                        pi.setUsuarioRecepcionProducto(usuario);
                        pi.setCancelado(true); // Mark as cancelled/rejected
                        
                        // Save the updated PedidoItem
                        pedidoItemService.save(pi);
                        
                        // Get all PedidoItemSucursal entities for this PedidoItem and sucursal
                        List<PedidoItemSucursal> pedidoItemSucursalList = pedidoItemSucursalService.getRepository()
                            .findByPedidoItemIdAndSucursalEntregaId(pi.getId(), sucursalId);
                        
                        for (PedidoItemSucursal pis : pedidoItemSucursalList) {
                            // Mark quantities as rejected (set received quantity to 0)
                            pis.setCantidadPorUnidadRecibida(0.0);
                            pis.setUsuario(usuario);
                            
                            // Save the updated PedidoItemSucursal
                            pedidoItemSucursalService.save(pis);
                            itemsRejected++;
                        }
                    }
                }
            }
            
            if (!productFound) {
                throw new RuntimeException("Producto con ID " + productoId + " no encontrado en la NotaRecepcionAgrupada");
            }
            
            if (itemsRejected == 0) {
                throw new RuntimeException("No se encontraron items para rechazar en la sucursal especificada");
            }
            
            return true;
            
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error rechazando producto: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al rechazar producto: " + e.getMessage());
        }
    }

    /**
     * GraphQL Query: Verify if all items are verified for a NotaRecepcionAgrupada
     * Checks if cantidadPorUnidad equals cantidadPorUnidadRecibida for all PedidoItemSucursal
     * that belong to this NotaRecepcionAgrupada and the specified sucursal
     * 
     * @param notaRecepcionAgrupadaId ID of the NotaRecepcionAgrupada
     * @param sucursalId ID of the sucursal to verify
     * @return Boolean indicating if all items are verified (all quantities match)
     */
    public Boolean verificarTodosItemsRecibidos(Long notaRecepcionAgrupadaId, Long sucursalId) {
        try {
            // Get the NotaRecepcionAgrupada
            NotaRecepcionAgrupada nra = service.findById(notaRecepcionAgrupadaId).orElse(null);
            if (nra == null) {
                throw new RuntimeException("NotaRecepcionAgrupada no encontrada con ID: " + notaRecepcionAgrupadaId);
            }

            // Get all NotaRecepcion entities for this NotaRecepcionAgrupada
            List<NotaRecepcion> notas = notaRecepcionService.findByNotaRecepcionAgrupadaId(notaRecepcionAgrupadaId);
            
            if (notas == null || notas.isEmpty()) {
                // No notes found, consider as verified (nothing to verify)
                return true;
            }
            
            for (NotaRecepcion nr : notas) {
                // Get all PedidoItem entities for each NotaRecepcion
                List<PedidoItem> pedidoItemList = pedidoItemService.findByNotaRecepcionId(nr.getId());
                
                if (pedidoItemList == null || pedidoItemList.isEmpty()) {
                    continue; // No items in this note, continue to next note
                }
                
                for (PedidoItem pi : pedidoItemList) {
                    // Get all PedidoItemSucursal entities for this PedidoItem and sucursal
                    List<PedidoItemSucursal> pedidoItemSucursalList = pedidoItemSucursalService.getRepository()
                        .findByPedidoItemIdAndSucursalEntregaId(pi.getId(), sucursalId);
                    
                    if (pedidoItemSucursalList == null || pedidoItemSucursalList.isEmpty()) {
                        continue; // No items for this sucursal, continue
                    }
                    
                    for (PedidoItemSucursal pis : pedidoItemSucursalList) {
                        Double cantidadEsperada = pis.getCantidadPorUnidad();
                        Double cantidadRecibida = pis.getCantidadPorUnidadRecibida();
                        
                        // Validate that both quantities exist
                        if (cantidadEsperada == null) {
                            // If expected quantity is null, consider as not verified
                            return false;
                        }
                        
                        if (cantidadRecibida == null) {
                            // If received quantity is null, definitely not verified
                            return false;
                        }
                        
                        // Compare quantities using Double.compare to handle floating point precision
                        if (Double.compare(cantidadEsperada, cantidadRecibida) != 0) {
                            // Quantities don't match, item is not fully verified
                            return false;
                        }
                        
                        // Additional validation: ensure received quantity is not negative
                        if (cantidadRecibida < 0) {
                            return false;
                        }
                    }
                }
            }
            
            // All items are verified - all expected quantities match received quantities
            return true;
            
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error verificando items recibidos: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al verificar items: " + e.getMessage());
        }
    }
}
