package com.aichatapp.services;

import com.aichatapp.models.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aichatapp.Server.logger;

public class UserService {
    public boolean registerUser(String username, String email, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // First check if username or email exists
            if (userExists(username, email)) {
                logger.error("User {} or email {} already exists", username, email);
                return false;
            }

            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, hashedPassword);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                logger.error("Failed to create user - no rows affected");
                return false;
            }

            logger.info("Successfully registered user: {}", username);
            return true;
        } catch (SQLException e) {
            logger.error("Registration error for user {}", username, e);
            return false;
        }
    }

    public boolean userExists(String username, String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ? OR email = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Returns true if user exists
            }
        }
    }
    public boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("Login attempt for non-existent user: {}", username);
                    return false;
                }

                String storedHash = rs.getString("password_hash");
                boolean passwordMatch = BCrypt.checkpw(password, storedHash);

                logger.info("Login attempt for user {}: {}", username,
                        passwordMatch ? "success" : "invalid password");
                return passwordMatch;
            }
        } catch (SQLException e) {
            logger.error("Authentication error for user {}", username, e);
            return false;
        }
    }

    private String hashPassword(String password) {

        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean checkPassword(String password, String storedHash) {
        return BCrypt.checkpw(password, storedHash);
    }
}