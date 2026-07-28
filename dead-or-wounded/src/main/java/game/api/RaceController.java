package game.api;

import game.api.dto.RaceView;
import game.core.RaceOutcome;
import game.core.RaceSession;
import game.core.RaceSessionManager;
import game.core.RaceStatus;
import game.server.ServerConfig;

public class RaceController {
    private final RaceSessionManager raceManager;
    private final ServerConfig config;

    public RaceController(RaceSessionManager raceManager, ServerConfig config) {
        this.raceManager = raceManager;
        this.config = config;
    }

    public RaceView createRace(String playerName) {
        RaceSession race = raceManager.createRace(config.getSecretNumberLength(), config.getMaxAttempts());
        race.join(playerName);
        return new RaceView(race.getRaceId(), playerName, race.getSecretLength(), race.getMaxAttemptsPerPlayer());
    }

    public RaceView joinRace(String raceId, String playerName) {
        RaceSession race = raceManager.getRace(raceId);
        race.join(playerName);
        return new RaceView(race.getRaceId(), playerName, race.getSecretLength(), race.getMaxAttemptsPerPlayer());
    }

    public RaceOutcome submitGuess(String raceId, String playerName, String guess) {
        RaceSession race = raceManager.getRace(raceId);
        return race.submitGuess(playerName, guess);
    }

    public RaceStatus getStatus(String raceId) {
        RaceSession race = raceManager.getRace(raceId);
        return race.getStatus();
    }
}
