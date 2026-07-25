package game.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.api.dto.GuessRequest;
import game.api.dto.GuessResponse;

/**
 * Thin JSON-over-HTTP adapter in front of GameController, for browser clients
 * that can't speak the raw line protocol ClientHandler uses. Uses only
 * com.sun.net.httpserver (JDK-bundled) so the project stays dependency-free.
 */
public class HttpApiServer {
    private final GameController controller;
    private final int port;
    private HttpServer httpServer;

    public HttpApiServer(GameController controller, int port) {
        this.controller = controller;
        this.port = port;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/api/new", this::handleNew);
        httpServer.createContext("/api/guess", this::handleGuess);
        httpServer.createContext("/api/history/", this::handleHistory);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("HTTP API server started on port " + port);
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
            String json = "{\"deadCount\":" + resp.deadCount()
                    + ",\"woundedCount\":" + resp.woundedCount()
                    + ",\"attemptsUsed\":" + resp.attemptsUsed()
                    + ",\"maxAttempts\":" + resp.maxAttempts()
                    + ",\"status\":\"" + resp.status() + "\"}";
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
