package game.core;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

public class RaceSessionManager {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, RaceSession> races = new ConcurrentHashMap<>();

    public RaceSession createRace(int secretLength, int maxAttemptsPerPlayer) {
        String secret = SecretNumberGenerator.generate(secretLength);
        RaceSession race;
        String raceId;
        do {
            raceId = generateCode();
            race = new RaceSession(raceId, secret, maxAttemptsPerPlayer);
        } while (races.putIfAbsent(raceId, race) != null);
        return race;
    }

    public RaceSession getRace(String raceId) {
        RaceSession race = races.get(raceId);
        if (race == null) {
            throw new IllegalArgumentException("No such race: " + raceId);
        }
        return race;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
