package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuraciones.ReplicationTable;
import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    
    // Find with pagination and optional search
    @Query("SELECT r FROM ReplicationTable r WHERE " +
           "(:query IS NULL OR LOWER(r.tableName) LIKE %:query% OR LOWER(r.description) LIKE %:query%)")
    Page<ReplicationTable> findBySearch(String query, Pageable pageable);
} 