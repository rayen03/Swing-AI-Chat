package com.aichatapp.controllers;

import com.aichatapp.models.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientController {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Gson gson;
    private String currentUsername;
    private int currentSessionId = -1;

    public ClientController() {
        this.gson = new Gson();
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean login(String username, String password) {
        JsonObject request = new JsonObject();
        request.addProperty("action", "login");
        request.addProperty("username", username);
        request.addProperty("password", password);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                currentUsername = username;

                System.out.println("Sending login: User=" + username);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean register(String username, String email, String password) {
        JsonObject request = new JsonObject();
        request.addProperty("action", "register");
        request.addProperty("username", username);
        request.addProperty("email", email);
        request.addProperty("password", password);

        out.println(gson.toJson(request));

        System.out.println("Sending registration: " +
                "User=" + username + ", Email=" + email);



        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            return jsonResponse.get("success").getAsBoolean();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String sendMessage(String message) {
        System.out.println("Sending message with session ID: " + currentSessionId);

        if (currentSessionId == -1) {
            System.out.println("No session selected, creating new one");
            createNewSession("New Chat");
        }

        JsonObject request = new JsonObject();
        request.addProperty("action", "send_message");
        request.addProperty("sessionId", currentSessionId);
        request.addProperty("message", message);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                return jsonResponse.get("aiResponse").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Error: Could not get AI response";
    }

    public int createNewSession(String sessionName) {
        if (currentUsername == null || currentUsername.isEmpty()) {
            System.err.println("Cannot create session: Not logged in");
            return -1;
        }

        JsonObject request = new JsonObject();
        request.addProperty("action", "create_session");
        request.addProperty("username", currentUsername);
        request.addProperty("sessionName", sessionName);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                currentSessionId = jsonResponse.get("sessionId").getAsInt();
                return currentSessionId;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }
    private Map<String, Integer> sessionIdMap = new HashMap<>();
    public List<String> getUserSessions() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            System.err.println("Cannot get sessions: Not logged in");
            return new ArrayList<>();
        }

        JsonObject request = new JsonObject();
        request.addProperty("action", "get_sessions");
        request.addProperty("username", currentUsername);

        System.out.println("Sending get_sessions request for user: " + currentUsername);
        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            System.out.println("Received get_sessions response: " + response);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                // Server returns an array of JsonObjects, not just names
                JsonArray sessionsArray = jsonResponse.getAsJsonArray("sessions");
                List<String> sessionNames = new ArrayList<>();

                // Extract session names and store session IDs
                for (int i = 0; i < sessionsArray.size(); i++) {
                    JsonObject session = sessionsArray.get(i).getAsJsonObject();
                    String sessionName = session.get("name").getAsString();
                    int sessionId = session.get("id").getAsInt();

                    // Store mapping of session name to ID
                    sessionIdMap.put(sessionName, sessionId);
                    sessionNames.add(sessionName);
                }

                return sessionNames;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public boolean selectSession(int sessionId) {
        currentSessionId = sessionId;

        JsonObject request = new JsonObject();
        request.addProperty("action", "select_session");
        request.addProperty("sessionId", sessionId);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            return jsonResponse.get("success").getAsBoolean();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public int getSessionIdByName(String sessionName) {
        // check locally
        if (sessionIdMap.containsKey(sessionName)) {
            return sessionIdMap.get(sessionName);
        }

        // Otherwise query the server
        JsonObject request = new JsonObject();
        request.addProperty("action", "get_session_id");
        request.addProperty("username", currentUsername);
        request.addProperty("sessionName", sessionName);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                int sessionId = jsonResponse.get("sessionId").getAsInt();
                // Cache it for future use
                sessionIdMap.put(sessionName, sessionId);
                return sessionId;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<ChatMessage> getChatHistory(int sessionId) {
        JsonObject request = new JsonObject();
        request.addProperty("action", "get_history");
        request.addProperty("sessionId", sessionId);

        out.println(gson.toJson(request));

        try {
            String response = in.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.get("success").getAsBoolean()) {
                JsonElement historyElement = jsonResponse.get("history");
                Type listType = new TypeToken<List<ChatMessage>>(){}.getType();
                return gson.fromJson(historyElement, listType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

}