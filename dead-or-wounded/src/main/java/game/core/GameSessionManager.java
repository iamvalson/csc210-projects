package game.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionManager {
    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();


    public GameSession createSession(Player player, int secretLength, int maxAttempts){
        String sessionId = UUID.randomUUID().toString();
        String secret = SecretNumberGenerator.generate(secretLength);
        GameSession session = new GameSession(sessionId, player, secret, maxAttempts);
        player.setSession(session);

        sessions.put(sessionId, session);
        return session;
    }

    public GameSession getSession(String sessionId){
        GameSession session = sessions.get(sessionId);
        if (session == null){
            throw new IllegalArgumentException("No such session: " + sessionId);
        }
        return session;
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    public void shutdown() {
        sessions.clear();
    }
}
