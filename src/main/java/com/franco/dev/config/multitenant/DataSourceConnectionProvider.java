//package com.franco.dev.config.multitenant;
//
//import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
//
//import javax.sql.DataSource;
//import java.sql.Connection;
//import java.sql.SQLException;
//
//public class DataSourceConnectionProvider implements ConnectionProvider {
//
//    private final DataSource dataSource;
//
//    public DataSourceConnectionProvider(DataSource dataSource) {
//        this.dataSource = dataSource;
//    }
//
//    @Override
//    public Connection getConnection() throws SQLException {
//        return dataSource.getConnection();
//    }
//
//    @Override
//    public void closeConnection(Connection conn) throws SQLException {
//        if (conn != null && !conn.isClosed()) {
//            conn.close();
//        }
//    }
//
//    @Override
//    public boolean supportsAggressiveRelease() {
//        return false;
//    }
//
//    @Override
//    public boolean isUnwrappableAs(Class unwrapType) {
//        return ConnectionProvider.class.equals(unwrapType) || DataSource.class.isAssignableFrom(unwrapType);
//    }
//
//    @Override
//    public <T> T unwrap(Class<T> unwrapType) {
//        if (DataSource.class.isAssignableFrom(unwrapType)) {
//            return (T) dataSource;
//        }
//        throw new IllegalArgumentException("Unknown unwrap type: " + unwrapType);
//    }
//}
//
