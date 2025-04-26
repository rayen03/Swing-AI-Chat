package com.aichatapp.services;

import com.aichatapp.models.ChatMessage;
import com.aichatapp.models.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.aichatapp.Server.logger;

public class ChatService {
    public int createChatSession(int userId, String sessionName) {
        String sql = "INSERT INTO chat_sessions (user_id, session_name) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, sessionName);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean saveMessage(int sessionId, String userMessage, String aiResponse) {
        String sql = "INSERT INTO chat_messages (session_id, user_message, ai_response, is_user_message) VALUES (?, ?, ?, ?)";
        if (!sessionExists(sessionId)) {
            logger.error("Attempt to save message to non-existent session: {}", sessionId);
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, sessionId);
            stmt.setString(2, userMessage);
            stmt.setString(3, aiResponse);
            stmt.setBoolean(4, true);
            stmt.executeUpdate();

            // Save AI response as a separate message
            stmt.setString(2, null);
            stmt.setString(3, aiResponse);
            stmt.setBoolean(4, false);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<ChatMessage> getChatHistory(int sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, sessionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(new ChatMessage(
                        rs.getInt("message_id"),
                        rs.getInt("session_id"),
                        rs.getString("user_message"),
                        rs.getString("ai_response"),
                        rs.getBoolean("is_user_message"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }
    private boolean sessionExists(int sessionId) {
        String sql = "SELECT 1 FROM chat_sessions WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, sessionId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Session validation failed", e);
            return false;
        }
    }
}