//package com.franco.dev.config;
//
//import org.postgresql.PGConnection;
//import org.postgresql.PGNotification;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.retry.annotation.Backoff;
//import org.springframework.retry.annotation.Recover;
//import org.springframework.retry.annotation.Retryable;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import javax.sql.DataSource;
//import java.sql.Connection;
//import java.sql.SQLException;
//import java.sql.Statement;
//
//@Service
//public class PostgresNotificationListener {
//
//    private static final Logger logger = LoggerFactory.getLogger(PostgresNotificationListener.class);
//
//    @Autowired
//    private DataSource dataSource;
//
//    @PostConstruct
//    @Retryable(
//            value = {SQLException.class},
//            maxAttempts = 5,
//            backoff = @Backoff(delay = 2000)
//    )
//    public void startListening() {
//        new Thread(() -> {
//            try (Connection connection = dataSource.getConnection()) {
//                // Unwrap to PGConnection to access PostgreSQL-specific functionality
//                PGConnection pgConnection = connection.unwrap(PGConnection.class);
//
//                try (Statement stmt = connection.createStatement()) {
//                    stmt.execute("LISTEN error_event_channel");
//                    logger.info("Started listening to channel: error_event_channel");
//
//                    while (true) {
//                        // Poll for notifications
//                        PGNotification[] notifications = pgConnection.getNotifications();
//
//                        if (notifications != null && notifications.length > 0) {
//                            for (PGNotification notification : notifications) {
//                                logger.info("Received notification: " + notification.getParameter());
//                                handleNotification(notification.getParameter());
//                            }
//                        }
//
//                        // Sleep for a short while to avoid busy-waiting
//                        try {
//                            Thread.sleep(500);
//                        } catch (InterruptedException e) {
//                            logger.error("Listening thread interrupted: " + e.getMessage());
//                            Thread.currentThread().interrupt();
//                            break; // Exit loop if interrupted
//                        }
//                    }
//                }
//            } catch (SQLException e) {
//                logger.error("SQL error while listening for notifications: " + e.getMessage());
//            }
//        }).start();
//    }
//
//
//    private void handleNotification(String payload) {
//        // Handle the notification payload as needed
//        logger.info("Handling notification payload: " + payload);
//        // Further processing logic here...
//    }
//
//    @Recover
//    public void recover(SQLException e) {
//        logger.error("Failed to start listening after retries: " + e.getMessage());
//        // Additional recovery logic here
//    }
//}
//
//
