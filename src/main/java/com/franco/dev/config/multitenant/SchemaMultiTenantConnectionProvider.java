package com.franco.dev.config.multitenant;

import graphql.GraphQLException;
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class SchemaMultiTenantConnectionProvider extends AbstractMultiTenantConnectionProvider {

    public static final String HIBERNATE_PROPERTIES_PATH = "/hibernate-%s.properties";
    private final Map<String, ConnectionProvider> connectionProviderMap;

    @Autowired
    private TenantContext tenantContext;

    public SchemaMultiTenantConnectionProvider() {
        this.connectionProviderMap = new HashMap<String, ConnectionProvider>();
    }

    @Override
    protected ConnectionProvider getAnyConnectionProvider() throws GraphQLException {
        return getConnectionProvider(TenantContext.DEFAULT_TENANT_ID);
    }

    @Override
    protected ConnectionProvider selectConnectionProvider(String tenantIdentifier) throws GraphQLException {
        return getConnectionProvider(tenantIdentifier);
    }

    private ConnectionProvider getConnectionProvider(String tenantIdentifier) throws GraphQLException {
        return Optional.ofNullable(tenantIdentifier)
                .map(connectionProviderMap::get)
                .orElseGet(() -> createNewConnectionProvider(tenantIdentifier));
    }

    private ConnectionProvider createNewConnectionProvider(String tenantIdentifier) throws GraphQLException {
        return Optional.ofNullable(tenantIdentifier)
                .map(this::createConnectionProvider)
                .map(connectionProvider -> {
                    connectionProviderMap.put(tenantIdentifier, connectionProvider);
                    return connectionProvider;
                })
                .orElseThrow(() -> new ConnectionProviderException(String.format("No se pudo establecer conexion con: %s", tenantIdentifier)));
    }

    private ConnectionProvider createConnectionProvider(String tenantIdentifier) {
        return Optional.ofNullable(tenantIdentifier)
                .map(this::getHibernatePropertiesForTenantId)
                .map(this::initConnectionProvider)
                .orElse(null);
    }

    private Properties getHibernatePropertiesForTenantId(String tenantId) {
        try {
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream(String.format(HIBERNATE_PROPERTIES_PATH, tenantId)));
            return properties;
        } catch (NullPointerException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ConnectionProvider initConnectionProvider(Properties hibernateProperties) {
        DriverManagerConnectionProviderImpl connectionProvider = new DriverManagerConnectionProviderImpl();
        connectionProvider.configure(hibernateProperties);
        return connectionProvider;
    }
}
