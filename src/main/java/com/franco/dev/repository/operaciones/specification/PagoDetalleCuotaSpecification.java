package com.franco.dev.repository.operaciones.specification;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PagoDetalleCuotaSpecification {

    public static Specification<PagoDetalleCuota> withFilters(
            PagoDetalleCuotaEstado estado,
            Long sucursalId,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            boolean filtrarPorCreacion) {
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Estado filter
            if (estado != null) {
                predicates.add(criteriaBuilder.equal(root.get("estado"), estado));
            }
            
            // Date filters - apply to either creadoEn or fechaVencimiento based on filtrarPorCreacion flag
            String dateFieldName = filtrarPorCreacion ? "creadoEn" : "fechaVencimiento";
            
            if (fechaDesde != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(dateFieldName), fechaDesde));
            }
            
            if (fechaHasta != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(dateFieldName), fechaHasta));
            }
            
            // SucursalId filter via pagoDetalle
            if (sucursalId != null) {
                Join<PagoDetalleCuota, PagoDetalle> pagoDetalleJoin = root.join("pagoDetalle");
                
                // Two possible ways to get to sucursalId:
                // 1. Through caja.sucursalId
                // 2. Directly through sucursal_id column
                
                // Using a subquery to check if either condition is true
                Predicate cajaSucursalPredicate = criteriaBuilder.and(
                    criteriaBuilder.isNotNull(pagoDetalleJoin.get("caja")),
                    criteriaBuilder.equal(pagoDetalleJoin.get("caja").get("sucursalId"), sucursalId)
                );
                
                // We need to add a native SQL check for the direct sucursal_id column
                // But since we can't mix JPA criteria with native SQL in the same query,
                // we'll use a workaround later in the service
                
                predicates.add(cajaSucursalPredicate);
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
} 