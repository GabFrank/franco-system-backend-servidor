package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuraciones.ReplicationTable;
import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReplicationTableRepository extends JpaRepository<ReplicationTable, Long>,
        JpaSpecificationExecutor<ReplicationTable> {

    // Find by table name
    Optional<ReplicationTable> findByTableName(String tableName);
    
    // Find all enabled tables
    List<ReplicationTable> findByEnabledTrue();
    
    // Find by direction and enabled
    List<ReplicationTable> findByDirectionAndEnabledTrue(ReplicationDirection direction);
    
    // BRANCH_TO_MAIN with flag true (tables that go in central_filialX_pub for all branches)
    List<ReplicationTable> findByDirectionAndReplicateCentralToBranchWithFilterTrueAndEnabledTrue(ReplicationDirection direction);
    
    /** Tables for central_filialX_pub for a given branch: BRANCH_TO_MAIN with flag + MAIN_TO_SPECIFIC with sucursalId in branch_ids */
    @Query(value = "SELECT * FROM configuraciones.replication_table r WHERE r.enabled = true " +
            "AND ((r.direction = 'BRANCH_TO_MAIN' AND r.replicate_central_to_branch_with_filter = true) " +
            "OR (r.direction = 'MAIN_TO_SPECIFIC' AND :sucursalId = ANY(r.branch_ids)))", nativeQuery = true)
    List<ReplicationTable> findTablesForCentralToBranchPublication(@Param("sucursalId") Long sucursalId);
    
    /**
     * Pagina con filtros opcionales y orden fijo por nombre (#772). Cada filtro entra solo si tiene valor:
     * un `:param IS NULL` con el enum o el booleano en null hace que PostgreSQL no sepa el tipo del parametro.
     */
    default Page<ReplicationTable> findBySearch(String query, ReplicationDirection direction, Boolean enabled,
                                                Pageable pageable) {
        Specification<ReplicationTable> spec = (root, cq, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (query != null) {
                String patron = "%" + query + "%";
                filtros.add(cb.or(cb.like(cb.lower(root.get("tableName")), patron),
                        cb.like(cb.lower(root.get("description")), patron)));
            }
            if (direction != null) filtros.add(cb.equal(root.get("direction"), direction));
            if (enabled != null) filtros.add(cb.equal(root.get("enabled"), enabled));
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("tableName").and(Sort.by("id")));
        return findAll(spec, ordenado);
    }
} 