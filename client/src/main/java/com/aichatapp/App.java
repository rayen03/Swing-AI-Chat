package com.aichatapp;

import com.aichatapp.controllers.ClientController;
import com.aichatapp.models.ChatMessage;
import com.aichatapp.views.ChatView;
import com.aichatapp.views.LoginView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Hello world!
 *
 */
public class App {
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cards;
    private LoginView loginView;
    private ChatView chatView;
    private ClientController controller;

    public App() {
        controller = new ClientController();
        initializeUI();
    }

    private void initializeUI() {
        frame = new JFrame("AI Chat Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);

        // Login View
        loginView = new LoginView(
                e -> handleLogin(),
                e -> handleSignup()
        );

        // Chat View
        chatView = new ChatView(
                e -> handleSendMessage(),
                e -> handleNewSession(),
                e -> handleSessionSelection()
        );

        cards.add(loginView, "LOGIN");
        cards.add(chatView, "CHAT");

        frame.add(cards);
        frame.setVisible(true);
    }

    private void handleLogin() {
        String username = loginView.getUsername();
        String password = loginView.getPassword();

        if (controller.login(username, password)) {
            // Load user sessions
            ArrayList<String> sessions = (ArrayList<String>) controller.getUserSessions();
            if (sessions.isEmpty()) {
                // Create a default session if user has none
                int sessionId = controller.createNewSession("Default Session");
                if (sessionId != -1) {
                    chatView.addSession("Default Session");
                }
            } else {
                // Add all existing sessions to the view
                for (String session : sessions) {
                    chatView.addSession(session);
                }
            }
            cardLayout.show(cards, "CHAT");
        } else {
            JOptionPane.showMessageDialog(frame, "Login failed. Please check your credentials.");
        }
    }

    private void handleSignup() {
        String username = loginView.getUsername();
        String email = loginView.getEmail();
        String password = loginView.getPassword();

        if (controller.register(username, email, password)) {
            JOptionPane.showMessageDialog(frame, "Registration successful. Please login.");
            cardLayout.show(cards, "LOGIN");
        } else {
            JOptionPane.showMessageDialog(frame, "Registration failed. Username or email may already exist.");
        }
    }

    private void handleSendMessage() {
        String message = chatView.getInputText();
        if (!message.isEmpty()) {
            chatView.appendMessage("You", message);
            String aiResponse = controller.sendMessage(message);
            chatView.appendMessage("AI Assistant", aiResponse);
        }
    }

    private void handleNewSession() {
        String sessionName = JOptionPane.showInputDialog(frame, "Enter session name:");
        if (sessionName != null && !sessionName.trim().isEmpty()) {
            int sessionId = controller.createNewSession(sessionName);
            if (sessionId != -1) {
                chatView.addSession(sessionName);
                chatView.clearChat();
            }
        }
    }

    private void handleSessionSelection() {
        int selectedIndex = chatView.getSelectedSessionIndex();
        if (selectedIndex != -1) {
            String sessionName = chatView.getSelectedSessionName();
            int sessionId = controller.getSessionIdByName(sessionName);

            if (sessionId != -1 && controller.selectSession(sessionId)) {
                chatView.clearChat();

                // Load chat history for this session
                ArrayList<ChatMessage> history = (ArrayList<ChatMessage>) controller.getChatHistory(sessionId);
                for (ChatMessage message : history) {
                    if (message.isUserMessage()) {
                        chatView.appendMessage("You", message.getUserMessage());
                    } else {
                        chatView.appendMessage("AI Assistant", message.getAiResponse());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App());
    }
}