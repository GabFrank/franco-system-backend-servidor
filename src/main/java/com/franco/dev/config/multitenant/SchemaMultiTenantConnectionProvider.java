package com.franco.dev.config.multitenant;

import com.franco.dev.config.RetryableDataSource;
import com.zaxxer.hikari.HikariDataSource;
import graphql.GraphQLException;
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

//@Component
//public class SchemaMultiTenantConnectionProvider extends AbstractMultiTenantConnectionProvider {
//
//    public static final String HIBERNATE_PROPERTIES_PATH = "/hibernate-%s.properties";
//    private final Map<String, ConnectionProvider> connectionProviderMap;
//    private final Logger log = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);
//
//    @Autowired
//    private Environment env;
//
//    @Autowired
//    private TenantContext tenantContext;
//
//    private static String activeProfile = null;
//    private static String externalPath = null;
//
//    public SchemaMultiTenantConnectionProvider() {
//        this.connectionProviderMap = new HashMap<String, ConnectionProvider>();
//    }
//
//    @Override
//    protected ConnectionProvider getAnyConnectionProvider() throws GraphQLException {
//        return getConnectionProvider(TenantContext.DEFAULT_TENANT_ID);
//    }
//
//    @Override
//    protected ConnectionProvider selectConnectionProvider(String tenantIdentifier) throws GraphQLException {
//        return getConnectionProvider(tenantIdentifier);
//    }
//
//    private ConnectionProvider getConnectionProvider(String tenantIdentifier) throws GraphQLException {
//        return Optional.ofNullable(tenantIdentifier)
//                .map(connectionProviderMap::get)
//                .orElseGet(() -> createNewConnectionProvider(tenantIdentifier));
//    }
//
////    private ConnectionProvider createNewConnectionProvider(String tenantIdentifier) {
////        try {
////            return Optional.ofNullable(tenantIdentifier)
////                    .map(this::createConnectionProvider)
////                    .map(connectionProvider -> {
////                        connectionProviderMap.put(tenantIdentifier, connectionProvider);
////                        return connectionProvider;
////                    }).orElse(null);
////        } catch (Exception e) {
////            log.info("No se pudo crear una nueva conexion: " + tenantIdentifier);
////            e.printStackTrace();
////            return null;
////        }
////    }
//
//    private ConnectionProvider createNewConnectionProvider(String tenantIdentifier) {
//        try {
//            return Optional.ofNullable(tenantIdentifier)
//                    .map(this::createConnectionProvider)
//                    .map(connectionProvider -> {
//                        connectionProviderMap.put(tenantIdentifier, wrapWithRetryable(connectionProvider));
//                        return connectionProvider;
//                    }).orElse(null);
//        } catch (Exception e) {
//            log.info("Failed to create a new connection: " + tenantIdentifier);
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    private ConnectionProvider wrapWithRetryable(ConnectionProvider connectionProvider) {
//        return new RetryableConnectionProvider(connectionProvider);
//    }
//
////    private ConnectionProvider createConnectionProvider(String tenantIdentifier) {
////        return Optional.ofNullable(tenantIdentifier)
////                .map(this::getHibernatePropertiesForTenantId)
////                .map(this::initConnectionProvider)
////                .orElse(null);
////    }
//
//    private ConnectionProvider createConnectionProvider(String tenantIdentifier) {
//        Properties tenantProperties = getHibernatePropertiesForTenantId(tenantIdentifier);
//
//        HikariDataSource dataSource = new HikariDataSource();
//        dataSource.setJdbcUrl(tenantProperties.getProperty("hibernate.connection.url"));
//        dataSource.setUsername(tenantProperties.getProperty("hibernate.connection.username"));
//        dataSource.setPassword(tenantProperties.getProperty("hibernate.connection.password"));
//        dataSource.setDriverClassName(tenantProperties.getProperty("hibernate.connection.driver_class"));
//
//        // Optional: Configure additional HikariCP settings
//        dataSource.setMaximumPoolSize(10);
//        dataSource.setMinimumIdle(5);
//        dataSource.setIdleTimeout(30000);
//        dataSource.setConnectionTimeout(20000);
//
//        // Create a custom ConnectionProvider or use DriverManagerConnectionProvider
//        ConnectionProvider connectionProvider = new DataSourceConnectionProvider(dataSource);
//
//        // Now wrap it with your RetryableConnectionProvider
//        return new RetryableConnectionProvider(connectionProvider);
//    }
//
//    private Properties getHibernatePropertiesForTenantId(String tenantId) {
//        Properties properties = new Properties();
//
//        try {
//            if ("prod".equals(activeProfile) && "default".equals(tenantId)) {
//                // Load default properties from external file in production
//                Path defaultFilePath = Paths.get(externalPath, "hibernate-default.properties");
//
//                log.info("Loading default tenant properties from: " + defaultFilePath.toAbsolutePath());
//                properties.load(Files.newInputStream(defaultFilePath));
//            } else {
//                // Load tenant-specific properties or from internal JAR
//                String resourcePath = String.format(HIBERNATE_PROPERTIES_PATH, tenantId);
//                log.info("Loading tenant properties from resource path: " + resourcePath);
//                properties.load(getClass().getResourceAsStream(resourcePath));
//            }
//        } catch (IOException e) {
//            log.error("Error loading properties for tenantId: " + tenantId, e);
//            return null;
//        }
//
//        return properties;
//    }
//
//    private ConnectionProvider initConnectionProvider(Properties hibernateProperties) {
//        DriverManagerConnectionProviderImpl connectionProvider = new DriverManagerConnectionProviderImpl();
//        connectionProvider.configure(hibernateProperties);
//        return connectionProvider;
//    }
//
//    @PostConstruct
//    public void getResources() {
//        String profile = env.getProperty("spring.profiles.active");
//        String path = env.getProperty("external.properties.path");
//        if(profile!=null){
//            activeProfile = profile;
//        }
//        if(path!= null){
//            externalPath = path;
//        }
//    }
//}