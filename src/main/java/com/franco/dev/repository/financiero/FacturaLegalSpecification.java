package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
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
            Boolean activo,
            Boolean sinNombre,
            Long ventaId) {

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
                predicates.add(
                        criteriaBuilder.like(criteriaBuilder.upper(root.get("ruc")), "%" + ruc.toUpperCase() + "%"));
            }

            if (nombre != null && !nombre.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.upper(root.get("nombre")),
                        "%" + nombre.toUpperCase() + "%"));
            }

            if (isElectronico != null) {
                if (isElectronico) {
                    // Filtrar facturas que tienen CDC (son electrónicas)
                    predicates.add(criteriaBuilder.isNotNull(root.get("cdc")));
                } else {
                    // Filtrar facturas que NO tienen CDC (no son electrónicas)
                    predicates.add(criteriaBuilder.isNull(root.get("cdc")));
                }
            }

            if (activo != null) {
                // Anulada = activo false o, en electronicas, documento CANCELADO en SIFEN: hay
                // caminos de cancelacion que no tocan la factura y activo queda en NULL. Es el mismo
                // criterio del resumen fiscal (FacturaLegalRepository.RESUMEN_FISCAL_SELECT).
                // NULL es activa: desde fines de 2025 las facturas nacieron sin el flag (filial #131).
                // Se compara contra NULL a mano: NOT (activo = false) descartaria las filas en NULL.
                Subquery<Long> deCancelado = query.subquery(Long.class);
                Root<DocumentoElectronico> de = deCancelado.from(DocumentoElectronico.class);
                deCancelado.select(de.get("id")).where(
                        criteriaBuilder.equal(de.get("facturaLegalId"), root.get("id")),
                        criteriaBuilder.equal(de.get("sucursalId"), root.get("sucursalId")),
                        criteriaBuilder.equal(de.get("estado"), EstadoDE.CANCELADO));
                if (activo) {
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("activo")),
                            criteriaBuilder.isTrue(root.get("activo"))));
                    predicates.add(criteriaBuilder.not(criteriaBuilder.exists(deCancelado)));
                } else {
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.isFalse(root.get("activo")),
                            criteriaBuilder.exists(deCancelado)));
                }
            }

            if (sinNombre != null) {
                if (sinNombre) {
                    predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("nombre")), "SIN NOMBRE"));
                } else {
                    predicates.add(criteriaBuilder.notEqual(criteriaBuilder.upper(root.get("nombre")), "SIN NOMBRE"));
                }
            }

            if (ventaId != null) {
                predicates.add(criteriaBuilder.equal(root.join("venta", JoinType.INNER).get("id"), ventaId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
