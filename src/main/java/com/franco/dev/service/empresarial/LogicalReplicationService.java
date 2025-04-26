package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogicalReplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final SucursalService sucursalService;
    
    private static final Logger logger = LoggerFactory.getLogger(LogicalReplicationService.class);
    
    @Value("${spring.datasource.username:franco}")
    private String dbUsername;
    
    @Value("${spring.datasource.password:franco}")
    private String dbPassword;
    
    @Value("${central.server.ip:172.25.1.200}")
    private String centralServerIp;
    
    @Value("${central.server.port:5551}")
    private String centralServerPort;
    
    @Value("${central.server.dbname:bodega}")
    private String centralDbName;
    
    // Central master data tables for replication
    private static final List<String> CENTRAL_MASTER_TABLES = Arrays.asList(
        "configuraciones.actualizacion",
        "configuraciones.local",
        "empresarial.cargo",
        "empresarial.configuracion_general",
        "empresarial.punto_de_venta",
        "empresarial.sector",
        "empresarial.sucursal",
        "empresarial.zona",
        "equipos.equipo",
        "equipos.tipo_equipo",
        "financiero.banco",
        "financiero.cambio",
        "financiero.cuenta_bancaria",
        "financiero.documento",
        "financiero.forma_pago",
        "financiero.moneda",
        "financiero.moneda_billetes",
        "financiero.timbrado",
        "financiero.timbrado_detalle",
        "financiero.tipo_gasto",
        "general.barrio",
        "general.ciudad",
        "general.contacto",
        "general.pais",
        "operaciones.precio_delivery",
        "personas.cliente",
        "personas.funcionario",
        "personas.grupo_role",
        "personas.persona",
        "personas.role",
        "personas.usuario",
        "personas.usuario_grupo",
        "personas.usuario_role",
        "productos.codigo",
        "productos.codigo_tipo_precio",
        "productos.costo_por_producto",
        "productos.familia",
        "productos.pdv_categoria",
        "productos.pdv_grupo",
        "productos.pdv_grupos_productos",
        "productos.precio_por_sucursal",
        "productos.presentacion",
        "productos.producto",
        "productos.producto_imagen",
        "productos.producto_por_sucursal",
        "productos.subfamilia",
        "productos.tipo_precio",
        "productos.tipo_presentacion",
        "vehiculos.marca",
        "vehiculos.modelo",
        "vehiculos.tipo_vehiculo",
        "vehiculos.vehiculo",
        "vehiculos.vehiculo_sucursal"
    );
    
    // Branch transaction tables for replication
    private static final List<String> BRANCH_TRANSACTION_TABLES = Arrays.asList(
        "administrativo.marcacion",
        "configuraciones.inicio_sesion",
        "financiero.cambio_caja",
        "financiero.conteo",
        "financiero.conteo_moneda",
        "financiero.factura_legal",
        "financiero.factura_legal_item",
        "financiero.gasto",
        "financiero.gasto_detalle",
        "financiero.maletin",
        "financiero.movimiento_caja",
        "financiero.movimiento_personas",
        "financiero.pdv_caja",
        "financiero.retiro",
        "financiero.retiro_detalle",
        "financiero.sencillo",
        "financiero.sencillo_detalle",
        "financiero.venta_credito",
        "financiero.venta_credito_cuota",
        "operaciones.cobro",
        "operaciones.cobro_detalle",
        "operaciones.delivery",
        "operaciones.movimiento_stock",
        "operaciones.stock_por_producto_sucursal",
        "operaciones.venta",
        "operaciones.venta_item",
        "operaciones.vuelto",
        "operaciones.vuelto_item"
    );
    
    // Add these methods to dynamically update table lists
    
    // The current central master tables list
    private List<String> dynamicCentralMasterTables = new ArrayList<>(CENTRAL_MASTER_TABLES);
    
    // The current branch transaction tables list
    private List<String> dynamicBranchTransactionTables = new ArrayList<>(BRANCH_TRANSACTION_TABLES);
    
    /**
     * Update the central master tables list
     * @param tables New list of tables
     */
    public void updateCentralMasterTables(List<String> tables) {
        if (tables != null && !tables.isEmpty()) {
            this.dynamicCentralMasterTables = new ArrayList<>(tables);
            logger.info("Updated central master tables list with " + tables.size() + " tables");
        }
    }
    
    /**
     * Update the branch transaction tables list
     * @param tables New list of tables
     */
    public void updateBranchTransactionTables(List<String> tables) {
        if (tables != null && !tables.isEmpty()) {
            this.dynamicBranchTransactionTables = new ArrayList<>(tables);
            logger.info("Updated branch transaction tables list with " + tables.size() + " tables");
        }
    }
    
    /**
     * Get the current central master tables
     * @return List of central master tables
     */
    public List<String> getCentralMasterTables() {
        return dynamicCentralMasterTables;
    }
    
    /**
     * Get the current branch transaction tables
     * @return List of branch transaction tables
     */
    public List<String> getBranchTransactionTables() {
        return dynamicBranchTransactionTables;
    }
    
    public LogicalReplicationService(JdbcTemplate jdbcTemplate, SucursalService sucursalService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sucursalService = sucursalService;
    }
    
    /**
     * Create a publication in the current database for all central master data
     * @return true if successful
     */
    public boolean createCentralPublication() {
        try {
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION central_pub FOR TABLE ");
            
            List<String> tables = getCentralMasterTables();
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(tables.get(i));
            }
            
            jdbcTemplate.execute(sql.toString());
            return true;
        } catch (Exception e) {
            logger.error("Error creating central publication: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Create a publication for a specific branch's transactions
     * @param sucursalId The branch ID
     * @return true if successful
     */
    public boolean createBranchPublication(Long sucursalId) {
        try {
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION filial" + sucursalId + "_pub FOR TABLE ");
            
            List<String> tables = getBranchTransactionTables();
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(tables.get(i));
            }
            
            jdbcTemplate.execute(sql.toString());
            return true;
        } catch (Exception e) {
            logger.error("Error creating branch publication: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Create a filtered publication for bidirectional replication from central to a branch
     * @param sucursalId The branch ID
     * @return true if successful
     */
    public boolean createCentralToBranchPublication(Long sucursalId) {
        try {
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION central_filial" + 
                    sucursalId + "_pub FOR TABLE ");
            
            List<String> tables = getBranchTransactionTables();
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(tables.get(i))
                   .append(" WHERE (sucursal_id = ").append(sucursalId).append(")");
            }
            
            jdbcTemplate.execute(sql.toString());
            return true;
        } catch (Exception e) {
            logger.error("Error creating central-to-branch publication: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Create a subscription from central to branch
     * @param sucursalId The branch ID
     * @param branchIp The branch IP
     * @param branchPort The branch port
     * @param branchDbName The branch database name
     * @return true if successful
     */
    public boolean createCentralToBranchSubscription(Long sucursalId, String branchIp, 
                                                  int branchPort, String branchDbName) {
        try {
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%d",
                branchDbName, branchIp, dbUsername, dbPassword, branchPort
            );
            
            String sql = "CREATE SUBSCRIPTION filial" + sucursalId + "_sub " +
                    "CONNECTION '" + connectionString + "' " +
                    "PUBLICATION filial" + sucursalId + "_pub WITH (copy_data = false, origin = 'none')";
            
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create a subscription from branch to central
     * @param sucursalId The branch ID
     * @return true if successful
     */
    public boolean createBranchToCentralSubscription(Long sucursalId) {
        try {
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%s",
                centralDbName, centralServerIp, dbUsername, dbPassword, centralServerPort
            );
            
            String sql = "CREATE SUBSCRIPTION filial" + sucursalId + "_central_sub " +
                    "CONNECTION '" + connectionString + "' " +
                    "PUBLICATION central_pub WITH (copy_data = false, origin = 'none')";
            
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create a bidirectional subscription from branch to central
     * @param sucursalId The branch ID
     * @return true if successful
     */
    public boolean createBidirectionalSubscription(Long sucursalId) {
        try {
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%s",
                centralDbName, centralServerIp, dbUsername, dbPassword, centralServerPort
            );
            
            String sql = "CREATE SUBSCRIPTION central_filial" + sucursalId + "_sub " +
                    "CONNECTION '" + connectionString + "' " +
                    "PUBLICATION central_filial" + sucursalId + "_pub WITH (copy_data = false, origin = 'none')";
            
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Alter an existing subscription
     * @param subscriptionName Name of the subscription to alter
     * @param enabled Whether to enable or disable the subscription
     * @return true if successful
     */
    public boolean alterSubscription(String subscriptionName, boolean enabled) {
        try {
            String sql = "ALTER SUBSCRIPTION " + subscriptionName + 
                    (enabled ? " ENABLE" : " DISABLE");
            
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Drop an existing subscription
     * @param subscriptionName Name of the subscription to drop
     * @return true if successful
     */
    public boolean dropSubscription(String subscriptionName) {
        try {
            // First disable the subscription
            jdbcTemplate.execute("ALTER SUBSCRIPTION " + subscriptionName + " DISABLE");
            
            // Then drop it
            jdbcTemplate.execute("DROP SUBSCRIPTION IF EXISTS " + subscriptionName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Drop a publication
     * @param publicationName Name of the publication to drop
     * @return true if successful
     */
    public boolean dropPublication(String publicationName) {
        try {
            jdbcTemplate.execute("DROP PUBLICATION IF EXISTS " + publicationName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * List all subscriptions in the current database
     * @return List of subscriptions with their details
     */
    public List<Map<String, Object>> listSubscriptions() {
        return jdbcTemplate.queryForList("SELECT * FROM pg_subscription");
    }
    
    /**
     * List subscriptions with pagination and optional search
     * @param pageable Pagination information
     * @param searchQuery Optional search query for subscription name
     * @return Page of subscriptions with pagination metadata
     */
    public Page<Map<String, Object>> listSubscriptionsWithPagination(Pageable pageable, String searchQuery) {
        List<Map<String, Object>> allSubscriptions = jdbcTemplate.queryForList("SELECT * FROM pg_subscription");
        
        // Apply search filter if provided
        if (searchQuery != null && !searchQuery.isEmpty()) {
            final String query = searchQuery.toLowerCase();
            allSubscriptions = allSubscriptions.stream()
                .filter(sub -> {
                    String subName = (String) sub.get("subname");
                    return subName != null && subName.toLowerCase().contains(query);
                })
                .collect(Collectors.toList());
        }
        
        // Apply pagination
        int total = allSubscriptions.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);
        
        // If start exceeds the size of the list, return empty page
        if (start >= total) {
            return new PageImpl<>(new ArrayList<>(), pageable, total);
        }
        
        List<Map<String, Object>> pagedSubscriptions = allSubscriptions.subList(start, end);
        return new PageImpl<>(pagedSubscriptions, pageable, total);
    }
    
    /**
     * List all publications in the current database
     * @return List of publications with their details
     */
    public List<Map<String, Object>> listPublications() {
        return jdbcTemplate.queryForList("SELECT * FROM pg_publication");
    }
    
    /**
     * List publications with pagination
     * @param pageable Pagination information
     * @return Page of publications with pagination metadata
     */
    public Page<Map<String, Object>> listPublicationsWithPagination(Pageable pageable) {
        List<Map<String, Object>> allPublications = jdbcTemplate.queryForList("SELECT * FROM pg_publication");
        
        // Apply pagination
        int total = allPublications.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);
        
        // If start exceeds the size of the list, return empty page
        if (start >= total) {
            return new PageImpl<>(new ArrayList<>(), pageable, total);
        }
        
        List<Map<String, Object>> pagedPublications = allPublications.subList(start, end);
        return new PageImpl<>(pagedPublications, pageable, total);
    }
    
    /**
     * Setup complete bidirectional replication for a branch from central server
     * This method should be called on the central server
     * @param sucursalId The branch ID to set up replication for
     * @return true if successful
     */
    public boolean setupCentralServerReplication(Long sucursalId) {
        try {
            Sucursal sucursal = sucursalService.findById(sucursalId).orElseThrow(() -> 
                new RuntimeException("Sucursal with ID " + sucursalId + " not found"));
            
            if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
                throw new RuntimeException("Sucursal is missing IP or port information");
            }
            
            // 1. Create central-to-branch filtered publication
            boolean centralToBranchPub = createCentralToBranchPublication(sucursalId);
            
            // 2. Create subscription to branch
            boolean subToBranch = createCentralToBranchSubscription(
                sucursalId, 
                sucursal.getIp(), 
                sucursal.getPuerto(),
                "general" // assuming this is the standard branch DB name
            );
            
            return centralToBranchPub && subToBranch;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Setup branch-side replication to communicate with central server
     * This method should be called on the branch server
     * @param sucursalId The ID of this branch
     * @return true if successful
     */
    public boolean setupBranchReplication(Long sucursalId) {
        try {
            // 1. Create branch publication
            boolean branchPub = createBranchPublication(sucursalId);
            
            // 2. Create subscription to central
            boolean subToCentral = createBranchToCentralSubscription(sucursalId);
            
            // 3. Create bidirectional subscription
            boolean bidirectionalSub = createBidirectionalSubscription(sucursalId);
            
            return branchPub && subToCentral && bidirectionalSub;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Remove all replication setup for a branch on central server
     * @param sucursalId The branch ID to remove replication for
     * @return true if successful
     */
    public boolean removeCentralServerReplication(Long sucursalId) {
        try {
            // Drop subscription to branch
            boolean dropSub = dropSubscription("filial" + sucursalId + "_sub");
            
            // Drop filtered publication
            boolean dropPub = dropPublication("central_filial" + sucursalId + "_pub");
            
            return dropSub && dropPub;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Remove all replication setup for a branch on the branch server
     * @param sucursalId The ID of this branch
     * @return true if successful
     */
    public boolean removeBranchReplication(Long sucursalId) {
        try {
            // Drop subscription to central
            boolean dropMainSub = dropSubscription("filial" + sucursalId + "_central_sub");
            
            // Drop bidirectional subscription
            boolean dropBiSub = dropSubscription("central_filial" + sucursalId + "_sub");
            
            // Drop publication
            boolean dropPub = dropPublication("filial" + sucursalId + "_pub");
            
            return dropMainSub && dropBiSub && dropPub;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create a connection to a remote PostgreSQL database
     * @param host Host address
     * @param port Port number
     * @param dbName Database name
     * @param username Username
     * @param password Password
     * @return JdbcTemplate for the remote connection
     */
    private JdbcTemplate createRemoteJdbcTemplate(String host, int port, String dbName, String username, String password) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
        
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        
        return new JdbcTemplate(dataSource);
    }
    
    /**
     * List subscriptions from a remote branch
     * @param sucursalId The ID of the branch
     * @return List of subscriptions from the remote branch
     */
    public List<Map<String, Object>> listRemoteSubscriptions(Long sucursalId) {
        Sucursal sucursal = sucursalService.findById(sucursalId)
            .orElseThrow(() -> new RuntimeException("Sucursal with ID " + sucursalId + " not found"));
        
        if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
            throw new RuntimeException("Sucursal is missing IP or port information");
        }
        
        JdbcTemplate remoteJdbc = createRemoteJdbcTemplate(
            sucursal.getIp(),
            sucursal.getPuerto(),
            "general", // Assuming this is the standard branch DB name
            dbUsername,
            dbPassword
        );
        
        try {
            return remoteJdbc.queryForList("SELECT * FROM pg_subscription");
        } catch (Exception e) {
            throw new RuntimeException("Error querying remote subscriptions: " + e.getMessage(), e);
        }
    }
    
    /**
     * List publications from a remote branch
     * @param sucursalId The ID of the branch
     * @return List of publications from the remote branch
     */
    public List<Map<String, Object>> listRemotePublications(Long sucursalId) {
        Sucursal sucursal = sucursalService.findById(sucursalId)
            .orElseThrow(() -> new RuntimeException("Sucursal with ID " + sucursalId + " not found"));
        
        if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
            throw new RuntimeException("Sucursal is missing IP or port information");
        }
        
        JdbcTemplate remoteJdbc = createRemoteJdbcTemplate(
            sucursal.getIp(),
            sucursal.getPuerto(),
            "general", // Assuming this is the standard branch DB name
            dbUsername,
            dbPassword
        );
        
        try {
            return remoteJdbc.queryForList("SELECT * FROM pg_publication");
        } catch (Exception e) {
            throw new RuntimeException("Error querying remote publications: " + e.getMessage(), e);
        }
    }
    
    /**
     * List subscriptions from a remote branch with pagination
     * @param sucursalId The ID of the branch
     * @param pageable Pagination information
     * @param searchQuery Optional search query for subscription name
     * @return Page of subscriptions from the remote branch
     */
    public Page<Map<String, Object>> listRemoteSubscriptionsWithPagination(
            Long sucursalId, Pageable pageable, String searchQuery) {
        List<Map<String, Object>> subscriptions = listRemoteSubscriptions(sucursalId);
        
        // Apply search filter if provided
        if (searchQuery != null && !searchQuery.isEmpty()) {
            final String query = searchQuery.toLowerCase();
            subscriptions = subscriptions.stream()
                .filter(sub -> {
                    String subName = (String) sub.get("subname");
                    return subName != null && subName.toLowerCase().contains(query);
                })
                .collect(Collectors.toList());
        }
        
        // Apply pagination
        int total = subscriptions.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);
        
        // If start exceeds the size of the list, return empty page
        if (start >= total) {
            return new PageImpl<>(new ArrayList<>(), pageable, total);
        }
        
        List<Map<String, Object>> pagedSubscriptions = subscriptions.subList(start, end);
        return new PageImpl<>(pagedSubscriptions, pageable, total);
    }
    
    /**
     * List publications from a remote branch with pagination
     * @param sucursalId The ID of the branch
     * @param pageable Pagination information
     * @return Page of publications from the remote branch
     */
    public Page<Map<String, Object>> listRemotePublicationsWithPagination(
            Long sucursalId, Pageable pageable) {
        List<Map<String, Object>> publications = listRemotePublications(sucursalId);
        
        // Apply pagination
        int total = publications.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);
        
        // If start exceeds the size of the list, return empty page
        if (start >= total) {
            return new PageImpl<>(new ArrayList<>(), pageable, total);
        }
        
        List<Map<String, Object>> pagedPublications = publications.subList(start, end);
        return new PageImpl<>(pagedPublications, pageable, total);
    }
    
    /**
     * Execute a replication command on a remote branch
     * @param sucursalId The ID of the branch
     * @param sql The SQL command to execute
     * @return true if successful
     */
    public boolean executeRemoteReplicationCommand(Long sucursalId, String sql) {
        Sucursal sucursal = sucursalService.findById(sucursalId)
            .orElseThrow(() -> new RuntimeException("Sucursal with ID " + sucursalId + " not found"));
        
        if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
            throw new RuntimeException("Sucursal is missing IP or port information");
        }
        
        JdbcTemplate remoteJdbc = createRemoteJdbcTemplate(
            sucursal.getIp(),
            sucursal.getPuerto(),
            "general", // Assuming this is the standard branch DB name
            dbUsername,
            dbPassword
        );
        
        try {
            remoteJdbc.execute(sql);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error executing remote command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Toggle a subscription on a remote branch
     * @param sucursalId The ID of the branch
     * @param subscriptionName The name of the subscription to toggle
     * @param enabled Whether to enable or disable the subscription
     * @return true if successful
     */
    public boolean toggleRemoteSubscription(Long sucursalId, String subscriptionName, boolean enabled) {
        String sql = "ALTER SUBSCRIPTION " + subscriptionName + (enabled ? " ENABLE" : " DISABLE");
        return executeRemoteReplicationCommand(sucursalId, sql);
    }
    
    /**
     * Drop a publication on a remote branch
     * @param sucursalId The ID of the branch
     * @param publicationName The name of the publication to drop
     * @return true if successful
     */
    public boolean dropRemotePublication(Long sucursalId, String publicationName) {
        String sql = "DROP PUBLICATION IF EXISTS " + publicationName;
        return executeRemoteReplicationCommand(sucursalId, sql);
    }
    
    /**
     * Creates a publication on a remote branch
     * @param branchId ID of the branch to connect to
     * @param publicationName Name for the publication
     * @param tables List of tables to include in the publication
     * @return true if the operation was successful
     */
    public boolean createRemotePublication(long branchId, String publicationName, List<String> tables) {
        try {
            Sucursal branch = sucursalService.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with ID: " + branchId));
                
            if (branch.getIp() == null || branch.getPuerto() == null) {
                throw new RuntimeException("Branch does not have IP or port information");
            }
            
            JdbcTemplate remoteJdbcTemplate = createRemoteJdbcTemplate(
                branch.getIp(), 
                branch.getPuerto(), 
                "general", // Database name
                dbUsername,
                dbPassword
            );
            
            // First check if publication already exists
            try {
                String checkQuery = "SELECT COUNT(*) FROM pg_publication WHERE pubname = ?";
                int count = remoteJdbcTemplate.queryForObject(checkQuery, Integer.class, publicationName);
                
                if (count > 0) {
                    // Publication already exists
                    logger.info("Publication " + publicationName + " already exists on remote branch " + branchId);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error checking for existing remote publication: " + e.getMessage());
            }
            
            // Build the CREATE PUBLICATION command
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION ")
                .append(publicationName)
                .append(" FOR TABLE ");
            
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(tables.get(i));
            }
            
            // Execute the command
            remoteJdbcTemplate.execute(sql.toString());
            logger.info("Successfully created remote publication " + publicationName + " on branch " + branchId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error creating remote publication: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates a subscription on a remote branch
     * @param branchId ID of the branch to connect to
     * @param subscriptionName Name for the subscription
     * @param connectionString Connection string to the publication server
     * @param publicationName Name of the publication to subscribe to
     * @return true if the operation was successful
     */
    public boolean createRemoteSubscription(long branchId, String subscriptionName, 
                                         String connectionString, String publicationName) {
        try {
            Sucursal branch = sucursalService.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with ID: " + branchId));
                
            if (branch.getIp() == null || branch.getPuerto() == null) {
                throw new RuntimeException("Branch does not have IP or port information");
            }
            
            JdbcTemplate remoteJdbcTemplate = createRemoteJdbcTemplate(
                branch.getIp(), 
                branch.getPuerto(), 
                "general", // Database name
                dbUsername,
                dbPassword
            );
            
            // First check if subscription already exists
            try {
                String checkQuery = "SELECT COUNT(*) FROM pg_subscription WHERE subname = ?";
                int count = remoteJdbcTemplate.queryForObject(checkQuery, Integer.class, subscriptionName);
                
                if (count > 0) {
                    // Subscription already exists
                    logger.info("Subscription " + subscriptionName + " already exists on remote branch " + branchId);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error checking for existing remote subscription: " + e.getMessage());
            }
            
            // Build the CREATE SUBSCRIPTION command
            String sql = "CREATE SUBSCRIPTION " + subscriptionName + 
                    " CONNECTION '" + connectionString + "' " +
                    " PUBLICATION " + publicationName + 
                    " WITH (copy_data = false, origin = 'none')";
            
            // Execute the command
            remoteJdbcTemplate.execute(sql);
            logger.info("Successfully created remote subscription " + subscriptionName + " on branch " + branchId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error creating remote subscription: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Edits a publication on a remote branch by dropping and recreating it with new tables
     * @param branchId ID of the branch to connect to
     * @param publicationName Name of the publication to edit
     * @param tables New list of tables for the publication
     * @return true if the operation was successful
     */
    public boolean editRemotePublication(long branchId, String publicationName, List<String> tables) {
        try {
            // First drop the existing publication
            boolean dropped = dropRemotePublication(branchId, publicationName);
            if (!dropped) {
                logger.error("Failed to drop existing publication " + publicationName + " on branch " + branchId);
                return false;
            }
            
            // Then create a new one with the updated tables
            return createRemotePublication(branchId, publicationName, tables);
        } catch (Exception e) {
            logger.error("Error editing remote publication: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Edits a subscription on a remote branch
     * @param branchId ID of the branch to connect to
     * @param subscriptionName Name of the subscription to edit
     * @param connectionString New connection string (null if not changing)
     * @param publicationName New publication name (null if not changing)
     * @return true if the operation was successful
     */
    public boolean editRemoteSubscription(long branchId, String subscriptionName, 
                                       String connectionString, String publicationName) {
        try {
            Sucursal branch = sucursalService.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found with ID: " + branchId));
                
            if (branch.getIp() == null || branch.getPuerto() == null) {
                throw new RuntimeException("Branch does not have IP or port information");
            }
            
            // First disable the subscription
            boolean disabled = toggleRemoteSubscription(branchId, subscriptionName, false);
            if (!disabled) {
                logger.error("Failed to disable subscription " + subscriptionName + " before editing on branch " + branchId);
                return false;
            }
            
            JdbcTemplate remoteJdbcTemplate = createRemoteJdbcTemplate(
                branch.getIp(), 
                branch.getPuerto(), 
                "general", // Database name
                dbUsername,
                dbPassword
            );
            
            // Update the connection string if provided
            if (connectionString != null && !connectionString.isEmpty()) {
                String sql = "ALTER SUBSCRIPTION " + subscriptionName + 
                        " CONNECTION '" + connectionString + "'";
                remoteJdbcTemplate.execute(sql);
                logger.info("Updated connection string for subscription " + subscriptionName + " on branch " + branchId);
            }
            
            // Update the publication name if provided
            if (publicationName != null && !publicationName.isEmpty()) {
                String sql = "ALTER SUBSCRIPTION " + subscriptionName + 
                        " SET PUBLICATION " + publicationName;
                remoteJdbcTemplate.execute(sql);
                logger.info("Updated publication for subscription " + subscriptionName + " on branch " + branchId);
            }
            
            // Re-enable the subscription
            boolean enabled = toggleRemoteSubscription(branchId, subscriptionName, true);
            if (!enabled) {
                logger.warn("Failed to re-enable subscription " + subscriptionName + " after editing on branch " + branchId);
                // Continue anyway, as the changes were made
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error editing remote subscription: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates a formatted PostgreSQL connection string for logical replication
     * @param host Database host
     * @param port Database port
     * @param dbName Database name
     * @param username Database username
     * @param password Database password
     * @return Formatted connection string
     */
    public String createConnectionString(String host, int port, String dbName, String username, String password) {
        return String.format(
            "dbname=%s host=%s user=%s password=%s port=%d",
            dbName, host, username, password, port
        );
    }
    
    /**
     * Creates a connection string for central server
     * @return Formatted connection string to central server
     */
    public String createCentralConnectionString() {
        return String.format(
            "dbname=%s host=%s user=%s password=%s port=%s",
            centralDbName, centralServerIp, dbUsername, dbPassword, centralServerPort
        );
    }
    
    /**
     * Generates a standardized branch publication name
     * @param sucursalId Branch ID
     * @return Standard publication name
     */
    public String generateBranchPublicationName(Long sucursalId) {
        return "filial" + sucursalId + "_pub";
    }
    
    /**
     * Generates a standardized branch subscription name
     * @param sucursalId Branch ID
     * @return Standard subscription name
     */
    public String generateBranchSubscriptionName(Long sucursalId) {
        return "filial" + sucursalId + "_sub";
    }
    
    /**
     * Generates a standardized central to branch publication name
     * @param sucursalId Branch ID
     * @return Standard central-to-branch publication name
     */
    public String generateCentralToBranchPublicationName(Long sucursalId) {
        return "central_filial" + sucursalId + "_pub";
    }
    
    /**
     * Generates a standardized central to branch subscription name
     * @param sucursalId Branch ID
     * @return Standard central-to-branch subscription name
     */
    public String generateCentralToBranchSubscriptionName(Long sucursalId) {
        return "central_filial" + sucursalId + "_sub";
    }
} 