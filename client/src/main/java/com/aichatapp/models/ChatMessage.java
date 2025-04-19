package com.aichatapp.models;

import java.time.LocalDateTime;

/**
 * Represents a single message in a chat conversation
 * Can be either a user message or AI response
 */
public class ChatMessage {
    private int messageId;
    private int sessionId;
    private String userMessage;
    private String aiResponse;
    private boolean isUserMessage;
    private LocalDateTime timestamp;

    // Constructors
    public ChatMessage() {
        // Default constructor
    }

    public ChatMessage(int messageId, int sessionId, String userMessage,
                       String aiResponse, boolean isUserMessage, LocalDateTime timestamp) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.aiResponse = aiResponse;
        this.isUserMessage = isUserMessage;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public void setAiResponse(String aiResponse) {
        this.aiResponse = aiResponse;
    }

    public boolean isUserMessage() {
        return isUserMessage;
    }

    public void setUserMessage(boolean userMessage) {
        isUserMessage = userMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // Utility method to get the appropriate message content
    public String getContent() {
        return isUserMessage ? userMessage : aiResponse;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "messageId=" + messageId +
                ", sessionId=" + sessionId +
                ", isUserMessage=" + isUserMessage +
                ", timestamp=" + timestamp +
                ", content='" + getContent() + '\'' +
                '}';
    }
}