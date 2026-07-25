package game.core;

import java.time.Instant;

public record GuessResult(String guess, int deadCount, int woundedCount, Instant timestamp) {

    @Override
    public String toString() {
        return String.format("Guess: %s, Dead: %d, Wounded: %d, Timestamp: %s", guess, deadCount, woundedCount, timestamp);
    }
}
