package game.api;

import java.util.List;
import java.util.stream.Collectors;

import game.api.dto.GuessRequest;
import game.api.dto.GuessResponse;
import game.core.GameSession;
import game.core.GameSessionManager;
import game.core.GuessResult;
import game.core.Player;
import game.server.ServerConfig;

public class GameController {
    private final GameSessionManager sessionManager;
    private final ServerConfig config;

    public GameController(GameSessionManager sessionManager, ServerConfig config) {
        this.sessionManager = sessionManager;
        this.config = config;
    }

    public record NewGameView(String sessionId, int secretLength, int maxAttempts) {}

    public NewGameView newGame(String playerName) {
        Player player = new Player(playerName);
        GameSession session = sessionManager.createSession(player, config.getSecretNumberLength(), config.getMaxAttempts());

        return new NewGameView(session.getSessionId(), session.getSecretLength(), session.getMaxAttempts());
    }

    public GuessResponse submitGuess(GuessRequest request) {
        GameSession session = sessionManager.getSession(request.sessionId());
        GuessResult result = session.submitGuess(request.guess());
        return new GuessResponse(result.deadCount(), result.woundedCount(), session.getHistorySnapshot().size(), session.getMaxAttempts(), session.getStatus().name());
    }

    public List<String> getHistory(String sessionId) {
        GameSession session = sessionManager.getSession(sessionId);
        
        return session.getHistorySnapshot().stream()
                .map(GuessResult::toString)
                .collect(Collectors.toList());
    }
}
