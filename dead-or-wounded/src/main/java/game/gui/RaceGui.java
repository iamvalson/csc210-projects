package game.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

/**
 * Networked Swing client for a "race" game: several players (each running
 * their own RaceGui, over LAN wifi) guess against the same secret at once -
 * first to get it fully Dead wins. Talks to the /api/race/* endpoints on a
 * running game.Main server over plain HTTP (java.net.http.HttpClient, JDK
 * built-in - no external dependencies).
 *
 * All the actual concurrency (the AtomicReference winner CAS, the
 * ConcurrentHashMap leaderboard) lives server-side in game.core.RaceSession -
 * this class is just a thin, polling client of it.
 */
public class RaceGui extends JFrame {
    private static final Pattern LEADERBOARD_ENTRY =
            Pattern.compile("\"playerName\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"attemptsUsed\"\\s*:\\s*(\\d+)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private String baseUrl;
    private String raceId;
    private String playerName;
    private int secretLength;
    private int maxAttempts;
    private boolean raceOver;
    private Timer pollTimer;

    private final JTextField serverField = new JTextField("192.168.1.X:8081", 16);
    private final JTextField hostNameField = new JTextField("Player", 10);
    private final JButton hostButton = new JButton("Host New Race");
    private final JTextField joinCodeField = new JTextField(8);
    private final JTextField joinNameField = new JTextField("Player", 10);
    private final JButton joinButton = new JButton("Join Race");

    private final JLabel raceInfoLabel = new JLabel(" ");
    private final JTextField guessField = new JTextField(14);
    private final JButton guessButton = new JButton("Guess");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton playAgainButton = new JButton("Back to connect");
    private final JLabel errorLabel = new JLabel(" ");

    private final DefaultTableModel leaderboardModel =
            new DefaultTableModel(new Object[]{"Player", "Attempts"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final JPanel connectPanel = new JPanel();
    private final JPanel gamePanel = new JPanel(new BorderLayout(0, 8));

    public RaceGui() {
        super("Dead or Wounded - Race Mode");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(errorLabel, BorderLayout.SOUTH);
        errorLabel.setForeground(new Color(0xC0392B));

        gamePanel.setVisible(false);
        setGuessEnabled(false);

        hostButton.addActionListener(e -> onHost());
        joinButton.addActionListener(e -> onJoin());
        guessButton.addActionListener(e -> onGuess());
        guessField.addActionListener(e -> onGuess());
        playAgainButton.addActionListener(e -> resetToConnectScreen());

        pack();
        setMinimumSize(new Dimension(460, 480));
        setLocationRelativeTo(null);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Dead or Wounded - Race Mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel(
                "<html>Host a race, share the code with a friend on the same wifi, and race to guess the secret first.</html>");
        subtitle.setForeground(new Color(0x666666));
        JPanel titleBlock = new JPanel(new BorderLayout());
        titleBlock.add(title, BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.SOUTH);
        panel.add(titleBlock, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 12));

        connectPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        connectPanel.add(new JLabel("Server address (host:port):"), c);
        c.gridx = 1;
        connectPanel.add(serverField, c);

        c.gridx = 0; c.gridy = 1;
        connectPanel.add(new JLabel("Your name:"), c);
        c.gridx = 1;
        connectPanel.add(hostNameField, c);
        c.gridx = 2;
        connectPanel.add(hostButton, c);

        c.gridx = 0; c.gridy = 2;
        connectPanel.add(new JLabel("Race code:"), c);
        c.gridx = 1;
        connectPanel.add(joinCodeField, c);
        c.gridx = 2;
        connectPanel.add(joinNameField, c);
        c.gridx = 3;
        connectPanel.add(joinButton, c);

        JLabel hint = new JLabel(
                "<html>To host: enter the server address, your name, and click \"Host New Race\" - "
                        + "you'll get a code to share.<br>To join: enter the server address, the code your "
                        + "friend gave you, your name, and click \"Join Race\".</html>");
        hint.setForeground(new Color(0x888888));

        JPanel guessRow = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 6);
        gc.gridy = 0;
        gc.gridx = 0;
        guessRow.add(new JLabel("Your guess:"), gc);
        gc.gridx = 1;
        guessRow.add(guessField, gc);
        gc.gridx = 2;
        gc.insets = new Insets(0, 0, 0, 0);
        guessRow.add(guessButton, gc);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        JTable leaderboardTable = new JTable(leaderboardModel);
        leaderboardTable.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(leaderboardTable);
        scrollPane.setPreferredSize(new Dimension(380, 160));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(raceInfoLabel, BorderLayout.NORTH);
        top.add(guessRow, BorderLayout.CENTER);
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(playAgainButton, BorderLayout.EAST);
        top.add(statusRow, BorderLayout.SOUTH);

        gamePanel.add(top, BorderLayout.NORTH);
        gamePanel.add(scrollPane, BorderLayout.CENTER);

        JPanel connectWrapper = new JPanel(new BorderLayout(0, 8));
        connectWrapper.add(connectPanel, BorderLayout.NORTH);
        connectWrapper.add(hint, BorderLayout.SOUTH);

        wrapper.add(connectWrapper, BorderLayout.NORTH);
        wrapper.add(gamePanel, BorderLayout.CENTER);
        return wrapper;
    }

    private void onHost() {
        clearError();
        playerName = nonBlank(hostNameField.getText(), "Player");
        try {
            baseUrl = normalizeBaseUrl(serverField.getText());
            String body = "{\"playerName\":\"" + jsonEscape(playerName) + "\"}";
            String resp = request("POST", "/race/new", body);

            raceId = extractString(resp, "raceId");
            secretLength = extractInt(resp, "secretLength");
            maxAttempts = extractInt(resp, "maxAttempts");

            enterRace("You are hosting. Share this code: " + raceId);
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void onJoin() {
        clearError();
        playerName = nonBlank(joinNameField.getText(), "Player");
        String code = joinCodeField.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            showError("Enter the race code your friend shared with you.");
            return;
        }
        try {
            baseUrl = normalizeBaseUrl(serverField.getText());
            String body = "{\"raceId\":\"" + jsonEscape(code) + "\",\"playerName\":\"" + jsonEscape(playerName) + "\"}";
            String resp = request("POST", "/race/join", body);

            raceId = extractString(resp, "raceId");
            secretLength = extractInt(resp, "secretLength");
            maxAttempts = extractInt(resp, "maxAttempts");

            enterRace("Joined race " + raceId + ".");
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void enterRace(String infoMessage) {
        raceOver = false;
        connectPanel.setVisible(false);
        gamePanel.setVisible(true);
        raceInfoLabel.setText(infoMessage + "  |  Secret length: " + secretLength + "  |  Max attempts: " + maxAttempts);
        leaderboardModel.setRowCount(0);
        guessField.setText("");
        applyGuessLengthFilter(secretLength);
        statusLabel.setText("Race in progress...");
        statusLabel.setForeground(new Color(0x555555));
        setGuessEnabled(true);
        guessField.requestFocusInWindow();
        revalidate();
        repaint();
        startPolling();
    }

    private void onGuess() {
        clearError();
        String guess = guessField.getText().trim();
        if (guess.isEmpty() || raceOver) {
            return;
        }
        try {
            String body = "{\"raceId\":\"" + jsonEscape(raceId) + "\",\"playerName\":\"" + jsonEscape(playerName)
                    + "\",\"guess\":\"" + jsonEscape(guess) + "\"}";
            String resp = request("POST", "/race/guess", body);

            String status = extractString(resp, "status");
            int attemptsUsed = extractInt(resp, "attemptsUsed");
            String winnerName = extractString(resp, "winnerName");
            String secretNumber = extractString(resp, "secretNumber");

            switch (status) {
                case "WON" -> endRace("You won! Solved it in " + attemptsUsed + " attempt(s). Secret was " + secretNumber + ".",
                        new Color(0x2E7D46));
                case "RACE_OVER" -> endRace(winnerName + " won the race! Secret was " + secretNumber + ".",
                        new Color(0xB03A3A));
                case "LOST" -> {
                    statusLabel.setText("Out of your " + maxAttempts + " attempts - still waiting to see who wins.");
                    statusLabel.setForeground(new Color(0xB03A3A));
                    setGuessEnabled(false);
                }
                default -> {
                    statusLabel.setText("Race in progress - attempt " + attemptsUsed + "/" + maxAttempts);
                    guessField.setText("");
                    guessField.requestFocusInWindow();
                }
            }
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void startPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
        pollTimer = new Timer(1000, e -> pollStatus());
        pollTimer.start();
    }

    private void pollStatus() {
        if (raceOver) {
            pollTimer.stop();
            return;
        }
        try {
            String resp = request("GET", "/race/status/" + raceId, null);
            updateLeaderboard(resp);

            String winnerName = extractString(resp, "winnerName");
            if (winnerName != null && !winnerName.isEmpty()) {
                String secretNumber = extractString(resp, "secretNumber");
                if (winnerName.equals(playerName)) {
                    endRace("You won! Secret was " + secretNumber + ".", new Color(0x2E7D46));
                } else {
                    endRace(winnerName + " won the race! Secret was " + secretNumber + ".", new Color(0xB03A3A));
                }
            }
        } catch (RuntimeException ex) {
            // Transient poll failures shouldn't interrupt the game - surface once, keep retrying.
            showError(ex.getMessage());
        }
    }

    private void updateLeaderboard(String statusJson) {
        List<Object[]> rows = new ArrayList<>();
        Matcher m = LEADERBOARD_ENTRY.matcher(statusJson);
        while (m.find()) {
            rows.add(new Object[]{m.group(1), Integer.parseInt(m.group(2))});
        }
        leaderboardModel.setRowCount(0);
        for (Object[] row : rows) {
            leaderboardModel.addRow(row);
        }
    }

    private void endRace(String message, Color color) {
        raceOver = true;
        statusLabel.setText(message);
        statusLabel.setForeground(color);
        setGuessEnabled(false);
        if (pollTimer != null) {
            pollTimer.stop();
        }
    }

    private void resetToConnectScreen() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
        raceOver = false;
        raceId = null;
        gamePanel.setVisible(false);
        connectPanel.setVisible(true);
        clearError();
        revalidate();
        repaint();
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

    private void showError(String message) {
        errorLabel.setText(message == null ? "Something went wrong." : message);
    }

    private void clearError() {
        errorLabel.setText(" ");
    }

    private static String nonBlank(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String normalizeBaseUrl(String hostPort) {
        String trimmed = hostPort.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter the server address (e.g. 192.168.1.23:8081).");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed + "/api";
    }

    private String request(String method, String path, String jsonBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json");
            builder = "POST".equals(method)
                    ? builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    : builder.GET();

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String err = extractString(response.body(), "error");
                throw new RuntimeException(err != null ? err : "Request failed (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (IOException e) {
            throw new RuntimeException("Could not reach server at " + baseUrl + " (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted.");
        }
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int extractInt(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to default look and feel
        }
        SwingUtilities.invokeLater(() -> new RaceGui().setVisible(true));
    }
}
