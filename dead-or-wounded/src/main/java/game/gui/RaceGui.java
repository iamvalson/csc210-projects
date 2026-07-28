package game.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.BindException;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
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

import game.api.GameController;
import game.api.HttpApiServer;
import game.api.RaceController;
import game.core.GameSessionManager;
import game.core.RaceSessionManager;
import game.server.ServerConfig;
import game.util.NetworkUtil;

/**
 * Networked Swing client for a "race" game: several players (each running
 * their own RaceGui, over LAN wifi) guess against the same secret at once -
 * first to get it fully Dead wins. Talks to the /api/race/* endpoints over
 * plain HTTP (java.net.http.HttpClient, JDK built-in - no external
 * dependencies).
 *
 * Hosting is self-contained: clicking "Start Hosting" spins up an
 * HttpApiServer in this same process (see ensureLocalServerRunning), so a
 * host never needs to separately run game.Main in another terminal. All the
 * actual concurrency (the AtomicReference winner CAS, the ConcurrentHashMap
 * leaderboard) lives server-side in game.core.RaceSession - this class is
 * just a thin, polling client of it.
 */
public class RaceGui extends JFrame {
    private static final Pattern LEADERBOARD_ENTRY =
            Pattern.compile("\"playerName\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"attemptsUsed\"\\s*:\\s*(\\d+)");
    private static final Pattern INVITE_PATTERN =
            Pattern.compile("([\\w.]+:\\d+)\\D+([A-Za-z0-9]{4,8})");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private HttpApiServer embeddedServer;
    private String baseUrl;
    private String raceId;
    private String playerName;
    private int secretLength;
    private int maxAttempts;
    private int guessCount;
    private int hostedPort;
    private boolean raceOver;
    private boolean isHost;
    private Timer pollTimer;

    // --- landing card ---
    private final JButton showHostFormButton = new JButton("Host New Race");
    private final JButton showJoinFormButton = new JButton("Join a Race");

    // --- host card ---
    private final JTextField hostNameField = new JTextField("Player", 12);
    private final JTextField hostPortField = new JTextField("8081", 6);
    private final JButton startHostingButton = new JButton("Start Hosting");
    private final JButton hostBackButton = new JButton("Back");

    // --- join card ---
    private final JTextField serverField = new JTextField(16);
    private final JTextField joinCodeField = new JTextField(8);
    private final JTextField joinNameField = new JTextField("Player", 12);
    private final JButton pasteInviteButton = new JButton("Paste Invite");
    private final JButton joinButton = new JButton("Join Race");
    private final JButton joinBackButton = new JButton("Back");

    // --- game card ---
    private final JLabel raceInfoLabel = new JLabel(" ");
    private final JPanel inviteRowsPanel = new JPanel();
    private final JTextField guessField = new JTextField(14);
    private final JButton guessButton = new JButton("Guess");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton playAgainButton = new JButton("Play again");
    private final JButton leaveButton = new JButton("Leave race");
    private final JLabel errorLabel = new JLabel(" ");

