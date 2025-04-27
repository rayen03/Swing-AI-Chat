package com.aichatapp.services;

import com.aichatapp.models.DatabaseConnection;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SessionService {
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    public int createSession(int userId, String sessionName) {
        String sql = "INSERT INTO chat_sessions (user_id, session_name, created_at) VALUES (?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, userId);
            stmt.setString(2, sessionName);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating session failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1); // return the generated session_id
                } else {
                    throw new SQLException("Creating session failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create new session", e);
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
    public List<JsonObject> getUserSessions(int userId) {
        List<JsonObject> sessions = new ArrayList<>();
        String sql = "SELECT session_id, session_name, created_at FROM chat_sessions WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject session = new JsonObject();
                    session.addProperty("id", rs.getInt("session_id"));
                    session.addProperty("name", rs.getString("session_name"));
                    session.addProperty("created", rs.getTimestamp("created_at").toString());
                    sessions.add(session);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving sessions for user ID: {}", userId, e);
        }
        return sessions;
    }
}