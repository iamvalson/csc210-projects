package com.com.CSC210.Ayogame;

public class AyoBoard {
    private final int[] pits = new int[12];
    private int score1 = 0;
    private int score2 = 0;
    private int turn = 1; // 1 for Player 1, 2 for Player 2
    private boolean gameOver = false;
    private AyoGUI gui;

    public AyoBoard() {
        // Initialize 12 pits with 4 seeds each
        for (int i = 0; i < 12; i++) {
            pits[i] = 4;
        }
    }

    public synchronized void setGUI(AyoGUI gui) {
        this.gui = gui;
        if (gui != null) {
            gui.updateDisplay(pits.clone(), score1, score2, turn, gameOver);
        }
    }

    public synchronized void playTurn(int playerNum, int chosenPit) throws InterruptedException {
        // Guard condition: wait for current player's turn or game end
        while (turn != playerNum && !gameOver) {
            wait();
        }

        if (gameOver) return;

        // Validate choice
        if (!isValidChoice(playerNum, chosenPit)) {
            return;
        }

        System.out.println("\n--- Player " + playerNum + "'s Turn (Selected Pit: " + chosenPit + ") ---");

        // Movement & Sowing
        int seeds = pits[chosenPit];
        pits[chosenPit] = 0;
        int currentIdx = chosenPit;

        while (seeds > 0) {
            currentIdx = (currentIdx + 1) % 12;
            pits[currentIdx]++;
            seeds--;
        }

        // Capture Logic
        boolean isOpponentSide = (playerNum == 1) ? (currentIdx >= 6 && currentIdx <= 11) : (currentIdx >= 0 && currentIdx <= 5);
        if (isOpponentSide && (pits[currentIdx] == 2 || pits[currentIdx] == 3)) {
            int captured = pits[currentIdx];
            pits[currentIdx] = 0;
            if (playerNum == 1) score1 += captured;
            else score2 += captured;
            System.out.println(">>> Player " + playerNum + " captured " + captured + " seeds at pit " + currentIdx + "!");
        }

        displayBoard();

        // The next player is the one whose side may now be empty.
        turn = (playerNum == 1) ? 2 : 1;

        // Check win conditions after advancing the turn.
        checkGameOver();

        // Update connected GUI if present
        if (gui != null) {
            gui.updateDisplay(pits.clone(), score1, score2, turn, gameOver);
        }

        // Wake the next player (or both players when the game has ended).
        notifyAll();
    }

    public synchronized boolean waitForTurn(int playerNum) throws InterruptedException {
        while (turn != playerNum && !gameOver) {
            wait();
        }
        return !gameOver;
    }

    public synchronized boolean hasSeeds(int pitIdx) {
        return pitIdx >= 0 && pitIdx < pits.length && pits[pitIdx] > 0;
    }

    private boolean isValidChoice(int playerNum, int pitIdx) {
        if (pitIdx < 0 || pitIdx > 11) return false;
        if (playerNum == 1 && (pitIdx < 0 || pitIdx > 5)) return false;
        if (playerNum == 2 && (pitIdx < 6 || pitIdx > 11)) return false;
        return pits[pitIdx] > 0;
    }

    private void checkGameOver() {
        if (score1 >= 25 || score2 >= 25) {
            gameOver = true;
            return;
        }

        int sumP1 = 0, sumP2 = 0;
        for (int i = 0; i < 6; i++) sumP1 += pits[i];
        for (int i = 6; i < 12; i++) sumP2 += pits[i];

        if ((turn == 1 && sumP1 == 0) || (turn == 2 && sumP2 == 0)) {
            gameOver = true;
            score1 += sumP1;
            score2 += sumP2;
        }
    }

    public synchronized boolean isGameOver() {
        return gameOver;
    }

    public synchronized void displayBoard() {
        System.out.println("----------------------------------------");
        System.out.print("P2 Pits (11-6): ");
        for (int i = 11; i >= 6; i--) System.out.print("[" + pits[i] + "] ");
        System.out.println("\n                P2 Score: " + score2);
        System.out.println("                P1 Score: " + score1);
        System.out.print("P1 Pits (0-5) : ");
        for (int i = 0; i < 6; i++) System.out.print("[" + pits[i] + "] ");
        System.out.println("\n----------------------------------------");
    }

    public synchronized void announceWinner() {
        System.out.println("\n================ GAME OVER ================");
        System.out.println("Final Scores -> Player 1: " + score1 + " | Player 2: " + score2);
        if (score1 > score2) System.out.println("Winner: Player 1!");
        else if (score2 > score1) System.out.println("Winner: Player 2!");
        else System.out.println("Result: It's a Draw!");
        System.out.println("===========================================");
    }
}
