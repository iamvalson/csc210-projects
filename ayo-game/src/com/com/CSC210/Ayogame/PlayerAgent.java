package com.com.CSC210.Ayogame;

import java.util.Random;

public class PlayerAgent implements Runnable {
    private static final long TURN_DELAY_MS = Math.max(0L, Long.getLong("ayo.turnDelayMillis", 800L));
    private final AyoBoard board;
    private final int playerNum;
    private final Random random = new Random();

    public PlayerAgent(AyoBoard board, int playerNum) {
        this.board = board;
        this.playerNum = playerNum;
    }

    @Override
    public void run() {
        while (!board.isGameOver()) {
            try {
                if (!board.waitForTurn(playerNum)) {
                    break;
                }

                int chosenPit = chooseRandomValidMove();
                if (chosenPit != -1) {
                    board.playTurn(playerNum, chosenPit);
                }
                // Delay between turns so moves remain visible in the GUI.
                Thread.sleep(TURN_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private int chooseRandomValidMove() {
        int start = (playerNum == 1) ? 0 : 6;
        int[] playablePits = new int[6];
        int playableCount = 0;

        for (int pit = start; pit < start + 6; pit++) {
            if (board.hasSeeds(pit)) {
                playablePits[playableCount++] = pit;
            }
        }

        return playableCount == 0 ? -1 : playablePits[random.nextInt(playableCount)];
    }
}
