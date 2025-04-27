package com.aichatapp;

import com.aichatapp.models.ChatMessage;
import com.aichatapp.models.DatabaseConnection;
import com.aichatapp.services.ChatService;
import com.aichatapp.services.SessionService;
import com.aichatapp.services.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
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
                            case "get_sessions":
                                handleGetSessions(jsonRequest, response);
                                break;

                            case "create_session":
                                handleCreateSession(jsonRequest, response);
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
            // Get the session ID from the request
            int sessionId = request.get("sessionId").getAsInt();
            String message = request.get("message").getAsString();

            // Validate session ID
            if (sessionId <= 0) {
                logger.warn("Invalid session ID provided: {}", sessionId);

                // Create a new session for this user if needed
                // Assuming you have the username stored somewhere or passed in the request
                String username = request.has("username") ? request.get("username").getAsString() : "anonymous";

                try {
                    // First check if this user exists in the database
                    // If not, you might want to create a temporary user
                    int userId = 1; // Default to a system user ID for demo purposes

                    // Create a new session
                    SessionService sessionService = new SessionService();
                    sessionId = sessionService.createSession(userId, "Default Session");

                    if (sessionId <= 0) {
                        logger.error("Failed to create a new session");
                        response.addProperty("success", false);
                        response.addProperty("error", "Failed to create chat session");
                        return;
                    }

                    logger.info("Created new session {} for message handling", sessionId);
                } catch (Exception e) {
                    logger.error("Error creating session", e);
                    response.addProperty("success", false);
                    response.addProperty("error", "Session creation error");
                    return;
                }
            }

            try {
                // Now call the AI API
                String aiResponse = callAIApi(message);

                // Save the message with the valid session ID
                boolean saveResult = chatService.saveMessage(sessionId, message, aiResponse);

                response.addProperty("success", saveResult);
                response.addProperty("aiResponse", aiResponse);
                response.addProperty("sessionId", sessionId); // Send back the session ID that was used

            } catch (Exception e) {
                logger.error("Message handling failed", e);
                response.addProperty("success", false);
                response.addProperty("error", "Message processing error");
            }
        }

        private void handleGetSessions(JsonObject request, JsonObject response) {
            String username = request.get("username").getAsString();

            try {
                // Get the user ID
                int userId = userService.getUserIdByUsername(username);
                if (userId == -1) {
                    response.addProperty("success", false);
                    response.addProperty("error", "User not found");
                    return;
                }

                // Get sessions for this user
                SessionService sessionService = new SessionService();
                List<JsonObject> sessions = sessionService.getUserSessions(userId);

                response.addProperty("success", true);
                response.add("sessions", gson.toJsonTree(sessions));

            } catch (Exception e) {
                logger.error("Failed to get sessions for user: {}", username, e);
                response.addProperty("success", false);
                response.addProperty("error", "Error retrieving sessions");
            }
        }

        private void handleCreateSession(JsonObject request, JsonObject response) {
            String username = request.get("username").getAsString();
            String sessionName = request.get("sessionName").getAsString();

            try {
                // Get the user ID
                int userId = userService.getUserIdByUsername(username);
                if (userId == -1) {
                    response.addProperty("success", false);
                    response.addProperty("error", "User not found");
                    return;
                }

                // Create the session
                SessionService sessionService = new SessionService();
                int sessionId = sessionService.createSession(userId, sessionName);

                response.addProperty("success", sessionId > 0);
                response.addProperty("sessionId", sessionId);

            } catch (Exception e) {
                logger.error("Failed to create session for user: {}", username, e);
                response.addProperty("success", false);
                response.addProperty("error", "Error creating session");
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
        final String API_KEY = "";
        private final List<JsonObject> messageHistory = new ArrayList<>();

        private String callAIApi(String userMessage) {
            try {
                URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Add the user's message to the history
                JsonObject userMessageObj = new JsonObject();
                userMessageObj.addProperty("role", "user");
                userMessageObj.addProperty("content", userMessage);
                messageHistory.add(userMessageObj);

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "llama3-8b-8192");

                JsonArray messagesArray = new JsonArray();
                for (JsonObject message : messageHistory) {
                    messagesArray.add(message);
                }

                requestBody.add("messages", messagesArray);
                requestBody.addProperty("temperature", 0.7);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int status = connection.getResponseCode();
                InputStream stream = (status >= 200 && status < 300) ? connection.getInputStream() : connection.getErrorStream();
                String responseBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

                JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
                JsonArray choices = response.getAsJsonArray("choices");

                if (choices == null || choices.size() == 0) {
                    System.err.println("Invalid API response, 'choices' missing: " + responseBody);
                    throw new RuntimeException("AI API response invalid: no choices found.");
                }

                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject assistantMessage = firstChoice.getAsJsonObject("message");
                String assistantReply = assistantMessage.get("content").getAsString().trim();

                // Save the assistant's reply into the conversation history
                JsonObject assistantMessageObj = new JsonObject();
                assistantMessageObj.addProperty("role", "assistant");
                assistantMessageObj.addProperty("content", assistantReply);
                messageHistory.add(assistantMessageObj);

                return assistantReply;

            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to call Groq AI API", e);
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
                logger.debug("Raw response to parse: {}", responseBody);
                JsonObject jsonResponse = new Gson().fromJson(responseBody, JsonObject.class);

                if (jsonResponse == null) {
                    logger.error("Null JSON response");
                    return "Error: Invalid API response format";
                }

                if (!jsonResponse.has("choices")) {
                    logger.error("Missing 'choices' in response: {}", jsonResponse);
                    return "Error: Unexpected API response structure";
                }

                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices.size() == 0) {
                    logger.error("Empty choices array");
                    return "Error: AI returned no choices";
                }

                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                logger.debug("First choice: {}", firstChoice);

                if (!firstChoice.has("message")) {
                    logger.error("No message in first choice: {}", firstChoice);
                    return "Error: Invalid response format";
                }

                JsonObject message = firstChoice.getAsJsonObject("message");
                logger.debug("Message object: {}", message);

                if (message == null || !message.has("content")) {
                    logger.error("Missing content in message: {}", message);
                    return "Error: Missing content in AI response";
                }

                String content = message.get("content").getAsString();
                logger.debug("Extracted content: {}", content);
                return content;

            } catch (Exception e) {
                logger.error("Failed to parse API response", e);
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