package game.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import game.api.GameController;
import game.api.dto.GuessRequest;
import game.api.dto.GuessResponse;


public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameController controller;

    public ClientHandler(Socket socket, GameController controller) {
        this.socket = socket;
        this.controller = controller;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        try(BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                inputLine = inputLine.trim();
                if(inputLine.isEmpty()) {
                    continue;
                }
                if(inputLine.equalsIgnoreCase("QUIT")) {
                    out.println("Goodbye!");
                    break;
                }
                try{
                    out.println(handleCommand(inputLine));
                } catch (Exception e) {
                    out.println("Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling client connection in thread " + threadName + ": " + e.getMessage());
        } finally {
            closeQuietly(socket);
        }
    }


    private String handleCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toUpperCase();

        return switch(cmd) {
            case "NEW" -> {
                String name = parts.length > 1 ? parts[1] : ("player-" + Thread.currentThread().getId());
                GameController.NewGameView view = controller.newGame(name);
                yield "OK SESSION " + view.sessionId() + " SECRET_LENGTH " + view.secretLength() + " MAX_ATTEMPTS " + view.maxAttempts();
            }
            case "GUESS" -> {
                if (parts.length < 3) {
                    yield "Error: GUESS command requires session ID and guess.";
                }

                GuessResponse resp =controller.submitGuess(new GuessRequest(parts[1], parts[2].trim()));
                
                yield "RESULT DEAD " + resp.deadCount() + " WOUNDED " + resp.woundedCount() + " ATTEMPTS " + resp.attemptsUsed() + "/" + resp.maxAttempts() + " STATUS " + resp.status();
            }
            case "HISTORY" -> {
                if (parts.length < 2) {
                    yield "Error: HISTORY command requires session ID.";
                }
                List<String> history = controller.getHistory(parts[1]);
                yield history.isEmpty() ? "No history available." : String.join("\n", history);
            }
            default -> "Error: Unknown command '" + cmd + "'";
        };
    }


    private void closeQuietly(Socket socket) {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            
        }
    }
}
