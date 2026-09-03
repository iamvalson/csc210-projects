package com.com.CSC210.Ayogame;

import javax.swing.*;
import java.awt.*;

public final class AyoGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JButton[] pitButtons = new JButton[12];
    private final JLabel statusLabel = new JLabel("Game Initializing...", SwingConstants.CENTER);

    public AyoGUI() {
        setTitle("CSC 210 - Concurrent Ayo Game");
        setSize(650, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center window on screen
        setLayout(new BorderLayout(10, 10));

        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(statusLabel, BorderLayout.NORTH);

        JPanel boardPanel = new JPanel(new GridLayout(2, 6, 8, 8));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create buttons for 12 pits
        for (int i = 0; i < 12; i++) {
            pitButtons[i] = new JButton("Pit " + i + ": 4");
            pitButtons[i].setFont(new Font("SansSerif", Font.BOLD, 14));
            pitButtons[i].setFocusable(false);
            pitButtons[i].setEnabled(false); // Display mode for automated player threads
        }

        // Top Row: Player 2 Pits (11 down to 6)
        for (int i = 11; i >= 6; i--) {
            pitButtons[i].setBackground(new Color(220, 230, 242));
            boardPanel.add(pitButtons[i]);
        }

        // Bottom Row: Player 1 Pits (0 up to 5)
        for (int i = 0; i <= 5; i++) {
            pitButtons[i].setBackground(new Color(230, 242, 220));
            boardPanel.add(pitButtons[i]);
        }

        add(boardPanel, BorderLayout.CENTER);
        setVisible(true);
    }

    // Thread-safe Swing UI update method called by AyoBoard
    public void updateDisplay(int[] pits, int p1Score, int p2Score, int currentTurn, boolean isGameOver) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 12; i++) {
                pitButtons[i].setText("Pit " + i + ": " + pits[i]);
            }

            if (isGameOver) {
                String winnerText;
                if (p1Score > p2Score) winnerText = "Player 1 Wins!";
                else if (p2Score > p1Score) winnerText = "Player 2 Wins!";
                else winnerText = "It's a Draw!";

                statusLabel.setText("GAME OVER | Final - P1: " + p1Score + " | P2: " + p2Score + " | " + winnerText);
            } else {
                statusLabel.setText("P1 Score: " + p1Score + " | P2 Score: " + p2Score + " | Active Turn: Player " + currentTurn);
            }
        });
    }
}
