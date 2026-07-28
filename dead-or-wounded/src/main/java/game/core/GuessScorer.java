package game.core;

import java.time.Instant;

/**
 * Guess validation and Dead/Wounded scoring, shared by any session type
 * (single-player GameSession, multiplayer RaceSession) that scores guesses
 * against a secret number of unique digits.
 */
public final class GuessScorer {
    private GuessScorer() {}

    public static void validateFormat(String guess, int expectedLength) {
        if (guess == null || guess.length() != expectedLength || !guess.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid guess format. Guess must be a numeric string of length " + expectedLength);
        }

        boolean[] seen = new boolean[10];
        for (char c : guess.toCharArray()) {
            int digit = c - '0';
            if (seen[digit]) {
                throw new IllegalArgumentException("Invalid guess format. Guess must not contain duplicate digits.");
            }
            seen[digit] = true;
        }
    }

    public static GuessResult score(String secretNumber, String guess) {
        int dead = 0;
        int wounded = 0;
        for (int i = 0; i < secretNumber.length(); i++) {
            char g = guess.charAt(i);
            if (g == secretNumber.charAt(i)) {
                dead++;
            } else if (secretNumber.indexOf(g) != -1) {
                wounded++;
            }
        }
        return new GuessResult(guess, dead, wounded, Instant.now());
    }
}
