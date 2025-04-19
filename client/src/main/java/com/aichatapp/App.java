package com.aichatapp;

import com.aichatapp.controllers.ClientController;
import com.aichatapp.views.ChatView;
import com.aichatapp.views.LoginView;

import javax.swing.*;
import java.awt.*;

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
            // For now, just add a dummy session
            chatView.addSession("Default Session");
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
            // In a real app, you would load the chat history for this session
            chatView.clearChat();
            chatView.appendMessage("System", "Session " + (selectedIndex + 1) + " selected");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App());
    }
}