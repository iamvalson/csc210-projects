package game.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.api.dto.GuessRequest;
import game.api.dto.GuessResponse;
import game.api.dto.RaceView;
import game.core.RaceOutcome;
import game.core.RaceStatus;

/**
 * Thin JSON-over-HTTP adapter in front of GameController/RaceController, for
 * browser or GUI clients that can't speak the raw line protocol ClientHandler
 * uses. Uses only com.sun.net.httpserver (JDK-bundled) so the project stays
 * dependency-free.
 */
public class HttpApiServer {
    private final GameController controller;
    private final RaceController raceController;
    private final int port;
    private HttpServer httpServer;

    public HttpApiServer(GameController controller, RaceController raceController, int port) {
        this.controller = controller;
        this.raceController = raceController;
        this.port = port;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/api/new", this::handleNew);
        httpServer.createContext("/api/guess", this::handleGuess);
        httpServer.createContext("/api/history/", this::handleHistory);
        httpServer.createContext("/api/race/new", this::handleRaceNew);
        httpServer.createContext("/api/race/join", this::handleRaceJoin);
        httpServer.createContext("/api/race/guess", this::handleRaceGuess);
        httpServer.createContext("/api/race/status/", this::handleRaceStatus);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("HTTP API server started on port " + port);
        printLanAddresses(port);
    }

    private void printLanAddresses(int port) {
        try {
            List<InetAddress> addresses = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(iface -> {
                        try {
                            return iface.isUp() && !iface.isLoopback() && !iface.isVirtual();
                        } catch (SocketException e) {
                            return false;
                        }
                    })
                    .flatMap(iface -> Collections.list(iface.getInetAddresses()).stream())
                    .filter(addr -> addr instanceof java.net.Inet4Address)
                    .toList();

            if (addresses.isEmpty()) {
                System.out.println("Could not determine a LAN address - check `ipconfig`/`ifconfig` manually.");
                return;
            }
            System.out.println("Reachable on your network at:");
            for (InetAddress addr : addresses) {
                System.out.println("  http://" + addr.getHostAddress() + ":" + port);
            }
        } catch (SocketException e) {
            System.out.println("Could not list network interfaces: " + e.getMessage());
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private void handleNew(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String body = readBody(exchange);
            String name = extractJsonString(body, "playerName");
            if (name == null || name.isBlank()) {
                name = "player-" + System.nanoTime();
            }

            GameController.NewGameView view = controller.newGame(name);
            String json = "{\"sessionId\":\"" + view.sessionId() + "\""
                    + ",\"secretLength\":" + view.secretLength()
                    + ",\"maxAttempts\":" + view.maxAttempts() + "}";
            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleGuess(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String body = readBody(exchange);
            String sessionId = extractJsonString(body, "sessionId");
            String guess = extractJsonString(body, "guess");

            GuessResponse resp = controller.submitGuess(new GuessRequest(sessionId, guess));
            String secretNumberJson = resp.secretNumber() == null ? "null" : "\"" + resp.secretNumber() + "\"";
            String json = "{\"deadCount\":" + resp.deadCount()
                    + ",\"woundedCount\":" + resp.woundedCount()
                    + ",\"attemptsUsed\":" + resp.attemptsUsed()
                    + ",\"maxAttempts\":" + resp.maxAttempts()
                    + ",\"status\":\"" + resp.status() + "\""
                    + ",\"secretNumber\":" + secretNumberJson + "}";
            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String sessionId = path.substring(path.lastIndexOf('/') + 1);

            List<String> history = controller.getHistory(sessionId);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(history.get(i))).append("\"");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleRaceNew(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String body = readBody(exchange);
            String name = extractJsonString(body, "playerName");
            if (name == null || name.isBlank()) {
                name = "player-" + System.nanoTime();
            }

            RaceView view = raceController.createRace(name);
            sendJson(exchange, 200, raceViewJson(view));
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleRaceJoin(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String body = readBody(exchange);
            String raceId = extractJsonString(body, "raceId");
            String name = extractJsonString(body, "playerName");
            if (name == null || name.isBlank()) {
                name = "player-" + System.nanoTime();
            }
            if (raceId != null) {
                raceId = raceId.trim().toUpperCase();
            }

            RaceView view = raceController.joinRace(raceId, name);
            sendJson(exchange, 200, raceViewJson(view));
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleRaceGuess(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String body = readBody(exchange);
            String raceId = extractJsonString(body, "raceId");
            String playerName = extractJsonString(body, "playerName");
            String guess = extractJsonString(body, "guess");

            RaceOutcome outcome = raceController.submitGuess(raceId, playerName, guess);
            String secretJson = outcome.secretNumber() == null ? "null" : "\"" + outcome.secretNumber() + "\"";
            String winnerJson = outcome.winnerName() == null ? "null" : "\"" + escape(outcome.winnerName()) + "\"";
            String json = "{\"deadCount\":" + outcome.deadCount()
                    + ",\"woundedCount\":" + outcome.woundedCount()
                    + ",\"attemptsUsed\":" + outcome.attemptsUsed()
                    + ",\"maxAttempts\":" + outcome.maxAttempts()
                    + ",\"status\":\"" + outcome.status() + "\""
                    + ",\"secretNumber\":" + secretJson
                    + ",\"winnerName\":" + winnerJson + "}";
            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleRaceStatus(HttpExchange exchange) throws IOException {
        if (handledPreflight(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String raceId = path.substring(path.lastIndexOf('/') + 1);

            RaceStatus status = raceController.getStatus(raceId);
            StringBuilder leaderboardJson = new StringBuilder("[");
            List<RaceStatus.PlayerStanding> standings = status.leaderboard();
            for (int i = 0; i < standings.size(); i++) {
                if (i > 0) leaderboardJson.append(",");
                RaceStatus.PlayerStanding s = standings.get(i);
                leaderboardJson.append("{\"playerName\":\"").append(escape(s.playerName()))
                        .append("\",\"attemptsUsed\":").append(s.attemptsUsed()).append("}");
            }
            leaderboardJson.append("]");

            String secretJson = status.secretNumber() == null ? "null" : "\"" + status.secretNumber() + "\"";
            String winnerJson = status.winnerName() == null ? "null" : "\"" + escape(status.winnerName()) + "\"";
            String json = "{\"raceId\":\"" + escape(status.raceId()) + "\""
                    + ",\"secretLength\":" + status.secretLength()
                    + ",\"maxAttempts\":" + status.maxAttempts()
                    + ",\"winnerName\":" + winnerJson
                    + ",\"secretNumber\":" + secretJson
                    + ",\"leaderboard\":" + leaderboardJson + "}";
            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private String raceViewJson(RaceView view) {
        return "{\"raceId\":\"" + escape(view.raceId()) + "\""
                + ",\"playerName\":\"" + escape(view.playerName()) + "\""
                + ",\"secretLength\":" + view.secretLength()
                + ",\"maxAttempts\":" + view.maxAttempts() + "}";
    }

    private boolean handledPreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            applyCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void applyCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        applyCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
