package com.franco.dev.config.multitenant;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

//@Data
//@Component
//public class TenantContext {
//
//    public static final String DEFAULT_TENANT_ID = "default";
//    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);
//    private static final ThreadLocal<String> currentTenant = ThreadLocal.withInitial(() -> DEFAULT_TENANT_ID);
//    private static final Set<String> tenantKeys = Collections.synchronizedSet(new HashSet<>());
//    @Value("${external.properties.path}")
//    private String externalPropertiesPath;
//    @Value("${spring.profiles.active:default}")
//    private String activeProfile;
//
//    public String getActiveProfile(){
//        return activeProfile != null ? activeProfile : "default";
//    }
//
//    public String getExternalPropertiesPath(){
//        return externalPropertiesPath != null ? externalPropertiesPath : "";
//    }
//
//    public static String getCurrentTenant() {
//        return currentTenant.get();
//    }
//
//    public static void setCurrentTenant(String tenant) {
//        logger.info("Setting current tenant to: {}", tenant);
//        currentTenant.set(tenant);
//        logger.info("Current tenant is now: {}", currentTenant.get());
//    }
//
//    public static void clear() {
//        logger.info("Clearing current tenant");
//        currentTenant.remove();
//    }
//
//    public static void addTenantKey(String tenantKey) {
//        logger.info("Adding tenant key: {}", tenantKey);
//        tenantKeys.add(tenantKey);
//    }
//
//    public static Set<String> getAllTenantKeys() {
//        return Collections.unmodifiableSet(tenantKeys);
//    }
//
//    @PostConstruct
//    public void loadAllTenantKeys() {
//        Set<String> tenants = scanForTenantPropertiesFiles();
//        tenantKeys.addAll(tenants);
//    }
//
////    private Set<String> scanForTenantPropertiesFiles() {
////        try {
////            Path resourceDir = Paths.get(getClass().getClassLoader().getResource("").toURI());
////            return Files.walk(resourceDir)
////                    .filter(path -> path.toString().endsWith(".properties"))
////                    .map(path -> path.getFileName().toString())
////                    .filter(filename -> filename.matches("hibernate-filial\\d+_bkp\\.properties"))
////                    .map(filename -> filename.substring("hibernate-".length(), filename.length() - ".properties".length()))
////                    .collect(Collectors.toSet());
////        } catch (IOException | URISyntaxException e) {
////            logger.error("Error scanning for tenant properties files", e);
////            return Collections.emptySet();
////        }
////    }
//
////    private Set<String> scanForTenantPropertiesFiles() {
////        try {
////            // Use the resource stream instead of Path to read from the JAR
////            Enumeration<URL> resources = getClass().getClassLoader().getResources("");
////
////            Set<String> tenantFiles = new HashSet<>();
////
////            while (resources.hasMoreElements()) {
////                URL resourceUrl = resources.nextElement();
////
////                if (resourceUrl.getProtocol().equals("jar")) {
////                    try (FileSystem fileSystem = FileSystems.newFileSystem(resourceUrl.toURI(), Collections.emptyMap())) {
////                        Path path = fileSystem.getPath("/");
////
////                        Files.walk(path)
////                                .filter(p -> p.toString().endsWith(".properties"))
////                                .forEach(p -> {
////                                    String filename = p.getFileName().toString();
////                                    if (filename.matches("hibernate-filial\\d+\\.properties")) {
////                                        tenantFiles.add(filename.substring("hibernate-".length(), filename.length() - ".properties".length()));
////                                    }
////                                });
////                    }
////                } else {
////                    // Fallback to regular file system if it's not inside a jar
////                    Path resourceDir = Paths.get(resourceUrl.toURI());
////                    Files.walk(resourceDir)
////                            .filter(path -> path.toString().endsWith(".properties"))
////                            .forEach(path -> {
////                                String filename = path.getFileName().toString();
////                                if (filename.matches("hibernate-filial\\d+\\.properties")) {
////                                    tenantFiles.add(filename.substring("hibernate-".length(), filename.length() - ".properties".length()));
////                                }
////                            });
////                }
////            }
////
////            return tenantFiles;
////
////        } catch (IOException | URISyntaxException e) {
////            logger.error("Error scanning for tenant properties files", e);
////            return Collections.emptySet();
////        }
////    }
//
//    public Set<String> scanForTenantPropertiesFiles() {
//        Set<String> tenantFiles = new HashSet<>();
//
//        try {
//            // Log the active profile
//            logger.info("Active profile: " + activeProfile);
//
//            // Check if we are running in production
//            if ("prod".equals(activeProfile)) {
//                logger.debug("Production mode detected. Loading tenant properties from external directory.");
//
//                // In production, load from the external directory
//                logger.debug("External properties path: " + externalPropertiesPath);
//
//                if (externalPropertiesPath == null) {
//                    logger.error("External properties path is not defined in the environment.");
//                    return Collections.emptySet();
//                }
//
//                Path externalDir = Paths.get(externalPropertiesPath);
//                if (!Files.exists(externalDir)) {
//                    logger.error("Directory for tenant properties files not found: " + externalDir);
//                    return Collections.emptySet();
//                }
//
//                // Walk through the external directory and find tenant files
//                tenantFiles = Files.walk(externalDir)
//                        .filter(path -> path.toString().endsWith(".properties"))
//                        .map(path -> path.getFileName().toString())
//                        .peek(filename -> logger.debug("Found properties file: " + filename))
//                        .filter(filename -> filename.matches("hibernate-filial\\d+\\.properties") || "hibernate-default.properties".equals(filename))
//                        .peek(filename -> {
//                            if ("hibernate-default.properties".equals(filename)) {
//                                logger.debug("Found default properties file: " + filename);
//                            } else {
//                                logger.debug("Found tenant properties file: " + filename);
//                            }
//                        })
//                        .map(filename -> filename.equals("hibernate-default.properties") ? "default" : filename.substring("hibernate-".length(), filename.length() - ".properties".length()))
//                        .collect(Collectors.toSet());
//
//
//            } else {
//                // In development, load tenant properties from the classpath
//                logger.info("Development mode detected. Loading tenant properties from classpath.");
//
//                Path resourceDir = Paths.get(getClass().getClassLoader().getResource("").toURI());
//                logger.info("Classpath resource directory: " + resourceDir);
//
//                tenantFiles = Files.walk(resourceDir)
//                        .filter(path -> path.toString().endsWith(".properties"))
//                        .map(path -> path.getFileName().toString())
//                        .peek(filename -> logger.info("Found properties file: " + filename))
//                        .filter(filename -> filename.matches("hibernate-filial\\d+\\.properties") || "hibernate-default.properties".equals(filename))
//                        .peek(filename -> {
//                            if ("hibernate-default.properties".equals(filename)) {
//                                logger.info("Found default properties file: " + filename);
//                            } else {
//                                logger.info("Found tenant properties file: " + filename);
//                            }
//                        })
//                        .map(filename -> filename.equals("hibernate-default.properties") ? "default" : filename.substring("hibernate-".length(), filename.length() - ".properties".length()))
//                        .collect(Collectors.toSet());
//            }
//
//            // Log the loaded tenant files
//            if (tenantFiles.isEmpty()) {
//                logger.warn("No tenant properties files found.");
//            } else {
//                logger.info("Loaded tenant properties files: " + tenantFiles);
//            }
//
//        } catch (IOException | URISyntaxException e) {
//            logger.error("Error scanning for tenant properties files", e);
//            return Collections.emptySet();
//        }
//
//        return tenantFiles;
//    }
//
//
//}
