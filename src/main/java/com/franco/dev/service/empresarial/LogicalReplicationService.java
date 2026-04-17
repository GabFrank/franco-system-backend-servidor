package com.franco.dev.service.empresarial;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.replication.types.ReplicationSetupState;
import com.franco.dev.repository.configuracion.LocalRepository;
import com.franco.dev.service.configuraciones.ReplicationTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Result of setupFullReplication for GraphQL (success + step-by-step message). */
    public static final class SetupFullReplicationResult {
        private final boolean success;
        private final String message;
        public SetupFullReplicationResult(boolean success, String message) {
            this.success = success;
            this.message = message != null ? message : "";
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    private final JdbcTemplate jdbcTemplate;
    private final SucursalService sucursalService;
    private final ReplicationTableService replicationTableService;
    private final LocalRepository localRepository;

    private static final Logger logger = LoggerFactory.getLogger(LogicalReplicationService.class);

    @Value("${spring.datasource.username:franco}")
    private String dbUsername;

    @Value("${spring.datasource.password:franco}")
    private String dbPassword;

    /** Fallback si no está configurado en configuraciones.local (no usar IP de producción por defecto). */
    @Value("${central.server.ip:localhost}")
    private String centralServerIpDefault;

    @Value("${central.server.port:5551}")
    private String centralServerPortDefault;

    @Value("${central.server.dbname:bodega}")
    private String centralDbNameDefault;

    /** Central master tables (MAIN_TO_ALL). Updated via updateCentralMasterTables from configuraciones.replication_table. */
    private List<String> dynamicCentralMasterTables = new ArrayList<>();
    /** Branch transaction tables (BRANCH_TO_MAIN). Updated via updateBranchTransactionTables from configuraciones.replication_table. */
    private List<String> dynamicBranchTransactionTables = new ArrayList<>();
    
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
    
    public LogicalReplicationService(JdbcTemplate jdbcTemplate, SucursalService sucursalService,
                                    ReplicationTableService replicationTableService,
                                    LocalRepository localRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.sucursalService = sucursalService;
        this.replicationTableService = replicationTableService;
        this.localRepository = localRepository;
    }

    /**
     * Nombre de la base de datos del servidor central. Si está configurado en configuraciones.local se usa; si no, el de aplicación.
     */
    private String getCentralDbName() {
        Optional<Local> localOpt = localRepository.findFirstByOrderByIdAsc();
        String value = localOpt
                .map(Local::getNombreBaseDatosMaster)
                .filter(s -> s != null && !s.isEmpty())
                .orElse(centralDbNameDefault);
        boolean fromConfig = localOpt.map(Local::getNombreBaseDatosMaster).filter(s -> s != null && !s.isEmpty()).isPresent();
        logger.debug("getCentralDbName: dbName={} source={}", value, fromConfig ? "configuraciones.local" : "default");
        return value;
    }

    /**
     * Nombre de la base de datos del servidor filial al conectar a una sucursal. Si está configurado en configuraciones.local se usa; si no, "general".
     */
    private String getBranchDbName() {
        Optional<Local> localOpt = localRepository.findFirstByOrderByIdAsc();
        String value = localOpt
                .map(Local::getNombreBaseDatosFilial)
                .filter(s -> s != null && !s.isEmpty())
                .orElse("general");
        boolean fromConfig = localOpt.map(Local::getNombreBaseDatosFilial).filter(s -> s != null && !s.isEmpty()).isPresent();
        logger.debug("getBranchDbName: dbName={} source={}", value, fromConfig ? "configuraciones.local" : "default");
        return value;
    }

    /**
     * IP o host del servidor central (para que las filiales se conecten). Primero configuraciones.local, luego central.server.ip.
     */
    private String getCentralServerIp() {
        Optional<Local> localOpt = localRepository.findFirstByOrderByIdAsc();
        String value = localOpt
                .map(Local::getIpServidorCentral)
                .filter(s -> s != null && !s.isEmpty())
                .orElse(centralServerIpDefault);
        logger.debug("getCentralServerIp: host={} source={}", value, localOpt.map(Local::getIpServidorCentral).filter(s -> s != null && !s.isEmpty()).isPresent() ? "configuraciones.local" : "application");
        return value;
    }

    /**
     * Puerto del servidor central. Primero configuraciones.local, luego central.server.port.
     */
    private String getCentralServerPort() {
        Optional<Local> localOpt = localRepository.findFirstByOrderByIdAsc();
        String value = localOpt
                .map(Local::getPuertoServidorCentral)
                .filter(p -> p != null)
                .map(Object::toString)
                .orElse(centralServerPortDefault);
        logger.debug("getCentralServerPort: port={} source={}", value, localOpt.map(Local::getPuertoServidorCentral).filter(p -> p != null).isPresent() ? "configuraciones.local" : "application");
        return value;
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
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION " + generateBranchPublicationName(sucursalId) + " FOR TABLE ");
            
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
     * Create a filtered publication for central to branch (central_filialX_pub).
     * Uses configuraciones.replication_table: BRANCH_TO_MAIN with flag + MAIN_TO_SPECIFIC with sucursalId in branch_ids.
     * Only operates for active sucursales (activo = true).
     */
    public boolean createCentralToBranchPublication(Long sucursalId) {
        if (sucursalId == null) {
            return false;
        }
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
        if (sucursal != null && !Boolean.TRUE.equals(sucursal.getActivo())) {
            logger.warn("Skipping createCentralToBranchPublication for inactive sucursal id={}", sucursalId);
            return false;
        }
        try {
            List<String> tables = replicationTableService.getTablesForCentralToBranchPublication(sucursalId);
            if (tables.isEmpty()) {
                logger.info("No tables configured for {}", generateCentralToBranchPublicationName(sucursalId));
                return true;
            }
            StringBuilder sql = new StringBuilder("CREATE PUBLICATION " + generateCentralToBranchPublicationName(sucursalId) + " FOR TABLE ");
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(tables.get(i)).append(" WHERE (sucursal_id = ").append(sucursalId).append(")");
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
            logger.info("Replicación: creando suscripción central->filial sucursalId={} conectando a base de datos branchDbName={} host={} port={}", sucursalId, branchDbName, branchIp, branchPort);
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%d",
                branchDbName, branchIp, dbUsername, dbPassword, branchPort
            );
            
            String sql = "CREATE SUBSCRIPTION " + generateBranchSubscriptionName(sucursalId) + " " +
                    "CONNECTION '" + connectionString + "' " +
                    "PUBLICATION " + generateBranchPublicationName(sucursalId) + " WITH (copy_data = false, origin = 'none')";
            
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
            String centralDb = getCentralDbName();
            logger.info("Replicación: creando suscripción filial->central sucursalId={} conectando a base de datos centralDbName={} host={} port={}", sucursalId, centralDb, getCentralServerIp(), getCentralServerPort());
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%s",
                centralDb, getCentralServerIp(), dbUsername, dbPassword, getCentralServerPort()
            );
            
            String sql = "CREATE SUBSCRIPTION " + generateBranchToCentralSubscriptionName(sucursalId) + " " +
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
            String centralDb = getCentralDbName();
            logger.info("Replicación: creando suscripción bidireccional sucursalId={} conectando a base central centralDbName={} host={} port={}", sucursalId, centralDb, getCentralServerIp(), getCentralServerPort());
            String connectionString = String.format(
                "dbname=%s host=%s user=%s password=%s port=%s",
                centralDb, getCentralServerIp(), dbUsername, dbPassword, getCentralServerPort()
            );
            
            String sql = "CREATE SUBSCRIPTION " + generateCentralToBranchSubscriptionName(sucursalId) + " " +
                    "CONNECTION '" + connectionString + "' " +
                    "PUBLICATION " + generateCentralToBranchPublicationName(sucursalId) + " WITH (copy_data = false, origin = 'none')";
            
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
     * Get current replication setup state for a branch: what exists on central and filial.
     * Used by setup dialog to avoid creating objects that already exist.
     * Usado en: Desktop (diálogo configurar replicación, al seleccionar sucursal).
     */
    public ReplicationSetupState getReplicationSetupState(Long sucursalId) {
        String centralPubName = generateCentralToBranchPublicationName(sucursalId);
        String centralSubName = generateBranchSubscriptionName(sucursalId);
        String filialPubName = generateBranchPublicationName(sucursalId);
        String filialSubBidiName = generateCentralToBranchSubscriptionName(sucursalId);
        String filialSubCentralName = generateBranchToCentralSubscriptionName(sucursalId);

        boolean centralPublicationExists = false;
        boolean centralSubscriptionExists = false;
        try {
            List<Map<String, Object>> pubs = listPublications();
            centralPublicationExists = pubs.stream().anyMatch(m -> centralPubName.equals(m.get("pubname")));
            List<Map<String, Object>> subs = listSubscriptions();
            centralSubscriptionExists = subs.stream().anyMatch(m -> centralSubName.equals(m.get("subname")));
        } catch (Exception e) {
            logger.warn("Error checking central state for sucursal {}: {}", sucursalId, e.getMessage());
        }

        boolean filialReachable = false;
        boolean filialPublicationExists = false;
        boolean filialSubscriptionBidiExists = false;
        boolean filialSubscriptionCentralExists = false;
        try {
            List<Map<String, Object>> remotePubs = listRemotePublications(sucursalId);
            filialReachable = true;
            filialPublicationExists = remotePubs.stream().anyMatch(m -> filialPubName.equals(m.get("pubname")));
            List<Map<String, Object>> remoteSubs = listRemoteSubscriptions(sucursalId);
            filialSubscriptionBidiExists = remoteSubs.stream().anyMatch(m -> filialSubBidiName.equals(m.get("subname")));
            filialSubscriptionCentralExists = remoteSubs.stream().anyMatch(m -> filialSubCentralName.equals(m.get("subname")));
        } catch (Exception e) {
            logger.warn("Filial not reachable or error checking filial state for sucursal {}: {}", sucursalId, e.getMessage());
        }

        return new ReplicationSetupState(
            centralPublicationExists,
            centralSubscriptionExists,
            filialReachable,
            filialPublicationExists,
            filialSubscriptionBidiExists,
            filialSubscriptionCentralExists
        );
    }

    /**
     * List all subscriptions in the current database
     * @return List of subscriptions with their details
     */
    public List<Map<String, Object>> listSubscriptions() {
        logger.info("Replicación: listando suscripciones locales (base de datos actual: {})", getCentralDbName());
        return jdbcTemplate.queryForList("SELECT * FROM pg_subscription");
    }
    
    /**
     * List subscriptions with pagination and optional search
     * @param pageable Pagination information
     * @param searchQuery Optional search query for subscription name
     * @return Page of subscriptions with pagination metadata
     */
    public Page<Map<String, Object>> listSubscriptionsWithPagination(Pageable pageable, String searchQuery) {
        logger.info("Replicación: listando suscripciones locales (base de datos actual: {})", getCentralDbName());
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
        logger.info("Replicación: listando publicaciones locales (base de datos actual: {})", getCentralDbName());
        return jdbcTemplate.queryForList("SELECT * FROM pg_publication");
    }
    
    /**
     * List publications with pagination
     * @param pageable Pagination information
     * @return Page of publications with pagination metadata
     */
    public Page<Map<String, Object>> listPublicationsWithPagination(Pageable pageable) {
        logger.info("Replicación: listando publicaciones locales (base de datos actual: {})", getCentralDbName());
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
     * Setup complete bidirectional replication for a branch from central server.
     * Only runs for active sucursales (activo = true).
     */
    public boolean setupCentralServerReplication(Long sucursalId) {
        try {
            Sucursal sucursal = sucursalService.findById(sucursalId).orElseThrow(() ->
                new RuntimeException("Sucursal with ID " + sucursalId + " not found"));
            if (!Boolean.TRUE.equals(sucursal.getActivo())) {
                logger.warn("Skipping setupCentralServerReplication for inactive sucursal id={}", sucursalId);
                return false;
            }
            if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
                throw new RuntimeException("Sucursal is missing IP or port information");
            }
            String branchDbName = getBranchDbName();
            logger.info("Replicación: setupCentralServerReplication sucursalId={} sucursal ip={} puerto={} branchDbName={}", sucursalId, sucursal.getIp(), sucursal.getPuerto(), branchDbName);
            boolean centralToBranchPub = createCentralToBranchPublication(sucursalId);
            
            // 2. Create subscription to branch
            boolean subToBranch = createCentralToBranchSubscription(
                sucursalId, 
                sucursal.getIp(), 
                sucursal.getPuerto(),
                branchDbName
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
     * Full replication setup from central: verify connections, create publications and subscriptions
     * on central and branch, verify workers, run E2E test. On failure rolls back in reverse order.
     * Usado en: Desktop (módulo replicación lógica, botón setup completo).
     *
     * @param sucursalId branch id
     * @return result with success and step-by-step log message
     */
    public SetupFullReplicationResult setupFullReplication(Long sucursalId) {
        StringBuilder log = new StringBuilder();
        boolean pubCentralCreated = false;
        boolean pubFilialCreated = false;
        boolean subCentralCreated = false;
        boolean subFilialBidiCreated = false;
        boolean subFilialCentralCreated = false;
        
        try {
            // 1. Validate sucursal
            Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Sucursal con ID " + sucursalId + " no encontrada"));
            if (!Boolean.TRUE.equals(sucursal.getActivo())) {
                throw new RuntimeException("Sucursal " + sucursalId + " no está activa");
            }
            if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
                throw new RuntimeException("Sucursal sin IP o puerto configurado");
            }
            log("Paso 1: Sucursal validada (activa, IP/puerto).", log);
            
            // 2. Verify central connection
            verifyConnectionCentral(log);
            log("Paso 2: Conexión a base central OK.", log);
            
            // 3. Verify filial connection
            verifyConnectionFilial(sucursalId, log);
            log("Paso 3: Conexión a base filial OK.", log);
            
            // 4. Create publication central_filial{id}_pub
            if (!createCentralToBranchPublication(sucursalId)) {
                throw new RuntimeException("No se pudo crear la publicación " + generateCentralToBranchPublicationName(sucursalId));
            }
            pubCentralCreated = true;
            log("Paso 4: Publicación " + generateCentralToBranchPublicationName(sucursalId) + " creada en central.", log);
            
            // 4b. Ensure replication_test table exists on filial (it's in BRANCH_TO_MAIN; filial may not have run V115 yet)
            ensureReplicationTestTableRemote(sucursalId);
            
            // 5. Create publication filial{id}_pub on branch (remote) — use DB-backed list so it's always up to date
            List<String> branchTables = replicationTableService.getBranchToMainTableNames();
            if (branchTables == null || branchTables.isEmpty()) {
                throw new RuntimeException("No hay tablas BRANCH_TO_MAIN configuradas en replication_table (enabled = true)");
            }
            if (!createRemotePublication(sucursalId, generateBranchPublicationName(sucursalId), branchTables)) {
                throw new RuntimeException("No se pudo crear la publicación " + generateBranchPublicationName(sucursalId) + " en filial");
            }
            pubFilialCreated = true;
            log("Paso 5: Publicación " + generateBranchPublicationName(sucursalId) + " creada en filial.", log);
            
            // 6. Create subscription filial{id}_sub on central (subscribes to filial's pub)
            String branchDbName = getBranchDbName();
            if (!createCentralToBranchSubscription(sucursalId, sucursal.getIp(), sucursal.getPuerto(), branchDbName)) {
                throw new RuntimeException("No se pudo crear la suscripción " + generateBranchSubscriptionName(sucursalId) + " en central");
            }
            subCentralCreated = true;
            log("Paso 6: Suscripción " + generateBranchSubscriptionName(sucursalId) + " creada en central.", log);
            
            // 7. Create subscription central_filial{id}_sub on branch (to central_filial{id}_pub on central)
            String centralConnStr = createCentralConnectionString();
            if (!createRemoteSubscription(sucursalId, generateCentralToBranchSubscriptionName(sucursalId), centralConnStr, generateCentralToBranchPublicationName(sucursalId))) {
                throw new RuntimeException("No se pudo crear la suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " en filial");
            }
            subFilialBidiCreated = true;
            log("Paso 7: Suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " creada en filial.", log);
            
            // 8. Create subscription filial{id}_central_sub on branch (to central_pub)
            if (!createRemoteSubscription(sucursalId, generateBranchToCentralSubscriptionName(sucursalId), centralConnStr, "central_pub")) {
                throw new RuntimeException("No se pudo crear la suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " en filial");
            }
            subFilialCentralCreated = true;
            log("Paso 8: Suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " creada en filial.", log);
            
            // 9. Wait and verify workers
            Thread.sleep(3000);
            String verifyMsg = verifyReplicationWorkers(sucursalId, log);
            log("Paso 9: " + verifyMsg, log);
            
            // 10. E2E test
            String e2eMsg = testReplicationE2E(sucursalId, log);
            log("Paso 10: " + e2eMsg, log);
            
            log.append("\nSetup de replicación completado correctamente.");
            return new SetupFullReplicationResult(true, log.toString());
            
        } catch (Exception e) {
            log.append("\nERROR: ").append(e.getMessage());
            logger.error("setupFullReplication failed: {}", e.getMessage(), e);
            // Rollback in reverse order
            if (subFilialCentralCreated) {
                try {
                    dropRemoteSubscription(sucursalId, generateBranchToCentralSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateBranchToCentralSubscriptionName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (subFilialBidiCreated) {
                try {
                    dropRemoteSubscription(sucursalId, generateCentralToBranchSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateCentralToBranchSubscriptionName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (subCentralCreated) {
                try {
                    dropSubscription(generateBranchSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateBranchSubscriptionName(sucursalId)).append(" eliminada en central.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (pubFilialCreated) {
                try {
                    dropRemotePublication(sucursalId, generateBranchPublicationName(sucursalId));
                    log.append("\nRollback: publicación ").append(generateBranchPublicationName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (pubCentralCreated) {
                try {
                    dropPublication(generateCentralToBranchPublicationName(sucursalId));
                    log.append("\nRollback: publicación ").append(generateCentralToBranchPublicationName(sucursalId)).append(" eliminada en central.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            return new SetupFullReplicationResult(false, log.toString());
        }
    }
    
    /**
     * Setup replication with granular control. target: SUBSCRIPTION, PUBLICATION, BOTH. scope: CENTRAL, FILIAL, BOTH.
     * BOTH+BOTH delegates to setupFullReplication. Other combinations run only the selected steps with rollback on failure.
     * Usado en: Desktop (diálogo configurar replicación con opciones avanzadas).
     *
     * @param sucursalId branch id
     * @param target SUBSCRIPTION, PUBLICATION, or BOTH
     * @param scope CENTRAL, FILIAL, or BOTH
     * @return result with success and step-by-step log message
     */
    public SetupFullReplicationResult setupReplicationAdvanced(Long sucursalId, String target, String scope) {
        String t = target != null ? target.toUpperCase() : "";
        String s = scope != null ? scope.toUpperCase() : "";
        if ("BOTH".equals(t) && "BOTH".equals(s)) {
            return setupFullReplication(sucursalId);
        }
        StringBuilder log = new StringBuilder();
        boolean pubCentralCreated = false;
        boolean pubFilialCreated = false;
        boolean subCentralCreated = false;
        boolean subFilialBidiCreated = false;
        boolean subFilialCentralCreated = false;
        boolean doPub = "PUBLICATION".equals(t) || "BOTH".equals(t);
        boolean doSub = "SUBSCRIPTION".equals(t) || "BOTH".equals(t);
        boolean onCentral = "CENTRAL".equals(s) || "BOTH".equals(s);
        boolean onFilial = "FILIAL".equals(s) || "BOTH".equals(s);
        try {
            // 1. Validate sucursal
            Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Sucursal con ID " + sucursalId + " no encontrada"));
            if (!Boolean.TRUE.equals(sucursal.getActivo())) {
                throw new RuntimeException("Sucursal " + sucursalId + " no está activa");
            }
            if (sucursal.getIp() == null || sucursal.getPuerto() == null) {
                throw new RuntimeException("Sucursal sin IP o puerto configurado");
            }
            log("Paso 1: Sucursal validada (activa, IP/puerto).", log);
            // 2. Verify central connection
            verifyConnectionCentral(log);
            log("Paso 2: Conexión a base central OK.", log);
            // 3. Verify filial connection only if scope includes filial
            if (onFilial) {
                verifyConnectionFilial(sucursalId, log);
                log("Paso 3: Conexión a base filial OK.", log);
            }
            int step = 4;
            if (doPub && onCentral) {
                if (!createCentralToBranchPublication(sucursalId)) {
                    throw new RuntimeException("No se pudo crear la publicación " + generateCentralToBranchPublicationName(sucursalId));
                }
                pubCentralCreated = true;
                log("Paso " + step + ": Publicación " + generateCentralToBranchPublicationName(sucursalId) + " creada en central.", log);
                step++;
            }
            if (doPub && onFilial) {
                ensureReplicationTestTableRemote(sucursalId);
                List<String> branchTables = replicationTableService.getBranchToMainTableNames();
                if (branchTables == null || branchTables.isEmpty()) {
                    throw new RuntimeException("No hay tablas BRANCH_TO_MAIN configuradas en replication_table (enabled = true)");
                }
                if (!createRemotePublication(sucursalId, generateBranchPublicationName(sucursalId), branchTables)) {
                    throw new RuntimeException("No se pudo crear la publicación " + generateBranchPublicationName(sucursalId) + " en filial");
                }
                pubFilialCreated = true;
                log("Paso " + step + ": Publicación " + generateBranchPublicationName(sucursalId) + " creada en filial.", log);
                step++;
            }
            if (doSub && onCentral) {
                String branchDbName = getBranchDbName();
                if (!createCentralToBranchSubscription(sucursalId, sucursal.getIp(), sucursal.getPuerto(), branchDbName)) {
                    throw new RuntimeException("No se pudo crear la suscripción " + generateBranchSubscriptionName(sucursalId) + " en central");
                }
                subCentralCreated = true;
                log("Paso " + step + ": Suscripción " + generateBranchSubscriptionName(sucursalId) + " creada en central.", log);
                step++;
            }
            if (doSub && onFilial) {
                String centralConnStr = createCentralConnectionString();
                if (!createRemoteSubscription(sucursalId, generateCentralToBranchSubscriptionName(sucursalId), centralConnStr, generateCentralToBranchPublicationName(sucursalId))) {
                    throw new RuntimeException("No se pudo crear la suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " en filial");
                }
                subFilialBidiCreated = true;
                log("Paso " + step + ": Suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " creada en filial.", log);
                step++;
                if (!createRemoteSubscription(sucursalId, generateBranchToCentralSubscriptionName(sucursalId), centralConnStr, "central_pub")) {
                    throw new RuntimeException("No se pudo crear la suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " en filial");
                }
                subFilialCentralCreated = true;
                log("Paso " + step + ": Suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " creada en filial.", log);
            }
            log.append("\nSetup de replicación completado correctamente.");
            return new SetupFullReplicationResult(true, log.toString());
        } catch (Exception e) {
            log.append("\nERROR: ").append(e.getMessage());
            logger.error("setupReplicationAdvanced failed: {}", e.getMessage(), e);
            if (subFilialCentralCreated) {
                try {
                    dropRemoteSubscription(sucursalId, generateBranchToCentralSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateBranchToCentralSubscriptionName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (subFilialBidiCreated) {
                try {
                    dropRemoteSubscription(sucursalId, generateCentralToBranchSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateCentralToBranchSubscriptionName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (subCentralCreated) {
                try {
                    dropSubscription(generateBranchSubscriptionName(sucursalId));
                    log.append("\nRollback: suscripción ").append(generateBranchSubscriptionName(sucursalId)).append(" eliminada en central.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (pubFilialCreated) {
                try {
                    dropRemotePublication(sucursalId, generateBranchPublicationName(sucursalId));
                    log.append("\nRollback: publicación ").append(generateBranchPublicationName(sucursalId)).append(" eliminada en filial.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            if (pubCentralCreated) {
                try {
                    dropPublication(generateCentralToBranchPublicationName(sucursalId));
                    log.append("\nRollback: publicación ").append(generateCentralToBranchPublicationName(sucursalId)).append(" eliminada en central.");
                } catch (Exception ex) {
                    log.append("\nRollback (warning): ").append(ex.getMessage());
                }
            }
            return new SetupFullReplicationResult(false, log.toString());
        }
    }
    
    /**
     * Remove replication with granular control. target: SUBSCRIPTION, PUBLICATION, BOTH. scope: CENTRAL, FILIAL, BOTH.
     * Returns step-by-step log in message. Usado en: Desktop (diálogo eliminar replicación).
     */
    public SetupFullReplicationResult removeReplicationAdvanced(Long sucursalId, String target, String scope) {
        StringBuilder log = new StringBuilder();
        String t = target != null ? target.toUpperCase() : "";
        String s = scope != null ? scope.toUpperCase() : "";
        try {
            boolean doSub = "SUBSCRIPTION".equals(t) || "BOTH".equals(t);
            boolean doPub = "PUBLICATION".equals(t) || "BOTH".equals(t);
            boolean onCentral = "CENTRAL".equals(s) || "BOTH".equals(s);
            boolean onFilial = "FILIAL".equals(s) || "BOTH".equals(s);
            
            if (onCentral) {
                if (doSub) {
                    if (dropSubscription(generateBranchSubscriptionName(sucursalId))) {
                        log("Suscripción " + generateBranchSubscriptionName(sucursalId) + " eliminada en central.", log);
                    } else {
                        log("Advertencia: no se pudo eliminar suscripción " + generateBranchSubscriptionName(sucursalId) + " en central.", log);
                    }
                }
                if (doPub) {
                    if (dropPublication(generateCentralToBranchPublicationName(sucursalId))) {
                        log("Publicación " + generateCentralToBranchPublicationName(sucursalId) + " eliminada en central.", log);
                    } else {
                        log("Advertencia: no se pudo eliminar publicación " + generateCentralToBranchPublicationName(sucursalId) + " en central.", log);
                    }
                }
            }
            if (onFilial) {
                if (doSub) {
                    if (dropRemoteSubscription(sucursalId, generateBranchToCentralSubscriptionName(sucursalId))) {
                        log("Suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " eliminada en filial.", log);
                    } else {
                        log("Advertencia: no se pudo eliminar suscripción " + generateBranchToCentralSubscriptionName(sucursalId) + " en filial.", log);
                    }
                    if (dropRemoteSubscription(sucursalId, generateCentralToBranchSubscriptionName(sucursalId))) {
                        log("Suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " eliminada en filial.", log);
                    } else {
                        log("Advertencia: no se pudo eliminar suscripción " + generateCentralToBranchSubscriptionName(sucursalId) + " en filial.", log);
                    }
                }
                if (doPub) {
                    if (dropRemotePublication(sucursalId, generateBranchPublicationName(sucursalId))) {
                        log("Publicación " + generateBranchPublicationName(sucursalId) + " eliminada en filial.", log);
                    } else {
                        log("Advertencia: no se pudo eliminar publicación " + generateBranchPublicationName(sucursalId) + " en filial.", log);
                    }
                }
            }
            log.append("\nEliminación completada.");
            return new SetupFullReplicationResult(true, log.toString());
        } catch (Exception e) {
            log.append("\nERROR: ").append(e.getMessage());
            logger.error("removeReplicationAdvanced failed: {}", e.getMessage(), e);
            return new SetupFullReplicationResult(false, log.toString());
        }
    }
    
    private static void log(String line, StringBuilder out) {
        if (out.length() > 0) {
            out.append("\n");
        }
        out.append(line);
    }
    
    private void verifyConnectionCentral(StringBuilder log) {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                throw new RuntimeException("SELECT 1 en central no devolvió 1");
            }
        } catch (DataAccessException e) {
            throw new RuntimeException("No hay conexión con la base central: " + e.getMessage(), e);
        }
    }
    
    private void verifyConnectionFilial(Long sucursalId, StringBuilder log) {
        try {
            Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
            JdbcTemplate remote = createRemoteJdbcTemplate(
                sucursal.getIp(), sucursal.getPuerto(), getBranchDbName(), dbUsername, dbPassword);
            Integer one = remote.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                throw new RuntimeException("SELECT 1 en filial no devolvió 1");
            }
        } catch (DataAccessException e) {
            throw new RuntimeException("No hay conexión con la base filial: " + e.getMessage(), e);
        }
    }
    
    private String verifyReplicationWorkers(Long sucursalId, StringBuilder log) {
        try {
            String subCentral = generateBranchSubscriptionName(sucursalId);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT pid FROM pg_stat_subscription WHERE subname = ?", subCentral);
            boolean centralOk = rows != null && !rows.isEmpty() && rows.get(0).get("pid") != null;
            
            Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
            if (sucursal == null) {
                return "Verificación workers: central " + (centralOk ? "OK" : "sin worker");
            }
            JdbcTemplate remote = createRemoteJdbcTemplate(
                sucursal.getIp(), sucursal.getPuerto(), getBranchDbName(), dbUsername, dbPassword);
            List<Map<String, Object>> remoteRows = remote.queryForList(
                "SELECT subname, pid FROM pg_stat_subscription WHERE subname IN (?, ?)",
                generateCentralToBranchSubscriptionName(sucursalId), generateBranchToCentralSubscriptionName(sucursalId));
            long withPid = remoteRows != null ? remoteRows.stream().filter(r -> r.get("pid") != null).count() : 0;
            boolean filialOk = withPid == 2;
            
            return "Workers: central " + (centralOk ? "OK" : "sin worker") + ", filial " + (filialOk ? "OK (2/2)" : "warning " + withPid + "/2");
        } catch (Exception e) {
            return "Verificación workers (warning): " + e.getMessage();
        }
    }
    
    private String testReplicationE2E(Long sucursalId, StringBuilder log) {
        String testUuid = UUID.randomUUID().toString();
        try {
            ensureReplicationTestTableRemote(sucursalId);
            // Insert in central
            jdbcTemplate.update(
                "INSERT INTO configuraciones.replication_test (test_uuid, source_db, sucursal_id) VALUES (?, ?, ?)",
                testUuid, "CENTRAL", sucursalId);
            // Sync sequence on filial so INSERT gets a free id (replication may have inserted rows with explicit id without advancing the sequence)
            try {
                executeRemoteReplicationCommand(sucursalId,
                    "SELECT setval(pg_get_serial_sequence('configuraciones.replication_test', 'id'), (SELECT COALESCE(MAX(id), 0) FROM configuraciones.replication_test))");
            } catch (Exception e) {
                logger.debug("Could not sync replication_test sequence on remote: {}", e.getMessage());
            }
            // Insert in filial
            executeRemoteReplicationCommand(sucursalId,
                "INSERT INTO configuraciones.replication_test (test_uuid, source_db, sucursal_id) VALUES ('" + testUuid.replace("'", "''") + "', 'FILIAL', " + sucursalId + ")");
            Thread.sleep(3000);
            Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
            JdbcTemplate remote = createRemoteJdbcTemplate(
                sucursal.getIp(), sucursal.getPuerto(), getBranchDbName(), dbUsername, dbPassword);
            Integer inFilialFromCentral = remote.queryForObject(
                "SELECT COUNT(*) FROM configuraciones.replication_test WHERE test_uuid = ? AND source_db = 'CENTRAL'",
                Integer.class, testUuid);
            Integer inCentralFromFilial = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM configuraciones.replication_test WHERE test_uuid = ? AND source_db = 'FILIAL'",
                Integer.class, testUuid);
            boolean centralToFilialOk = inFilialFromCentral != null && inFilialFromCentral >= 1;
            boolean filialToCentralOk = inCentralFromFilial != null && inCentralFromFilial >= 1;
            jdbcTemplate.update("DELETE FROM configuraciones.replication_test WHERE test_uuid = ?", testUuid);
            executeRemoteReplicationCommand(sucursalId, "DELETE FROM configuraciones.replication_test WHERE test_uuid = '" + testUuid.replace("'", "''") + "'");
            return "Test E2E: central->filial " + (centralToFilialOk ? "OK" : "FAIL") + ", filial->central " + (filialToCentralOk ? "OK" : "FAIL");
        } catch (Exception e) {
            try {
                jdbcTemplate.update("DELETE FROM configuraciones.replication_test WHERE test_uuid = ?", testUuid);
            } catch (Exception ignored) {}
            try {
                executeRemoteReplicationCommand(sucursalId, "DELETE FROM configuraciones.replication_test WHERE test_uuid = '" + testUuid.replace("'", "''") + "'");
            } catch (Exception ignored) {}
            return "Test E2E (warning): " + e.getMessage();
        }
    }
    
    private void ensureReplicationTestTableRemote(Long sucursalId) {
        try {
            executeRemoteReplicationCommand(sucursalId, "CREATE SCHEMA IF NOT EXISTS configuraciones");
        } catch (Exception e) {
            logger.debug("Schema configuraciones on remote (may already exist): {}", e.getMessage());
        }
        try {
            executeRemoteReplicationCommand(sucursalId,
                "CREATE TABLE IF NOT EXISTS configuraciones.replication_test (" +
                "id BIGSERIAL PRIMARY KEY, test_uuid VARCHAR(64) NOT NULL, source_db VARCHAR(20) NOT NULL, " +
                "sucursal_id BIGINT, created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW())");
        } catch (Exception e) {
            logger.warn("Could not create replication_test on remote: {}", e.getMessage());
            throw new RuntimeException("No se pudo crear la tabla configuraciones.replication_test en filial: " + e.getMessage(), e);
        }
        try {
            executeRemoteReplicationCommand(sucursalId, "ALTER TABLE configuraciones.replication_test REPLICA IDENTITY FULL");
        } catch (Exception e) {
            logger.warn("Could not set REPLICA IDENTITY FULL on remote replication_test: {}", e.getMessage());
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
            boolean dropSub = dropSubscription(generateBranchSubscriptionName(sucursalId));

            // Drop filtered publication
            boolean dropPub = dropPublication(generateCentralToBranchPublicationName(sucursalId));
            
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
            boolean dropMainSub = dropSubscription(generateBranchToCentralSubscriptionName(sucursalId));

            // Drop bidirectional subscription
            boolean dropBiSub = dropSubscription(generateCentralToBranchSubscriptionName(sucursalId));

            // Drop publication
            boolean dropPub = dropPublication(generateBranchPublicationName(sucursalId));
            
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
        logger.info("Replicación: creando conexión JDBC remota host={} port={} dbName={}", host, port, dbName);
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
            getBranchDbName(),
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
            getBranchDbName(),
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
            getBranchDbName(),
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
     * Drop a subscription on a remote branch (disables then drops).
     * @param sucursalId The ID of the branch
     * @param subscriptionName The name of the subscription to drop
     * @return true if successful
     */
    public boolean dropRemoteSubscription(Long sucursalId, String subscriptionName) {
        try {
            executeRemoteReplicationCommand(sucursalId, "ALTER SUBSCRIPTION " + subscriptionName + " DISABLE");
        } catch (Exception e) {
            logger.warn("Could not disable remote subscription {} before drop: {}", subscriptionName, e.getMessage());
        }
        return executeRemoteReplicationCommand(sucursalId, "DROP SUBSCRIPTION IF EXISTS " + subscriptionName);
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
                getBranchDbName(),
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
                getBranchDbName(),
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
                getBranchDbName(),
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
            getCentralDbName(), getCentralServerIp(), dbUsername, dbPassword, getCentralServerPort()
        );
    }
    
    /**
     * Generates a standardized branch publication name
     * @param sucursalId Branch ID
     * @return Standard publication name
     */
    public String generateBranchPublicationName(Long sucursalId) {
        return getCentralDbName() + "_filial" + sucursalId + "_pub";
    }

    /**
     * Generates a standardized branch subscription name (on central, subscribes to filial's pub)
     * @param sucursalId Branch ID
     * @return Standard subscription name
     */
    public String generateBranchSubscriptionName(Long sucursalId) {
        return getCentralDbName() + "_filial" + sucursalId + "_sub";
    }

    /**
     * Generates a standardized central to branch publication name
     * @param sucursalId Branch ID
     * @return Standard central-to-branch publication name
     */
    public String generateCentralToBranchPublicationName(Long sucursalId) {
        return "central_" + getCentralDbName() + "_filial" + sucursalId + "_pub";
    }

    /**
     * Generates a standardized central to branch subscription name (on filial, subscribes to central's filtered pub)
     * @param sucursalId Branch ID
     * @return Standard central-to-branch subscription name
     */
    public String generateCentralToBranchSubscriptionName(Long sucursalId) {
        return "central_" + getCentralDbName() + "_filial" + sucursalId + "_sub";
    }

    /**
     * Generates a standardized branch-to-central subscription name (on filial, subscribes to central_pub)
     * @param sucursalId Branch ID
     * @return Standard branch-to-central subscription name
     */
    public String generateBranchToCentralSubscriptionName(Long sucursalId) {
        return getCentralDbName() + "_filial" + sucursalId + "_central_sub";
    }

    /**
     * Extract sucursal ID from a publication or subscription name.
     * Handles dynamic DB-prefixed names like: beta_filial2_pub, central_beta_filial2_sub, etc.
     */
    public Long extractSucursalIdFromName(String name) {
        if (name == null) return null;
        try {
            // Pattern: ...filial<id>_pub, ...filial<id>_sub, ...filial<id>_central_sub
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("filial(\\d+)_").matcher(name);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    /**
     * Refresh a specific subscription to sync with its publication
     * @param subscriptionName Name of the subscription to refresh
     * @return true if successful
     */
    public boolean refreshSubscription(String subscriptionName) {
        try {
            String sql = "ALTER SUBSCRIPTION " + subscriptionName + " REFRESH PUBLICATION WITH (copy_data = false)";
            jdbcTemplate.execute(sql);
            logger.info("Successfully refreshed subscription: " + subscriptionName);
            return true;
        } catch (Exception e) {
            logger.error("Error refreshing subscription " + subscriptionName + ": " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Refresh all subscriptions in the database
     * @return true if all subscriptions were refreshed successfully
     */
    public boolean refreshAllSubscriptions() {
        try {
            List<Map<String, Object>> subscriptions = listSubscriptions();
            int successCount = 0;
            int failCount = 0;
            
            for (Map<String, Object> subscription : subscriptions) {
                String subscriptionName = (String) subscription.get("subname");
                if (subscriptionName != null) {
                    if (refreshSubscription(subscriptionName)) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }
            }
            
            logger.info("Refreshed " + successCount + " subscriptions successfully, " + failCount + " failed");
            return failCount == 0;
        } catch (Exception e) {
            logger.error("Error refreshing all subscriptions: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Refresh a specific subscription on a remote branch
     * @param sucursalId The ID of the branch
     * @param subscriptionName Name of the subscription to refresh
     * @return true if successful
     */
    public boolean refreshRemoteSubscription(Long sucursalId, String subscriptionName) {
        try {
            Sucursal branch = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Branch not found with ID: " + sucursalId));
                
            if (branch.getIp() == null || branch.getPuerto() == null) {
                throw new RuntimeException("Branch does not have IP or port information");
            }
            
            JdbcTemplate remoteJdbcTemplate = createRemoteJdbcTemplate(
                branch.getIp(), 
                branch.getPuerto(), 
                getBranchDbName(),
                dbUsername,
                dbPassword
            );
            
            String sql = "ALTER SUBSCRIPTION " + subscriptionName + " REFRESH PUBLICATION WITH (copy_data = false)";
            remoteJdbcTemplate.execute(sql);
            logger.info("Successfully refreshed remote subscription " + subscriptionName + " on branch " + sucursalId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error refreshing remote subscription " + subscriptionName + 
                " on branch " + sucursalId + ": " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Refresh all subscriptions on a remote branch
     * @param sucursalId The ID of the branch
     * @return true if all subscriptions were refreshed successfully
     */
    public boolean refreshAllRemoteSubscriptions(Long sucursalId) {
        try {
            List<Map<String, Object>> subscriptions = listRemoteSubscriptions(sucursalId);
            int successCount = 0;
            int failCount = 0;
            
            for (Map<String, Object> subscription : subscriptions) {
                String subscriptionName = (String) subscription.get("subname");
                if (subscriptionName != null) {
                    if (refreshRemoteSubscription(sucursalId, subscriptionName)) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }
            }
            
            logger.info("Refreshed " + successCount + " remote subscriptions successfully on branch " + 
                sucursalId + ", " + failCount + " failed");
            return failCount == 0;
        } catch (Exception e) {
            logger.error("Error refreshing all remote subscriptions on branch " + 
                sucursalId + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Refresh all subscriptions: local (central) and on every active filial.
     * Lightweight (metadata only). Use as a safety net when a filial was down during sync:
     * when it comes back, the next run of this will bring it up to date.
     *
     * Usado en:
     * - Desktop: Sí (scheduler opcional replication.refresh.enabled).
     * - Mobile: No.
     */
    public void refreshAllSubscriptionsEverywhere() {
        List<Sucursal> sucursales = sucursalService.findAllExcludingServer().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getIp() != null && s.getPuerto() != null)
                .collect(Collectors.toList());
        for (Sucursal s : sucursales) {
            Long sid = s.getId();
            if (sid == null || sid == 0) continue;
            try {
                refreshAllRemoteSubscriptions(sid);
            } catch (Exception e) {
                logger.warn("Refresh all remote subscriptions for sucursal {} failed: {}", sid, e.getMessage());
            }
        }
        try {
            refreshAllSubscriptions();
        } catch (Exception e) {
            logger.warn("Refresh all local subscriptions failed: {}", e.getMessage());
        }
    }

    /**
     * Sync existing publications with configuraciones.replication_table: add any table that is in
     * replication_table but not yet in the publication. Runs on central (local) and on each
     * active filial (remote). Use this so that after Flyway only creates the table and inserts
     * into replication_table, no manual ALTER PUBLICATION is required.
     *
     * Usado en:
     * - Desktop: Sí (módulo replicación, botón "Sincronizar publicaciones" y/o scheduler).
     * - Mobile: No.
     */
    public SetupFullReplicationResult syncPublicationsWithReplicationTable() {
        StringBuilder log = new StringBuilder();
        try {
            log.append("Sincronizando publicaciones con replication_table...\n");
            replicationTableService.updateReplicationServiceTables(this);

            // 1. central_pub: add missing MAIN_TO_ALL tables (only if publication exists)
            List<String> mainToAll = replicationTableService.getMainToAllTableNames();
            if (!mainToAll.isEmpty()) {
                Integer centralPubExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_publication WHERE pubname = 'central_pub'", Integer.class);
                if (centralPubExists == null || centralPubExists == 0) {
                    log.append("  central_pub: no existe, omitido.\n");
                } else {
                List<String> currentCentralPub = getPublicationTableNamesLocal("central_pub");
                List<String> toAdd = mainToAll.stream().filter(t -> !currentCentralPub.contains(t)).collect(Collectors.toList());
                for (String table : toAdd) {
                    try {
                        ensureReplicaIdentityFull(jdbcTemplate, table, log, "  central_pub: ");
                        jdbcTemplate.execute("ALTER PUBLICATION central_pub ADD TABLE " + table);
                        log.append("  central_pub: agregada tabla ").append(table).append(".\n");
                    } catch (DataAccessException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already in")) {
                            log.append("  central_pub: ").append(table).append(" ya estaba (OK).\n");
                        } else {
                            log.append("  central_pub: error agregando ").append(table).append(": ").append(e.getMessage()).append("\n");
                        }
                    }
                }
                if (toAdd.isEmpty() && !currentCentralPub.isEmpty()) {
                    log.append("  central_pub: sin tablas faltantes.\n");
                }
                }
            }

            // 2. central_filialX_pub: for each existing publication, add missing tables with WHERE (sucursal_id = X)
            String centralPubPrefix = "central_" + getCentralDbName() + "_filial";
            List<Map<String, Object>> pubs = jdbcTemplate.queryForList(
                "SELECT pubname FROM pg_publication WHERE pubname LIKE '" + centralPubPrefix + "%_pub' ORDER BY pubname");
            for (Map<String, Object> row : pubs) {
                String pubname = (String) row.get("pubname");
                if (pubname == null) continue;
                String suffix = pubname.replace(centralPubPrefix, "").replace("_pub", "");
                Long sucursalId = null;
                try {
                    sucursalId = Long.parseLong(suffix);
                } catch (NumberFormatException e) {
                    continue;
                }
                List<String> expected = replicationTableService.getTablesForCentralToBranchPublication(sucursalId);
                List<String> current = getPublicationTableNamesLocal(pubname);
                List<String> toAdd = expected.stream().filter(t -> !current.contains(t)).collect(Collectors.toList());
                for (String table : toAdd) {
                    try {
                        ensureReplicaIdentityFull(jdbcTemplate, table, log, "  " + pubname + ": ");
                        String sql = "ALTER PUBLICATION " + pubname + " ADD TABLE " + table + " WHERE (sucursal_id = " + sucursalId + ")";
                        jdbcTemplate.execute(sql);
                        log.append("  ").append(pubname).append(": agregada tabla ").append(table).append(".\n");
                    } catch (DataAccessException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already in")) {
                            log.append("  ").append(pubname).append(": ").append(table).append(" ya estaba (OK).\n");
                        } else {
                            log.append("  ").append(pubname).append(": error agregando ").append(table).append(": ").append(e.getMessage()).append("\n");
                        }
                    }
                }
            }

            // 3. Filiales: for each active sucursal, add missing BRANCH_TO_MAIN tables to filialX_pub on remote
            List<String> branchTables = replicationTableService.getBranchToMainTableNames();
            if (branchTables.isEmpty()) {
                log.append("  Filiales: no hay tablas BRANCH_TO_MAIN en replication_table.\n");
            } else {
                List<Sucursal> sucursales = sucursalService.findAllExcludingServer().stream()
                    .filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getIp() != null && s.getPuerto() != null)
                    .collect(Collectors.toList());
                for (Sucursal sucursal : sucursales) {
                    Long sid = sucursal.getId();
                    if (sid == null || sid == 0) continue;
                    String filialPubName = generateBranchPublicationName(sid);
                    try {
                        JdbcTemplate remoteJdbc = createRemoteJdbcTemplate(
                            sucursal.getIp(),
                            sucursal.getPuerto(),
                            getBranchDbName(),
                            dbUsername,
                            dbPassword);
                        List<String> currentFilial = getPublicationTableNames(remoteJdbc, filialPubName);
                        List<String> toAdd = branchTables.stream().filter(t -> !currentFilial.contains(t)).collect(Collectors.toList());
                        for (String table : toAdd) {
                            try {
                                ensureReplicaIdentityFull(remoteJdbc, table, log, "  Filial " + sid + ": ");
                                remoteJdbc.execute("ALTER PUBLICATION " + filialPubName + " ADD TABLE " + table);
                                log.append("  Filial ").append(sid).append(" ").append(filialPubName).append(": agregada tabla ").append(table).append(".\n");
                            } catch (DataAccessException e) {
                                if (e.getMessage() != null && (e.getMessage().contains("already in") || e.getMessage().contains("duplicate"))) {
                                    log.append("  Filial ").append(sid).append(": ").append(table).append(" ya estaba (OK).\n");
                                } else {
                                    log.append("  Filial ").append(sid).append(": error agregando ").append(table).append(": ").append(e.getMessage()).append("\n");
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.append("  Filial ").append(sid).append(": no se pudo conectar o sincronizar: ").append(e.getMessage()).append("\n");
                    }
                }
            }

            // 4. REFRESH PUBLICATION en suscripciones para que recojan tablas nuevas (si una filial falla, queda pendiente; el scheduler de refresh la actualizará cuando vuelva)
            log.append("  Refrescando suscripciones...\n");
            List<Sucursal> sucursalesForRefresh = sucursalService.findAllExcludingServer().stream()
                    .filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getIp() != null && s.getPuerto() != null)
                    .collect(Collectors.toList());
            for (Sucursal sucursal : sucursalesForRefresh) {
                Long sid = sucursal.getId();
                if (sid == null || sid == 0) continue;
                try {
                    String branchToCentralSubName = generateBranchToCentralSubscriptionName(sid);
                    if (refreshRemoteSubscription(sid, branchToCentralSubName)) {
                        log.append("  Filial ").append(sid).append(": ").append(branchToCentralSubName).append(" refrescada.\n");
                    }
                    String centralToBranchSubName = generateCentralToBranchSubscriptionName(sid);
                    if (refreshRemoteSubscription(sid, centralToBranchSubName)) {
                        log.append("  Filial ").append(sid).append(": ").append(centralToBranchSubName).append(" refrescada.\n");
                    }
                    String branchSubName = generateBranchSubscriptionName(sid);
                    if (refreshSubscription(branchSubName)) {
                        log.append("  Central: ").append(branchSubName).append(" refrescada.\n");
                    }
                } catch (Exception e) {
                    log.append("  Refresco sucursal ").append(sid).append(": ").append(e.getMessage()).append("\n");
                }
            }

            log.append("Sincronización finalizada.");
            return new SetupFullReplicationResult(true, log.toString());
        } catch (Exception e) {
            log.append("\nERROR: ").append(e.getMessage());
            logger.error("syncPublicationsWithReplicationTable failed: {}", e.getMessage(), e);
            return new SetupFullReplicationResult(false, log.toString());
        }
    }

    /**
     * Ensure the table has REPLICA IDENTITY FULL before adding to a publication.
     * If relreplident is not 'f', runs ALTER TABLE ... REPLICA IDENTITY FULL and appends a log line.
     */
    private void ensureReplicaIdentityFull(JdbcTemplate template, String fullTableName, StringBuilder log, String logPrefix) {
        if (fullTableName == null || !fullTableName.contains(".")) return;
        String[] parts = fullTableName.split("\\.", 2);
        String schema = parts[0];
        String tableName = parts[1];
        try {
            String replIdent = template.queryForObject(
                "SELECT c.relreplident::text FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relname = ?",
                String.class, schema, tableName);
            if (replIdent != null && "f".equals(replIdent.trim())) {
                return; // already FULL
            }
            template.execute("ALTER TABLE " + fullTableName + " REPLICA IDENTITY FULL");
            log.append(logPrefix).append("REPLICA IDENTITY FULL aplicado a ").append(fullTableName).append(".\n");
        } catch (Exception e) {
            logger.warn("Could not check/set replica identity for {}: {}", fullTableName, e.getMessage());
            // Continue anyway; ADD TABLE might still work if table has primary key (default replica identity)
        }
    }

    /** Get full table names (schemaname.tablename) for a publication on the given JdbcTemplate. */
    private List<String> getPublicationTableNames(JdbcTemplate template, String pubname) {
        try {
            List<Map<String, Object>> rows = template.queryForList(
                "SELECT schemaname, tablename FROM pg_publication_tables WHERE pubname = ?",
                pubname);
            List<String> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String schema = (String) row.get("schemaname");
                String table = (String) row.get("tablename");
                if (schema != null && table != null) {
                    out.add(schema + "." + table);
                }
            }
            return out;
        } catch (Exception e) {
            logger.warn("Could not list tables for publication {}: {}", pubname, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getPublicationTableNamesLocal(String pubname) {
        return getPublicationTableNames(jdbcTemplate, pubname);
    }
} 