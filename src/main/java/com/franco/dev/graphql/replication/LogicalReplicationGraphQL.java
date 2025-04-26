package com.franco.dev.graphql.replication;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.replication.input.LogicalReplicationInput;
import com.franco.dev.graphql.replication.types.LogicalReplication;
import com.franco.dev.graphql.replication.types.ReplicationStatus;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LogicalReplicationGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LogicalReplicationService replicationService;
    
    @Autowired
    private SucursalService sucursalService;
    
    // Paginated Query resolvers
    
    /**
     * Get subscriptions with pagination
     */
    public Page<LogicalReplication> replicationSubscriptions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> subscriptionsPage = replicationService.listSubscriptionsWithPagination(pageable, null);
        
        List<LogicalReplication> content = subscriptionsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, subscriptionsPage.getTotalElements());
    }
    
    /**
     * Get publications with pagination
     */
    public Page<LogicalReplication> replicationPublications(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> publicationsPage = replicationService.listPublicationsWithPagination(pageable);
        
        List<LogicalReplication> content = publicationsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, publicationsPage.getTotalElements());
    }
    
    /**
     * Search subscriptions with pagination by name
     */
    public Page<LogicalReplication> searchReplicationSubscriptions(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> subscriptionsPage = replicationService.listSubscriptionsWithPagination(pageable, query);
        
        List<LogicalReplication> content = subscriptionsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, subscriptionsPage.getTotalElements());
    }
    
    // Non-paginated Query resolvers
    
    /**
     * Get all subscriptions without pagination
     */
    public List<LogicalReplication> allReplicationSubscriptions() {
        List<Map<String, Object>> subscriptions = replicationService.listSubscriptions();
        return subscriptions.stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all publications without pagination
     */
    public List<LogicalReplication> allReplicationPublications() {
        List<Map<String, Object>> publications = replicationService.listPublications();
        return publications.stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
    }
    
    /**
     * Get subscriptions from a remote branch with pagination
     */
    public Page<LogicalReplication> remoteReplicationSubscriptions(String sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> subscriptionsPage = replicationService.listRemoteSubscriptionsWithPagination(
            Long.parseLong(sucursalId), pageable, null);
        
        List<LogicalReplication> content = subscriptionsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, subscriptionsPage.getTotalElements());
    }
    
    /**
     * Get publications from a remote branch with pagination
     */
    public Page<LogicalReplication> remoteReplicationPublications(String sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> publicationsPage = replicationService.listRemotePublicationsWithPagination(
            Long.parseLong(sucursalId), pageable);
        
        List<LogicalReplication> content = publicationsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, publicationsPage.getTotalElements());
    }
    
    /**
     * Search for subscriptions by name from a remote branch with pagination
     */
    public Page<LogicalReplication> searchRemoteReplicationSubscriptions(
            String sucursalId, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> subscriptionsPage = replicationService.listRemoteSubscriptionsWithPagination(
            Long.parseLong(sucursalId), pageable, query);
        
        List<LogicalReplication> content = subscriptionsPage.getContent().stream()
            .map(this::mapToLogicalReplication)
            .collect(Collectors.toList());
            
        return new PageImpl<>(content, pageable, subscriptionsPage.getTotalElements());
    }
    
    // Helper method to map database results to LogicalReplication objects
    private LogicalReplication mapToLogicalReplication(Map<String, Object> item) {
        LogicalReplication lr = new LogicalReplication();
        
        // Determine if it's a subscription or publication
        if (item.containsKey("subname")) {
            // It's a subscription
            lr.setName((String) item.get("subname"));
            lr.setEnabled((Boolean) item.get("subenabled"));
        } else if (item.containsKey("pubname")) {
            // It's a publication
            lr.setName((String) item.get("pubname"));
            lr.setEnabled(true); // Publications are always enabled
        }
        
        // Extract sucursal ID from name
        String name = lr.getName();
        if (name != null) {
            Long sucursalId = extractSucursalIdFromName(name);
            if (sucursalId != null) {
                lr.setSucursal(sucursalService.findById(sucursalId).orElse(null));
            }
        }
        
        // Extract tables if available (for publications)
        Object tables = item.get("tables");
        if (tables instanceof String[]) {
            String[] tablesArray = (String[]) tables;
            List<String> tablesList = new ArrayList<>(Arrays.asList(tablesArray));
            lr.setTables(tablesList);
        }
        
        return lr;
    }
    
    // Helper method to extract sucursal ID from name
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
    
    // Mutation resolvers
    
    public ReplicationStatus setupReplication(LogicalReplicationInput input) {
        ReplicationStatus status = new ReplicationStatus();
        Long sucursalId = Long.parseLong(input.getSucursalId());
        
        try {
            boolean success = replicationService.setupCentralServerReplication(sucursalId);
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Logical replication successfully set up for branch " + sucursalId);
            } else {
                status.setMessage("Failed to set up logical replication for branch " + sucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    public ReplicationStatus setupBranchReplication(String sucursalId) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.setupBranchReplication(Long.parseLong(sucursalId));
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Branch replication successfully set up");
            } else {
                status.setMessage("Failed to set up branch replication");
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    public ReplicationStatus removeReplication(String sucursalId) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.removeCentralServerReplication(Long.parseLong(sucursalId));
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Logical replication successfully removed for branch " + sucursalId);
            } else {
                status.setMessage("Failed to remove logical replication for branch " + sucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    public ReplicationStatus removeBranchReplication(String sucursalId) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.removeBranchReplication(Long.parseLong(sucursalId));
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Branch replication successfully removed");
            } else {
                status.setMessage("Failed to remove branch replication");
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    public ReplicationStatus toggleReplication(String sucursalId, boolean enabled, boolean isSubscription) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success;
            
            if (isSubscription) {
                // Toggle subscription
                String subscriptionName = "filial" + sucursalId + "_sub";
                success = replicationService.alterSubscription(subscriptionName, enabled);
                
                if (success) {
                    status.setMessage("Logical replication subscription successfully " + 
                            (enabled ? "enabled" : "disabled") + " for branch " + sucursalId);
                } else {
                    status.setMessage("Failed to " + (enabled ? "enable" : "disable") + 
                            " logical replication subscription for branch " + sucursalId);
                }
            } else {
                // Toggle publication - publications cannot be disabled directly in PostgreSQL,
                // but we can implement custom logic here (like dropping and recreating)
                if (enabled) {
                    // Enable publication (recreate if it doesn't exist)
                    success = replicationService.createCentralToBranchPublication(Long.parseLong(sucursalId));
                    
                    if (success) {
                        status.setMessage("Logical replication publication successfully created for branch " + sucursalId);
                    } else {
                        status.setMessage("Failed to create logical replication publication for branch " + sucursalId);
                    }
                } else {
                    // Disable publication (drop it)
                    String publicationName = "central_filial" + sucursalId + "_pub";
                    success = replicationService.dropPublication(publicationName);
                    
                    if (success) {
                        status.setMessage("Logical replication publication successfully dropped for branch " + sucursalId);
                    } else {
                        status.setMessage("Failed to drop logical replication publication for branch " + sucursalId);
                    }
                }
            }
            
            status.setSuccess(success);
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Toggle a subscription on a remote branch
     */
    public ReplicationStatus toggleRemoteSubscription(
            String branchSucursalId, String subscriptionName, boolean enabled) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.toggleRemoteSubscription(
                Long.parseLong(branchSucursalId), subscriptionName, enabled);
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote subscription " + subscriptionName + " successfully " +
                    (enabled ? "enabled" : "disabled") + " on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to " + (enabled ? "enable" : "disable") +
                    " remote subscription " + subscriptionName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Drop a publication on a remote branch
     */
    public ReplicationStatus dropRemotePublication(String branchSucursalId, String publicationName) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.dropRemotePublication(
                Long.parseLong(branchSucursalId), publicationName);
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote publication " + publicationName + 
                    " successfully dropped on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to drop remote publication " + 
                    publicationName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Create a publication on a remote branch
     */
    public ReplicationStatus createRemotePublication(String branchSucursalId, String publicationName, List<String> tables) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.createRemotePublication(
                Long.parseLong(branchSucursalId), publicationName, tables);
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote publication " + publicationName + 
                    " successfully created on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to create remote publication " + 
                    publicationName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Create a remote subscription
     */
    public ReplicationStatus createRemoteSubscription(
            String branchSucursalId, String subscriptionName, 
            String connectionString, String publicationName) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.createRemoteSubscription(
                Long.parseLong(branchSucursalId), 
                subscriptionName, 
                connectionString, 
                publicationName
            );
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote subscription " + subscriptionName + 
                    " successfully created on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to create remote subscription " + 
                    subscriptionName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Edit a remote publication
     */
    public ReplicationStatus editRemotePublication(
            String branchSucursalId, String publicationName, List<String> tables) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.editRemotePublication(
                Long.parseLong(branchSucursalId), 
                publicationName, 
                tables
            );
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote publication " + publicationName + 
                    " successfully updated on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to update remote publication " + 
                    publicationName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Edit a remote subscription
     */
    public ReplicationStatus editRemoteSubscription(
            String branchSucursalId, String subscriptionName, 
            String connectionString, String publicationName) {
        ReplicationStatus status = new ReplicationStatus();
        
        try {
            boolean success = replicationService.editRemoteSubscription(
                Long.parseLong(branchSucursalId), 
                subscriptionName, 
                connectionString, 
                publicationName
            );
            
            status.setSuccess(success);
            if (success) {
                status.setMessage("Remote subscription " + subscriptionName + 
                    " successfully updated on branch " + branchSucursalId);
            } else {
                status.setMessage("Failed to update remote subscription " + 
                    subscriptionName + " on branch " + branchSucursalId);
            }
        } catch (Exception e) {
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Generate a remote connection string for creating subscriptions
     */
    public String generateConnectionString(String host, int port, String dbName, String username, String password) {
        return replicationService.createConnectionString(host, port, dbName, username, password);
    }
    
    /**
     * Generate a connection string for the central server
     */
    public String generateCentralConnectionString() {
        return replicationService.createCentralConnectionString();
    }
    
    /**
     * Generate a standard branch publication name
     */
    public String generateBranchPublicationName(String sucursalId) {
        return replicationService.generateBranchPublicationName(Long.parseLong(sucursalId));
    }
    
    /**
     * Generate a standard branch subscription name
     */
    public String generateBranchSubscriptionName(String sucursalId) {
        return replicationService.generateBranchSubscriptionName(Long.parseLong(sucursalId));
    }
    
    /**
     * Generate a standard central-to-branch publication name
     */
    public String generateCentralToBranchPublicationName(String sucursalId) {
        return replicationService.generateCentralToBranchPublicationName(Long.parseLong(sucursalId));
    }
    
    /**
     * Generate a standard central-to-branch subscription name
     */
    public String generateCentralToBranchSubscriptionName(String sucursalId) {
        return replicationService.generateCentralToBranchSubscriptionName(Long.parseLong(sucursalId));
    }
} 