    private final DefaultTableModel historyModel =
            new DefaultTableModel(new Object[]{"#", "Guess", "Dead", "Wounded"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final DefaultTableModel leaderboardModel =
            new DefaultTableModel(new Object[]{"Player", "Attempts"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final java.awt.CardLayout cardLayout = new java.awt.CardLayout();
    private final JPanel screens = new JPanel(cardLayout);

    public RaceGui() {
        super("Dead or Wounded - Race Mode");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(buildHeaderPanel(), BorderLayout.NORTH);

        screens.add(buildLandingCard(), "landing");
        screens.add(buildHostCard(), "host");
        screens.add(buildJoinCard(), "join");
        screens.add(buildGameCard(), "game");
        add(screens, BorderLayout.CENTER);

        add(errorLabel, BorderLayout.SOUTH);
        errorLabel.setForeground(new Color(0xC0392B));

        setGuessEnabled(false);
        wireActions();

        pack();
        setMinimumSize(new Dimension(520, 520));
        setLocationRelativeTo(null);
    }

    private void wireActions() {
        showHostFormButton.addActionListener(e -> { clearError(); cardLayout.show(screens, "host"); });
        showJoinFormButton.addActionListener(e -> { clearError(); cardLayout.show(screens, "join"); });
        hostBackButton.addActionListener(e -> { clearError(); cardLayout.show(screens, "landing"); });
        joinBackButton.addActionListener(e -> { clearError(); cardLayout.show(screens, "landing"); });

        startHostingButton.addActionListener(e -> onHost());
        joinButton.addActionListener(e -> onJoin());
        pasteInviteButton.addActionListener(e -> onPasteInvite());

        guessButton.addActionListener(e -> onGuess());
        guessField.addActionListener(e -> onGuess());
        playAgainButton.addActionListener(e -> onPlayAgain());
        leaveButton.addActionListener(e -> resetToLandingScreen());
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Dead or Wounded - Race Mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel(
                "<html>Host a race, share the invite with a friend on the same wifi, and race to guess the secret first.</html>");
        subtitle.setForeground(new Color(0x666666));
        JPanel titleBlock = new JPanel(new BorderLayout());
        titleBlock.add(title, BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.SOUTH);
        panel.add(titleBlock, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildLandingCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.gridx = 0;
        c.gridy = 0;
        c.ipadx = 30;
        c.ipady = 14;
        Font big = showHostFormButton.getFont().deriveFont(Font.BOLD, 15f);
        showHostFormButton.setFont(big);
        showJoinFormButton.setFont(big);
        panel.add(showHostFormButton, c);
        c.gridy = 1;
        panel.add(showJoinFormButton, c);
        return panel;
    }

    private JPanel buildHostCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JLabel hint = new JLabel(
                "<html>Starts a race on this computer - no separate server needed.<br>"
                        + "You'll get a code and address to share with your friend.</html>");
        hint.setForeground(new Color(0x888888));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        panel.add(hint, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 1;
        panel.add(new JLabel("Your name:"), c);
        c.gridx = 1;
        panel.add(hostNameField, c);

        c.gridx = 0; c.gridy = 2;
        panel.add(new JLabel("Port (advanced, default 8081):"), c);
        c.gridx = 1;
        panel.add(hostPortField, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        buttonRow.add(startHostingButton);
        buttonRow.add(hostBackButton);
        panel.add(buttonRow, c);

        return panel;
    }

    private JPanel buildJoinCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JLabel hint = new JLabel(
                "<html>Ask your host for their invite (address + code) and either paste it<br>"
                        + "in one go, or type the two fields in yourself.</html>");
        hint.setForeground(new Color(0x888888));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3;
        panel.add(hint, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 1;
        panel.add(new JLabel("Server address (host:port):"), c);
        c.gridx = 1;
        serverField.setToolTipText("e.g. 172.20.10.2:8081 - your host will give you this");
        panel.add(serverField, c);
        c.gridx = 2;
        panel.add(pasteInviteButton, c);

        c.gridx = 0; c.gridy = 2;
        panel.add(new JLabel("Race code:"), c);
        c.gridx = 1;
        panel.add(joinCodeField, c);

        c.gridx = 0; c.gridy = 3;
        panel.add(new JLabel("Your name:"), c);
        c.gridx = 1;
        panel.add(joinNameField, c);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        buttonRow.add(joinButton);
        buttonRow.add(joinBackButton);
        panel.add(buttonRow, c);

        return panel;
    }

    private JPanel buildGameCard() {
        JPanel gamePanel = new JPanel(new BorderLayout(0, 8));

        inviteRowsPanel.setLayout(new BoxLayout(inviteRowsPanel, BoxLayout.Y_AXIS));

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
        playAgainButton.setEnabled(false);
        JPanel statusButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        statusButtons.add(playAgainButton);
        statusButtons.add(leaveButton);
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(statusButtons, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        raceInfoLabel.setAlignmentX(0f);
        inviteRowsPanel.setAlignmentX(0f);
        guessRow.setAlignmentX(0f);
        statusRow.setAlignmentX(0f);
        top.add(raceInfoLabel);
        top.add(inviteRowsPanel);
        top.add(Box.createVerticalStrut(6));
        top.add(guessRow);
        top.add(statusRow);

        JTable historyTable = new JTable(historyModel);
        historyTable.setEnabled(false);
        JPanel historyPanel = new JPanel(new BorderLayout(0, 4));
        JLabel historyHeader = new JLabel("Your guesses");
        historyHeader.setFont(historyHeader.getFont().deriveFont(Font.BOLD));
        historyPanel.add(historyHeader, BorderLayout.NORTH);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JTable leaderboardTable = new JTable(leaderboardModel);
        leaderboardTable.setEnabled(false);
        JPanel leaderboardPanel = new JPanel(new BorderLayout(0, 4));
        JLabel leaderboardHeader = new JLabel("Leaderboard");
        leaderboardHeader.setFont(leaderboardHeader.getFont().deriveFont(Font.BOLD));
        leaderboardPanel.add(leaderboardHeader, BorderLayout.NORTH);
        leaderboardPanel.add(new JScrollPane(leaderboardTable), BorderLayout.CENTER);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 0));
        tables.add(historyPanel);
        tables.add(leaderboardPanel);
        tables.setPreferredSize(new Dimension(460, 220));

        gamePanel.add(top, BorderLayout.NORTH);
        gamePanel.add(tables, BorderLayout.CENTER);
        return gamePanel;
    }

    // ---- hosting ----

    private void onHost() {
        clearError();
        playerName = nonBlank(hostNameField.getText(), "Player");
        int port;
        try {
            port = Integer.parseInt(hostPortField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Port must be a number, e.g. 8081.");
            return;
        }

        try {
            ensureLocalServerRunning(port);
            baseUrl = "http://localhost:" + port + "/api";
            hostedPort = port;
            isHost = true;
            startNewHostedRace();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** Creates a brand-new race on the already-running host server and enters it - used both
     *  for the initial "Start Hosting" and for "Play again" (same server, same player, fresh secret). */
    private void startNewHostedRace() {
        String body = "{\"playerName\":\"" + jsonEscape(playerName) + "\"}";
        String resp = request("POST", "/race/new", body);

        raceId = extractString(resp, "raceId");
        secretLength = extractInt(resp, "secretLength");
        maxAttempts = extractInt(resp, "maxAttempts");

        List<String> addresses = NetworkUtil.listLanIpv4Addresses();
        enterRace("Hosting - Race code: " + raceId);
        populateInviteRows(addresses, hostedPort, raceId);
    }

    private void ensureLocalServerRunning(int port) {
        if (embeddedServer != null) {
            return;
        }
        ServerConfig config = ServerConfig.fromArgsOrDefaults(new String[]{"--httpPort", String.valueOf(port)});
        GameController gameController = new GameController(new GameSessionManager(), config);
        RaceController raceController = new RaceController(new RaceSessionManager(), config);
        HttpApiServer server = new HttpApiServer(gameController, raceController, port);
        try {
            server.start();
            embeddedServer = server;
        } catch (BindException e) {
            // Something (maybe a game.Main you started yourself) is already listening
            // here - assume it's a compatible server and just talk to it.
        } catch (IOException e) {
            throw new RuntimeException("Could not start a local server on port " + port + ": " + e.getMessage());
        }
    }

    private void populateInviteRows(List<String> addresses, int port, String code) {
        inviteRowsPanel.removeAll();
        if (addresses.isEmpty()) {
            JLabel none = new JLabel("Could not detect a LAN address - run `ipconfig` and share manually.");
            none.setForeground(new Color(0x888888));
            inviteRowsPanel.add(none);
        } else {
            JLabel label = new JLabel("Share one of these with your friend:");
            label.setForeground(new Color(0x888888));
            label.setAlignmentX(0f);
            inviteRowsPanel.add(label);
            for (String addr : addresses) {
                String invite = addr + ":" + port + " " + code;
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setAlignmentX(0f);
                JLabel text = new JLabel(invite);
                text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                JButton copy = new JButton("Copy");
                copy.addActionListener(e -> copyToClipboard(invite));
                row.add(text, BorderLayout.CENTER);
                row.add(copy, BorderLayout.EAST);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                inviteRowsPanel.add(row);
            }
        }
        inviteRowsPanel.revalidate();
        inviteRowsPanel.repaint();
    }

    // ---- joining ----

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
            isHost = false;

            inviteRowsPanel.removeAll();
            enterRace("Joined race " + raceId + ".");
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void onPasteInvite() {
        clearError();
        String clip = readClipboard();
        if (clip == null) {
            showError("Nothing to paste - ask your host to click their Copy button first.");
            return;
        }
        Matcher m = INVITE_PATTERN.matcher(clip);
        if (!m.find()) {
            showError("Could not read an invite from the clipboard - enter the address and code manually.");
            return;
        }
        serverField.setText(m.group(1));
        joinCodeField.setText(m.group(2).toUpperCase());
    }

    // ---- shared game flow ----

    private void enterRace(String infoMessage) {
        raceOver = false;
        guessCount = 0;
        cardLayout.show(screens, "game");
        raceInfoLabel.setText(infoMessage + "  |  Secret length: " + secretLength + "  |  Max attempts: " + maxAttempts);
        historyModel.setRowCount(0);
        leaderboardModel.setRowCount(0);
        guessField.setText("");
        applyGuessLengthFilter(secretLength);
        statusLabel.setText("Race in progress...");
        statusLabel.setForeground(new Color(0x555555));
        setGuessEnabled(true);
        playAgainButton.setEnabled(false);
        guessField.requestFocusInWindow();
        startPolling();
    }

    /** Lets a player start a fresh race without going back to the landing screen. A host gets
     *  an instant new race on the same server; a joiner needs a new code from their host (a race
     *  is one-shot once it has a winner), so they're dropped onto the join form with their
     *  server address and name already filled in - only the code needs re-entering/pasting. */
    private void onPlayAgain() {
        clearError();
        if (isHost) {
            try {
                startNewHostedRace();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        } else {
            joinCodeField.setText("");
            cardLayout.show(screens, "join");
            joinCodeField.requestFocusInWindow();
        }
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
            int deadCount = extractInt(resp, "deadCount");
            int woundedCount = extractInt(resp, "woundedCount");
            int attemptsUsed = extractInt(resp, "attemptsUsed");
            String winnerName = extractString(resp, "winnerName");
            String secretNumber = extractString(resp, "secretNumber");

            if (!"RACE_OVER".equals(status)) {
                guessCount++;
                historyModel.addRow(new Object[]{guessCount, guess, deadCount, woundedCount});
            }

            switch (status) {
                case "WON" -> endRace("You won! Solved it in " + attemptsUsed + " attempt(s). Secret was " + secretNumber + ".",
                        new Color(0x2E7D46));
                case "RACE_OVER" -> endRace(winnerName + " won the race! Secret was " + secretNumber + ".",
                        new Color(0xB03A3A));
                case "LOST" -> {
                    statusLabel.setText("Out of your " + maxAttempts + " attempts - still waiting to see who wins.");
                    statusLabel.setForeground(new Color(0xB03A3A));
                    setGuessEnabled(false);
                    playAgainButton.setEnabled(true);
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
        playAgainButton.setEnabled(true);
        if (pollTimer != null) {
            pollTimer.stop();
        }
    }

    private void resetToLandingScreen() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
        raceOver = false;
        raceId = null;
        clearError();
        cardLayout.show(screens, "landing");
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
            throw new IllegalArgumentException("Enter the server address your host gave you (e.g. 172.20.10.2:8081).");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed + "/api";
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    private String readClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
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
