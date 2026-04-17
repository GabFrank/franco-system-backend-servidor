package com.franco.dev.graphql.replication;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.replication.types.LogicalReplication;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReplicationResolver implements GraphQLResolver<LogicalReplication> {

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private LogicalReplicationService replicationService;

    public Sucursal sucursal(LogicalReplication replication) {
        if (replication.getSucursal() != null) {
            return replication.getSucursal();
        }

        String name = replication.getName();
        if (name != null) {
            Long sucursalId = replicationService.extractSucursalIdFromName(name);
            if (sucursalId != null) {
                return sucursalService.findById(sucursalId).orElse(null);
            }
        }

        return null;
    }
} 