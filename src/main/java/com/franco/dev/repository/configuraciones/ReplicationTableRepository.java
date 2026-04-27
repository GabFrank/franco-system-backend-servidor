package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuraciones.ReplicationTable;
import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplicationTableRepository extends JpaRepository<ReplicationTable, Long> {

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
    
    // Find with pagination and optional search
    @Query("SELECT r FROM ReplicationTable r WHERE " +
           "(:query IS NULL OR LOWER(r.tableName) LIKE %:query% OR LOWER(r.description) LIKE %:query%)")
    Page<ReplicationTable> findBySearch(String query, Pageable pageable);
} 