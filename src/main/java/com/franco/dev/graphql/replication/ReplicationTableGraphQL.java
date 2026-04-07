package com.franco.dev.graphql.replication;

import com.franco.dev.domain.configuraciones.ReplicationTable;
import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import com.franco.dev.graphql.replication.input.ReplicationTableInput;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.configuraciones.ReplicationTableService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ReplicationTableGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ReplicationTableService service;
    
    @Autowired
    private LogicalReplicationService replicationService;
    
    // Query resolvers
    
    public List<ReplicationTable> replicationTables() {
        return service.findAll();
    }
    
    public ReplicationTable replicationTable(Long id) {
        return service.findById(id).orElse(null);
    }
    
    public Page<ReplicationTable> replicationTablesWithPagination(int page, int size, String search) {
        return service.findWithPagination(search, PageRequest.of(page, size));
    }
    
    public List<ReplicationTable> replicationTablesByDirection(ReplicationDirection direction) {
        return service.findByDirection(direction);
    }
    
    public List<ReplicationTable> enabledReplicationTables() {
        return service.findAllEnabled();
    }
    
    public List<String> mainToAllTableNames() {
        return service.getMainToAllTableNames();
    }
    
    public List<String> mainToSpecificTableNames() {
        return service.getMainToSpecificTableNames();
    }
    
    public List<String> branchToMainTableNames() {
        return service.getBranchToMainTableNames();
    }
    
    // Mutation resolvers
    
    public ReplicationTable saveReplicationTable(ReplicationTableInput input) {
        ModelMapper m = new ModelMapper();
        ReplicationTable table = m.map(input, ReplicationTable.class);
        table.setBranchIdsList(input.getBranchIds());
        table.setReplicateCentralToBranchWithFilter(Boolean.TRUE.equals(input.getReplicateCentralToBranchWithFilter()));
        if (input.getId() != null) {
            Optional<ReplicationTable> existingTable = service.findById(input.getId());
            if (existingTable.isPresent()) {
                ReplicationTable existing = existingTable.get();
                table.setCreadoEn(existing.getCreadoEn());
                if (existing.getUsuario() != null && (input.getUsuarioId() == null || input.getUsuarioId().equals(existing.getUsuario().getId()))) {
                    table.setUsuario(existing.getUsuario());
                }
            }
        }
        if (table.getEnabled() == null) {
            table.setEnabled(true);
        }
        return service.save(table, input.getUsuarioId());
    }
    
    public boolean deleteReplicationTable(Long id) {
        try {
            service.delete(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean toggleReplicationTableEnabled(Long id) {
        return service.toggleEnabled(id);
    }
    
    public boolean updateReplicationServiceTables() {
        try {
            service.updateReplicationServiceTables(replicationService);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 