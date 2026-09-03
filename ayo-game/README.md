# Concurrent Ayo Game

A Java Swing implementation of Ayo, built for CSC 210. Two automated player threads share one board, take turns safely, and update the graphical interface as the game progresses.

## Features

- 12 pits, initialized with four seeds each
- Concurrent Player 1 and Player 2 agents
- Synchronized turn handling and board updates
- Seed sowing, captures, scoring, and game-over detection
- Swing user interface with live board and score updates

## Run the GUI

From the `ayo-game` directory:

```bash
javac -d out src/com/com/CSC210/Ayogame/*.java
java -cp out com.com.CSC210.Ayogame.Main
```

## Run a fast terminal test

```bash
java -Djava.awt.headless=true -Dayo.turnDelayMillis=0 -cp out com.com.CSC210.Ayogame.Main
```

The optional `ayo.turnDelayMillis` property controls the delay between automated turns; it defaults to 800 milliseconds for the GUI.
