package game.core;

import java.util.List;

/**
 * Point-in-time snapshot of a race, for clients that poll instead of
 * (or in addition to) guessing - this is how a player who has stopped
 * guessing still finds out promptly that the race ended.
 */
public record RaceStatus(
        String raceId,
        int secretLength,
        int maxAttempts,
        String winnerName,
        String secretNumber,
        List<PlayerStanding> leaderboard
) {
    public record PlayerStanding(String playerName, int attemptsUsed) {}
}
