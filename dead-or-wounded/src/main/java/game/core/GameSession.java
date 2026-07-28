package game.core;

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
            GuessScorer.validateFormat(guess, secretNumber.length());

            GuessResult result = GuessScorer.score(secretNumber, guess);
            history.add(result);

            if (result.deadCount() == secretNumber.length()) {
                status = Status.WON;
            } else if (history.size() >= maxAttempts) {
                status = Status.LOST;
            }

            return result;
        } finally {
            lock.unlock();
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
    public String getSecretNumber() {
        return secretNumber;
    }
    public Player getPlayer() {
        return player;
    }
    public int getMaxAttempts() {
        return maxAttempts;
    }
    public int getSecretLength() {return secretNumber.length();}
}
