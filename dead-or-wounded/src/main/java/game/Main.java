package game;

import game.api.HttpApiServer;
import game.server.GameServer;
import game.server.ServerConfig;

public class Main {
    public static void main(String[] args) {
        ServerConfig config = ServerConfig.fromArgsOrDefaults(args);
        GameServer server = new GameServer(config);
        HttpApiServer httpApiServer = new HttpApiServer(server.getController(), config.getHttpPort());

        try {
            httpApiServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(httpApiServer::stop, "http-shutdown-hook"));

            server.start();
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            throw new RuntimeException("Failed to start server", e);
        }
    }
}
