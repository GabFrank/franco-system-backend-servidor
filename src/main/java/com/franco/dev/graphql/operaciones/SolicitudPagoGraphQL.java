package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionAgrupadaEstado;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoInput;
import com.franco.dev.service.operaciones.NotaRecepcionAgrupadaService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SolicitudPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SolicitudPagoService service;

    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private PedidoService pedidoService;
    
    @Autowired
    private NotaRecepcionAgrupadaService notaRecepcionAgrupadaService;

    public List<SolicitudPago> solicitudPagoPorUsuarioId(Long id){
        return service.getRepository().findByUsuarioId(id);
    }

    public SolicitudPago solicitudPago(Long id){
        return service.findById(id).orElse(null);
    }
    
    /**
     * Query to filter SolicitudPago by multiple criteria
     * @param solicitudPagoId The ID of the solicitud pago to filter by (optional)
     * @param referenciaId The reference ID to filter by (optional)
     * @param tipo The tipo to filter by (optional)
     * @param estado The estado to filter by (optional)
     * @param fechaInicio The start date to filter by (optional)
     * @param fechaFin The end date to filter by (optional)
     * @param page The page number (default: 0)
     * @param size The page size (default: 20)
     * @return A page of SolicitudPago that match the filter criteria
     */
    public Page<SolicitudPago> solicitudPagoConFiltros(
            Long solicitudPagoId,
            Long referenciaId,
            TipoSolicitudPago tipo,
            SolicitudPagoEstado estado,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {
        
        return service.findAllWithFilters(
                solicitudPagoId,
                referenciaId,
                tipo,
                estado,
                fechaInicio,
                fechaFin,
                page,
                size
        );
    }

    public SolicitudPago saveSolicitudPago(SolicitudPagoInput input) {
        ModelMapper m = new ModelMapper();
        SolicitudPago e = m.map(input, SolicitudPago.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getCreadoEn() != null){
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        }
        SolicitudPago solicitudPago = service.save(e);
        return solicitudPago;
    }

    /**
     * UPDATED: Finalize solicitud pago step - create ONE SolicitudPago for EACH grupo in this pedido
     * This provides more granular control for financial team
     */
    @Transactional
    public List<SolicitudPago> finalizarSolicitudPagoStep(Long pedidoId) {
        try {
            System.out.println("=== FINALIZE SOLICITUD PAGO STEP ===");
            System.out.println("Processing pedido ID: " + pedidoId);
            
            Pedido pedido = pedidoService.findById(pedidoId).orElse(null);
            if (pedido == null) {
                System.err.println("ERROR: Pedido not found: " + pedidoId);
                throw new GraphQLException("Pedido not found: " + pedidoId);
            }
            
            System.out.println("Found pedido: " + pedido.getId() + " in estado: " + pedido.getEstado());

            // Get all groups for this pedido - EFFICIENT: Direct database query
            List<NotaRecepcionAgrupada> grupos = notaRecepcionAgrupadaService.findByPedidoId(pedidoId);

            if (grupos.isEmpty()) {
                System.err.println("ERROR: No hay grupos para procesar en el pedido: " + pedidoId);
                throw new GraphQLException("No hay grupos para procesar en el pedido: " + pedidoId);
            }
            
            System.out.println("Found " + grupos.size() + " grupos to process");

            // NEW: Create ONE SolicitudPago for EACH grupo
            List<SolicitudPago> solicitudesCreadas = new ArrayList<>();
            
            for (NotaRecepcionAgrupada grupo : grupos) {
                SolicitudPago solicitudPago = new SolicitudPago();
                solicitudPago.setUsuario(pedido.getUsuario());
                solicitudPago.setCreadoEn(LocalDateTime.now());
                solicitudPago.setEstado(SolicitudPagoEstado.PENDIENTE);
                solicitudPago.setTipo(TipoSolicitudPago.COMPRA);
                solicitudPago.setReferenciaId(grupo.getId()); // Reference the grupo, not pedido
                
                solicitudPago = service.save(solicitudPago);
                solicitudesCreadas.add(solicitudPago);
                
                System.out.println("Created SolicitudPago: " + solicitudPago.getId() + " for grupo: " + grupo.getId());
                
                // Update grupo to CONCLUIDO
                grupo.setEstado(NotaRecepcionAgrupadaEstado.CONCLUIDO);
                notaRecepcionAgrupadaService.save(grupo);
                System.out.println("Updated grupo " + grupo.getId() + " to CONCLUIDO");
            }

            // Update pedido to CONCLUIDO
            pedido.setEstado(PedidoEstado.CONCLUIDO);
            pedido.setFechaFinSolicitudPago(LocalDateTime.now());
            pedido.setProgresoSolicitudPago(100);
            pedidoService.save(pedido);
            System.out.println("Updated pedido " + pedido.getId() + " to CONCLUIDO");

            System.out.println("=== FINALIZATION SUCCESSFUL - Created " + solicitudesCreadas.size() + " SolicitudPago ===");
            return solicitudesCreadas;

        } catch (Exception e) {
            System.err.println("Error in finalization for pedido " + pedidoId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al crear las solicitudes de pago: " + e.getMessage());
        }
    }
}

