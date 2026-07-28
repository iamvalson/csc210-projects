package game.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

import game.api.GameController;
import game.api.dto.GuessRequest;
import game.api.dto.GuessResponse;
import game.core.GameSessionManager;
import game.server.ServerConfig;

/**
 * Desktop Swing client for Dead or Wounded. Plays directly against the core
 * game engine in-process (its own GameSessionManager) - no TCP/HTTP server
 * needed. For the networked servers, run game.Main instead.
 */
public class GameGui extends JFrame {
    private final GameController controller;

    private String sessionId;
    private int secretLength;
    private int maxAttempts;
    private int guessCount;

    private final JTextField playerNameField = new JTextField("Player", 14);
    private final JButton newGameButton = new JButton("New Game");
    private final JLabel infoLabel = new JLabel(" ");
    private final JTextField guessField = new JTextField(14);
    private final JButton guessButton = new JButton("Guess");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    private final DefaultTableModel historyModel =
            new DefaultTableModel(new Object[]{"#", "Guess", "Dead", "Wounded"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    public GameGui(GameController controller) {
        super("Dead or Wounded");
        this.controller = controller;

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildGamePanel(), BorderLayout.CENTER);
        add(errorLabel, BorderLayout.SOUTH);

        errorLabel.setForeground(new Color(0xC0392B));
        setGuessEnabled(false);

        newGameButton.addActionListener(this::onNewGame);
        guessButton.addActionListener(this::onGuess);
        guessField.addActionListener(this::onGuess);

        pack();
        setMinimumSize(new Dimension(420, 420));
        setLocationRelativeTo(null);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        JLabel title = new JLabel("Dead or Wounded");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JLabel subtitle = new JLabel(
                "<html>Guess the secret number.<br>"
                        + "<b>Dead</b> = right digit, right spot. <b>Wounded</b> = right digit, wrong spot.</html>");
        subtitle.setForeground(new Color(0x666666));

        JPanel titleBlock = new JPanel(new BorderLayout());
        titleBlock.add(title, BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.SOUTH);

        JPanel newGameRow = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 6);
        c.gridy = 0;

        c.gridx = 0;
        newGameRow.add(new JLabel("Player name:"), c);
        c.gridx = 1;
        newGameRow.add(playerNameField, c);
        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        newGameRow.add(newGameButton, c);

        panel.add(titleBlock, BorderLayout.NORTH);
        panel.add(newGameRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        infoLabel.setForeground(new Color(0x666666));

        JPanel guessRow = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 6);
        c.gridy = 0;

        c.gridx = 0;
        guessRow.add(new JLabel("Your guess:"), c);
        c.gridx = 1;
        guessRow.add(guessField, c);
        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        guessRow.add(guessButton, c);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        JTable historyTable = new JTable(historyModel);
        historyTable.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setPreferredSize(new Dimension(380, 220));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(infoLabel, BorderLayout.NORTH);
        top.add(guessRow, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void onNewGame(ActionEvent e) {
        clearError();
        String name = playerNameField.getText().trim();
        if (name.isEmpty()) {
            name = "Player";
        }

        try {
            GameController.NewGameView view = controller.newGame(name);
            sessionId = view.sessionId();
            secretLength = view.secretLength();
            maxAttempts = view.maxAttempts();
            guessCount = 0;

            infoLabel.setText("Secret length: " + secretLength + "   |   Max attempts: " + maxAttempts);
            historyModel.setRowCount(0);
            guessField.setText("");
            applyGuessLengthFilter(secretLength);
            setStatus("IN_PROGRESS", 0, maxAttempts, null);
            setGuessEnabled(true);
            guessField.requestFocusInWindow();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void onGuess(ActionEvent e) {
        clearError();
        String guess = guessField.getText().trim();
        if (guess.isEmpty()) {
            return;
        }

        try {
            GuessResponse resp = controller.submitGuess(new GuessRequest(sessionId, guess));
            guessCount++;
            historyModel.addRow(new Object[]{guessCount, guess, resp.deadCount(), resp.woundedCount()});
            setStatus(resp.status(), resp.attemptsUsed(), resp.maxAttempts(), resp.secretNumber());

            if (!"IN_PROGRESS".equals(resp.status())) {
                setGuessEnabled(false);
            } else {
                guessField.setText("");
                guessField.requestFocusInWindow();
            }
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void applyGuessLengthFilter(int length) {
        PlainDocument doc = (PlainDocument) guessField.getDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                String result = fb.getDocument().getText(0, fb.getDocument().getLength()) + string;
                if (result.length() <= length && string.chars().allMatch(Character::isDigit)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length2, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String result = current.substring(0, offset) + text + current.substring(offset + length2);
                if (result.length() <= length && text.chars().allMatch(Character::isDigit)) {
                    super.replace(fb, offset, length2, text, attrs);
                }
            }
        });
    }

    private void setGuessEnabled(boolean enabled) {
        guessField.setEnabled(enabled);
        guessButton.setEnabled(enabled);
    }

    private void setStatus(String status, int attemptsUsed, int maxAttempts, String secretNumber) {
        switch (status) {
            case "WON" -> {
                statusLabel.setText("You won! Solved in " + attemptsUsed + " attempt(s).");
                statusLabel.setForeground(new Color(0x2E7D46));
            }
            case "LOST" -> {
                statusLabel.setText("Out of attempts (" + attemptsUsed + "/" + maxAttempts
                        + "). The secret number was " + secretNumber + ". Better luck next time.");
                statusLabel.setForeground(new Color(0xB03A3A));
            }
            default -> {
                statusLabel.setText("In progress - attempt " + attemptsUsed + "/" + maxAttempts);
                statusLabel.setForeground(new Color(0x555555));
            }
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "Something went wrong." : message);
    }

    private void clearError() {
        errorLabel.setText(" ");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to default look and feel
        }

        ServerConfig config = ServerConfig.fromArgsOrDefaults(args);
        GameController controller = new GameController(new GameSessionManager(), config);

        SwingUtilities.invokeLater(() -> new GameGui(controller).setVisible(true));
    }
}
