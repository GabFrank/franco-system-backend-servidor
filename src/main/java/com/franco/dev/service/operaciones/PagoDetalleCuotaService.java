package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import com.franco.dev.repository.operaciones.PagoDetalleCuotaRepository;
import com.franco.dev.repository.operaciones.specification.PagoDetalleCuotaSpecification;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class PagoDetalleCuotaService extends CrudService<PagoDetalleCuota, PagoDetalleCuotaRepository, Long> {
    private final PagoDetalleCuotaRepository repository;

    @Override
    public PagoDetalleCuotaRepository getRepository() {
        return repository;
    }

    @Override
    public PagoDetalleCuota save(PagoDetalleCuota entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            if (entity.getEstado() == null) {
                entity.setEstado(PagoDetalleCuotaEstado.PENDIENTE);
            }
            if (entity.getTotalPagado() == null) {
                entity.setTotalPagado(0.0);
            }
        }
        return super.save(entity);
    }
    
    public List<PagoDetalleCuota> findByPagoDetalleId(Long pagoDetalleId) {
        return repository.findByPagoDetalleId(pagoDetalleId);
    }
    
    public List<PagoDetalleCuota> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }
    
    public Page<PagoDetalleCuota> findByFiltro(
            String estadoStr, 
            Long sucursalId, 
            String fechaDesde, 
            String fechaHasta,
            Boolean filtrarPorCreacion,
            Integer page,
            Integer size) {
        
        // Convert string estado to enum if provided
        PagoDetalleCuotaEstado estado = null;
        if (estadoStr != null && !estadoStr.isEmpty()) {
            try {
                estado = PagoDetalleCuotaEstado.valueOf(estadoStr);
            } catch (IllegalArgumentException e) {
                // Handle invalid estado value
            }
        }
        
        LocalDateTime fechaDesdeDate = fechaDesde != null ? stringToDate(fechaDesde) : null;
        LocalDateTime fechaHastaDate = fechaHasta != null ? stringToDate(fechaHasta) : null;
        
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 10);
        
        Specification<PagoDetalleCuota> spec = PagoDetalleCuotaSpecification.withFilters(
                estado, 
                sucursalId, 
                fechaDesdeDate, 
                fechaHastaDate,
                filtrarPorCreacion != null ? filtrarPorCreacion : false
        );
        
        // For the direct sucursal_id case, we need to handle it separately:
        if (sucursalId != null) {
            // Get all PDC IDs that match the sucursalId via direct column
            List<Long> idsFromDirectSucursal = repository.findIdsBySucursalId(sucursalId);
            
            if (!idsFromDirectSucursal.isEmpty()) {
                // Create a predicate for these IDs
                Specification<PagoDetalleCuota> idSpec = (root, query, cb) -> 
                    root.get("id").in(idsFromDirectSucursal);
                
                // Combine with our existing specification (using OR since we want either condition)
                spec = spec.or(idSpec);
            }
        }
        
        return repository.findAll(spec, pageable);
    }
} 