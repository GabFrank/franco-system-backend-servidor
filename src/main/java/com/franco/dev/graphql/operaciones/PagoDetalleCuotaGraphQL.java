package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.graphql.operaciones.input.PagoDetalleCuotaInput;
import com.franco.dev.service.operaciones.PagoDetalleCuotaService;
import com.franco.dev.service.operaciones.PagoDetalleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.util.List;
import java.util.Optional;

@Component
public class PagoDetalleCuotaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PagoDetalleCuotaService service;

    @Autowired
    private PagoDetalleService pagoDetalleService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<PagoDetalleCuota> pagoDetalleCuota(Long id) {
        return service.findById(id);
    }

    public List<PagoDetalleCuota> pagoDetalleCuotas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }
    
    public List<PagoDetalleCuota> pagoDetalleCuotasPorPagoDetalleId(Long pagoDetalleId) {
        return service.findByPagoDetalleId(pagoDetalleId);
    }

    public PagoDetalleCuota savePagoDetalleCuota(PagoDetalleCuotaInput input) {
        ModelMapper m = new ModelMapper();
        
        // Fix ambiguity by adding a PropertyMap to skip the PagoDetalle total mapping
        m.addMappings(new PropertyMap<PagoDetalleCuotaInput, PagoDetalleCuota>() {
            @Override
            protected void configure() {
                skip(destination.getPagoDetalle().getTotal());
            }
        });
        
        PagoDetalleCuota e = m.map(input, PagoDetalleCuota.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getPagoDetalleId() != null) {
            e.setPagoDetalle(pagoDetalleService.findById(input.getPagoDetalleId()).orElse(null));
        }
        if(input.getFechaVencimiento() != null){
            e.setFechaVencimiento(stringToDate(input.getFechaVencimiento()));
        }
        if(input.getCreadoEn() != null){
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        }
        e = service.save(e);
        return e;
    }

    public List<PagoDetalleCuota> pagoDetalleCuotasSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deletePagoDetalleCuota(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countPagoDetalleCuota() {
        return service.count();
    }

    /**
     * Query to filter PagoDetalleCuota by multiple criteria
     * @param estado The estado to filter by (optional)
     * @param sucursalId The sucursal ID to filter by (optional)
     * @param fechaDesde The start date to filter by (optional)
     * @param fechaHasta The end date to filter by (optional)
     * @param filtrarPorCreacion Flag to indicate if filtering should be done by created date (true) or due date (false)
     * @param page The page number (default: 0)
     * @param size The page size (default: 10)
     * @return A page of PagoDetalleCuota that match the filter criteria
     */
    public Page<PagoDetalleCuota> getPagoDetalleCuotasFiltrado(
            String estado,
            Long sucursalId,
            String fechaDesde,
            String fechaHasta,
            Boolean filtrarPorCreacion,
            Integer page,
            Integer size) {
        
        return service.findByFiltro(
                estado,
                sucursalId,
                fechaDesde,
                fechaHasta,
                filtrarPorCreacion,
                page,
                size
        );
    }
} 