package com.aichatapp;

import com.aichatapp.models.ChatMessage;
import com.aichatapp.models.DatabaseConnection;
import com.aichatapp.services.ChatService;
import com.aichatapp.services.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT = 8080;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private UserService userService;
    private ChatService chatService;

    public Server() {
        try {
            // Initialize database connection first
            DatabaseConnection.validateConfiguration();
            logger.info("Database configuration validated");

            // Test database connection
            try (var conn = DatabaseConnection.getConnection()) {
                logger.info("Database connection test successful");
            }

            this.userService = new UserService();
            this.chatService = new ChatService();
            this.executorService = Executors.newFixedThreadPool(10);

        } catch (SQLException e) {
            logger.error("Failed to initialize database connection", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            logger.info("Server started successfully on port {}", PORT);

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.debug("New client connected: {}", clientSocket.getInetAddress());
                    executorService.execute(new ClientHandler(clientSocket, userService, chatService));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        logger.error("Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start server on port {}", PORT, e);
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        logger.info("Shutting down server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final UserService userService;
        private final ChatService chatService;
        private final Gson gson;
        private final Logger logger;

        public ClientHandler(Socket socket, UserService userService, ChatService chatService) {
            this.clientSocket = socket;
            this.userService = userService;
            this.chatService = chatService;
            this.gson = new Gson();
            this.logger = LoggerFactory.getLogger(ClientHandler.class);
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String request;
                while ((request = in.readLine()) != null) {
                    try {
                        JsonObject jsonRequest = gson.fromJson(request, JsonObject.class);
                        String action = jsonRequest.get("action").getAsString();
                        JsonObject response = new JsonObject();

                        switch (action) {
                            case "login":
                                handleLogin(jsonRequest, response);
                                break;

                            case "register":
                                handleRegistration(jsonRequest, response);
                                break;

                            case "send_message":
                                handleMessage(jsonRequest, response);
                                break;

                            case "get_history":
                                handleHistoryRequest(jsonRequest, response);
                                break;

                            default:
                                response.addProperty("success", false);
                                response.addProperty("error", "Unknown action");
                                break;
                        }

                        out.println(gson.toJson(response));
                    } catch (Exception e) {
                        logger.error("Error processing client request", e);
                        JsonObject errorResponse = new JsonObject();
                        errorResponse.addProperty("success", false);
                        errorResponse.addProperty("error", "Internal server error");
                        out.println(gson.toJson(errorResponse));
                    }
                }
            } catch (IOException e) {
                logger.error("Client connection error", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logger.warn("Error closing client socket", e);
                }
            }
        }

        private void handleLogin(JsonObject request, JsonObject response) {
            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();

            try {
                boolean authResult = userService.authenticateUser(username, password);
                response.addProperty("success", authResult);
                logger.info("Login attempt for user {}: {}", username, authResult ? "success" : "failure");
            } catch (Exception e) {
                logger.error("Login failed for user: {}", username, e);
                response.addProperty("success", false);
                response.addProperty("error", "Authentication error");
            }
        }

        private void handleRegistration(JsonObject request, JsonObject response) {
            String username = request.get("username").getAsString();
            String email = request.get("email").getAsString();
            String password = request.get("password").getAsString();

            logger.debug("Registration attempt - Username: {}, Email: {}", username, email);

            try {
                boolean regResult = userService.registerUser(username, email, password);
                response.addProperty("success", regResult);

                if (!regResult) {
                    // Check if it's a duplicate error
                    if (userService.userExists(username, email)) {
                        response.addProperty("error", "Username or email already exists");
                        logger.warn("Duplicate registration attempt: {} / {}", username, email);
                    } else {
                        response.addProperty("error", "Registration failed");
                    }
                }
            } catch (Exception e) {
                logger.error("Registration exception", e);
                response.addProperty("success", false);
                response.addProperty("error", "Server error during registration");
            }
        }

        private void handleMessage(JsonObject request, JsonObject response) {
            int sessionId = request.get("sessionId").getAsInt();
            String message = request.get("message").getAsString();

            try {
                String aiResponse = callAIApi(message);
                boolean saveResult = chatService.saveMessage(sessionId, message, aiResponse);
                response.addProperty("success", saveResult);
                response.addProperty("aiResponse", aiResponse);
                logger.debug("Message processed for session {}: {}", sessionId, message);
            } catch (Exception e) {
                logger.error("Failed to process message for session {}", sessionId, e);
                response.addProperty("success", false);
                response.addProperty("error", "Message processing error");
            }
        }

        private void handleHistoryRequest(JsonObject request, JsonObject response) {
            int sessionId = request.get("sessionId").getAsInt();

            try {
                List<ChatMessage> history = chatService.getChatHistory(sessionId);
                response.add("history", gson.toJsonTree(history));
                response.addProperty("success", true);
                logger.debug("Retrieved history for session {}", sessionId);
            } catch (Exception e) {
                logger.error("Failed to retrieve history for session {}", sessionId, e);
                response.addProperty("success", false);
                response.addProperty("error", "History retrieval error");
            }
        }

        private String callAIApi(String message) {
            try {
                // Implement actual API call here
                // This is a placeholder implementation
                String response = "This is a simulated AI response to: " + message;
                logger.debug("AI API called with message: {}", message);
                return response;
            } catch (Exception e) {
                logger.error("AI API call failed", e);
                return "Sorry, I couldn't process your request due to an error.";
            }
        }
    }

    public static void main(String[] args) {
        try {
            logger.info("Starting AI Chat Server...");
            new Server().start();
        } catch (Exception e) {
            logger.error("Server startup failed", e);
            System.exit(1);
        }
    }
}