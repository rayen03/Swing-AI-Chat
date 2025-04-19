package com.aichatapp.views;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class LoginView extends JPanel {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField emailField; // For registration
    private JButton loginButton;
    private JButton signupButton;
    private JButton switchToSignupButton;
    private JButton switchToLoginButton;
    private CardLayout cardLayout;
    private JPanel cards;

    public LoginView(ActionListener loginAction, ActionListener signupAction) {
        setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);

        // Login Panel
        JPanel loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        loginPanel.add(new JLabel("Username:"), gbc);

        gbc.gridy++;
        usernameField = new JTextField(20);
        loginPanel.add(usernameField, gbc);

        gbc.gridy++;
        loginPanel.add(new JLabel("Password:"), gbc);

        gbc.gridy++;
        passwordField = new JPasswordField(20);
        loginPanel.add(passwordField, gbc);

        gbc.gridy++;
        loginButton = new JButton("Login");
        loginButton.addActionListener(loginAction);
        loginPanel.add(loginButton, gbc);

        gbc.gridy++;
        switchToSignupButton = new JButton("Create new account");
        switchToSignupButton.addActionListener(e -> cardLayout.show(cards, "SIGNUP"));
        loginPanel.add(switchToSignupButton, gbc);

        // Signup Panel
        JPanel signupPanel = new JPanel(new GridBagLayout());

        gbc.gridx = 0;
        gbc.gridy = 0;
        signupPanel.add(new JLabel("Username:"), gbc);

        gbc.gridy++;
        JTextField signupUsernameField = new JTextField(20);
        signupPanel.add(signupUsernameField, gbc);

        gbc.gridy++;
        signupPanel.add(new JLabel("Email:"), gbc);

        gbc.gridy++;
        emailField = new JTextField(20);
        signupPanel.add(emailField, gbc);

        gbc.gridy++;
        signupPanel.add(new JLabel("Password:"), gbc);

        gbc.gridy++;
        JPasswordField signupPasswordField = new JPasswordField(20);
        signupPanel.add(signupPasswordField, gbc);

        gbc.gridy++;
        signupButton = new JButton("Sign Up");
        signupButton.addActionListener(signupAction);
        signupPanel.add(signupButton, gbc);

        gbc.gridy++;
        switchToLoginButton = new JButton("Already have an account? Login");
        switchToLoginButton.addActionListener(e -> cardLayout.show(cards, "LOGIN"));
        signupPanel.add(switchToLoginButton, gbc);

        cards.add(loginPanel, "LOGIN");
        cards.add(signupPanel, "SIGNUP");

        add(cards, BorderLayout.CENTER);
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public String getEmail() {
        return emailField.getText();
    }
}