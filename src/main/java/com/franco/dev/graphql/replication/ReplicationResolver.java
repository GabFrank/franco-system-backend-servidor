package com.franco.dev.graphql.replication;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.replication.types.LogicalReplication;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReplicationResolver implements GraphQLResolver<LogicalReplication> {

    @Autowired
    private SucursalService sucursalService;
    
    /**
     * Resolve sucursal field if it's null but name provides enough information
     */
    public Sucursal sucursal(LogicalReplication replication) {
        if (replication.getSucursal() != null) {
            return replication.getSucursal();
        }
        
        // If sucursal is null but we can extract ID from name, resolve it
        String name = replication.getName();
        if (name != null) {
            Long sucursalId = extractSucursalIdFromName(name);
            if (sucursalId != null) {
                return sucursalService.findById(sucursalId).orElse(null);
            }
        }
        
        return null;
    }
    
    /**
     * Extract sucursal ID from replication name
     */
    private Long extractSucursalIdFromName(String name) {
        try {
            if (name.startsWith("filial") && name.contains("_")) {
                // Format: filial<id>_pub or filial<id>_sub
                String idPart = name.substring(6, name.indexOf("_"));
                return Long.parseLong(idPart);
            } else if (name.startsWith("central_filial") && name.contains("_pub")) {
                // Format: central_filial<id>_pub
                String idPart = name.substring(14, name.indexOf("_pub"));
                return Long.parseLong(idPart);
            } else if (name.startsWith("central_filial") && name.contains("_sub")) {
                // Format: central_filial<id>_sub
                String idPart = name.substring(14, name.indexOf("_sub"));
                return Long.parseLong(idPart);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // Handle parsing error
        }
        return null;
    }
} 