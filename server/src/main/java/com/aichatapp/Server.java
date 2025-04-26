package com.aichatapp;

import com.aichatapp.models.ChatMessage;
import com.aichatapp.models.DatabaseConnection;
import com.aichatapp.services.ChatService;
import com.aichatapp.services.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;
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
            // Init db conn
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

            // Add validation
            if (sessionId <= 0) {
                logger.error("Invalid session ID: {}", sessionId);
                response.addProperty("success", false);
                response.addProperty("error", "Invalid chat session");
                return;
            }

            String message = request.get("message").getAsString();

            try {
                String aiResponse = callAIApi(message);
                boolean saveResult = chatService.saveMessage(sessionId, message, aiResponse);
                response.addProperty("success", saveResult);
                response.addProperty("aiResponse", aiResponse);
            } catch (Exception e) {
                logger.error("Message handling failed", e);
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
            final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
            final String API_KEY = "api key";
            final String MODEL = "llama-3-3-70b-versatile";
            final Logger logger = LoggerFactory.getLogger(Server.class);

            // 1. Validate inputs and configuration
            if (message == null || message.trim().isEmpty()) {
                logger.error("Empty message received");
                return "Error: Message cannot be empty";
            }

            if (API_KEY == null || API_KEY.isEmpty()) {
                logger.error("API key not configured");
                return "Error: API configuration issue";
            }

            // 2. Create HTTP client with timeouts
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(10000)  // 10 seconds
                    .setSocketTimeout(30000)   // 30 seconds
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .build()) {

                // 3. Build the request
                HttpPost httpPost = new HttpPost(API_URL);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("Authorization", "Bearer " + API_KEY);
                httpPost.setHeader("Accept", "application/json");

                // Construct the request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", MODEL);
                requestBody.addProperty("temperature", 0.7);
                requestBody.addProperty("max_tokens", 1024);

                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", "You are a helpful AI assistant.");

                JsonObject userMessage = new JsonObject();
                userMessage.addProperty("role", "user");
                userMessage.addProperty("content", message);

                JsonArray messages = new JsonArray();
                messages.add(systemMessage);
                messages.add(userMessage);

                requestBody.add("messages", messages);

                logger.debug("Sending request to {} with model {}", API_URL, MODEL);
                logger.trace("Request body: {}", requestBody.toString());

                httpPost.setEntity(new StringEntity(requestBody.toString()));

                // 4. Execute and process response
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    logger.debug("Received status code: {}", statusCode);
                    logger.trace("Raw response: {}", responseBody);

                    if (statusCode != 200) {
                        logger.error("API request failed with status {}", statusCode);
                        return parseErrorResponse(responseBody);
                    }

                    return parseSuccessResponse(responseBody);
                }

            } catch (IOException e) {
                logger.error("Network error during API call", e);
                return "Error: Network connection failed";
            } catch (Exception e) {
                logger.error("Unexpected error during API call", e);
                return "Error: Processing failed";
            }
        }


        private String parseErrorResponse(String responseBody) {
            try {
                JsonObject jsonResponse = new Gson().fromJson(responseBody, JsonObject.class);
                if (jsonResponse != null && jsonResponse.has("error")) {
                    JsonObject error = jsonResponse.getAsJsonObject("error");
                    String errorMsg = error.has("message") ? error.get("message").getAsString() : "Unknown error";
                    return "API Error: " + errorMsg;
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(Server.class).error("Failed to parse error response", e);
            }
            return "Error: Invalid API response";
        }

        private String parseSuccessResponse(String responseBody) {
            try {
                JsonObject jsonResponse = new Gson().fromJson(responseBody, JsonObject.class);

                if (jsonResponse == null || !jsonResponse.has("choices")) {
                    throw new IllegalStateException("Missing choices in response");
                }

                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices.size() == 0) {
                    throw new IllegalStateException("Empty choices array");
                }

                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");

                if (message == null || !message.has("content")) {
                    throw new IllegalStateException("Missing message content");
                }

                return message.get("content").getAsString();

            } catch (Exception e) {
                LoggerFactory.getLogger(Server.class).error("Failed to parse API response", e);
                return "Error: Could not process AI response";
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