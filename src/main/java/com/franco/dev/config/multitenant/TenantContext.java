package com.franco.dev.config.multitenant;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Component
public class TenantContext {
    public static final String DEFAULT_TENANT_ID = "default";
    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);
    @Autowired
    private static DatabaseSessionManager databaseSessionManager;
    private static ThreadLocal<String> currentTenant = ThreadLocal.withInitial(() -> DEFAULT_TENANT_ID);
    private static Set<String> tenantKeys = Collections.synchronizedSet(new HashSet<>());
    private static ThreadLocal<EntityManagerHolder> threadLocalEntityManager = new ThreadLocal<>();

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public void setCurrentTenant(String tenant) {
        logger.info("Setting current tenant to: {}", tenant);

        // Unbind any existing session
//        databaseSessionManager.unbindSession();

        // Set the tenant context
        currentTenant.set(tenant);
        logger.info("Current tenant is now: {}", currentTenant.get());

        // Bind a new session with the current tenant context
//        databaseSessionManager.bindSession();
    }

    public void clear() {
        logger.info("Clearing current tenant");
//        databaseSessionManager.unbindSession();
        currentTenant.remove();
    }

    public static void addTenantKey(String tenantKey) {
        logger.info("Adding tenant key: {}", tenantKey);
        tenantKeys.add(tenantKey);
    }

    public static Set<String> getAllTenantKeys() {
        return Collections.unmodifiableSet(tenantKeys);
    }

    @PostConstruct
    public void loadAllTenantKeys() {
        Set<String> tenants = scanForTenantPropertiesFiles();
        tenantKeys.addAll(tenants);
    }

    private Set<String> scanForTenantPropertiesFiles() {
        try {
            Path resourceDir = Paths.get(getClass().getClassLoader().getResource("").toURI());
            return Files.walk(resourceDir)
                    .filter(path -> path.toString().endsWith(".properties"))
                    .map(path -> path.getFileName().toString())
                    .filter(filename -> filename.matches("hibernate-filial\\d+_bkp\\.properties"))
                    .map(filename -> filename.substring("hibernate-".length(), filename.length() - ".properties".length()))
                    .collect(Collectors.toSet());
        } catch (IOException | URISyntaxException e) {
            logger.error("Error scanning for tenant properties files", e);
            return Collections.emptySet();
        }
    }
}
