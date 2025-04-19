package com.aichatapp.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Handles MySQL database connections with proper logging and validation
 */
public class DatabaseConnection {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/aichat_db";
    private static final String USER = "root";
    private static final String PASS = "";

    static {
        initializeDriver();
    }

    /**
     * Loads the MySQL JDBC driver and verifies it's available
     */
    private static void initializeDriver() {
        try {
            Class.forName(JDBC_DRIVER);
            logger.info("MySQL JDBC Driver successfully registered");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found! Include it in your library path.", e);
            throw new RuntimeException("Failed to load MySQL JDBC driver", e);
        }
    }

    /**
     * Establishes a new database connection
     * @return Connection object
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);

            // Test the connection
            if (conn.isValid(2)) { // 2 second timeout for validation
                logger.info("Successfully connected to database: {}", DB_URL);
                return conn;
            } else {
                logger.error("Database connection is invalid");
                throw new SQLException("Failed to establish valid database connection");
            }
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", DB_URL, e);
            throw e;
        }
    }

    /**
     * Validates the database connection configuration
     * @throws RuntimeException if configuration is invalid
     */
    public static void validateConfiguration() {
        if (DB_URL == null || DB_URL.isEmpty()) {
            throw new RuntimeException("Database URL is not configured");
        }
        if (USER == null || USER.isEmpty()) {
            throw new RuntimeException("Database username is not configured");
        }
        if (PASS == null) {
            throw new RuntimeException("Database password is not configured");
        }
        logger.info("Database configuration validated successfully");
    }
}