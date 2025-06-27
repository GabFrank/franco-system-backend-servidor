package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoProjection;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.operaciones.NotaRecepcionAgrupadaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class NotaRecepcionAgrupadaService extends CrudService<NotaRecepcionAgrupada, NotaRecepcionAgrupadaRepository, Long> {

    private final NotaRecepcionAgrupadaRepository repository;
    
    @Autowired
    private SolicitudPagoService solicitudPagoService;
    
    @Autowired
    private PagoService pagoService;

    @Override
    public NotaRecepcionAgrupadaRepository getRepository() {
        return repository;
    }

    public Page<PedidoRecepcionProductoDto> findRecepcionProductoByNotaRecepcionAgrupada(Long id, PedidoRecepcionProductoEstado estado, Pageable page) {
        return repository.findRecepcionProductoByRecepcionByNotaAgrupada(id, estado != null ? estado.toString() : null, page);
    }

    public PedidoRecepcionProductoDto findRecepcionProductoByNotaRecepcionAgrupadaAndProducto(Long notaRecepcionAgrupadaId, Long productoId, PedidoRecepcionProductoEstado estado) {
        return repository.findRecepcionProductoByRecepcionByNotaAgrupadaAndProducto(notaRecepcionAgrupadaId, productoId, estado != null ? estado.toString() : null);
    }

    /**
     * Find all NotaRecepcionAgrupada for a specific pedido
     * More efficient than loading NotaRecepcion and filtering in Java
     */
    public List<NotaRecepcionAgrupada> findByPedidoId(Long pedidoId) {
        return repository.findByPedidoId(pedidoId);
    }

    /**
     * Find all NotaRecepcionAgrupada for a specific pedido with pagination
     * Used for solicitud-pago step to show grupos created for this specific pedido
     * Shows grupos regardless of SolicitudPago status
     */
    public Page<NotaRecepcionAgrupada> findGruposByPedidoIdPaginated(Long pedidoId, Pageable pageable) {
        return repository.findGruposByPedidoIdPaginated(pedidoId, pageable);
    }

    /**
     * Find available grupos for solicitud pago with business logic filtering
     * Only returns grupos that can receive new notas for payment request.
     * 
     * Complete filtering logic:
     * 1. Database level: Same proveedor + EN_RECEPCION estado only
     * 2. Service level: Exclude grupos with CONCLUIDO payments
     * 
     * Business rules applied:
     * ✅ Same proveedor.id (database)
     * ✅ Estado = 'EN_RECEPCION' (database)  
     * ✅ Payment not CONCLUIDO (service)
     * ✅ Allow empty grupos (cantNotas = 0) to receive notas
     * 
     * @param proveedorId ID of the proveedor
     * @param pageable Pagination parameters
     * @return Page of available NotaRecepcionAgrupada entities
     */
    public Page<NotaRecepcionAgrupada> findGruposDisponiblesParaSolicitudPago(Long proveedorId, Pageable pageable) {
        // First get all EN_RECEPCION grupos for the proveedor from database
        Page<NotaRecepcionAgrupada> allGrupos = repository.findGruposDisponiblesParaSolicitudPago(proveedorId, pageable);
        
        // Filter out grupos with concluded payments at service level
        List<NotaRecepcionAgrupada> filteredGrupos = allGrupos.getContent().stream()
            .filter(this::isGrupoAvailableForSolicitudPago)
            .collect(Collectors.toList());
        
        // Create new Page with filtered content but original pagination info
        return new PageImpl<>(filteredGrupos, pageable, allGrupos.getTotalElements());
    }
    
    /**
     * Check if a grupo is available for solicitud pago
     * A grupo is available if it doesn't have a concluded payment
     * 
     * VISIBILITY RULES:
     * ✅ No SolicitudPago = Available for creating SolicitudPago
     * ✅ SolicitudPago exists, no Pago = SolicitudPago created but not paid yet (VISIBLE)
     * ✅ SolicitudPago + Pago PENDIENTE/EN_PROCESO = Payment in progress (VISIBLE)
     * ❌ SolicitudPago + Pago CONCLUIDO = Fully paid (HIDDEN)
     */
    private boolean isGrupoAvailableForSolicitudPago(NotaRecepcionAgrupada grupo) {
        // Check if there's an existing solicitud pago for this grupo
        SolicitudPago solicitudPago = solicitudPagoService.findByTipoAndReferenciaId(
            TipoSolicitudPago.COMPRA, 
            grupo.getId()
        );
        
        // If no solicitud pago exists, grupo is available
        if (solicitudPago == null) {
            return true;
        }
        
        // Check if this SolicitudPago has a Pago assigned
        Pago pago = solicitudPago.getPago();
        
        // If no pago is assigned, grupo is still available/visible
        // This allows seeing grupos that have SolicitudPago but aren't paid yet
        if (pago == null) {
            return true;
        }
        
        // Only hide grupos that are FULLY PAID (Pago CONCLUIDO)
        // All other pago estados (PENDIENTE, EN_PROCESO, etc.) should remain visible
        PagoEstado pagoEstado = pago.getEstado();
        return pagoEstado != PagoEstado.CONCLUIDO;
    }

    @Override
    public NotaRecepcionAgrupada save(NotaRecepcionAgrupada entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        NotaRecepcionAgrupada e = super.save(entity);

        return e;
    }
}