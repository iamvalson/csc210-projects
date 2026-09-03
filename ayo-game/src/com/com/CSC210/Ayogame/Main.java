package com.com.CSC210.Ayogame;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        System.out.println("Initializing Concurrent Ayo Game with GUI...");

        // 1. Create shared monitor object on Heap
        AyoBoard sharedBoard = new AyoBoard();

        // 2. Launch GUI window and register it with the monitor
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                AyoGUI gui = new AyoGUI();
                sharedBoard.setGUI(gui);
            });
        } else {
            System.out.println("Headless environment detected; running without the GUI.");
        }

        // 3. Create player threads
        Thread player1Thread = new Thread(new PlayerAgent(sharedBoard, 1), "Thread-Player1");
        Thread player2Thread = new Thread(new PlayerAgent(sharedBoard, 2), "Thread-Player2");

        // 4. Start concurrent execution
        player1Thread.start();
        player2Thread.start();

        // 5. Wait for game threads to complete
        try {
            player1Thread.join();
            player2Thread.join();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted.");
        }

        // 6. Console final result announcement
        sharedBoard.announceWinner();
    }
}
