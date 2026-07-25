package game.api.dto;

public record GuessResponse (int deadCount, int woundedCount, int attemptsUsed, int maxAttempts, String status) {
    
}
