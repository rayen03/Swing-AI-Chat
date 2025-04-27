package com.aichatapp.views;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

public class ChatView extends JPanel {
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JList<String> sessionsList;
    private DefaultListModel<String> sessionsModel;
    private JButton newSessionButton;

    public ChatView(ActionListener sendAction, ActionListener newSessionAction, ListSelectionListener sessionSelectionListener) {
        setLayout(new BorderLayout());

        // Left panel with sessions
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, getHeight()));

        sessionsModel = new DefaultListModel<>();
        sessionsList = new JList<>(sessionsModel);
        sessionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionsList.addListSelectionListener(sessionSelectionListener);

        newSessionButton = new JButton("New Chat");
        newSessionButton.addActionListener(newSessionAction);

        leftPanel.add(new JScrollPane(sessionsList), BorderLayout.CENTER);
        leftPanel.add(newSessionButton, BorderLayout.SOUTH);

        // Center panel with chat
        JPanel centerPanel = new JPanel(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        sendButton.addActionListener(sendAction);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(inputPanel, BorderLayout.SOUTH);

        // Add panels to main view
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
    }

    public String getInputText() {
        String text = inputField.getText();
        inputField.setText("");
        return text;
    }

    public void appendMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void addSession(String sessionName) {
        sessionsModel.addElement(sessionName);
        // Select the new session
        sessionsList.setSelectedIndex(sessionsModel.size() - 1);
    }
    private Map<String, Integer> sessionIdMap = new HashMap<>();

    public void addSession(String sessionName, int sessionId) {
        sessionsModel.addElement(sessionName);
        sessionIdMap.put(sessionName, sessionId);
    }

    public int getSelectedSessionId() {
        String selectedName = sessionsList.getSelectedValue();
        return sessionIdMap.getOrDefault(selectedName, -1);
    }

    public String getSelectedSessionName() {
        return sessionsList.getSelectedValue();
    }

    public int getSelectedSessionIndex() {
        return sessionsList.getSelectedIndex();
    }

    public void clearChat() {
        chatArea.setText("");
    }
}