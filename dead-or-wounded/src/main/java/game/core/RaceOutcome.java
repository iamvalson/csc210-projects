package game.core;

/**
 * Result of one player's guess in a RaceSession.
 *
 * status is one of:
 *  - IN_PROGRESS: race still open, this player can keep guessing
 *  - WON: this player was the one whose guess CAS'd the winner slot
 *  - LOST: this player used up their own attempts without winning
 *  - RACE_OVER: someone else already won (possibly just now, possibly earlier)
 *
 * secretNumber and winnerName are null until the race has a winner.
 */
public record RaceOutcome(
        int deadCount,
        int woundedCount,
        int attemptsUsed,
        int maxAttempts,
        String status,
        String secretNumber,
        String winnerName
) {}
