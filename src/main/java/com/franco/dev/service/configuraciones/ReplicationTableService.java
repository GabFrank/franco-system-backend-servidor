package com.franco.dev.service.configuraciones;

import com.franco.dev.domain.configuraciones.ReplicationTable;
import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuraciones.ReplicationTableRepository;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReplicationTableService {

    private final ReplicationTableRepository repository;
    private final UsuarioService usuarioService;
    
    public Optional<ReplicationTable> findById(Long id) {
        return repository.findById(id);
    }
    
    public Optional<ReplicationTable> findByTableName(String tableName) {
        return repository.findByTableName(tableName);
    }
    
    public List<ReplicationTable> findAll() {
        return repository.findAll();
    }
    
    public List<ReplicationTable> findAllEnabled() {
        return repository.findByEnabledTrue();
    }
    
    public List<ReplicationTable> findByDirection(ReplicationDirection direction) {
        return repository.findByDirectionAndEnabledTrue(direction);
    }
    
    public Page<ReplicationTable> findWithPagination(String query, Pageable pageable) {
        return repository.findBySearch(query, pageable);
    }
    
    public ReplicationTable save(ReplicationTable replicationTable, Long usuarioId) {
        if (replicationTable.getId() == null) {
            replicationTable.setCreadoEn(LocalDateTime.now());
            if (usuarioId != null) {
                Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
                replicationTable.setUsuario(usuario);
            }
        }
        return repository.save(replicationTable);
    }
    
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    public boolean toggleEnabled(Long id) {
        Optional<ReplicationTable> tableOptional = repository.findById(id);
        if (tableOptional.isPresent()) {
            ReplicationTable table = tableOptional.get();
            table.setEnabled(!table.getEnabled());
            repository.save(table);
            return true;
        }
        return false;
    }
    
    /**
     * Get tables for main to all branches replication
     */
    public List<String> getMainToAllTableNames() {
        return repository.findByDirectionAndEnabledTrue(ReplicationDirection.MAIN_TO_ALL)
                .stream()
                .map(ReplicationTable::getTableName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get tables for main to specific branches replication
     */
    public List<String> getMainToSpecificTableNames() {
        return repository.findByDirectionAndEnabledTrue(ReplicationDirection.MAIN_TO_SPECIFIC)
                .stream()
                .map(ReplicationTable::getTableName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get tables for branch to main replication
     */
    public List<String> getBranchToMainTableNames() {
        return repository.findByDirectionAndEnabledTrue(ReplicationDirection.BRANCH_TO_MAIN)
                .stream()
                .map(ReplicationTable::getTableName)
                .collect(Collectors.toList());
    }
    
    /**
     * Update the replication service to use these tables
     */
    public void updateReplicationServiceTables(LogicalReplicationService replicationService) {
        // Main to all and main to specific are both part of central master tables
        List<String> centralMasterTables = repository.findByDirectionAndEnabledTrue(ReplicationDirection.MAIN_TO_ALL)
                .stream()
                .map(ReplicationTable::getTableName)
                .collect(Collectors.toList());
        
        // Branch tables are used for branch to main replication
        List<String> branchTransactionTables = repository.findByDirectionAndEnabledTrue(ReplicationDirection.BRANCH_TO_MAIN)
                .stream()
                .map(ReplicationTable::getTableName)
                .collect(Collectors.toList());
        
        // Update the replication service with these tables
        replicationService.updateCentralMasterTables(centralMasterTables);
        replicationService.updateBranchTransactionTables(branchTransactionTables);
    }
} 