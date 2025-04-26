package com.aichatapp.services;

import com.aichatapp.models.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class SessionService {
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    public int createSession(int userId, String sessionName) {
        String sql = "INSERT INTO chat_sessions (user_id, session_name) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, userId);
            stmt.setString(2, sessionName != null ? sessionName : "New Chat");

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating session failed, no rows affected");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newSessionId = generatedKeys.getInt(1);
                    logger.info("Created new session ID {} for user {}", newSessionId, userId);
                    return newSessionId;
                }
            }

            throw new SQLException("Creating session failed, no ID obtained");

        } catch (SQLException e) {
            logger.error("Failed to create session for user {}", userId, e);
            return -1; // Indicate failure
        }
    }

    public boolean validateSession(int userId, int sessionId) {
        String sql = "SELECT 1 FROM chat_sessions WHERE session_id = ? AND user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            stmt.setInt(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Returns true if session exists and belongs to user
            }

        } catch (SQLException e) {
            logger.error("Session validation failed", e);
            return false;
        }
    }
}