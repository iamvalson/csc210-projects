package game.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import game.api.GameController;
import game.core.GameSessionManager;

public class GameServer {
    private final ServerConfig config;
    private final ExecutorService clientPool;
    private final GameSessionManager sessionManager;
    private final GameController controller;

    private volatile boolean running = false;
    private ServerSocket serverSocket;


    public GameServer(ServerConfig config) {
        this.config = config;
        this.clientPool = Executors.newFixedThreadPool(config.getThreadPoolSize());
        this.sessionManager = new GameSessionManager();
        this.controller = new GameController(sessionManager, config);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        running = true;
        System.out.println("Server started on port " + config.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown-hook"));

        while (running) {
            try{
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
            clientPool.submit(new ClientHandler(clientSocket, controller));
            }
            catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    public GameController getController() {
        return controller;
    }

    public synchronized void stop() {
        if(!running) return;
        running = false;

        System.out.println("Shutting down server...");

        try{
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        clientPool.shutdownNow();
        sessionManager.shutdown();
    }
}
