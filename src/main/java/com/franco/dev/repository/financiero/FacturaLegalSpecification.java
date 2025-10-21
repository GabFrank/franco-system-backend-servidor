package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

public final class FacturaLegalSpecification {

    public static Specification<FacturaLegal> withFilters(
            String fechaInicio,
            String fechaFin,
            List<Long> sucId,
            String ruc,
            String nombre,
            Boolean isElectronico,
            Boolean activo) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (fechaInicio != null) {
                LocalDateTime inicio = stringToDate(fechaInicio);
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("creadoEn"), inicio));
            }

            if (fechaFin != null) {
                LocalDateTime fin = stringToDate(fechaFin);
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("creadoEn"), fin));
            }

            if (sucId != null && !sucId.isEmpty()) {
                predicates.add(root.get("sucursalId").in(sucId));
            }

            if (ruc != null && !ruc.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.upper(root.get("ruc")), "%" + ruc.toUpperCase() + "%"));
            }

            if (nombre != null && !nombre.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.upper(root.get("nombre")), "%" + nombre.toUpperCase() + "%"));
            }

            if (isElectronico != null) {
                predicates.add(criteriaBuilder.equal(root.join("timbradoDetalle", JoinType.LEFT).join("timbrado", JoinType.LEFT).get("isElectronico"), isElectronico));
            }

            if (activo != null) {
                predicates.add(criteriaBuilder.equal(root.get("activo"), activo));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
