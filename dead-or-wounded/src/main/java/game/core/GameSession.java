package game.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class GameSession {
    public enum Status {IN_PROGRESS, WON, LOST}

    private final String sessionId;
    private final Player player;
    private final String secretNumber;
    private final int maxAttempts;
    private final List<GuessResult> history = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private Status status = Status.IN_PROGRESS;

    public GameSession(String sessionId, Player player, String secretNumber, int maxAttempts) {
        this.sessionId = sessionId;
        this.player = player;
        this.secretNumber = secretNumber;
        this.maxAttempts = maxAttempts;
    }


    public GuessResult submitGuess(String guess) {
        lock.lock();
        try{
            if(status != Status.IN_PROGRESS) {
                throw new IllegalStateException("Game session is not in progress." + status);
            }
            validateGuessFormat(guess);

            int dead = 0;
            int wounded = 0;
            for (int i = 0; i < secretNumber.length(); i++){
                char g = guess.charAt(i);
                if (g == secretNumber.charAt(i)) {
                    dead++;
                } else if (secretNumber.indexOf(g) != -1) {
                    wounded++;
                }
            }

            GuessResult result = new GuessResult(guess, dead, wounded, Instant.now());
            history.add(result);

            if (dead == secretNumber.length()) {
                status = Status.WON;
            } else if (history.size() >= maxAttempts) {
                status = Status.LOST;
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    private void validateGuessFormat(String guess) {
        if (guess == null || guess.length() != secretNumber.length() || !guess.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid guess format. Guess must be a numeric string of length " + secretNumber.length());
        }

        boolean[] seen = new boolean[10];
        for (char c: guess.toCharArray()) {
            int digit = c - '0';
            if (seen[digit]) {
                throw new IllegalArgumentException("Invalid guess format. Guess must not contain duplicate digits.");
            }
            seen[digit] = true;
        }
    }

    public List<GuessResult> getHistorySnapshot() {
        lock.lock();
        try{
            return Collections.unmodifiableList(new ArrayList<>(history));
        } finally {
            lock.unlock();
        }
    }

    public Status getStatus () {
        lock.lock();
        try{
            return status;
        } finally {
            lock.unlock();
        }
    }

    public String getSessionId() {
        return sessionId;
    }
    public Player getPlayer() {
        return player;
    }
    public int getMaxAttempts() {
        return maxAttempts;
    }
    public int getSecretLength() {return secretNumber.length();}
}
