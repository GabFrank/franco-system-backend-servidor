//package com.franco.dev.config.multitenant;
//
//import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
//import org.springframework.retry.annotation.Backoff;
//import org.springframework.retry.annotation.Retryable;
//
//import java.sql.Connection;
//import java.sql.SQLException;
//
//public class RetryableConnectionProvider implements ConnectionProvider {
//
//    private final ConnectionProvider delegate;
//
//    public RetryableConnectionProvider(ConnectionProvider delegate) {
//        this.delegate = delegate;
//    }
//
//    @Override
//    @Retryable(maxAttempts = 10, backoff = @Backoff(multiplier = 1.3, maxDelay = 10000))
//    public Connection getConnection() throws SQLException {
//        return delegate.getConnection();
//    }
//
//    @Override
//    public void closeConnection(Connection conn) throws SQLException {
//        delegate.closeConnection(conn);
//    }
//
//    @Override
//    public boolean supportsAggressiveRelease() {
//        return delegate.supportsAggressiveRelease();
//    }
//
//    @Override
//    public boolean isUnwrappableAs(Class aClass) {
//        return false;
//    }
//
//    @Override
//    public <T> T unwrap(Class<T> aClass) {
//        return null;
//    }
//}
