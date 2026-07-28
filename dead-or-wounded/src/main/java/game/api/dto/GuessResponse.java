package game.api.dto;

/**
 * secretNumber is null while the game is IN_PROGRESS and only populated
 * once the game has ended (WON or LOST), so guesses in progress can't leak
 * the answer.
 */
public record GuessResponse (int deadCount, int woundedCount, int attemptsUsed, int maxAttempts, String status, String secretNumber) {

}
