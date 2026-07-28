package game.api.dto;

public record RaceView(String raceId, String playerName, int secretLength, int maxAttempts) {
}
