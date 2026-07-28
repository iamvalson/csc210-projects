package game.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Several players guessing against one shared secret at once - first to
 * guess it all-Dead wins. Unlike GameSession (one player, one lock around
 * its own state), a race has state that many player threads touch
 * concurrently, so it leans on lock-free/concurrent structures instead:
 *
 *  - secretNumber is set once at construction and never mutated, so every
 *    thread can read it without synchronization.
 *  - winner is an AtomicReference, set exactly once via compareAndSet. If
 *    two players both submit a fully-correct guess "at the same time",
 *    only the thread whose CAS succeeds is declared the winner - the loser
 *    of that race gets RACE_OVER instead of WON, deterministically, with
 *    no lock needed.
 *  - attemptsByPlayer / guessLogByPlayer are ConcurrentHashMaps (with
 *    per-player CopyOnWriteArrayList/AtomicInteger values) so concurrent
 *    guesses from different players never corrupt the shared leaderboard.
 *
 * There's no long-lived per-player thread to interrupt here (guesses
 * arrive as discrete HTTP requests, not a blocking loop) - cooperative
 * cancellation instead means every submitGuess/getStatus call checks the
 * winner reference first, so a player is told the race is over on their
 * very next guess or status poll rather than being allowed to "win" late.
 */
public class RaceSession {
    private final String raceId;
    private final String secretNumber;
    private final int maxAttemptsPerPlayer;

    private final AtomicReference<String> winner = new AtomicReference<>(null);
    private final Map<String, AtomicInteger> attemptsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, List<GuessResult>> guessLogByPlayer = new ConcurrentHashMap<>();

    public RaceSession(String raceId, String secretNumber, int maxAttemptsPerPlayer) {
        this.raceId = raceId;
        this.secretNumber = secretNumber;
        this.maxAttemptsPerPlayer = maxAttemptsPerPlayer;
    }

    public void join(String playerName) {
        attemptsByPlayer.putIfAbsent(playerName, new AtomicInteger(0));
        guessLogByPlayer.putIfAbsent(playerName, new CopyOnWriteArrayList<>());
    }

    public RaceOutcome submitGuess(String playerName, String guess) {
        if (!attemptsByPlayer.containsKey(playerName)) {
            throw new IllegalArgumentException("Player has not joined this race: " + playerName);
        }

        String currentWinner = winner.get();
        if (currentWinner != null) {
            return raceOverOutcome(playerName, currentWinner);
        }

        GuessScorer.validateFormat(guess, secretNumber.length());

        GuessResult result = GuessScorer.score(secretNumber, guess);
        guessLogByPlayer.get(playerName).add(result);
        int attemptsUsed = attemptsByPlayer.get(playerName).incrementAndGet();

        if (result.deadCount() == secretNumber.length()) {
            boolean iWon = winner.compareAndSet(null, playerName);
            String declaredWinner = iWon ? playerName : winner.get();
            String status = iWon ? "WON" : "RACE_OVER";
            return new RaceOutcome(result.deadCount(), result.woundedCount(), attemptsUsed, maxAttemptsPerPlayer,
                    status, secretNumber, declaredWinner);
        }

        currentWinner = winner.get();
        if (currentWinner != null) {
            return raceOverOutcome(playerName, currentWinner);
        }

        if (attemptsUsed >= maxAttemptsPerPlayer) {
            return new RaceOutcome(result.deadCount(), result.woundedCount(), attemptsUsed, maxAttemptsPerPlayer,
                    "LOST", null, null);
        }

        return new RaceOutcome(result.deadCount(), result.woundedCount(), attemptsUsed, maxAttemptsPerPlayer,
                "IN_PROGRESS", null, null);
    }

    private RaceOutcome raceOverOutcome(String playerName, String winnerName) {
        int attemptsUsed = attemptsByPlayer.get(playerName).get();
        return new RaceOutcome(0, 0, attemptsUsed, maxAttemptsPerPlayer, "RACE_OVER", secretNumber, winnerName);
    }

    public RaceStatus getStatus() {
        String currentWinner = winner.get();
        List<RaceStatus.PlayerStanding> leaderboard = new ArrayList<>();
        for (Map.Entry<String, AtomicInteger> entry : attemptsByPlayer.entrySet()) {
            leaderboard.add(new RaceStatus.PlayerStanding(entry.getKey(), entry.getValue().get()));
        }
        return new RaceStatus(raceId, secretNumber.length(), maxAttemptsPerPlayer, currentWinner,
                currentWinner != null ? secretNumber : null, leaderboard);
    }

    public String getRaceId() {
        return raceId;
    }

    public int getSecretLength() {
        return secretNumber.length();
    }

    public int getMaxAttemptsPerPlayer() {
        return maxAttemptsPerPlayer;
    }

    public boolean hasPlayer(String playerName) {
        return attemptsByPlayer.containsKey(playerName);
    }
}
